package com.example.petcollection.ui.collection

import androidx.compose.runtime.*
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedPhoto
import com.example.petcollection.data.EncounterRecord
import java.time.LocalDateTime

/** In-memory UI session only. Hoist this state when connecting recognition or persistence.
 * Unlocking is independent from saving/cancelling an individual encounter.
 */
class CollectionUnlockState {
    private val unlockedIds = mutableStateListOf<String>()
    private val records = mutableStateListOf<EncounterRecord>()
    val encounters: List<EncounterRecord> get() = records.toList()
    var pendingEncounter by mutableStateOf<EncounterRecord?>(null)
        private set
    var showUnlockCelebration by mutableStateOf(false)
        private set

    fun applyTo(breed: Breed): Breed {
        val saved = records.filter { it.breedId == breed.id }
        val photos = saved.mapNotNull { record -> record.photoUri?.let { BreedPhoto(record.id, it) } }
        return breed.copy(
            isDiscovered = breed.isDiscovered || breed.id in unlockedIds,
            encounterCount = breed.encounterCount + saved.size,
            collectedPhotos = breed.collectedPhotos + photos,
            coverPhoto = breed.coverPhoto ?: photos.firstOrNull()
        )
    }

    /** Future recognition entry point. Already-unlocked breeds go straight to editing.
     * Returns false while another encounter is being edited, without dropping that draft.
     */
    fun onBreedRecognized(breed: Breed, photoUri: String?, photoTime: LocalDateTime? = null): Boolean {
        if (pendingEncounter != null) return false
        val isNew = !applyTo(breed).isDiscovered
        if (isNew) unlockedIds.add(breed.id)
        pendingEncounter = EncounterRecord(breedId = breed.id, photoUri = photoUri,
            encounteredAt = photoTime ?: LocalDateTime.now())
        showUnlockCelebration = isNew
        return true
    }

    fun editEncounter() { showUnlockCelebration = false }
    fun dismissEncounter() {
        pendingEncounter = null
        showUnlockCelebration = false
    }

    fun saveEncounter(record: EncounterRecord) {
        val draft = pendingEncounter ?: return
        require(record.id == draft.id && record.breedId == draft.breedId) { "Encounter does not match the active draft" }
        records.add(record)
        dismissEncounter()
    }
}

@Composable
fun rememberCollectionUnlockState(): CollectionUnlockState = remember { CollectionUnlockState() }
