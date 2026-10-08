package com.example.petcollection.data

import org.junit.Assert.*
import org.junit.Test

class RecognitionPolicyTest {
    private fun breed(id: String, species: Species?) = Breed(id, id, species, "", "", "", "", "")
    private val catalog = listOf(breed("abyssinian", Species.CAT), breed("corgi", Species.DOG),
        breed("monkey", null), breed("other_cat", Species.CAT).copy(isCollectible = false),
        breed("other_dog", Species.DOG).copy(isCollectible = false),
        breed("unregistered", null).copy(isCollectible = false))

    @Test fun catAndDogUseOnlyTheirOwnBreedClassifiers() {
        for ((animal, species, label, id) in listOf(
            listOf(AnimalCategory.CAT, Species.CAT, "Abyssinian", "abyssinian"),
            listOf(AnimalCategory.DOG, Species.DOG, "Corgi", "corgi"))) {
            var calls = 0
            val result = RecognitionPolicy.route(AnimalPrediction(animal as AnimalCategory, .9f), catalog) {
                assertEquals(species, it)
                calls++
                (label as String) to .8f
            }
            assertEquals(id, result.breedId)
            assertEquals(1, calls)
        }
    }

    @Test fun monkeyAndLowConfidenceNeverRunABreedClassifier() {
        val monkey = RecognitionPolicy.route(AnimalPrediction(AnimalCategory.MONKEY, .95f), catalog) {
            fail("Monkey must skip both breed models"); "" to 0f
        }
        assertEquals(MockBreedRepository.MONKEY_ID, monkey.breedId)
        for (animal in AnimalCategory.entries) {
            val low = RecognitionPolicy.route(AnimalPrediction(animal, RecognitionPolicy.MIN_ANIMAL_CONFIDENCE - .001f), catalog) {
                fail("Low-confidence animals must skip breed models"); "" to 0f
            }
            assertEquals("unregistered", low.breedId)
        }
        val boundary = RecognitionPolicy.route(AnimalPrediction(AnimalCategory.MONKEY, RecognitionPolicy.MIN_ANIMAL_CONFIDENCE), catalog) {
            fail("Monkey must skip breed models"); "" to 0f
        }
        assertEquals("monkey", boundary.breedId)
    }

    @Test fun unsupportedBreedLabelsUseExistingSpeciesAlbums() {
        assertEquals("other_cat", RecognitionPolicy.route(AnimalPrediction(AnimalCategory.CAT, .9f), catalog) { "unknown" to .8f }.breedId)
        assertEquals("other_dog", RecognitionPolicy.route(AnimalPrediction(AnimalCategory.DOG, .9f), catalog) { "unknown" to .8f }.breedId)
    }

    @Test fun outputIndicesFollowLabelsAndInvalidOutputIsRejected() {
        val labels = listOf("cat", "dog", "monkey")
        for ((i, category) in AnimalCategory.entries.withIndex()) {
            val output = FloatArray(3) { if (it == i) .9f else .05f }
            assertEquals(category, RecognitionPolicy.fromProbabilities(labels, output).category)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecognitionPolicy.fromProbabilities(labels.reversed(), floatArrayOf(.1f, .1f, .8f))
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecognitionPolicy.fromProbabilities(labels, floatArrayOf(Float.NaN, .1f, .9f))
        }
    }
}