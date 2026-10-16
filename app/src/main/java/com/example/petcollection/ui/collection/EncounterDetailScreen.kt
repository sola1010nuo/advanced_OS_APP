package com.example.petcollection.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.*
import java.time.format.DateTimeFormatter

@Composable
fun EncounterDetailScreen(breed: Breed, record: EncounterRecord, onBack: () -> Unit,
    onEdit: () -> Unit, onDelete: () -> Unit, onSetCover: () -> Unit,
    busy: Boolean, modifier: Modifier = Modifier) {
    var confirmDelete by rememberSaveable(record.id) { mutableStateOf(false) }
    if (confirmDelete) LocalizedEncounterAlertDialog(
        onDismissRequest = { if (!busy) confirmDelete = false },
        title = { Text(stringResource(R.string.encounter_delete_question)) },
        text = { Text(stringResource(R.string.encounter_delete_message)) },
        confirmButton = { TextButton(enabled = !busy, onClick = onDelete) {
            Text(stringResource(R.string.encounter_delete), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) {
            Text(stringResource(R.string.encounter_cancel))
        } }
    )
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EncounterPhotoLayout.pagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.encounter_back_album)) }
        Text(breed.name, style = MaterialTheme.typography.headlineMedium)
        Card { BreedImageArea(breed, BreedPhoto(record.id, record.photoUri), false,
            Modifier.fillMaxWidth().aspectRatio(EncounterPhotoLayout.aspectRatio).testTag("encounter-detail-photo")) }
        record.petName?.let { EncounterField(R.string.encounter_name, it) }
        EncounterField(R.string.encounter_date, record.encounteredAt?.toLocalDate()?.toString() ?: stringResource(R.string.photo_date_missing))
        record.encounteredAt?.let { EncounterField(R.string.encounter_time, it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
        record.location?.let { EncounterField(R.string.encounter_location, it) }
        if (record.latitude != null && record.longitude != null) {
            EncounterField(R.string.photo_gps, String.format(java.util.Locale.ROOT, "%.6f, %.6f", record.latitude, record.longitude))
        }
        record.note?.let { EncounterField(R.string.encounter_note, it) }
        if (record.photoUri != null) {
            if (breed.coverPhoto?.id == record.id) Text(stringResource(R.string.encounter_current_cover))
            else OutlinedButton(enabled = !busy, onClick = onSetCover) { Text(stringResource(R.string.encounter_set_cover)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy, onClick = onEdit) { Text(stringResource(R.string.encounter_edit)) }
            OutlinedButton(enabled = !busy, onClick = { confirmDelete = true }) { Text(stringResource(R.string.encounter_delete)) }
        }
    }
}

@Composable
private fun EncounterField(label: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
