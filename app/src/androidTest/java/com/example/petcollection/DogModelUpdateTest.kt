package com.example.petcollection

import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

class DogModelUpdateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun labels() = context.assets.open("dog_labels.txt").bufferedReader().use { it.readLines() }

    @Test fun newModelContractAndAll36LabelsMatchBothLanguages() {
        assertEquals(36, labels().size)
        val bytes = context.assets.open("dog_model.tflite").use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes).rewind()
        Interpreter(buffer).use { model ->
            assertEquals(DataType.FLOAT32, model.getInputTensor(0).dataType())
            assertEquals(DataType.FLOAT32, model.getOutputTensor(0).dataType())
            assertArrayEquals(intArrayOf(1, 224, 224, 3), model.getInputTensor(0).shape())
            assertArrayEquals(intArrayOf(1, 36), model.getOutputTensor(0).shape())
        }
        for (language in listOf("zh-Hant", "en")) {
            val resources = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language))
            }).resources
            val catalog = MockBreedRepository(resources).getCollectionEntries()
            val activeDogs = catalog.filter { it.species == Species.DOG && it.isCollectible }
            assertEquals(labels().map(ModelBreedMapping::canonicalId).toSet(), activeDogs.map { it.id }.toSet())
            labels().forEach { label ->
                val breed = requireNotNull(ModelBreedMapping.resolve(label, Species.DOG, catalog))
                assertNotNull(breed.rarity)
                assertTrue(listOf(breed.name, breed.description, breed.origin, breed.personality, breed.size, breed.coat).all { it.isNotBlank() })
                val accepted = RecognitionPolicy.route(AnimalPrediction(AnimalCategory.DOG, .99f), catalog) { label to .75f }
                assertEquals(breed.id, accepted.breedId)
                assertEquals("other_dog", RecognitionPolicy.route(AnimalPrediction(AnimalCategory.DOG, .99f), catalog) { label to .749f }.breedId)
            }
            assertEquals(66, CollectionData().statistics(catalog).totalBreeds)
            assertEquals(36, CollectionData().statistics(catalog, Species.DOG).totalBreeds)
            assertEquals(29, CollectionData().statistics(catalog, Species.CAT).totalBreeds)
            catalog.filter { it.isCoatCategory || it.isSecretMonkey || !it.showsDiscoveryState }.forEach { assertNull(it.rarity) }
        }
        val image = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        try {
            image.eraseColor(android.graphics.Color.rgb(255, 128, 0))
            val rgb = BreedClassifier.rgbInput(image, 1, 1)
            assertEquals(255f, rgb.float, 0f)
            assertEquals(128f, rgb.float, 0f)
            assertEquals(0f, rgb.float, 0f)
            BreedClassifier(context, BreedClassifier.AnimalType.DOG).use { model ->
                val result = model.classify(image)
                assertTrue(result.breed in labels())
                assertTrue(result.confidence.isFinite() && result.confidence in 0f..1f)
            }
        } finally { image.recycle() }
    }

    @Test fun allDogAlbumsAndLegacyRecordsSurviveDatabaseReopen() = runBlocking {
        val name = "dog-update-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CollectionDatabase::class.java, name).build()
        var db = open()
        val fixture = File(context.cacheDir, "dog-update-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val catalog = MockBreedRepository(context.resources).getCollectionEntries()
        val activeIds = labels().map(ModelBreedMapping::canonicalId)
        try {
            val repository = EncounterRepository(db.encounters(), context)
            for (id in activeIds + MockBreedRepository.legacyBreedIds) {
                val (_, first) = repository.create(EncounterRecord(id, Uri.fromFile(fixture).toString(), id = "saved-$id"))
                assertTrue(first)
            }
            val (_, firstRepeat) = repository.create(EncounterRecord("boxer", Uri.fromFile(fixture).toString(), id = "repeat-boxer"))
            assertFalse(firstRepeat)
            repository.selectCover("boxer", "repeat-boxer")
            db.close()
            db = open()
            val loaded = EncounterRepository(db.encounters(), context).collection.first { it.loaded }
            assertEquals(CollectionStatistics(36, 36, 43), loaded.statistics(catalog, Species.DOG))
            assertEquals(36, loaded.statistics(catalog).discovered)
            assertEquals(66, loaded.statistics(catalog).totalBreeds)
            for (id in activeIds) {
                val breed = loaded.applyTo(catalog.first { it.id == id })
                assertTrue(breed.isDiscovered)
                assertNotNull(breed.coverPhoto)
                assertEquals(if (id == "boxer") 2 else 1, breed.collectedPhotos.size)
            }
            assertEquals("repeat-boxer", loaded.applyTo(catalog.first { it.id == "boxer" }).coverPhoto!!.id)
            for (id in MockBreedRepository.legacyBreedIds) {
                assertEquals(1, loaded.forBreed(id).size)
                assertTrue(loaded.applyTo(catalog.first { it.id == id }).isVisibleInCollection)
                val photo = requireNotNull(loaded.applyTo(catalog.first { it.id == id }).coverPhoto?.uri)
                context.contentResolver.openInputStream(Uri.parse(photo)).use { assertTrue(requireNotNull(it).read() >= 0) }
            }
        } finally {
            val repository = EncounterRepository(db.encounters(), context)
            db.encounters().observeAll().first().forEach { repository.delete(it.id) }
            db.close(); context.deleteDatabase(name); fixture.delete()
        }
    }
}
