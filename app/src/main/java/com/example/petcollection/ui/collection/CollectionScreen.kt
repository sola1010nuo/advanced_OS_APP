package com.example.petcollection.ui.collection

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedRepository
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.data.Species
import com.example.petcollection.data.PhotoAlbumKey
import androidx.compose.foundation.shape.RoundedCornerShape

internal enum class CollectionCategory(val species: Species?, val labelRes: Int) {
    CAT(Species.CAT, R.string.filter_cats),
    DOG(Species.DOG, R.string.filter_dogs),
    OTHER(null, R.string.filter_other)
}

/** Small local grid/detail route; the existing app owns the bottom navigation. */
@Composable
fun CollectionScreen(
    modifier: Modifier = Modifier,
    repository: BreedRepository? = null,
    unlockState: CollectionUnlockState = rememberCollectionUnlockState(),
    initialSpecies: Species = Species.CAT
) {
    val context = LocalContext.current
    val resources = context.resources
    val configuration = LocalConfiguration.current
    val source = repository ?: remember(resources, configuration) { MockBreedRepository(resources) }
    val catalog = remember(source) { source.getCollectionEntries() }
    val data by unlockState.collection.collectAsState()
    val breeds = catalog.map { unlockState.applyTo(it, data) }
    CollectionEncounterHost(unlockState, breeds)
    var selectedSpecies by rememberSaveable(initialSpecies) { mutableStateOf(initialSpecies.name) }
    var selectedBreedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedEncounterId by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) breeds.firstOrNull { it.id == pickerTargetId }?.let {
            unlockState.onBreedRecognized(it, uri.toString())
        }
        pickerTargetId = null
    }
    val catGridState = rememberLazyGridState()
    val dogGridState = rememberLazyGridState()
    val otherGridState = rememberLazyGridState()
    val selectedBreed = breeds.firstOrNull { it.id == selectedBreedId && it.isVisibleInCollection }
    val selectedEncounter = data.encounters.firstOrNull { it.id == selectedEncounterId && it.breedId == selectedBreedId }
    BackHandler(enabled = selectedBreed != null && unlockState.pendingEncounter == null) {
        if (selectedEncounterId != null) selectedEncounterId = null else selectedBreedId = null
    }
    LaunchedEffect(data, selectedEncounterId) {
        if (data.loaded && selectedEncounterId != null && selectedEncounter == null) selectedEncounterId = null
        if (data.loaded && selectedBreedId != null && selectedBreed == null) selectedBreedId = null
    }
    if (!data.loaded) {
        Column(modifier.padding(24.dp)) {
            if (unlockState.operationError) Text(stringResource(R.string.collection_storage_error))
            else CircularProgressIndicator()
        }
        return
    }
    if (unlockState.operationError && unlockState.pendingEncounter == null) {
        LocalizedEncounterAlertDialog(onDismissRequest = unlockState::clearError,
            title = { Text(stringResource(R.string.collection_storage_error)) }, text = {},
            confirmButton = { TextButton(onClick = unlockState::clearError) { Text(stringResource(R.string.collection_done)) } },
            dismissButton = {})
    }
    if (selectedSpecies == CollectionCategory.OTHER.name && selectedBreedId == null) {
        OtherAlbumsScreen(data, breeds, unlockState, modifier,
            onCategory = { selectedSpecies = it.name }, onMonkey = { selectedBreedId = it })
        return
    }
    if (selectedBreed != null && selectedEncounter != null) {
        EncounterDetailScreen(selectedBreed, selectedEncounter,
            onBack = { selectedEncounterId = null },
            onEdit = { unlockState.startEdit(selectedEncounter) },
            onDelete = { unlockState.deleteEncounter(selectedEncounter.id) { selectedEncounterId = null } },
            onSetCover = { unlockState.selectCover(selectedBreed.id, selectedEncounter.id) },
            busy = unlockState.isWorking, modifier = modifier)
    } else if (selectedBreed != null) {
        BreedDetailScreen(selectedBreed, onBack = { selectedBreedId = null }, modifier = modifier,
            encounters = data.sortedPhotos(PhotoAlbumKey.breed(selectedBreed.id), data.forBreed(selectedBreed.id)),
            sortMode = data.photoSortMode(PhotoAlbumKey.breed(selectedBreed.id)), busy = unlockState.isWorking,
            onSortMode = { unlockState.setPhotoSortMode(PhotoAlbumKey.breed(selectedBreed.id), it) },
            onReorder = { ids, finished -> unlockState.reorderPhotos(PhotoAlbumKey.breed(selectedBreed.id), ids, finished) },
            onOpenEncounter = { selectedEncounterId = it },
            onAddEncounter = {
                if (!unlockState.isWorking) {
                    pickerTargetId = selectedBreed.id
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            })
    } else {
        val category = CollectionCategory.valueOf(selectedSpecies)
        val species = category.species
        val statistics = data.statistics(catalog, species)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = modifier.fillMaxSize(),
            state = when (category) {
                CollectionCategory.CAT -> catGridState
                CollectionCategory.DOG -> dogGridState
                CollectionCategory.OTHER -> otherGridState
            },
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.collection), style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold)
                    if (species != null) CollectionSummary(statistics.discovered, statistics.totalBreeds, statistics.encounterCount)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CollectionCategory.entries.forEach { filter ->
                            FilterChip(
                                selected = category == filter, onClick = { selectedSpecies = filter.name },
                                label = { Text(stringResource(filter.labelRes)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                }
            }
            items(breeds.filter { it.isVisibleInCollection && it.species == species && (species != null || it.isCollectible)
                && !it.isCoatCategory && !(species == Species.CAT && it.id == "other_cat") }
                .sortedBy { !it.isCollectible }, key = { it.id }) { breed ->
                BreedCard(breed, onClick = { selectedBreedId = breed.id })
            }
            if (species == Species.CAT) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "coat-heading") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text(stringResource(R.string.coat_categories), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        HorizontalDivider(Modifier.weight(1f))
                    }
                }
                items(breeds.filter { it.isCoatCategory && it.isVisibleInCollection }, key = { it.id }) { breed ->
                    BreedCard(breed, onClick = { selectedBreedId = breed.id })
                }
                if (breeds.any { it.id == "other_cat" }) item(span = { GridItemSpan(maxLineSpan) }, key = "other-cat-album") {
                    TextButton(onClick = { selectedBreedId = "other_cat" }) {
                        Text(stringResource(R.string.other_cats))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionSummary(discovered: Int, total: Int, encounterCount: Int) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.collection_discovered_count, discovered, total),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(pluralStringResource(R.plurals.breed_encounters, encounterCount, encounterCount))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else discovered.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun BreedCard(breed: Breed, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp),
        colors = if (breed.isSecretMonkey) monkeyCardColors()
            else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        BreedImageArea(
            breed = breed, photo = breed.coverPhoto,
            locked = breed.showsDiscoveryState && !breed.isDiscovered, modifier = Modifier.fillMaxWidth().aspectRatio(1.15f)
        )
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (breed.isSecretMonkey) MonkeyLegendaryBadge()
            Text(breed.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.heightIn(min = 40.dp))
            Text(if (breed.showsDiscoveryState) stringResource(if (breed.isDiscovered) R.string.breed_discovered else R.string.breed_undiscovered)
                else pluralStringResource(R.plurals.breed_encounters, breed.encounterCount, breed.encounterCount),
                style = MaterialTheme.typography.labelMedium,
                color = if (breed.isDiscovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (breed.isCoatCategory) Text(pluralStringResource(R.plurals.breed_encounters,
                breed.encounterCount, breed.encounterCount), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
