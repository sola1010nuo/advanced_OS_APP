package com.example.petcollection.ui.collection

import com.example.petcollection.data.Breed
import com.example.petcollection.data.Species
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CollectionUnlockStateTest {
    private val breed = Breed("british_shorthair", "British Shorthair", Species.CAT,
        "Description", "United Kingdom", "Calm", "Medium", "Plush")

    @Test fun firstRecognitionCelebratesAndLaterRecognitionOnlyEdits() {
        val state = CollectionUnlockState()
        val photoTime = LocalDateTime.of(2026, 10, 7, 15, 30)
        assertFalse(state.applyTo(breed).isDiscovered)
        assertTrue(state.onBreedRecognized(breed, "content://photos/1", photoTime))
        assertTrue(state.showUnlockCelebration)
        assertEquals(photoTime, state.pendingEncounter!!.encounteredAt)
        assertTrue(state.applyTo(breed).isDiscovered)
        assertEquals(0, state.applyTo(breed).encounterCount)
        state.editEncounter()
        assertFalse(state.showUnlockCelebration)
        state.saveEncounter(state.pendingEncounter!!.copy(petName = "Snow", encounteredAt = photoTime.plusDays(1).withHour(16), location = "School", note = "Friendly"))
        assertNull(state.pendingEncounter)
        assertEquals("Snow", state.encounters.single().petName)
        assertEquals(photoTime.plusDays(1).withHour(16), state.encounters.single().encounteredAt)
        assertEquals("School", state.encounters.single().location)
        assertEquals("Friendly", state.encounters.single().note)
        val unlocked = state.applyTo(breed)
        assertEquals(breed.description, unlocked.description)
        assertEquals(breed.origin, unlocked.origin)
        assertEquals(breed.personality, unlocked.personality)
        assertEquals(breed.size, unlocked.size)
        assertEquals(breed.coat, unlocked.coat)
        assertEquals(1, state.applyTo(breed).encounterCount)
        assertEquals("content://photos/1", state.applyTo(breed).coverPhoto!!.uri)
        assertTrue(state.onBreedRecognized(breed, "content://photos/2"))
        assertFalse(state.showUnlockCelebration)
        state.saveEncounter(state.pendingEncounter!!)
        assertEquals(2, state.applyTo(breed).collectedPhotos.size)
        assertEquals(2, state.encounters.map { it.id }.toSet().size)
    }

    @Test fun dismissKeepsUnlockAndDoesNotSaveAnEncounter() {
        val state = CollectionUnlockState()
        state.onBreedRecognized(breed, null)
        val draft = state.pendingEncounter
        assertFalse(state.onBreedRecognized(breed, "content://photos/other"))
        assertEquals(draft, state.pendingEncounter)
        state.dismissEncounter()
        assertTrue(state.applyTo(breed).isDiscovered)
        assertTrue(state.encounters.isEmpty())
        state.onBreedRecognized(breed, null)
        assertFalse(state.showUnlockCelebration)
    }

    @Test fun existingRepositoryDiscoveryDoesNotCelebrate() {
        val state = CollectionUnlockState()
        state.onBreedRecognized(breed.copy(isDiscovered = true), null)
        assertFalse(state.showUnlockCelebration)
        assertNotNull(state.pendingEncounter)
    }
}
