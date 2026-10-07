package com.example.petcollection.data

import org.junit.Assert.*
import org.junit.Test

class MockBreedRepositoryTest {
    @Test fun datasetMatchesTrainingScope() {
        assertEquals(22, MockBreedRepository.total(Species.CAT))
        assertEquals(34, MockBreedRepository.total(Species.DOG))
        assertEquals(56, Species.entries.sumOf { MockBreedRepository.total(it) })
    }

    @Test fun breedDefaultsAreLockedWithoutEncounterData() {
        val breed = Breed("test", "Test", Species.CAT, "Description", "Origin", "Personality", "Size", "Coat")
        assertFalse(breed.isDiscovered)
        assertEquals(0, breed.encounterCount)
        assertNull(breed.coverPhoto)
        assertTrue(breed.collectedPhotos.isEmpty())
    }
}
