package com.example.petcollection.ui.collection

import androidx.compose.runtime.Composable
import com.example.petcollection.data.Breed
import com.example.petcollection.data.EncounterRecord

/** Every new encounter shows its recognition result before editing; only Save writes to Room. */
@Composable
fun CollectionEncounterHost(
    state: CollectionUnlockState,
    breeds: List<Breed>,
    onEncounterSaved: (EncounterRecord) -> Unit = {}
) {
    val draft = state.pendingEncounter ?: return
    val breed = breeds.firstOrNull { it.id == draft.breedId } ?: return
    if (state.showRecognitionResult) {
        RecognitionResultDialog(breed, draft, state.isFirstDiscovery,
            onContinue = state::editEncounter, onDismiss = state::dismissEncounter)
    } else {
        EncounterEditorDialog(breed, draft, onSave = { record ->
            state.saveEncounter(record, onEncounterSaved)
        }, onDismiss = state::dismissEncounter, saving = state.isWorking, hasError = state.operationError)
    }
}