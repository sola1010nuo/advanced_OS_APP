package com.example.petcollection

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

class CollectionPersistenceTest {
    @Test fun monkeyUnlockAndChosenCoverSurviveDatabaseReopenAndDeleteLastEncounter() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "monkey-test-" + UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, CollectionDatabase::class.java, name).build()
        var db = open()
        var repository = EncounterRepository(db.encounters(), context)
        val fixture = File(context.cacheDir, "monkey-" + UUID.randomUUID() + ".png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val catalog = MockBreedRepository(context.resources).getCollectionEntries()
            val monkey = catalog.first { it.id == MockBreedRepository.MONKEY_ID }
            assertTrue(monkey.isCollectible)
            assertNull(monkey.species)
            val total = CollectionData().statistics(catalog).totalBreeds
            val (first, isFirst) = repository.create(EncounterRecord(monkey.id, Uri.fromFile(fixture).toString()))
            assertTrue(isFirst)
            assertEquals(first.id, db.encounters().cover(monkey.id)!!.encounterId)
            val (second, repeated) = repository.create(EncounterRecord(monkey.id, Uri.fromFile(fixture).toString()))
            assertFalse(repeated)
            repository.selectCover(monkey.id, second.id)
            db.close()
            db = open()
            repository = EncounterRepository(db.encounters(), context)
            var snapshot = repository.collection.first()
            assertTrue(snapshot.applyTo(monkey).isVisibleInCollection)
            assertEquals(1, snapshot.statistics(catalog).discovered)
            assertEquals(total, snapshot.statistics(catalog).totalBreeds)
            assertEquals(2, snapshot.applyTo(monkey).encounterCount)
            assertEquals(second.id, snapshot.applyTo(monkey).coverPhoto!!.id)
            repository.delete(second.id)
            assertEquals(first.id, db.encounters().cover(monkey.id)!!.encounterId)
            repository.delete(first.id)
            snapshot = repository.collection.first { it.encounters.isEmpty() }
            assertFalse(snapshot.applyTo(monkey).isVisibleInCollection)
            assertEquals(0, snapshot.statistics(catalog).discovered)
            assertEquals(total, snapshot.statistics(catalog).totalBreeds)
            assertNull(snapshot.applyTo(monkey).coverPhoto)
        } finally {
            repository.collection.first().encounters.forEach { repository.delete(it.id) }
            db.close()
            context.deleteDatabase(name)
            fixture.delete()
        }
    }
    @Test fun reclassificationUpdatesRoomStatisticsAndRepairsCovers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        try {
            val repository = EncounterRepository(db.encounters(), context)
            val catalog = MockBreedRepository(context.resources).getCollectionEntries()
            val first = EncounterRecord("corgi", "content://fixture/first", id = "first")
            val second = EncounterRecord("corgi", "content://fixture/second", id = "second")
            db.encounters().create(first)
            db.encounters().create(second)
            assertEquals(1, repository.collection.first().statistics(catalog, Species.DOG).discovered)
            repository.update(first.copy(breedId = "unregistered"))
            var snapshot = repository.collection.first { it.forBreed("unregistered").size == 1 }
            assertEquals(1, snapshot.statistics(catalog, Species.DOG).discovered)
            assertEquals(second.id, db.encounters().cover("corgi")!!.encounterId)
            assertEquals(first.id, db.encounters().cover("unregistered")!!.encounterId)
            repository.update(second.copy(breedId = "other_dog"))
            snapshot = repository.collection.first { it.forBreed("corgi").isEmpty() }
            assertEquals(0, snapshot.statistics(catalog, Species.DOG).discovered)
            assertEquals(1, snapshot.statistics(catalog, Species.DOG).encounterCount)
            assertEquals(2, snapshot.statistics(catalog).encounterCount)
            assertNull(db.encounters().cover("corgi"))
        } finally {
            db.close()
        }
    }

    @Test fun scenariosAToLCrudCoversCategoriesAndDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "collection-test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CollectionDatabase::class.java, name).build()
        var db = open()
        var repository = EncounterRepository(db.encounters(), context)
        val fixture = File(context.cacheDir, "photo-${UUID.randomUUID()}.png")
        val image = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        fixture.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        try {
            val catalog = MockBreedRepository(context.resources).getCollectionEntries()
            val corgi = catalog.first { it.id == "corgi" }
            val uri = Uri.fromFile(fixture).toString()
            val (first, isFirst) = repository.create(EncounterRecord("corgi", uri, petName = "First"))
            assertTrue(isFirst)
            var snapshot = repository.collection.first { it.forBreed("corgi").size == 1 }
            assertTrue(snapshot.applyTo(corgi).isDiscovered)
            assertEquals(1, snapshot.discoveredCount(MockBreedRepository.normalBreedIds))
            val (second, isSecondFirst) = repository.create(EncounterRecord("corgi", uri, petName = "Second"))
            assertFalse(isSecondFirst)
            snapshot = repository.collection.first { it.forBreed("corgi").size == 2 }
            assertEquals(2, snapshot.applyTo(corgi).encounterCount)
            assertEquals(1, snapshot.discoveredCount(MockBreedRepository.normalBreedIds))
            assertEquals(first, db.encounters().get(first.id))
            assertEquals(2, db.encounters().observeByBreed("corgi").first().size)
            assertEquals(2, db.encounters().observeCounts().first().single().count)
            assertEquals(listOf("corgi"), db.encounters().observeDiscovered(MockBreedRepository.normalBreedIds.toList()).first())

            val edited = first.copy(petName = "Edited", encounteredAt = LocalDateTime.of(2026, 10, 8, 9, 42),
                location = "Park", note = "Friendly")
            repository.update(edited)
            repository.selectCover("corgi", second.id)
            fixture.delete() // External session-only input is gone; the stored private reference must remain readable.
            db.close()
            db = open()
            repository = EncounterRepository(db.encounters(), context)
            assertEquals(edited, db.encounters().get(first.id))
            assertEquals(second.id, db.encounters().cover("corgi")!!.encounterId)
            context.contentResolver.openInputStream(Uri.parse(first.photoUri)).use { assertNotNull(it) }
            assertEquals(first.createdAt, db.encounters().get(first.id)!!.createdAt)

            // Delete a non-cover row: preserve the chosen cover.
            val extraSource = File(context.cacheDir, "extra-${UUID.randomUUID()}.png")
            File(Uri.parse(second.photoUri).path!!).copyTo(extraSource)
            val (extra, _) = repository.create(EncounterRecord("corgi", Uri.fromFile(extraSource).toString()))
            extraSource.delete()
            repository.delete(extra.id)
            assertEquals(second.id, db.encounters().cover("corgi")!!.encounterId)
            // Delete selected cover: fall back to the remaining photo and remain discovered.
            repository.delete(second.id)
            assertEquals(1, db.encounters().count("corgi"))
            assertEquals(first.id, db.encounters().cover("corgi")!!.encounterId)
            snapshot = repository.collection.first { it.forBreed("corgi").size == 1 }
            assertTrue(snapshot.applyTo(corgi).isDiscovered)

            for (id in listOf("other_cat", "other_dog", "unregistered")) {
                val source = File(context.cacheDir, "special-${UUID.randomUUID()}.png")
                File(Uri.parse(first.photoUri).path!!).copyTo(source)
                val (record, _) = repository.create(EncounterRecord(id, Uri.fromFile(source).toString()))
                source.delete()
                repository.update(record.copy(petName = id, location = "Place", note = "Note"))
                assertEquals(id, db.encounters().get(record.id)!!.petName)
                snapshot = repository.collection.first { it.forBreed(id).size == 1 }
                assertEquals(1, snapshot.discoveredCount(MockBreedRepository.normalBreedIds))
                assertFalse(catalog.first { it.id == id }.isCollectible)
                repository.delete(record.id)
                assertEquals(0, db.encounters().count(id))
                assertNull(db.encounters().cover(id))
            }
            repository.delete(first.id)
            snapshot = repository.collection.first { it.encounters.isEmpty() }
            assertFalse(snapshot.applyTo(corgi).isDiscovered)
            assertNull(snapshot.applyTo(corgi).coverPhoto)
            assertNull(db.encounters().cover("corgi"))
            assertEquals(0, snapshot.discoveredCount(MockBreedRepository.normalBreedIds))
            assertEquals(22, catalog.count { it.isCollectible && it.species == Species.CAT })
            assertEquals(34, catalog.count { it.isCollectible && it.species == Species.DOG })
        } finally {
            db.close()
            context.deleteDatabase(name)
            fixture.delete()
        }
    }
}
