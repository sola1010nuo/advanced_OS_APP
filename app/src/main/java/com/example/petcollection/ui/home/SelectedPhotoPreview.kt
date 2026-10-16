package com.example.petcollection.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.petcollection.ui.collection.EncounterPhotoLayout
import com.example.petcollection.R

/** The photo develops inside a Material 3 Polaroid frame; results use catalog names. */
@Composable
fun SelectedPhotoPreview(state: PhotoRecognitionState, breedName: String?, onRetry: () -> Unit) {
    val photo = state.bitmap
    val alpha = if (state.processing) {
        val transition = rememberInfiniteTransition(label = "developing")
        val pulse by transition.animateFloat(0.3f, 0.8f,
            infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "photo reveal")
        pulse
    } else 1f
    Card(
        modifier = Modifier.fillMaxWidth().padding(6.dp).graphicsLayer { rotationZ = -1f },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(EncounterPhotoLayout.aspectRatio).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center) {
                if (photo != null) {
                    Image(photo.asImageBitmap(), contentDescription = stringResource(R.string.selected_photo),
                        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                        contentScale = ContentScale.Fit)
                }
                if (state.processing) CircularProgressIndicator()
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.processing) {
                    Text(stringResource(R.string.photo_developing), style = MaterialTheme.typography.titleMedium)
                } else if (state.failed) {
                    Text(stringResource(R.string.inference_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.inference_retry)) }
                } else if (breedName != null) {
                    Text(breedName, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
