package com.example.petcollection.ui.collection

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.petcollection.data.*
import com.example.petcollection.ui.effects.RecognitionEffectsState
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Room is the collection source of truth; only the active unsaved form is kept in UI state. */
class CollectionUnlockState(private val repository: EncounterRepository) : ViewModel() {
    val recognitionEffects = RecognitionEffectsState()
    var operationError by mutableStateOf(false)
        private set
    var isWorking by mutableStateOf(false)
        private set
    val collection = repository.collection.catch {
        operationError = true
        emit(CollectionData())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CollectionData())
    val encounters: List<EncounterRecord> get() = collection.value.encounters
    val achievements = repository.achievements.catch {
        operationError = true
        emit(AchievementData())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AchievementData())
    suspend fun claimAchievementAnnouncement(id: String) = repository.claimAchievementAnnouncement(id)
    var pendingEncounter by mutableStateOf<EncounterRecord?>(null)
        private set
    var showRecognitionResult by mutableStateOf(false)
        private set
    var isFirstDiscovery by mutableStateOf(false)
        private set
    var recognitionConfidence by mutableStateOf<Float?>(null)
        private set
    private var editingExisting = false

    fun applyTo(breed: Breed, data: CollectionData = collection.value): Breed = data.applyTo(breed)

    /** Future callers supply a canonical entry; a draft never unlocks a breed before INSERT. */
    fun onBreedRecognized(breed: Breed, photoUri: String?, photoTime: LocalDateTime? = null): Boolean {
        if (pendingEncounter != null || isWorking || !collection.value.loaded) return false
        require(breed.id in MockBreedRepository.collectionIds)
        recognitionEffects.clear()
        recognitionConfidence = null
        pendingEncounter = EncounterRecord(breedId = breed.id, photoUri = photoUri,
            encounteredAt = photoTime ?: LocalDateTime.now())
        editingExisting = false
        isFirstDiscovery = breed.showsDiscoveryState && collection.value.forBreed(breed.id).isEmpty()
        showRecognitionResult = true
        operationError = false
        return true
    }

    /** Future three-class classifier: pass canonical "monkey" here, with the selected URI.
     * This only creates a preview draft; the editor remains the sole save entry point.
     */
    fun onClassificationResult(canonicalId: String, catalog: List<Breed>, photoUri: String, confidence: Float? = null): Boolean {
        val breed = catalog.firstOrNull { it.id == canonicalId } ?: return false
        if (!onBreedRecognized(breed, photoUri)) return false
        recognitionConfidence = confidence
        recognitionEffects.onClassificationCompleted(requireNotNull(pendingEncounter).id, breed)
        return true
    }

    fun startEdit(record: EncounterRecord) {
        if (pendingEncounter != null || isWorking) return
        pendingEncounter = record
        editingExisting = true
        showRecognitionResult = false
        operationError = false
    }
    fun editEncounter() {
        recognitionEffects.clear()
        showRecognitionResult = false
    }
    fun dismissEncounter() {
        if (isWorking) return
        recognitionEffects.clear()
        pendingEncounter = null
        showRecognitionResult = false
        operationError = false
    }
    fun clearError() { operationError = false }

    fun saveEncounter(record: EncounterRecord, onSaved: (EncounterRecord) -> Unit = {}) {
        val draft = pendingEncounter ?: return
        if (isWorking || showRecognitionResult) return
        require(record.id == draft.id && record.breedId in MockBreedRepository.collectionIds)
        if (!editingExisting) require(record.breedId == draft.breedId)
        val updating = editingExisting
        perform {
            if (updating) {
                repository.update(record)
                pendingEncounter = null
                onSaved(record)
            } else {
                val (stored, _) = repository.create(record, recognized = true)
                pendingEncounter = null
                onSaved(stored)
            }
        }
    }
    fun selectCover(breedId: String, encounterId: String) = perform { repository.selectCover(breedId, encounterId) }
    fun createAlbum(name: String, onCreated: (String) -> Unit = {}) = perform { onCreated(repository.createAlbum(name)) }
    fun renameAlbum(id: String, name: String, onRenamed: () -> Unit = {}) = perform { repository.renameAlbum(id, name); onRenamed() }
    fun reorderAlbum(id: String, offset: Int) = perform { repository.reorderAlbum(id, offset) }
    fun reorderAlbums(ids: List<String>, onFinished: () -> Unit = {}) = perform {
        repository.reorderAlbums(ids)
        collection.first { it.albums.map { album -> album.id } == ids }
        onFinished()
    }
    fun setPhotoSortMode(key: String, mode: PhotoSortMode) = perform { repository.setPhotoSortMode(key, mode) }
    fun reorderPhotos(key: String, ids: List<String>, onFinished: () -> Unit) = perform {
        try {
            repository.reorderPhotos(key, ids)
            collection.first { data -> data.photoSortMode(key) == PhotoSortMode.CUSTOM &&
                data.photoOrder.filter { it.albumKey == key }.sortedBy { it.position }.map { it.encounterId } == ids }
        } finally { onFinished() }
    }
    fun movePhotos(ids: List<String>, albumId: String, onMoved: () -> Unit = {}) = perform { repository.movePhotos(ids, albumId); onMoved() }
    fun unassignPhotos(ids: List<String>, onMoved: () -> Unit = {}) = perform { repository.unassignPhotos(ids); onMoved() }
    fun deletePhotos(ids: List<String>, onDeleted: () -> Unit = {}) = perform { repository.deletePhotos(ids); onDeleted() }
    fun albumCover(id: String, encounterId: String) = perform { repository.albumCover(id, encounterId) }
    fun deleteAlbum(id: String, deletePhotos: Boolean, onDeleted: () -> Unit = {}) = perform { repository.deleteAlbum(id, deletePhotos); onDeleted() }
    fun deleteEncounter(id: String, onDeleted: () -> Unit) = perform {
        repository.delete(id)
        onDeleted()
    }
    private fun perform(action: suspend () -> Unit) {
        if (isWorking) return
        isWorking = true
        operationError = false
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { operationError = true }
            finally { isWorking = false }
        }
    }
}

@Composable
fun rememberCollectionUnlockState(): CollectionUnlockState {
    val context = LocalContext.current.applicationContext
    val factory = remember(context) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionUnlockState(
                EncounterRepository(CollectionDatabase.getInstance(context).encounters(), context)
            ) as T
        }
    }
    return viewModel(factory = factory)
}
