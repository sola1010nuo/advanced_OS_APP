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
}
