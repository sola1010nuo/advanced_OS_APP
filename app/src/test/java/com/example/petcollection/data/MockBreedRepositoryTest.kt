package com.example.petcollection.data

import org.junit.Assert.*
import org.junit.Test

class MockBreedRepositoryTest {
    @Test fun coatAlbumsHaveIndependentDiscoveryAndKeepBreedProgress() {
        assertEquals(setOf("coat_orange_tabby", "coat_calico", "coat_tortoiseshell", "coat_tabby",
            "coat_tuxedo", "coat_black", "other_cat"), MockBreedRepository.coatCategoryIds)
        assertTrue(MockBreedRepository.collectionIds.containsAll(MockBreedRepository.coatCategoryIds))
        assertFalse("blue" in MockBreedRepository.collectionIds)
        assertTrue("russian_blue" in MockBreedRepository.normalBreedIds)
        assertTrue(MockBreedRepository.normalBreedIds.intersect(MockBreedRepository.coatCategoryIds).isEmpty())
        val coats = MockBreedRepository.coatCategoryIds.map { id ->
            Breed(id, id, Species.CAT, "", "", "", "", "", isCollectible = false, isCoatCategory = true)
        }
        val official = Breed("abyssinian", "Abyssinian", Species.CAT, "", "", "", "", "")
        val records = coats.map { EncounterRecord(it.id, "file:///${it.id}") }
        val data = CollectionData(records, loaded = true)
        coats.forEach { coat ->
            assertTrue(coat.showsDiscoveryState)
            assertFalse(coat.isDiscovered)
            val saved = data.applyTo(coat)
            assertTrue(saved.isDiscovered)
            assertEquals(1, saved.encounterCount)
            assertEquals(records.first { it.breedId == coat.id }.photoUri, saved.coverPhoto!!.uri)
            assertEquals(1, data.forBreed(coat.id).size)
        }
        assertEquals(CollectionStatistics(0, 1, 7), data.statistics(coats + official, Species.CAT))
        assertFalse(data.applyTo(official).isDiscovered)
    }

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
