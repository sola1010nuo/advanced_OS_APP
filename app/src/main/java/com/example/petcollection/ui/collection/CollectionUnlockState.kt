package com.example.petcollection.ui.collection

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.petcollection.data.*
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Room is the collection source of truth; only the active unsaved form is kept in UI state. */
class CollectionUnlockState(private val repository: EncounterRepository) : ViewModel() {
    var operationError by mutableStateOf(false)
        private set
    var isWorking by mutableStateOf(false)
        private set
    val collection = repository.collection.catch {
        operationError = true
        emit(CollectionData())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CollectionData())
    val encounters: List<EncounterRecord> get() = collection.value.encounters
    var pendingEncounter by mutableStateOf<EncounterRecord?>(null)
        private set
    var showRecognitionResult by mutableStateOf(false)
        private set
    var isFirstDiscovery by mutableStateOf(false)
        private set
    private var editingExisting = false

    fun applyTo(breed: Breed, data: CollectionData = collection.value): Breed = data.applyTo(breed)

    /** Future callers supply a canonical entry; a draft never unlocks a breed before INSERT. */
    fun onBreedRecognized(breed: Breed, photoUri: String?, photoTime: LocalDateTime? = null): Boolean {
        if (pendingEncounter != null || isWorking || !collection.value.loaded) return false
        require(breed.id in MockBreedRepository.collectionIds)
        pendingEncounter = EncounterRecord(breedId = breed.id, photoUri = photoUri,
            encounteredAt = photoTime ?: LocalDateTime.now())
        editingExisting = false
        isFirstDiscovery = breed.id in MockBreedRepository.normalBreedIds && collection.value.forBreed(breed.id).isEmpty()
        showRecognitionResult = true
        operationError = false
        return true
    }

    fun startEdit(record: EncounterRecord) {
        if (pendingEncounter != null || isWorking) return
        pendingEncounter = record
        editingExisting = true
        showRecognitionResult = false
        operationError = false
    }
    fun editEncounter() { showRecognitionResult = false }
    fun dismissEncounter() {
        if (isWorking) return
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
                val (stored, _) = repository.create(record)
                pendingEncounter = null
                onSaved(stored)
            }
        }
    }
    fun selectCover(breedId: String, encounterId: String) = perform { repository.selectCover(breedId, encounterId) }
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
