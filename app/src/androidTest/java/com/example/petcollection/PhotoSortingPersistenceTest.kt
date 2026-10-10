package com.example.petcollection

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class PhotoSortingPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun allAlbumTypesPersistIndependentModesAndCustomOrdersAcrossReopen() = runBlocking {
        val name = "photo-sort-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, CollectionDatabase::class.java, name)
            .addMigrations(CollectionDatabase.MIGRATION_1_2, CollectionDatabase.MIGRATION_2_3).build()
        var db = open()
        try {
            var dao = db.encounters()
            dao.createAlbum("custom", "Custom")
            val keys = mutableListOf<String>()
            for (breed in listOf("abyssinian", "corgi", "monkey", "other_cat", "other_dog", "unregistered")) {
                for (i in 0..2) dao.create(EncounterRecord(breed, "content://fixture/$breed/$i",
                    id = "$breed-$i", encounteredAt = LocalDateTime.of(2026, 10, i + 1, 0, 0), createdAt = i.toLong()))
                val key = if (breed == "unregistered") PhotoAlbumKey.UNASSIGNED else PhotoAlbumKey.breed(breed)
                keys += key
                dao.reorderPhotos(key, listOf("$breed-2", "$breed-0", "$breed-1"))
            }
            val others = (0..2).map { i -> EncounterRecord("unregistered", "content://fixture/album/$i",
                id = "custom-$i", encounteredAt = if (i == 2) null else LocalDateTime.of(2026, 10, i + 1, 0, 0)) }
            others.forEach { dao.create(it) }
            dao.movePhotos(others.map { it.id }, "custom")
            val customKey = PhotoAlbumKey.other("custom")
            dao.reorderPhotos(customKey, listOf("custom-1", "custom-2", "custom-0"))
            keys += customKey
            keys.forEachIndexed { i, key -> dao.setPhotoSortMode(key, PhotoSortMode.entries[i % 3]) }
            val stored = keys.associateWith { dao.photoOrder(it) }
            db.close()
            db = open()
            dao = db.encounters()
            val repo = EncounterRepository(dao, context)
            val data = repo.collection.first()
            keys.forEachIndexed { i, key ->
                assertEquals(stored[key], dao.photoOrder(key))
                assertEquals(PhotoSortMode.entries[i % 3], data.photoSortMode(key))
            }
            val breedKey = PhotoAlbumKey.breed("corgi")
            dao.create(EncounterRecord("corgi", "content://fixture/new", id = "new", encounteredAt = null))
            assertEquals(listOf("corgi-2", "corgi-0", "corgi-1", "new"), dao.photoOrder(breedKey).map { it.encounterId })
            val first = requireNotNull(dao.get("corgi-0"))
            dao.updateAndRepairCovers(first.copy(encounteredAt = LocalDateTime.of(2030, 1, 1, 0, 0)))
            assertEquals(listOf("corgi-2", "corgi-0", "corgi-1", "new"), dao.photoOrder(breedKey).map { it.encounterId })
            dao.setPhotoSortMode(breedKey, PhotoSortMode.NEWEST)
            var snapshot = repo.collection.first()
            assertEquals(listOf("corgi-0", "corgi-2", "corgi-1", "new"), snapshot.sortedPhotos(breedKey, snapshot.forBreed("corgi")).map { it.id })
            dao.setPhotoSortMode(breedKey, PhotoSortMode.CUSTOM)
            snapshot = repo.collection.first()
            assertEquals(listOf("corgi-2", "corgi-0", "corgi-1", "new"), snapshot.sortedPhotos(breedKey, snapshot.forBreed("corgi")).map { it.id })
            dao.deleteAndRepairCover("corgi-0")
            assertEquals(listOf("corgi-2", "corgi-1", "new"), dao.photoOrder(breedKey).map { it.encounterId })
            assertEquals(stored[customKey], dao.photoOrder(customKey))
            // Moving/unassigning appends at destination and prunes the source order.
            dao.unassignPhotos(listOf("custom-2"))
            assertEquals(listOf("custom-1", "custom-0"), dao.photoOrder(customKey).map { it.encounterId })
            assertEquals("custom-2", dao.photoOrder(PhotoAlbumKey.UNASSIGNED).last().encounterId)
            dao.movePhotos(listOf("custom-2"), "custom")
            assertEquals(listOf("custom-1", "custom-0", "custom-2"), dao.photoOrder(customKey).map { it.encounterId })
            try { dao.reorderPhotos(breedKey, listOf("custom-0")); fail("Wrong album must be rejected") }
            catch (_: IllegalArgumentException) { }
            dao.deleteAlbum("custom", false)
            assertTrue(dao.photoOrder(customKey).isEmpty())
            assertFalse(repo.collection.first().photoSortModes.any { it.albumKey == customKey })
            val lone = EncounterRecord("siamese", "content://fixture/lone", id = "lone")
            dao.create(lone)
            dao.updateAndRepairCovers(lone.copy(breedId = "corgi"))
            assertTrue(dao.photoOrder(PhotoAlbumKey.breed("siamese")).isEmpty())
            assertEquals("lone", dao.photoOrder(breedKey).last().encounterId)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationFromVersionTwoRetainsDatesPhotosCoversAndAlbumMembers() = runBlocking {
        val name = "photo-migration-${UUID.randomUUID()}.db"
        val raw = context.openOrCreateDatabase(name, 0, null)
        raw.execSQL("CREATE TABLE encounters (id TEXT NOT NULL PRIMARY KEY, breedId TEXT NOT NULL, photoUri TEXT, encounteredAt TEXT NOT NULL, timeZoneId TEXT NOT NULL, petName TEXT, location TEXT, note TEXT, createdAt INTEGER NOT NULL)")
        raw.execSQL("CREATE INDEX index_encounters_breedId ON encounters(breedId)")
        raw.execSQL("CREATE TABLE breed_covers (breedId TEXT NOT NULL PRIMARY KEY, encounterId TEXT NOT NULL, FOREIGN KEY(encounterId) REFERENCES encounters(id) ON DELETE CASCADE)")
        raw.execSQL("CREATE INDEX index_breed_covers_encounterId ON breed_covers(encounterId)")
        raw.execSQL("CREATE TABLE other_albums (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL, coverId TEXT)")
        raw.execSQL("CREATE TABLE other_album_members (encounterId TEXT NOT NULL PRIMARY KEY, albumId TEXT NOT NULL, FOREIGN KEY(albumId) REFERENCES other_albums(id) ON DELETE CASCADE, FOREIGN KEY(encounterId) REFERENCES encounters(id) ON DELETE CASCADE)")
        raw.execSQL("CREATE INDEX index_other_album_members_albumId ON other_album_members(albumId)")
        val records = (1..3).map { EncounterRecord("unregistered", "content://fixture/$it", id = "photo-$it",
            encounteredAt = LocalDateTime.of(2026, 10, it, 12, 0), petName = "Pet $it", note = "Keep", createdAt = it.toLong()) }
        records.forEach { r -> raw.execSQL("INSERT INTO encounters VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(r.id, r.breedId, r.photoUri, r.encounteredAt.toString(), r.timeZoneId, r.petName, r.location, r.note, r.createdAt)) }
        raw.execSQL("INSERT INTO other_albums VALUES ('saved', 'Saved', 0, 'photo-2')")
        raw.execSQL("INSERT INTO breed_covers VALUES ('unregistered', 'photo-3')")
        raw.execSQL("INSERT INTO other_album_members VALUES ('photo-2', 'saved')")
        raw.version = 2
        raw.close()
        val db = Room.databaseBuilder(context, CollectionDatabase::class.java, name).addMigrations(CollectionDatabase.MIGRATION_2_3).build()
        try {
            val dao = db.encounters()
            records.forEach { assertEquals(it, dao.get(it.id)) }
            assertEquals("photo-3", dao.cover("unregistered")!!.encounterId)
            assertEquals("photo-2", dao.albums().single().coverId)
            assertEquals(listOf(OtherAlbumMember("photo-2", "saved")), dao.members("saved"))
            assertEquals(listOf("photo-3", "photo-1"), dao.photoOrder(PhotoAlbumKey.UNASSIGNED).map { it.encounterId })
            assertEquals(listOf("photo-2"), dao.photoOrder(PhotoAlbumKey.other("saved")).map { it.encounterId })
            val missing = records.first().copy(id = "missing", encounteredAt = null)
            dao.create(missing)
            assertEquals(missing, dao.get("missing"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
