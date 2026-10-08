package com.example.petcollection

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assert.*
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.data.ModelBreedMapping
import com.example.petcollection.data.Species
import com.example.petcollection.ui.collection.CollectionUnlockState
import com.example.petcollection.ui.home.PhotoRecognitionState
import android.net.Uri
import android.content.res.Configuration
import java.util.Locale
import org.junit.Test

/** Runs the shipped native models rather than mocking classifier output. */
class BreedClassifierInferenceTest {
    @Test fun inspectAnimalModelContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = context.assets.open("cat_dog_model.tflite").use { it.readBytes() }
        val buffer = java.nio.ByteBuffer.allocateDirect(model.size).order(java.nio.ByteOrder.nativeOrder())
        buffer.put(model).rewind()
        org.tensorflow.lite.Interpreter(buffer).use { interpreter ->
            val input = interpreter.getInputTensor(0)
            val output = interpreter.getOutputTensor(0)
            android.util.Log.i("AnimalModelContract", "input=${input.shape().contentToString()} ${input.dataType()} output=${output.shape().contentToString()} ${output.dataType()}")
            assertTrue(input.shape().contentEquals(intArrayOf(1, 224, 224, 3)))
            assertTrue(output.shape().contentEquals(intArrayOf(1, 2)))
        }
    }
    @Test fun everyModelLabelMapsToExistingLocalizedCatalog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (language in listOf("zh-Hant", "en")) {
            val resources = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            ).resources
            val catalog = MockBreedRepository(resources).getBreeds()
            for ((file, species) in listOf("cat_labels.txt" to Species.CAT, "dog_labels.txt" to Species.DOG)) {
                val labels = context.assets.open(file).bufferedReader().use { it.readLines() }
                labels.forEach { label ->
                    val breed = ModelBreedMapping.resolve(label, species, catalog)
                    assertNotNull("Missing mapping for $label in $language", breed)
                    assertEquals(catalog.first { it.id == breed!!.id }.name, breed!!.name)
                }
            }
        }
        assertEquals("corgi", ModelBreedMapping.canonicalId("Corgi"))
        assertEquals("golden_retriever", ModelBreedMapping.canonicalId("Golden_Retriever"))
        assertEquals("german_shorthaired_pointer", ModelBreedMapping.canonicalId("german_shorthaired"))
        assertEquals("soft_coated_wheaten_terrier", ModelBreedMapping.canonicalId("wheaten_terrier"))
    }

    @Test fun automaticUriRecognitionUnlocksOnceAndSavesRepeatPhotoEncounters() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        photo.eraseColor(Color.rgb(180, 120, 60))
        val file = java.io.File(context.cacheDir, "recognition-test.png")
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        photo.recycle()
        val database = androidx.room.Room.inMemoryDatabaseBuilder(
            context, com.example.petcollection.data.CollectionDatabase::class.java
        ).build()
        try {
            val uri = Uri.fromFile(file)
            val catalog = MockBreedRepository(context.resources).getBreeds()
            val state = CollectionUnlockState(com.example.petcollection.data.EncounterRepository(database.encounters(), context))
            val recognition = PhotoRecognitionState()
            recognition.recognize(context, uri, catalog, state)
            assertFalse(recognition.failed)
            assertFalse(recognition.processing)
            assertNotNull(recognition.bitmap)
            val breed = catalog.first { it.id == recognition.breedId }
            assertTrue(state.applyTo(breed).isDiscovered)
            assertTrue(state.showUnlockCelebration)
            assertEquals(uri.toString(), state.pendingEncounter!!.photoUri)
            state.editEncounter()
            state.saveEncounter(state.pendingEncounter!!.copy(petName = "Test pet"))
            recognition.recognize(context, uri, catalog, state)
            assertFalse(recognition.failed)
            assertFalse(state.showUnlockCelebration)
            assertNotNull(state.pendingEncounter)
            state.saveEncounter(state.pendingEncounter!!)
            assertEquals(2, state.applyTo(breed).encounterCount)
            assertEquals(2, state.applyTo(breed).collectedPhotos.size)
            assertTrue(state.encounters.all { it.photoUri == uri.toString() && it.breedId == breed.id })
            assertEquals(1, catalog.count { state.applyTo(it).isDiscovered })
        } finally {
            database.close()
            file.delete()
        }
    }
    @Test fun bothModelsProduceLabelAndProbabilityOffMainThread() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        photo.eraseColor(Color.rgb(180, 120, 60))
        try {
            for (type in BreedClassifier.AnimalType.entries) {
                val result = withContext(Dispatchers.Default) {
                    assertTrue(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
                    val classifier = BreedClassifier(context, type)
                    try { classifier.classify(photo) } finally { classifier.close() }
                }
                val labelsFile = if (type == BreedClassifier.AnimalType.CAT) "cat_labels.txt" else "dog_labels.txt"
                val labels = context.assets.open(labelsFile).bufferedReader().use { it.readLines() }
                assertTrue(result.breed in labels)
                assertTrue(result.confidence.isFinite() && result.confidence in 0f..1f)
            }
        } finally {
            photo.recycle()
        }
    }
}
