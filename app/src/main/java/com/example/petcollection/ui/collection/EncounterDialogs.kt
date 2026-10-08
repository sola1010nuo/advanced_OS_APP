package com.example.petcollection.ui.collection

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedPhoto
import com.example.petcollection.data.EncounterRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The selected photo and localized classification, without persisting the draft. */
@Composable
fun RecognitionResultDialog(
    breed: Breed,
    draft: EncounterRecord,
    firstDiscovery: Boolean,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (breed.id) {
        "other_cat" -> R.string.recognition_other_cat
        "other_dog" -> R.string.recognition_other_dog
        "unregistered" -> R.string.recognition_unregistered
        else -> if (firstDiscovery) R.string.recognition_first else R.string.recognition_again
    }
    LocalizedEncounterAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recognition_result)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(6.dp)) {
                Card(
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        BreedImageArea(breed, BreedPhoto(draft.id, draft.photoUri), locked = false,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(message), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            if (breed.isCollectible) Text(breed.name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onContinue) { Text(stringResource(R.string.recognition_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.encounter_cancel)) } }
    )
}
/** Editable encounter draft. Save emits a record; it does not perform database writes. */
@Composable
fun EncounterEditorDialog(
    breed: Breed,
    initialEncounter: EncounterRecord,
    onSave: (EncounterRecord) -> Unit,
    onDismiss: () -> Unit,
    saving: Boolean = false,
    hasError: Boolean = false
) {
    var name by rememberSaveable(initialEncounter.id) { mutableStateOf(initialEncounter.petName.orEmpty()) }
    var date by rememberSaveable(initialEncounter.id) { mutableStateOf(initialEncounter.encounteredAt.toLocalDate().toString()) }
    var time by rememberSaveable(initialEncounter.id) { mutableStateOf(initialEncounter.encounteredAt.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var location by rememberSaveable(initialEncounter.id) { mutableStateOf(initialEncounter.location.orEmpty()) }
    var note by rememberSaveable(initialEncounter.id) { mutableStateOf(initialEncounter.note.orEmpty()) }
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val parsedTime = if (time.matches(Regex("\\d{2}:\\d{2}"))) runCatching { LocalTime.parse(time) }.getOrNull() else null
    LocalizedEncounterAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.encounter_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasError) Text(stringResource(R.string.collection_storage_error), color = MaterialTheme.colorScheme.error)
                Card(shape = RoundedCornerShape(20.dp)) {
                    BreedImageArea(breed, BreedPhoto(initialEncounter.id, initialEncounter.photoUri),
                        locked = false, modifier = Modifier.fillMaxWidth().height(160.dp))
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.encounter_name)) }, singleLine = true)
                OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.encounter_date)) }, singleLine = true,
                    isError = parsedDate == null,
                    supportingText = { Text(stringResource(if (parsedDate == null) R.string.encounter_date_error else R.string.encounter_date_hint)) })
                OutlinedTextField(value = time, onValueChange = { time = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.encounter_time)) }, singleLine = true,
                    isError = parsedTime == null,
                    supportingText = { Text(stringResource(if (parsedTime == null) R.string.encounter_time_error else R.string.encounter_time_hint)) })
                OutlinedTextField(value = location, onValueChange = { location = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.encounter_location)) }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.encounter_note)) }, minLines = 2, maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
        },
        confirmButton = {
            Button(enabled = !saving && parsedDate != null && parsedTime != null, onClick = {
                if (parsedDate != null && parsedTime != null) {
                    onSave(initialEncounter.copy(
                        petName = name.trim().ifEmpty { null }, encounteredAt = parsedDate.atTime(parsedTime),
                        location = location.trim().ifEmpty { null }, note = note.trim().ifEmpty { null }
                    ))
                }
            }) { Text(stringResource(R.string.encounter_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.encounter_cancel)) } }
    )
}

/** Dialog windows have their own Android composition locals. Keep every slot on the
 * app-selected resources, including nested photo placeholders and accessibility text.
 */
@Composable
internal fun LocalizedEncounterAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { LocalizedDialogContent(context, configuration, title) },
        text = { LocalizedDialogContent(context, configuration, text) },
        confirmButton = { LocalizedDialogContent(context, configuration, confirmButton) },
        dismissButton = { LocalizedDialogContent(context, configuration, dismissButton) }
    )
}

@Composable
private fun LocalizedDialogContent(
    context: Context,
    configuration: Configuration,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides configuration,
        LocalResources provides context.resources,
        content = content
    )
}
