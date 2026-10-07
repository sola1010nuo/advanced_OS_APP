package com.example.petcollection.ui.collection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.Species

@Composable
fun BreedDetailScreen(breed: Breed, onBack: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.breed_back)) }
            Text(breed.name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(if (breed.species == Species.CAT) R.string.species_cat else R.string.species_dog),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                BreedImageArea(breed, if (breed.isDiscovered) breed.coverPhoto else null,
                    locked = !breed.isDiscovered, modifier = Modifier.fillMaxWidth().height(220.dp))
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if (breed.isDiscovered) R.string.breed_discovered else R.string.breed_undiscovered),
                        style = MaterialTheme.typography.titleMedium)
                    if (breed.isDiscovered) {
                        Text(pluralStringResource(R.plurals.breed_encounters, breed.encounterCount, breed.encounterCount))
                        Text(stringResource(R.string.breed_cover), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(stringResource(R.string.breed_locked_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailHeading(stringResource(R.string.breed_about))
                Text(breed.description, style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.breed_preview_notes), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
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
        // Educational details are always visible; personal collection content is discovered-only.
        if (breed.isDiscovered) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailHeading(stringResource(R.string.breed_photos))
                    Text(pluralStringResource(R.plurals.breed_photo_count, breed.collectedPhotos.size, breed.collectedPhotos.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (breed.collectedPhotos.isEmpty()) {
                        Text(stringResource(R.string.breed_no_photos))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(breed.collectedPhotos, key = { it.id }) { photo ->
                                Card(shape = RoundedCornerShape(18.dp)) {
                                    BreedImageArea(breed, photo, locked = false, modifier = Modifier.size(144.dp))
                                }
                            }
                        }
                    }
                }
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
