package com.example.petcollection

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.*
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AchievementPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String) = Room.databaseBuilder(context, CollectionDatabase::class.java, name)
        .addMigrations(CollectionDatabase.MIGRATION_1_2, CollectionDatabase.MIGRATION_2_3, CollectionDatabase.MIGRATION_3_4).build()

    @Test fun versionThreeMigrationPreservesPhotosCoversAlbumsAndSortOrdersAndBackfillsWithoutDates() = runBlocking {
        val name = "ach-migration-${UUID.randomUUID()}.db"
        var db = open(name)
        val source = File(context.cacheDir, "legacy-${UUID.randomUUID()}").apply { writeText("legacy photo") }
        val uri = Uri.fromFile(source).toString()
        val record = EncounterRecord("coat_tortoiseshell", uri, id = "legacy", location = "Old location", note = "Old note")
        try {
            db.encounters().create(record)
            db.encounters().create(EncounterRecord("monkey", uri, id = "monkey"))
            db.encounters().create(EncounterRecord("unregistered", uri, id = "other"))
            db.encounters().createAlbum("album", "Old album")
            db.encounters().movePhotos(listOf("other"), "album")
            db.encounters().albumCover("album", "other")
            db.encounters().setPhotoSortMode(PhotoAlbumKey.breed(record.breedId), PhotoSortMode.OLDEST)
            val before = db.encounters().allRecords()
            val order = db.encounters().allPhotoOrder()
            db.close()
            // Recreate the precise version-three table set in this disposable test database.
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
                raw.execSQL("DROP TABLE achievement_unlocks")
                raw.execSQL("DROP TABLE achievement_photos")
                raw.execSQL("UPDATE room_master_table SET identity_hash = 'b642b4bb8b0851bd69adcb8a2fc8aae9' WHERE id = 42")
                raw.version = 3
            }
            db = open(name)
            assertEquals(before, db.encounters().allRecords())
            assertEquals(order, db.encounters().allPhotoOrder())
            assertEquals("legacy", db.encounters().cover(record.breedId)?.encounterId)
            assertEquals("other", db.encounters().albums().single().coverId)
            assertEquals("album", db.encounters().allMembers().single().albumId)
            val repo = EncounterRepository(db.encounters(), context)
            val data = repo.achievements.first { it.loaded }
            assertNotNull(data.unlock(Achievement.TORTOISESHELL))
            assertNotNull(data.unlock(Achievement.MONKEY))
            assertNotNull(data.unlock(Achievement.FIRST))
            assertNull(data.unlock(Achievement.FIRST)?.unlockedAt)
            assertNull(data.unlock(Achievement.TORTOISESHELL)?.unlockedAt)
            assertTrue(data.unlocks.all { it.announced })
            assertNull(data.unlock(Achievement.NIGHT))
            assertEquals(1, data.progress.first { it.achievement == Achievement.SHUTTER }.current)
            assertEquals("legacy photo", source.readText())
            assertEquals(PhotoSortMode.OLDEST.name, repo.collection.first { it.loaded }.photoSortModes.single().mode)
        } finally { db.close(); context.deleteDatabase(name); source.delete() }
    }

    @Test fun renamedAchievementIdsKeepTheirOriginalDatesAcrossReconciliationAndRestart() = runBlocking {
        val name = "ach-rename-${UUID.randomUUID()}.db"
        var db = open(name)
        val previousFirst = AchievementUnlock("FIRST", 1600000000000, true)
        val previousMaster = AchievementUnlock("COMPLETE", 1700000000000, true)
        try {
            db.encounters().create(EncounterRecord("other_cat", null))
            db.encounters().insertAchievement(previousFirst)
            db.encounters().insertAchievement(previousMaster)
            var data = EncounterRepository(db.encounters(), context).achievements.first { it.loaded }
            assertEquals(previousFirst, data.unlock(Achievement.FIRST))
            assertEquals(previousMaster, data.unlock(Achievement.COMPLETE))
            db.close()
            db = open(name)
            data = EncounterRepository(db.encounters(), context).achievements.first { it.loaded }
            assertEquals(previousFirst, data.unlock(Achievement.FIRST))
            assertEquals(previousMaster, data.unlock(Achievement.COMPLETE))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun sameBytesUnderDifferentUrisDoNotInflateCountsAndNewUnlocksSurviveRestartAndDeletion() = runBlocking {
        val name = "ach-dedup-${UUID.randomUUID()}.db"
        var db = open(name)
        var repo = EncounterRepository(db.encounters(), context)
        val sources = mutableListOf<File>()
        val stored = mutableListOf<EncounterRecord>()
        try {
            repo.initializeAchievements()
            val before = System.currentTimeMillis()
            for (index in 1..12) {
                val source = File(context.cacheDir, "duplicate-${UUID.randomUUID()}").apply { writeText("same bytes") }
                sources += source
                stored += repo.create(EncounterRecord("british_shorthair", Uri.fromFile(source).toString(),
                    encounteredAt = LocalDateTime.of(2020, 1, 1, 2, 0), createdAt = 0), recognized = true).first
            }
            var data = repo.achievements.first { it.loaded }
            assertEquals(1, data.progress.first { it.achievement == Achievement.SHUTTER }.current)
            assertNull(data.unlock(Achievement.REPEAT))
            val first = requireNotNull(data.unlock(Achievement.FIRST))
            assertTrue(requireNotNull(first.unlockedAt) >= before)
            assertTrue(repo.claimAchievementAnnouncement(Achievement.FIRST.name))
            assertFalse(repo.claimAchievementAnnouncement(Achievement.FIRST.name))
            val metadata = db.encounters().achievementPhotos()
            assertTrue(metadata.all { requireNotNull(it.recognizedSavedAt) >= before })
            assertEquals(1, metadata.map { it.fingerprint }.distinct().size)
            for (index in 2..10) {
                val source = File(context.cacheDir, "unique-${UUID.randomUUID()}").apply { writeText("different $index") }
                sources += source
                stored += repo.create(EncounterRecord("british_shorthair", Uri.fromFile(source).toString())).first
            }
            data = repo.achievements.first { it.loaded }
            assertEquals(10, data.progress.first { it.achievement == Achievement.SHUTTER }.current)
            assertNotNull(data.unlock(Achievement.REPEAT))
            db.close()
            db = open(name)
            repo = EncounterRepository(db.encounters(), context)
            data = repo.achievements.first { it.loaded }
            assertEquals(first.unlockedAt, data.unlock(Achievement.FIRST)?.unlockedAt)
            assertTrue(requireNotNull(data.unlock(Achievement.FIRST)).announced)
            assertFalse(repo.claimAchievementAnnouncement(Achievement.FIRST.name))
            assertEquals(stored.size, db.encounters().allRecords().size)
            stored.forEach { repo.delete(it.id) }
            assertTrue(db.encounters().allRecords().isEmpty())
            assertNotNull(repo.achievements.first { it.loaded }.unlock(Achievement.REPEAT))
        } finally { db.close(); context.deleteDatabase(name); sources.forEach { it.delete() } }
    }

    @Test fun entireLiveCatalogRequiresOtherCatsAndMonkeyAndNeverCountsLegacyBreeds() = runBlocking {
        val name = "ach-complete-${UUID.randomUUID()}.db"
        val db = open(name)
        try {
            val catalog = MockBreedRepository(context.resources).getCollectionEntries()
            val required = catalog.filter { it.countsInCollection }
            val repo = EncounterRepository(db.encounters(), context)
            assertEquals(66, required.size)
            for (entry in required.filter { it.id != "other_cat" && it.id != "monkey" }) db.encounters().create(EncounterRecord(entry.id, null))
            var data = repo.achievements.first { it.loaded }
            assertNull(data.unlock(Achievement.COMPLETE))
            assertNotNull(data.unlock(Achievement.CATS))
            assertNotNull(data.unlock(Achievement.DOGS))
            assertNotNull(data.unlock(Achievement.COLORS))
            repo.create(EncounterRecord("other_cat", null))
            assertNull(repo.achievements.first { it.loaded }.unlock(Achievement.COMPLETE))
            repo.create(EncounterRecord("monkey", null))
            data = repo.achievements.first { it.loaded }
            assertNotNull(data.unlock(Achievement.COMPLETE))
            assertEquals(66, repo.collection.first { it.loaded }.statistics(catalog).discovered)
            assertEquals(36, catalog.count { it.isCollectible && it.species == Species.DOG })
            assertTrue(catalog.filter { it.isLegacyBreed }.none { it.countsInCollection })
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun unavailableHistoricalPhotosAndLocationsHaveNoFabricatedProgress() = runBlocking {
        val name = "ach-unavailable-${UUID.randomUUID()}.db"
        val db = open(name)
        try {
            for (index in 1..5) db.encounters().create(EncounterRecord("corgi", "file:///missing/$index", location = "Location $index"))
            val data = EncounterRepository(db.encounters(), context).achievements.first { it.loaded }
            assertEquals(5, data.unreadablePhotos)
            assertEquals(0, data.progress.first { it.achievement == Achievement.SHUTTER }.current)
            assertNull(data.progress.first { it.achievement == Achievement.PLACES }.current)
            assertNull(data.unlock(Achievement.PLACES))
            assertNull(data.unlock(Achievement.NIGHT))
            assertNull(data.unlock(Achievement.FIRST)?.unlockedAt)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun existingMixedCatCollectionsBackfillObserverAndRequireEveryBreedAndColorForCats() = runBlocking {
        val name = "ach-cat-progress-${UUID.randomUUID()}.db"
        var db = open(name)
        try {
            val catalog = MockBreedRepository(context.resources).getCollectionEntries()
            val catBreeds = catalog.filter { it.species == Species.CAT && it.isCollectible && !it.isCoatCategory && !it.isLegacyBreed }
            val colors = catalog.filter { it.isCoatCategory }
            assertEquals(6, colors.size)
            val mixed = catBreeds.take(3) + colors.take(2)
            for (entry in mixed) db.encounters().create(EncounterRecord(entry.id, null))
            db.encounters().create(EncounterRecord(mixed.first().id, null))
            for (id in listOf("corgi", "monkey", "other_cat")) db.encounters().create(EncounterRecord(id, null))
            val oldRecords = db.encounters().allRecords()
            var repo = EncounterRepository(db.encounters(), context)
            var data = repo.achievements.first { it.loaded }
            val observer = requireNotNull(data.unlock(Achievement.CAT_OBSERVER))
            assertNull(observer.unlockedAt)
            assertTrue(observer.announced)
            assertNull(data.unlock(Achievement.CATS))
            assertEquals(5, data.progress.first { it.achievement == Achievement.CATS }.current)
            assertEquals(catBreeds.size + colors.size, data.progress.first { it.achievement == Achievement.CATS }.target)
            assertEquals(oldRecords, db.encounters().allRecords())
            // Completing breeds alone still cannot unlock CATS while some colors are missing.
            for (entry in catBreeds.drop(3)) repo.create(EncounterRecord(entry.id, null))
            assertNull(repo.achievements.first { it.loaded }.unlock(Achievement.CATS))
            for (entry in colors.drop(2)) repo.create(EncounterRecord(entry.id, null))
            data = repo.achievements.first { it.loaded }
            val cats = requireNotNull(data.unlock(Achievement.CATS))
            assertNotNull(cats.unlockedAt)
            assertEquals(observer, data.unlock(Achievement.CAT_OBSERVER))
            assertTrue(db.encounters().allRecords().containsAll(oldRecords))
            db.close()
            db = open(name)
            repo = EncounterRepository(db.encounters(), context)
            data = repo.achievements.first { it.loaded }
            assertEquals(cats, data.unlock(Achievement.CATS))
            assertEquals(observer, data.unlock(Achievement.CAT_OBSERVER))
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun stricterCatGoalNeverResetsAnExistingUnlockOrItsDate() = runBlocking {
        val name = "ach-cat-preserve-${UUID.randomUUID()}.db"
        var db = open(name)
        val oldCats = AchievementUnlock(Achievement.CATS.name, 1600000000000, true)
        val oldObserver = AchievementUnlock(Achievement.CAT_OBSERVER.name, 1700000000000, true)
        try {
            val record = EncounterRecord("british_shorthair", null)
            db.encounters().create(record)
            db.encounters().insertAchievement(oldCats)
            db.encounters().insertAchievement(oldObserver)
            var data = EncounterRepository(db.encounters(), context).achievements.first { it.loaded }
            assertFalse(data.progress.first { it.achievement == Achievement.CATS }.fulfilled)
            assertEquals(oldCats, data.unlock(Achievement.CATS))
            assertEquals(oldObserver, data.unlock(Achievement.CAT_OBSERVER))
            db.close()
            db = open(name)
            data = EncounterRepository(db.encounters(), context).achievements.first { it.loaded }
            assertEquals(oldCats, data.unlock(Achievement.CATS))
            assertEquals(oldObserver, data.unlock(Achievement.CAT_OBSERVER))
            assertEquals(listOf(record), db.encounters().allRecords())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
