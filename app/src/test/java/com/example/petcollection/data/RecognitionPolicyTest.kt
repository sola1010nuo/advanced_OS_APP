package com.example.petcollection.data

import org.junit.Assert.*
import org.junit.Test

class RecognitionPolicyTest {
    @Test fun catColorFallbackUsesIndependentThresholdAndAllSixLabels() {
        for ((label, id) in RecognitionPolicy.colorAlbumIds) {
            for (score in listOf(.599f, .60f, .601f, .699f, .749f, 1f)) {
                var calls = 0
                val result = RecognitionPolicy.route(AnimalPrediction(AnimalCategory.CAT, .9f), catalog,
                    classifyColor = { calls++; label to score }) { "Abyssinian" to .749f }
                assertEquals(1, calls)
                assertEquals(if (score >= .60f) id else "other_cat", result.breedId)
                assertEquals(score, result.confidence, 0f)
            }
        }
    }

    @Test fun colorNeverRunsForAcceptedCatBreedDogMonkeyOrUncertainAnimal() {
        val cases = listOf(AnimalPrediction(AnimalCategory.CAT, .9f) to .75f,
            AnimalPrediction(AnimalCategory.DOG, .9f) to .1f,
            AnimalPrediction(AnimalCategory.MONKEY, .9f) to .1f,
            AnimalPrediction(AnimalCategory.CAT, .69f) to .1f)
        for ((animal, breedScore) in cases) {
            RecognitionPolicy.route(animal, catalog,
                classifyColor = { fail("Color must be skipped"); "orange" to 1f }) {
                "Abyssinian" to breedScore
            }
        }
    }

    @Test fun colorFailureInvalidScoreAndUnknownLabelUseOtherCats() {
        val failures: List<() -> Pair<String, Float>> = listOf(
            { throw java.io.IOException("Missing model") },
            { throw IllegalStateException("Inference failed") },
            { "orange" to Float.NaN }, { "unknown" to .99f })
        for (classify in failures) {
            val logs = mutableListOf<String>()
            val result = RecognitionPolicy.route(AnimalPrediction(AnimalCategory.CAT, .9f), catalog,
                classifyColor = classify, trace = logs::add) { "Abyssinian" to .5f }
            assertEquals("other_cat", result.breedId)
            assertTrue(logs.any { it.contains("final=other_cat") })
        }
    }

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

    @Test fun breedThresholdBoundariesPreserveScoresAndSpeciesAlbums() {
        data class Case(val animal: AnimalCategory, val label: String, val officialId: String,
            val otherId: String, val threshold: Float)
        for ((animal, label, officialId, otherId, threshold) in listOf(
            Case(AnimalCategory.CAT, "Abyssinian", "abyssinian", "other_cat", RecognitionPolicy.CAT_BREED_THRESHOLD),
            Case(AnimalCategory.DOG, "Corgi", "corgi", "other_dog", RecognitionPolicy.DOG_BREED_THRESHOLD))) {
            for (score in listOf(0f, threshold - .001f, threshold, threshold + .001f, 1f)) {
                val result = RecognitionPolicy.route(AnimalPrediction(animal, .9f), catalog) {
                    label to score
                }
                assertEquals(if (score < threshold) otherId else officialId, result.breedId)
                assertEquals(score, result.confidence, 0f)
                if (score < threshold) {
                    val record = EncounterRecord(result.breedId, "file:///low-confidence")
                    val data = CollectionData(listOf(record), loaded = true)
                    val album = catalog.first { it.id == otherId }
                    assertTrue(record.breedId in MockBreedRepository.collectionIds)
                    assertEquals(1, data.forBreed(album.id).size)
                    assertEquals(1, data.applyTo(album).collectedPhotos.size)
                    assertEquals(0, data.statistics(catalog, album.species).discovered)
                    assertEquals(1, data.statistics(catalog, album.species).encounterCount)
                    assertEquals(0, data.applyTo(catalog.first { it.id == officialId }).encounterCount)
                }
            }
        }
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
