package com.example.petcollection.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MockBreedRepositoryTest {
    @Test fun datasetMatchesTrainingScope() {
        assertEquals(22, MockBreedRepository.total(Species.CAT))
        assertEquals(34, MockBreedRepository.total(Species.DOG))
        assertEquals(56, Species.entries.sumOf { MockBreedRepository.total(it) })
    }

    @Test fun mockDiscoveryCountsMatchInitialCollection() {
        assertEquals(4, MockBreedRepository.discovered(Species.CAT))
        assertEquals(4, MockBreedRepository.discovered(Species.DOG))
        assertEquals(8, Species.entries.sumOf { MockBreedRepository.discovered(it) })
    }
}
