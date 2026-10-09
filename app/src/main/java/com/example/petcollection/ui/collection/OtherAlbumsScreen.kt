package com.example.petcollection.ui.collection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.*

@Composable
internal fun OtherAlbumsScreen(data: CollectionData, breeds: List<Breed>, state: CollectionUnlockState,
    modifier: Modifier, onCategory: (CollectionCategory) -> Unit, onMonkey: (String) -> Unit) {
    var albumId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var destructive by rememberSaveable { mutableStateOf(false) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var choosingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingPhotos by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val grid = rememberLazyGridState()
    LaunchedEffect(albumId, choosingFor) { grid.scrollToItem(0) }
    val scope = rememberCoroutineScope()
    val drag = remember(grid, scope) { AlbumGridDragState(grid, scope) }
    val orderedAlbums = drag.order.mapNotNull { id -> data.albums.find { it.id == id } }
    LaunchedEffect(data.albums, state.isWorking) {
        if (!state.isWorking) drag.finishSaving()
        drag.sync(data.albums.map { it.id })
    }
    DisposableEffect(drag) { onDispose { drag.cancel() } }
    fun exitSelection() { selected = emptySet(); selecting = false; moving = false }
    val other = breeds.first { it.id == "unregistered" }
    val album = data.albums.find { it.id == albumId }
    val memberIds = data.members.filter { it.albumId == albumId }.map { it.encounterId }.toSet()
    val assigned = data.members.map { it.encounterId }.toSet()
    val photos = data.forBreed(other.id).filter { it.photoUri != null &&
        if (album != null && choosingFor == null) it.id in memberIds else it.id !in assigned }
    val detail = data.encounters.find { it.id == detailId && it.breedId == other.id }
    BackHandler(albumId != null || detailId != null || selecting || choosingFor != null) {
        if (state.isWorking) return@BackHandler
        when {
            detailId != null -> detailId = null
            choosingFor != null -> { albumId = choosingFor; choosingFor = null; exitSelection() }
            selecting -> exitSelection()
            else -> { albumId = null; exitSelection() }
        }
    }
    LaunchedEffect(albumId, data.members, data.encounters) { selected = selected.intersect(photos.map { it.id }.toSet()) }
    LaunchedEffect(data.albums) { if (albumId != null && album == null) albumId = null }
    if (detail != null) {
        val albumCover = photos.find { it.id == album?.coverId } ?: data.encounters.firstOrNull { it.id in memberIds && it.photoUri != null }
        val detailBreed = if (album == null) other else other.copy(name = album.name,
            coverPhoto = albumCover?.let { BreedPhoto(it.id, it.photoUri) })
        EncounterDetailScreen(detailBreed, detail, onBack = { detailId = null },
            onEdit = { state.startEdit(detail) },
            onDelete = { state.deleteEncounter(detail.id) { detailId = null } },
            onSetCover = { if (album != null) state.albumCover(album.id, detail.id) else state.selectCover(other.id, detail.id) },
            busy = state.isWorking, modifier = modifier)
    } else LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (choosingFor != null) {
                    Text(stringResource(R.string.other_choose_photos), style = MaterialTheme.typography.headlineMedium)
                    Text(data.albums.find { it.id == choosingFor }?.name.orEmpty())
                } else if (album != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(album.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).testTag("album_title").clickable(enabled = !state.isWorking) {
                                renameId = album.id; name = album.name; naming = true
                            })
                        TextButton(enabled = !state.isWorking, onClick = { deleteId = album.id; destructive = false }) {
                            Text(stringResource(R.string.other_delete_album), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(enabled = !state.isWorking, onClick = { albumId = null; exitSelection() }) { Text(stringResource(R.string.breed_back)) }
                } else {
                    Text(stringResource(R.string.collection), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CollectionCategory.entries.forEach { category ->
                            FilterChip(selected = category == CollectionCategory.OTHER, onClick = { onCategory(category) },
                                label = { Text(stringResource(category.labelRes)) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (album == null && choosingFor == null) {
            item(span = { GridItemSpan(2) }, key = "albums_heading") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.other_albums), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Button(enabled = !state.isWorking, onClick = { name = ""; renameId = null; naming = true }) {
                        Text(stringResource(R.string.other_add_album))
                    }
                }
            }
            breeds.firstOrNull { it.isSecretMonkey && it.isVisibleInCollection }?.let { monkey ->
                item(key = "monkey") {
                    Card(onClick = { onMonkey(monkey.id) }, shape = RoundedCornerShape(22.dp), colors = monkeyCardColors()) {
                        BreedImageArea(monkey, monkey.coverPhoto, false, Modifier.fillMaxWidth().aspectRatio(1f))
                        MonkeyLegendaryBadge()
                        Text(monkey.name, Modifier.padding(12.dp))
                    }
                }
            }
            items(orderedAlbums, key = { it.id }) { entry ->
                val ids = data.members.filter { it.albumId == entry.id }.map { it.encounterId }.toSet()
                val records = data.encounters.filter { it.id in ids }
                val cover = records.find { it.id == entry.coverId } ?: records.firstOrNull { it.photoUri != null }
                val dragging = drag.draggingId == entry.id
                val translation = drag.translation(entry.id)
                Card(onClick = { if (drag.draggingId == null && !state.isWorking) { albumId = entry.id; exitSelection() } },
                    modifier = Modifier.testTag("album_${entry.id}").animateItem(fadeInSpec = null, fadeOutSpec = null,
                        placementSpec = if (dragging) null else androidx.compose.animation.core.spring())
                        .then(drag.modifier(entry.id, !selecting && !state.isWorking) { ids ->
                            state.reorderAlbums(ids) { drag.finishSaving() }
                        })
                        .zIndex(if (dragging) 1f else 0f).graphicsLayer {
                            translationX = translation.x; translationY = translation.y
                            scaleX = if (dragging) 1.04f else 1f; scaleY = if (dragging) 1.04f else 1f
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 12.dp else 0.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column {
                            BreedImageArea(other, cover?.let { BreedPhoto(it.id, it.photoUri) }, false, Modifier.fillMaxWidth().aspectRatio(1f))
                            Text(entry.name, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(14.dp))
                            Text(pluralStringResource(R.plurals.breed_encounters, records.size, records.size),
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp))
                        }
                }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (album == null || choosingFor != null) R.string.other_photos else R.string.breed_photos),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (!selecting && choosingFor == null) TextButton(enabled = photos.isNotEmpty() && !state.isWorking,
                        onClick = { selecting = true }) { Text(stringResource(R.string.other_organize)) }
                }
                if (photos.isEmpty()) Text(stringResource(R.string.collection_album_empty))
                if (selecting || choosingFor != null) {
                    Text(stringResource(R.string.other_selected_count, selected.size))
                    Row {
                        TextButton(enabled = !state.isWorking, onClick = {
                            if (choosingFor != null) { albumId = choosingFor; choosingFor = null }
                            exitSelection()
                        }) { Text(stringResource(R.string.other_cancel)) }
                        if (choosingFor != null) Button(enabled = !state.isWorking, onClick = {
                            val target = choosingFor!!
                            if (selected.isEmpty()) { albumId = target; choosingFor = null; exitSelection() }
                            else state.movePhotos(selected.toList(), target) { albumId = target; choosingFor = null; exitSelection() }
                        }) { Text(stringResource(R.string.collection_done)) }
                        else TextButton(enabled = selected.isNotEmpty() && !state.isWorking && data.albums.any { it.id != albumId },
                            onClick = { moving = true }) { Text(stringResource(R.string.other_move)) }
                    }
                    if (album != null && choosingFor == null) {
                        TextButton(enabled = selected.isNotEmpty() && !state.isWorking,
                            onClick = { state.unassignPhotos(selected.toList()) { exitSelection() } }) { Text(stringResource(R.string.other_return_photos)) }
                        TextButton(enabled = selected.isNotEmpty() && !state.isWorking,
                            onClick = { deletingPhotos = true }) { Text(stringResource(R.string.other_delete_selected)) }
                    }
                }
            }
        }
        items(photos, key = { it.id }) { photo ->
            val selectionMode = selecting || choosingFor != null
            val checked = photo.id in selected
            fun toggle() { selected = if (checked) selected - photo.id else selected + photo.id }
            Card(onClick = { if (!state.isWorking) { if (selectionMode) toggle() else detailId = photo.id } },
                modifier = Modifier.testTag("photo_${photo.id}").then(if (checked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)) {
                BreedImageArea(other, BreedPhoto(photo.id, photo.photoUri), false, Modifier.fillMaxWidth().aspectRatio(1f))
                Row {
                    if (selectionMode) Checkbox(checked = checked, enabled = !state.isWorking, onCheckedChange = { toggle() })
                    Text(photo.petName ?: photo.encounteredAt.toLocalDate().toString(), Modifier.padding(top = 12.dp))
                }
            }
        }
    }
    if (naming) LocalizedEncounterAlertDialog(onDismissRequest = { naming = false },
        title = { Text(stringResource(if (renameId == null) R.string.other_add_album else R.string.other_rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !state.isWorking, onClick = {
            renameId?.let { state.renameAlbum(it, name) { naming = false } } ?: state.createAlbum(name) { id ->
                naming = false; choosingFor = id; selected = emptySet(); selecting = true
            }
        }) { Text(stringResource(R.string.collection_done)) } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.other_cancel)) } })
    if (moving) LocalizedEncounterAlertDialog(onDismissRequest = { moving = false }, title = { Text(stringResource(R.string.other_move)) },
        text = { LazyColumn(Modifier.heightIn(max = 320.dp)) { items(data.albums.filter { it.id != albumId }, key = { it.id }) { destination ->
            TextButton(enabled = !state.isWorking, onClick = { state.movePhotos(selected.toList(), destination.id) { exitSelection() } }) { Text(destination.name) }
        } } }, confirmButton = {}, dismissButton = { TextButton(onClick = { moving = false }) { Text(stringResource(R.string.other_cancel)) } })
    if (deleteId != null) LocalizedEncounterAlertDialog(onDismissRequest = { deleteId = null },
        title = { Text(stringResource(R.string.other_delete_album)) },
        text = { Column {
            Text(stringResource(if (destructive) R.string.other_irreversible else R.string.other_delete_choices))
            if (!destructive) TextButton(enabled = !state.isWorking, onClick = {
                state.deleteAlbum(deleteId!!, false) { deleteId = null; albumId = null; exitSelection() }
            }) { Text(stringResource(R.string.other_keep_photos)) }
        } },
        confirmButton = { TextButton(enabled = !state.isWorking, onClick = {
            if (destructive) state.deleteAlbum(deleteId!!, true) { deleteId = null; albumId = null; exitSelection() } else destructive = true
        }) { Text(stringResource(R.string.other_delete_photos)) } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.other_cancel)) } })
    if (deletingPhotos) LocalizedEncounterAlertDialog(onDismissRequest = { if (!state.isWorking) deletingPhotos = false },
        title = { Text(stringResource(R.string.other_delete_selected)) },
        text = { Text(stringResource(R.string.other_delete_selected_confirmation, selected.size)) },
        confirmButton = { TextButton(enabled = !state.isWorking, onClick = {
            state.deletePhotos(selected.toList()) { deletingPhotos = false; exitSelection() }
        }) { Text(stringResource(R.string.encounter_delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(enabled = !state.isWorking, onClick = { deletingPhotos = false }) { Text(stringResource(R.string.other_cancel)) } })
}
