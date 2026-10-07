package com.example.petcollection.ui.collection

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedPhoto
import com.example.petcollection.data.EncounterRecord
import com.example.petcollection.data.MockBreedRepository
import java.util.Locale
import com.example.petcollection.R

/** Reuse the encounter form and celebrate the first saved encounter of a normal breed.
 * Room-backed discovery prevents repeat celebrations for already collected breeds.
 */
@Composable
fun CollectionEncounterHost(
    state: CollectionUnlockState,
    breeds: List<Breed>,
    onEncounterSaved: (EncounterRecord) -> Unit = {}
) {
    val draft = state.pendingEncounter ?: return
    val breed = breeds.firstOrNull { it.id == draft.breedId } ?: return
    if (state.showUnlockCelebration) {
        val context = LocalContext.current
        val englishName = remember(context, breed.id) {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
            MockBreedRepository(context.createConfigurationContext(configuration).resources).getBreed(breed.id)?.name
        }
        BreedUnlockDialog(breed, BreedPhoto(draft.id, draft.photoUri),
            onRecordEncounter = state::dismissEncounter, onDismiss = state::dismissEncounter,
            englishName = englishName, confirmLabel = R.string.collection_done)
    } else {
        EncounterEditorDialog(breed, draft, onSave = { record ->
            state.saveEncounter(record, onEncounterSaved)
        }, onDismiss = state::dismissEncounter, saving = state.isWorking, hasError = state.operationError)
    }
}
