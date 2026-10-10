package com.example.petcollection.ui.effects

import com.example.petcollection.data.Breed
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.data.Species
import org.junit.Assert.*
import org.junit.Test

class RecognitionEffectsStateTest {
    private val monkey = Breed(MockBreedRepository.MONKEY_ID, "Monkey", null, "", "", "", "", "")

    @Test fun onlyTheFinalMonkeyEntrySelectsBananaRain() {
        assertEquals(RecognitionEffectKind.BANANA_RAIN, RecognitionEffectRegistry.effectFor(monkey))
        listOf("abyssinian" to Species.CAT, "corgi" to Species.DOG,
            "other_cat" to Species.CAT, "other_dog" to Species.DOG, "unregistered" to null).forEach { (id, species) ->
            assertNull(RecognitionEffectRegistry.effectFor(monkey.copy(id = id, species = species)))
        }
    }

    @Test fun aResultCanOnlyBeClaimedOnceEvenWhenOfferedAgain() {
        val state = RecognitionEffectsState()
        state.onClassificationCompleted("first", monkey)
        assertEquals(RecognitionEffectKind.BANANA_RAIN, state.claim("first", true))
        state.onClassificationCompleted("first", monkey)
        repeat(5) { assertNull(state.claim("first", true)) }
    }

    @Test fun disabledResultsDoNotPlayWhenTheSwitchIsLaterEnabled() {
        val state = RecognitionEffectsState()
        state.onClassificationCompleted("first", monkey)
        assertNull(state.claim("first", false))
        assertNull(state.claim("first", true))
        state.onClassificationCompleted("second", monkey)
        assertEquals(RecognitionEffectKind.BANANA_RAIN, state.claim("second", true))
    }

    @Test fun anotherPhotoReplacesTheEventAndRejectsStaleClaims() {
        val state = RecognitionEffectsState()
        state.onClassificationCompleted("first", monkey)
        state.claim("first", true)
        state.onClassificationCompleted("second", monkey)
        assertNull(state.claim("first", true))
        state.finish("first")
        assertEquals(RecognitionEffectKind.BANANA_RAIN, state.claim("second", true))
    }

    @Test fun leavingOrStartingRecognitionClearsTheEvent() {
        val state = RecognitionEffectsState()
        state.onClassificationCompleted("first", monkey)
        state.clear()
        assertNull(state.event)
        assertNull(state.claim("first", true))
        state.onClassificationCompleted("second", monkey)
        assertEquals(RecognitionEffectKind.BANANA_RAIN, state.claim("second", true))
    }

    @Test fun finishedOrInterruptedResultsCannotRestart() {
        val state = RecognitionEffectsState()
        state.onClassificationCompleted("first", monkey)
        state.finish("first")
        assertNull(state.claim("first", true))
    }

    @Test fun nonMonkeyResultClearsAPreviousMonkeyEvent() {
        val state = RecognitionEffectsState()
        state.onClassificationCompleted("first", monkey)
        state.onClassificationCompleted("second", monkey.copy(id = "unregistered"))
        assertNull(state.event)
        assertNull(state.claim("first", true))
    }

    @Test fun particleBudgetIsFiniteStaggeredAndDeterministic() {
        assertEquals(bananaParticles(42), bananaParticles(42))
        for (seed in 0..99) {
            val particles = bananaParticles(seed)
            assertEquals(BANANA_PARTICLE_COUNT, particles.size)
            assertEquals(4000, BANANA_RAIN_DURATION_MS)
            assertTrue(particles.any { it.delayMs > 1500 })
            assertTrue(particles.any { it.x < .125f })
            assertTrue(particles.any { it.x > .875f })
            assertTrue(particles.map { it.delayMs }.toSet().size > 1)
            assertTrue(particles.map { it.fallMs }.toSet().size > 1)
            assertTrue(particles.map { it.sizeDp }.toSet().size > 1)
            particles.forEach {
                assertTrue(it.delayMs >= 0 && it.fallMs > 0)
                assertTrue(it.fallMs in 1500..2123)
                assertTrue(it.delayMs < BANANA_RAIN_EMISSION_MS)
                assertTrue(it.delayMs + it.fallMs <= BANANA_RAIN_DURATION_MS)
                assertTrue(it.x in 0f..1f)
            }
        }
    }
}
