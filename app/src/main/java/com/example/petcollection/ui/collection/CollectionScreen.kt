package com.example.petcollection.ui.collection

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedRepository
import com.example.petcollection.data.MockBreedRepository
import com.example.petcollection.data.Species
import androidx.compose.foundation.shape.RoundedCornerShape

/** Small local grid/detail route; the existing app owns the bottom navigation. */
@Composable
fun CollectionScreen(modifier: Modifier = Modifier, repository: BreedRepository? = null) {
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val source = repository ?: remember(resources, configuration) { MockBreedRepository(resources) }
    val breeds = remember(source) { source.getBreeds() }
    var selectedSpecies by rememberSaveable { mutableStateOf(Species.CAT.name) }
    var selectedBreedId by rememberSaveable { mutableStateOf<String?>(null) }
    val catGridState = rememberLazyGridState()
    val dogGridState = rememberLazyGridState()
    val selectedBreed = breeds.firstOrNull { it.id == selectedBreedId }
    BackHandler(enabled = selectedBreed != null) { selectedBreedId = null }
    if (selectedBreed != null) {
        BreedDetailScreen(selectedBreed, onBack = { selectedBreedId = null }, modifier = modifier)
    } else {
        val species = Species.valueOf(selectedSpecies)
        val discovered = breeds.count { it.isDiscovered }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = modifier.fillMaxSize(),
            state = if (species == Species.CAT) catGridState else dogGridState,
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.collection), style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold)
                    CollectionSummary(discovered, breeds.size)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Species.entries.forEach { filter ->
                            FilterChip(
                                selected = species == filter, onClick = { selectedSpecies = filter.name },
                                label = { Text(stringResource(if (filter == Species.CAT) R.string.filter_cats else R.string.filter_dogs)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            items(breeds.filter { it.species == species }, key = { it.id }) { breed ->
                BreedCard(breed, onClick = { selectedBreedId = breed.id })
            }
        }
    }
}

@Composable
private fun CollectionSummary(discovered: Int, total: Int) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.collection_discovered_count, discovered, total),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        BreedImageArea(
            breed = breed, photo = if (breed.isDiscovered) breed.coverPhoto else null,
            locked = !breed.isDiscovered, modifier = Modifier.fillMaxWidth().aspectRatio(1.15f)
        )
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(breed.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.heightIn(min = 40.dp))
            Text(stringResource(if (breed.isDiscovered) R.string.breed_discovered else R.string.breed_undiscovered),
                style = MaterialTheme.typography.labelMedium,
                color = if (breed.isDiscovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
