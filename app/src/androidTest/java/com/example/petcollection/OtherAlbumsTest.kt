package com.example.petcollection

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class OtherAlbumsTest {
    @Test fun albumsPersistOrderMovePreserveAndDeletePhotos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "albums-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CollectionDatabase::class.java, name)
            .addMigrations(CollectionDatabase.MIGRATION_1_2, CollectionDatabase.MIGRATION_2_3, CollectionDatabase.MIGRATION_3_4, CollectionDatabase.MIGRATION_4_5).build()
        var db = open()
        var repo = EncounterRepository(db.encounters(), context)
        val source = File(context.cacheDir, "album-${UUID.randomUUID()}").apply { writeText("photo fixture") }
        try {
            repo.createAlbum("First")
            repo.createAlbum("Second")
            val first = db.encounters().albums()[0].id
            val second = db.encounters().albums()[1].id
            val (photo, _) = repo.create(EncounterRecord("unregistered", Uri.fromFile(source).toString()))
            assertTrue(repo.collection.first().members.isEmpty())
            repo.movePhotos(listOf(photo.id), first)
            repo.albumCover(first, photo.id)
            assertEquals(photo, db.encounters().get(photo.id))
            assertEquals(photo.id, db.encounters().albums()[0].coverId)
            repo.renameAlbum(first, "Renamed")
            repo.reorderAlbums(listOf(second, first))
            db.close()
            db = open()
            repo = EncounterRepository(db.encounters(), context)
            assertEquals(listOf(second, first), db.encounters().albums().map { it.id })
            assertEquals("Renamed", db.encounters().albums()[1].name)
            assertEquals(photo.id, db.encounters().albums()[1].coverId)
            repo.deleteAlbum(first, false)
            assertEquals(photo, db.encounters().get(photo.id))
            assertTrue(repo.collection.first().members.isEmpty())
            val privatePhoto = File(Uri.parse(photo.photoUri).path!!)
            assertTrue(privatePhoto.exists())
            repo.movePhotos(listOf(photo.id), second)
            repo.unassignPhotos(listOf(photo.id))
            assertEquals(photo, db.encounters().get(photo.id))
            assertTrue(db.encounters().members(second).isEmpty())
            repo.movePhotos(listOf(photo.id), second)
            repo.deleteAlbum(second, true)
            assertNull(db.encounters().get(photo.id))
            assertFalse(privatePhoto.exists())
            assertTrue(db.encounters().albums().isEmpty())
            assertNull(db.encounters().cover("unregistered"))
        } finally {
            repo.collection.first().encounters.forEach { repo.delete(it.id) }
            db.close()
            context.deleteDatabase(name)
            source.delete()
        }
    }

    @Test fun invalidMovesAreAtomicAndReclassificationClearsMembership() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        try {
            val dao = db.encounters()
            dao.createAlbum("album", "Album")
            val photo = EncounterRecord("unregistered", "content://fixture/other")
            val monkey = EncounterRecord(MockBreedRepository.MONKEY_ID, "content://fixture/monkey")
            dao.create(photo)
            dao.create(monkey)
            try { dao.movePhotos(listOf(photo.id, monkey.id), "album"); fail("Monkey must be rejected") }
            catch (_: IllegalArgumentException) { }
            assertTrue(dao.members("album").isEmpty())
            try { dao.deleteOtherPhotos(listOf(photo.id, monkey.id)); fail("Batch must be atomic") }
            catch (_: IllegalArgumentException) { }
            assertNotNull(dao.get(photo.id))
            try { dao.reorderAlbums(listOf("album", "album")); fail("Duplicate order must be rejected") }
            catch (_: IllegalArgumentException) { }
            try { dao.movePhotos(listOf(photo.id), MockBreedRepository.MONKEY_ID); fail("Secret album must be rejected") }
            catch (_: IllegalArgumentException) { }
            dao.movePhotos(listOf(photo.id), "album")
            dao.albumCover("album", photo.id)
            dao.updateAndRepairCovers(photo.copy(breedId = "corgi"))
            assertTrue(dao.members("album").isEmpty())
            assertNull(dao.albums().single().coverId)
            assertNotNull(dao.get(monkey.id))
            assertNotNull(dao.get(photo.id))
        } finally { db.close() }
    }

    @Test fun batchDeleteRepairsCoversAndRetainsSharedFilesUntilLastReference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, CollectionDatabase::class.java).build()
        val source = File(context.cacheDir, "batch-${UUID.randomUUID()}").apply { writeText("fixture") }
        val repo = EncounterRepository(db.encounters(), context)
        try {
            val album = repo.createAlbum("Batch")
            val (photo, _) = repo.create(EncounterRecord("unregistered", Uri.fromFile(source).toString()))
            val duplicate = photo.copy(id = UUID.randomUUID().toString())
            db.encounters().create(duplicate)
            repo.movePhotos(listOf(photo.id, duplicate.id), album)
            repo.albumCover(album, photo.id)
            repo.deletePhotos(listOf(photo.id))
            val file = File(Uri.parse(photo.photoUri).path!!)
            assertTrue(file.exists())
            assertNull(db.encounters().albums().single().coverId)
            assertEquals(duplicate.id, db.encounters().cover("unregistered")!!.encounterId)
            repo.deletePhotos(listOf(duplicate.id))
            assertFalse(file.exists())
            assertTrue(db.encounters().members(album).isEmpty())
        } finally {
            repo.collection.first().encounters.forEach { repo.delete(it.id) }
            db.close(); source.delete()
        }
    }

    @Test fun migrationPreservesVersionOneEncountersAndCovers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val record = EncounterRecord("unregistered", "content://fixture/migration")
        val raw = context.openOrCreateDatabase(name, 0, null)
        raw.execSQL("CREATE TABLE encounters (id TEXT NOT NULL PRIMARY KEY, breedId TEXT NOT NULL, photoUri TEXT, encounteredAt TEXT NOT NULL, timeZoneId TEXT NOT NULL, petName TEXT, location TEXT, note TEXT, createdAt INTEGER NOT NULL)")
        raw.execSQL("CREATE INDEX index_encounters_breedId ON encounters(breedId)")
        raw.execSQL("CREATE TABLE breed_covers (breedId TEXT NOT NULL PRIMARY KEY, encounterId TEXT NOT NULL, FOREIGN KEY(encounterId) REFERENCES encounters(id) ON DELETE CASCADE)")
        raw.execSQL("CREATE INDEX index_breed_covers_encounterId ON breed_covers(encounterId)")
        raw.execSQL("INSERT INTO encounters VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>(record.id, record.breedId, record.photoUri, record.encounteredAt.toString(), record.timeZoneId, null, null, null, record.createdAt))
        raw.execSQL("INSERT INTO breed_covers VALUES (?, ?)", arrayOf(record.breedId, record.id))
        raw.version = 1
        raw.close()
        val db = Room.databaseBuilder(context, CollectionDatabase::class.java, name)
            .addMigrations(CollectionDatabase.MIGRATION_1_2, CollectionDatabase.MIGRATION_2_3, CollectionDatabase.MIGRATION_3_4, CollectionDatabase.MIGRATION_4_5).build()
        try {
            assertEquals(record, db.encounters().get(record.id))
            assertEquals(record.id, db.encounters().cover(record.breedId)!!.encounterId)
            assertTrue(db.encounters().albums().isEmpty())
            db.encounters().createAlbum("first", "First")
            db.encounters().movePhotos(listOf(record.id), "first")
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
