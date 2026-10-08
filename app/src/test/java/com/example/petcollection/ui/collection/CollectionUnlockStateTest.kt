package com.example.petcollection.ui.collection

import com.example.petcollection.data.*
import org.junit.Assert.*
import org.junit.Test

class CollectionUnlockStateTest {
    private val breed = Breed("corgi", "Corgi", Species.DOG, "Description", "Origin", "Personality", "Size", "Coat")

    @Test fun discoveryAndCountsComeOnlyFromSavedEncounters() {
        val first = EncounterRecord("corgi", "file:///first", id = "first")
        val second = EncounterRecord("corgi", "file:///second", id = "second")
        val empty = CollectionData().applyTo(breed.copy(isDiscovered = true, encounterCount = 5))
        assertFalse(empty.isDiscovered)
        assertEquals(0, empty.encounterCount)
        val data = CollectionData(listOf(first, second), listOf(BreedCover("corgi", "second")), true)
        val collected = data.applyTo(breed)
        assertTrue(collected.isDiscovered)
        assertEquals(2, collected.encounterCount)
        assertEquals("second", collected.coverPhoto!!.id)
        assertEquals(1, data.discoveredCount(MockBreedRepository.normalBreedIds))
        assertFalse(data.copy(encounters = emptyList()).applyTo(breed).isDiscovered)
        assertNull(data.copy(encounters = emptyList()).applyTo(breed).coverPhoto)
    }

    @Test fun specialAlbumsDoNotChangeOfficialProgress() {
        val records = listOf("other_cat", "other_dog", "unregistered").map { EncounterRecord(it, null) }
        assertEquals(0, CollectionData(records).discoveredCount(MockBreedRepository.normalBreedIds))
        assertEquals(22, MockBreedRepository.total(Species.CAT))
        assertEquals(34, MockBreedRepository.total(Species.DOG))
    }

    @Test fun statisticsSeparateDistinctBreedsFromEncountersAndFollowReclassificationAndDeletion() {
        val cat = breed.copy(id = "cat", species = Species.CAT)
        val dog = breed.copy(id = "dog")
        val catalog = listOf(cat, dog,
            cat.copy(id = "other_cat", isCollectible = false),
            dog.copy(id = "other_dog", isCollectible = false),
            dog.copy(id = "unregistered", species = null, isCollectible = false))
        val first = EncounterRecord("dog", null, id = "first")
        val second = EncounterRecord("dog", "file:///second", id = "second")
        val records = listOf(first, second, EncounterRecord("other_cat", null),
            EncounterRecord("other_dog", null), EncounterRecord("unregistered", null))
        val data = CollectionData(records, loaded = true)
        assertEquals(CollectionStatistics(1, 1, 3), data.statistics(catalog, Species.DOG))
        assertEquals(CollectionStatistics(0, 1, 1), data.statistics(catalog, Species.CAT))
        assertEquals(CollectionStatistics(1, 2, 5), data.statistics(catalog))
        val reclassified = data.copy(encounters = records.map { if (it.id == first.id) it.copy(breedId = "cat") else it })
        assertEquals(CollectionStatistics(2, 2, 5), reclassified.statistics(catalog))
        val deleted = reclassified.copy(encounters = reclassified.encounters.filter { it.id != second.id })
        assertEquals(CollectionStatistics(0, 1, 1), deleted.statistics(catalog, Species.DOG))
        assertEquals(CollectionStatistics(1, 2, 4), deleted.statistics(catalog))
    }
}
