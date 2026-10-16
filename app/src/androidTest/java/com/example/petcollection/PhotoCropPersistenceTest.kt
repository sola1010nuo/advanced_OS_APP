package com.example.petcollection

import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import com.example.petcollection.ui.collection.CollectionUnlockState
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class PhotoCropPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun await(condition: () -> Boolean) = withTimeout(30000) { while (!condition()) delay(20) }

    @Test fun freePortraitAndLandscapeUseTheConfirmedPixelsForRealModelsAndCollection() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val state = CollectionUnlockState(EncounterRepository(db.encounters(), context))
        val original = PhotoCropTestFixtures.original(context)
        val originalBytes = original.readBytes()
        val files = mutableListOf<File>()
        try {
            state.collection.first { it.loaded }
            for (region in listOf(CropRegion(.55f, .1f, .25f, .8f), CropRegion(.55f, .2f, .4f, .4f))) {
                withContext(Dispatchers.Main) { state.photoInput.begin(Uri.fromFile(original)) }
                await { state.photoInput.source != null }
                assertEquals(CropMode.FREE, state.photoInput.mode)
                val source = requireNotNull(state.photoInput.source)
                val crop = SquareCrop.fromRegion(region, source.bitmap.width, source.bitmap.height)
                val expected = PhotoCropIO.render(source.bitmap, crop)
                withContext(Dispatchers.Main) { state.photoInput.confirm(crop) }
                await { state.pendingEncounter != null || state.photoInput.error }
                assertFalse(state.photoInput.error)
                assertTrue(expected.sameAs(state.photoInput.recognition.bitmap))
                val draft = requireNotNull(state.pendingEncounter)
                withContext(Dispatchers.Main) { state.editEncounter(); state.saveEncounter(draft) }
                await { !state.isWorking && state.pendingEncounter == null }
                val saved = state.collection.first { it.encounters.any { row -> row.id == draft.id } }.encounters.first { it.id == draft.id }
                val file = File(requireNotNull(Uri.parse(saved.photoUri).path)).also(files::add)
                val decoded = BitmapFactory.decodeFile(file.path)
                assertTrue(expected.sameAs(decoded))
                assertEquals(draft.encounteredAt, saved.encounteredAt)
                assertEquals(draft.latitude, saved.latitude)
                decoded.recycle(); expected.recycle()
            }
            assertArrayEquals(originalBytes, original.readBytes())
        } finally {
            withContext(Dispatchers.Main) { ViewModelStore().apply { put("free", state); clear() } }
            db.close(); original.delete(); files.forEach { it.delete() }
        }
    }

    @Test fun realRecognitionAndEveryAlbumKeepOnlyTheCropWithOriginalMetadataAfterRestart() = runBlocking {
        val name = "crop-persistence-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CollectionDatabase::class.java, name).build()
        var db = open()
        var repo = EncounterRepository(db.encounters(), context)
        val state = CollectionUnlockState(repo)
        val original = PhotoCropTestFixtures.original(context)
        val before = original.readBytes()
        var savedFile: File? = null
        try {
            state.collection.first { it.loaded }
            withContext(Dispatchers.Main) { state.photoInput.begin(Uri.fromFile(original)) }
            await { state.photoInput.source != null }
            assertNull(state.pendingEncounter)
            assertNull(state.photoInput.recognition.bitmap)
            withContext(Dispatchers.Main) { state.photoInput.confirm(SquareCrop(.75f, .5f, 2f, 4f / 3f)) }
            await { state.pendingEncounter != null || state.photoInput.error }
            assertFalse(state.photoInput.error)
            val draft = requireNotNull(state.pendingEncounter)
            val temp = File(requireNotNull(Uri.parse(draft.photoUri).path))
            val cropBytes = temp.readBytes()
            assertNotEquals(Uri.fromFile(original).toString(), draft.photoUri)
            val recognizedBitmap = requireNotNull(state.photoInput.recognition.bitmap)
            assertEquals(160, recognizedBitmap.width)
            assertEquals(120, recognizedBitmap.height)
            val exportedBitmap = BitmapFactory.decodeFile(temp.path)
            assertTrue(exportedBitmap.sameAs(recognizedBitmap))
            exportedBitmap.recycle()
            assertTrue(Color.blue(recognizedBitmap.getPixel(60, 60)) > 240)
            assertEquals(LocalDateTime.of(2021, 6, 7, 8, 9, 10), draft.encounteredAt)
            assertEquals("+08:00", draft.timeZoneId)
            assertEquals(25.0416667, requireNotNull(draft.latitude), .00001)
            assertEquals(121.5, requireNotNull(draft.longitude), .00001)
            withContext(Dispatchers.Main) { state.editEncounter(); state.saveEncounter(draft) }
            await { !state.isWorking && state.pendingEncounter == null }
            val saved = state.collection.first { it.encounters.size == 1 }.encounters.single()
            assertEquals(draft.encounteredAt, saved.encounteredAt)
            assertEquals(draft.timeZoneId, saved.timeZoneId)
            assertEquals(draft.latitude, saved.latitude)
            assertEquals(draft.longitude, saved.longitude)
            savedFile = File(requireNotNull(Uri.parse(saved.photoUri).path))
            assertTrue(savedFile.canonicalPath.startsWith(File(context.filesDir, "encounter_photos").canonicalPath))
            assertArrayEquals(cropBytes, savedFile.readBytes())
            assertFalse(temp.exists())
            assertArrayEquals(before, original.readBytes())
            val catalog = MockBreedRepository(context.resources).getCollectionEntries()
            assertEquals(saved.photoUri, state.collection.value.applyTo(catalog.first { it.id == saved.breedId }).collectedPhotos.single().uri)
            // Moving the same cropped photo into Other/custom albums preserves bytes and source metadata.
            repo.update(saved.copy(breedId = "unregistered"))
            val album = repo.createAlbum("Cropped photos")
            repo.movePhotos(listOf(saved.id), album)
            val store = ViewModelStore(); store.put("crop", state); withContext(Dispatchers.Main) { store.clear() }
            db.close()
            db = open(); repo = EncounterRepository(db.encounters(), context)
            val restored = repo.collection.first { it.loaded }
            val record = restored.encounters.single()
            assertEquals(saved.photoUri, record.photoUri)
            assertEquals(saved.encounteredAt, record.encounteredAt)
            assertEquals("+08:00", record.timeZoneId)
            assertEquals(LocalDateTime.of(2021, 6, 7, 8, 9, 10), record.encounteredAt)
            assertEquals(25.0416667, requireNotNull(record.latitude), .00001)
            assertEquals(121.5, requireNotNull(record.longitude), .00001)
            assertEquals(saved.latitude, record.latitude)
            assertEquals(saved.longitude, record.longitude)
            assertEquals(album, restored.members.single().albumId)
            assertArrayEquals(cropBytes, savedFile.readBytes())
            val decoded = BitmapFactory.decodeFile(savedFile.path)
            assertEquals(160, decoded.width)
            assertEquals(120, decoded.height)
            assertTrue(decoded.sameAs(recognizedBitmap))
            decoded.recycle()
        } finally {
            val store = ViewModelStore(); store.put("crop", state); withContext(Dispatchers.Main) { store.clear() }
            db.close(); context.deleteDatabase(name); original.delete(); savedFile?.delete()
        }
    }

    @Test fun cancellingCropResultAndEditorLeavesNoPhotosOrEncounters() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val state = CollectionUnlockState(EncounterRepository(db.encounters(), context))
        val original = PhotoCropTestFixtures.original(context, exif = false)
        try {
            state.collection.first { it.loaded }
            withContext(Dispatchers.Main) { state.photoInput.begin(Uri.fromFile(original)) }
            await { state.photoInput.source != null }
            withContext(Dispatchers.Main) { state.photoInput.discard() }
            assertNull(state.pendingEncounter)
            assertNull(state.photoInput.recognition.bitmap)
            for (editor in listOf(false, true)) {
                withContext(Dispatchers.Main) { state.photoInput.begin(Uri.fromFile(original)) }
                await { state.photoInput.source != null }
                withContext(Dispatchers.Main) { state.photoInput.confirm(SquareCrop()) }
                await { state.pendingEncounter != null || state.photoInput.error }
                val draft = requireNotNull(state.pendingEncounter)
                assertNull(draft.encounteredAt)
                val temp = File(requireNotNull(Uri.parse(draft.photoUri).path))
                assertTrue(temp.exists())
                withContext(Dispatchers.Main) { if (editor) state.editEncounter(); state.dismissEncounter() }
                assertFalse(temp.exists())
                assertNull(state.photoInput.displayedUri)
                assertTrue(db.encounters().allRecords().isEmpty())
            }
        } finally {
            val store = ViewModelStore(); store.put("crop", state); withContext(Dispatchers.Main) { store.clear() }
            db.close(); original.delete()
        }
    }
}
