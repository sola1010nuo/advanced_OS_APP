package com.example.petcollection.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.*

@Composable
fun BreedDetailScreen(
    breed: Breed, onBack: () -> Unit, modifier: Modifier = Modifier,
    encounters: List<EncounterRecord> = emptyList(),
    onAddEncounter: () -> Unit = {}, onOpenEncounter: (String) -> Unit = {},
    sortMode: PhotoSortMode = PhotoSortMode.NEWEST, busy: Boolean = false,
    onSortMode: (PhotoSortMode) -> Unit = {},
    onReorder: (List<String>, () -> Unit) -> Unit = { _, finished -> finished() }
) {
    val grid = rememberLazyGridState()
    val drag = rememberPhotoGridDragState(PhotoAlbumKey.breed(breed.id), grid, encounters.map { it.id })
    val byId = encounters.associateBy { it.id }
    val photos = drag.order.mapNotNull { byId[it] }
    LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid,
        userScrollEnabled = drag.draggingId == null,
        modifier = modifier.fillMaxSize().testTag("album-photo-grid").then(
            drag.gestureModifier(!busy && !drag.saving) { ids -> onReorder(ids, drag::finishSaving) }),
        contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.breed_back)) }
                }
                Text(breed.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                if (breed.isSecretMonkey) {
                    Text(stringResource(R.string.monkey_english_name), style = MaterialTheme.typography.titleMedium)
                    MonkeyLegendaryBadge()
                    Surface(shape = RoundedCornerShape(16.dp), color = monkeyContainerColor(), contentColor = monkeyContentColor()) {
                        Text(stringResource(R.string.monkey_legend), modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.monkey_inspiration), style = MaterialTheme.typography.bodyMedium)
                }
                breed.species?.let { species ->
                    Text(stringResource(if (species == Species.CAT) R.string.species_cat else R.string.species_dog),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Card(shape = RoundedCornerShape(24.dp)) {
                BreedImageArea(breed, breed.coverPhoto,
                    locked = breed.isCollectible && !breed.isDiscovered,
                    modifier = Modifier.fillMaxWidth().height(220.dp))
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (breed.isCollectible) {
                        Text(stringResource(if (breed.isDiscovered) R.string.breed_discovered else R.string.breed_undiscovered),
                            style = MaterialTheme.typography.titleMedium)
                        if (!breed.isDiscovered) Text(stringResource(R.string.breed_locked_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(pluralStringResource(R.plurals.breed_encounters, breed.encounterCount, breed.encounterCount))
                    if (breed.coverPhoto != null) Text(stringResource(R.string.breed_cover),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (breed.isCollectible) DetailHeading(stringResource(R.string.breed_about))
                Text(breed.description, style = MaterialTheme.typography.bodyLarge)
                if (breed.isSecretMonkey) Text(stringResource(R.string.monkey_model_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (breed.isCollectible) Text(stringResource(R.string.breed_preview_notes),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (breed.isCollectible) item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailHeading(stringResource(R.string.breed_characteristics))
                OutlinedCard(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Characteristic(stringResource(R.string.breed_origin), breed.origin)
                        Characteristic(stringResource(R.string.breed_personality), breed.personality)
                        Characteristic(stringResource(R.string.breed_size), breed.size)
                        Characteristic(stringResource(R.string.breed_coat), breed.coat)
                        breed.lifespan?.let { Characteristic(stringResource(R.string.breed_lifespan), it) }
                    }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().testTag("photo-section-actions"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.breed_photos), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    PhotoSortButton(if (drag.draggingId != null) PhotoSortMode.CUSTOM else sortMode,
                        !busy && drag.draggingId == null && !drag.saving, onSortMode)
                }
                Button(onClick = onAddEncounter) { Text(stringResource(R.string.collection_add_encounter)) }
                Text(pluralStringResource(R.plurals.breed_encounters, encounters.size, encounters.size))
                if (encounters.isEmpty()) Text(stringResource(R.string.collection_album_empty))
            }
        }
        items(photos, key = { it.id }) { encounter ->
            val dragging = drag.draggingId == encounter.id
            Card(onClick = { if (!busy && drag.draggingId == null && !drag.saving) onOpenEncounter(encounter.id) },
                modifier = Modifier.testTag("photo_${encounter.id}")
                    .animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = if (dragging) null else androidx.compose.animation.core.spring())
                    .then(drag.itemModifier(encounter.id)),
                shape = RoundedCornerShape(18.dp)) {
                BreedImageArea(breed, BreedPhoto(encounter.id, encounter.photoUri), locked = false,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                Text(encounter.petName ?: encounter.encounteredAt?.toLocalDate()?.toString() ?: stringResource(R.string.photo_date_missing),
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun DetailHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Characteristic(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
