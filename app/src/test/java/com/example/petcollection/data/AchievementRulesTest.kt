package com.example.petcollection.data

import java.time.LocalDateTime
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class AchievementRulesTest {
    private fun breed(id: String, species: Species?) = Breed(id, id, species, "", "", "", "", "")
    private val cats = (1..6).map { breed("cat$it", Species.CAT) }
    private val dogs = (1..6).map { breed("dog$it", Species.DOG) }
    private val coats = MockBreedRepository.coatCategoryIds.map { breed(it, Species.CAT).copy(isCollectible = false, isCoatCategory = true) }
    private val other = breed("other_cat", Species.CAT).copy(isCollectible = false)
    private val monkey = breed("monkey", null).copy(isHiddenUntilDiscovered = true)
    private val catalog = cats + dogs + coats + other + monkey
    private fun records(ids: List<String>) = ids.mapIndexed { i, id -> EncounterRecord(id, "file:///$i", id = "record$i") }
    private fun progress(achievement: Achievement, rows: List<EncounterRecord>, photos: List<AchievementPhoto> = emptyList(), entries: List<Breed> = catalog) =
        AchievementRules.evaluate(entries, rows, photos).first { it.achievement == achievement }

    @Test fun fifteenAchievementsHaveElevenPublicAndFourHidden() {
        assertEquals(15, Achievement.entries.size)
        assertEquals(11, Achievement.entries.count { !it.hidden })
        assertEquals(4, Achievement.entries.count { it.hidden })
    }

    @Test fun breedGoalsExcludeCoatsSpecialsLegacyAndDuplicateEncounters() {
        val rows = records((coats + other + monkey).map { it.id } + listOf("cat1", "cat1", "retired"))
        val entries = catalog + breed("retired", Species.DOG).copy(isCollectible = false, isLegacyBreed = true)
        assertEquals(1, progress(Achievement.FIRST, rows, entries = entries).current)
        assertFalse(progress(Achievement.EXPLORER, rows, entries = entries).fulfilled)
        assertEquals(5, progress(Achievement.CAT_OBSERVER, rows, entries = entries).current)
        assertEquals(0, progress(Achievement.DOG_FRIEND, rows, entries = entries).current)
        assertEquals(1, progress(Achievement.FIRST, records((coats + other + monkey).map { it.id })).current)
    }

    @Test fun sayCheeseAcceptsEachCurrentCollectionEntryButNotUnregisteredOrRetiredAlbums() {
        for (entry in catalog) assertTrue(entry.id, progress(Achievement.FIRST, records(listOf(entry.id))).fulfilled)
        assertFalse(progress(Achievement.FIRST, records(listOf("unregistered", "other_dog", "retired"))).fulfilled)
        assertEquals("FIRST", Achievement.FIRST.name)
        assertEquals("COMPLETE", Achievement.COMPLETE.name)
    }

    @Test fun masterRequiresEveryCatalogEntryIncludingEveryColorOtherCatsAndMonkey() {
        val all = records(catalog.map { it.id })
        assertTrue(progress(Achievement.COMPLETE, all).fulfilled)
        for (entry in catalog) assertFalse(entry.id, progress(Achievement.COMPLETE, all.filter { it.breedId != entry.id }).fulfilled)
        assertFalse(progress(Achievement.COMPLETE, all, entries = catalog + breed("future", Species.CAT)).fulfilled)
        assertEquals(CollectionData().statistics(catalog).totalBreeds, progress(Achievement.COMPLETE, all).target)
    }

    @Test fun catDogAndColorCompletionAreIndependent() {
        val catRows = records(cats.map { it.id })
        assertFalse(progress(Achievement.CATS, catRows).fulfilled)
        assertFalse(progress(Achievement.COLORS, catRows).fulfilled)
        assertFalse(progress(Achievement.DOGS, catRows).fulfilled)
        val colorRows = records(coats.map { it.id })
        assertTrue(progress(Achievement.COLORS, colorRows).fulfilled)
        assertFalse(progress(Achievement.CATS, colorRows).fulfilled)
        assertTrue(progress(Achievement.CATS, catRows + colorRows).fulfilled)
        assertEquals(cats.size + coats.size, progress(Achievement.CATS, catRows + colorRows).target)
        for (entry in cats + coats) {
            assertFalse(entry.id, progress(Achievement.CATS, (catRows + colorRows).filter { it.breedId != entry.id }).fulfilled)
        }
        assertTrue(progress(Achievement.DOGS, records(dogs.map { it.id })).fulfilled)
    }

    @Test fun catObserverCountsFiveDistinctMixedBreedAndCoatEntriesOnly() {
        val mixedIds = cats.take(3).map { it.id } + coats.take(2).map { it.id }
        val fourIds = mixedIds.dropLast(1)
        val extras = dogs.map { it.id } + listOf("other_cat", "monkey", "unregistered", "retired")
        val fourWithRepeatsAndExcludedEntries = records(fourIds + fourIds + extras)
        assertEquals(4, progress(Achievement.CAT_OBSERVER, fourWithRepeatsAndExcludedEntries).current)
        assertFalse(progress(Achievement.CAT_OBSERVER, fourWithRepeatsAndExcludedEntries).fulfilled)
        assertTrue(progress(Achievement.CAT_OBSERVER, records(mixedIds)).fulfilled)
        assertEquals(0, progress(Achievement.CAT_OBSERVER, records(extras)).current)
        assertEquals(0, progress(Achievement.CATS, records(extras)).current)
        assertTrue(progress(Achievement.CAT_OBSERVER, records(coats.take(5).map { it.id })).fulfilled)
        assertTrue(progress(Achievement.CAT_OBSERVER, records(cats.take(5).map { it.id })).fulfilled)
        // The breed-only and color-only achievements keep their original independent calculations.
        assertEquals(3, progress(Achievement.EXPLORER, records(mixedIds)).current)
        assertEquals(2, progress(Achievement.COLORS, records(mixedIds)).current)
        assertEquals("CATS", Achievement.CATS.name)
        assertEquals("CAT_OBSERVER", Achievement.CAT_OBSERVER.name)
    }

    @Test fun fiftyPhotosAndTenOfOneBreedCountContentFingerprintsNotUrisOrRows() {
        val rows = (1..50).map { EncounterRecord("cat1", "file:///$it", id = "p$it") }
        val duplicates = rows.map { AchievementPhoto(it.id, "same-content") }
        assertEquals(1, progress(Achievement.SHUTTER, rows, duplicates).current)
        assertFalse(progress(Achievement.REPEAT, rows, duplicates).fulfilled)
        val distinct = rows.map { AchievementPhoto(it.id, it.id) }
        assertTrue(progress(Achievement.SHUTTER, rows, distinct).fulfilled)
        assertTrue(progress(Achievement.REPEAT, rows, distinct).fulfilled)
        assertFalse(progress(Achievement.REPEAT, rows.map { it.copy(breedId = "coat_tabby") }, distinct).fulfilled)
        assertFalse(progress(Achievement.SHUTTER, rows, distinct.drop(1)).fulfilled)
        assertEquals(0, progress(Achievement.SHUTTER, rows, emptyList()).current)
    }

    @Test fun rareHunterUsesOnlyFixedFiveStarBreedData() {
        val entries = catalog.map { if (it.id == "cat1" || it.id == "monkey") it.copy(rarity = BreedRarity.LEGENDARY) else it }
        assertFalse(progress(Achievement.RARE, records(listOf("monkey")), entries = entries).fulfilled)
        assertTrue(progress(Achievement.RARE, records(listOf("cat1")), entries = entries).fulfilled)
    }

    @Test fun nightUsesRecognizedSaveTimeAndStoredZoneNeverExifOrPageOpening() {
        val row = EncounterRecord("cat1", "file:///cat", id = "night", encounteredAt = LocalDateTime.of(2020, 1, 1, 2, 0), createdAt = 0)
        fun metadata(hour: Int, minute: Int) = AchievementPhoto(row.id, "photo",
            ZonedDateTime.of(2026, 10, 15, hour, minute, 0, 0, java.time.ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli(), "Asia/Taipei")
        assertFalse(progress(Achievement.NIGHT, listOf(row)).fulfilled)
        assertTrue(progress(Achievement.NIGHT, listOf(row), listOf(metadata(0, 0))).fulfilled)
        assertTrue(progress(Achievement.NIGHT, listOf(row), listOf(metadata(4, 59))).fulfilled)
        assertFalse(progress(Achievement.NIGHT, listOf(row), listOf(metadata(5, 0))).fulfilled)
        assertFalse(progress(Achievement.NIGHT, listOf(row), listOf(metadata(23, 59))).fulfilled)
        assertFalse(progress(Achievement.NIGHT, listOf(row.copy(breedId = "monkey")), listOf(metadata(2, 0))).fulfilled)
        assertTrue(progress(Achievement.NIGHT, listOf(row.copy(breedId = "other_cat")), listOf(metadata(2, 0))).fulfilled)
    }

    @Test fun freeTextLocationsCannotManufactureFootprintProgress() {
        val rows = (1..5).map { EncounterRecord("cat1", null, location = "Place $it") }
        assertNull(progress(Achievement.PLACES, rows).current)
        assertFalse(progress(Achievement.PLACES, rows).fulfilled)
    }

    @Test fun hiddenDiscoveryGoalsUseSavedCanonicalIds() {
        assertTrue(progress(Achievement.MONKEY, records(listOf("monkey"))).fulfilled)
        assertTrue(progress(Achievement.TORTOISESHELL, records(listOf("coat_tortoiseshell"))).fulfilled)
        assertFalse(progress(Achievement.TORTOISESHELL, records(listOf("coat_calico"))).fulfilled)
    }
}
