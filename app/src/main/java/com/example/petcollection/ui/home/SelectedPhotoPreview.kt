package com.example.petcollection.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.BreedClassifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SelectedPhotoPreview(uri: Uri) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var animalType by rememberSaveable(uri) { mutableStateOf<String?>(null) }
    var result by remember(uri) { mutableStateOf<BreedClassifier.Result?>(null) }
    var running by remember(uri) { mutableStateOf(false) }
    var inferenceFailed by remember(uri) { mutableStateOf(false) }
    var loading by remember(uri) { mutableStateOf(true) }
    val bitmap by produceState<Bitmap?>(null, uri, resolver) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val scale = (1024f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                } else {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = 1
                    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) {
                        options.inSampleSize *= 2
                    }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                }
            }.getOrNull()
        }
        loading = false
    }
    LaunchedEffect(uri, bitmap, animalType) {
        result = null
        inferenceFailed = false
        val photo = bitmap ?: return@LaunchedEffect
        val type = animalType?.let(BreedClassifier.AnimalType::valueOf) ?: return@LaunchedEffect
        running = true
        try {
            result = withContext(Dispatchers.Default) {
                val classifier = BreedClassifier(context.applicationContext, type)
                try {
                    classifier.classify(photo)
                } finally {
                    classifier.close()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            inferenceFailed = true
        } finally {
            running = false
        }
    }
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.selected_photo), style = MaterialTheme.typography.titleMedium)
            val photo = bitmap
            if (photo != null) {
                Image(photo.asImageBitmap(), contentDescription = stringResource(R.string.selected_photo),
                    modifier = Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit)
                Text(stringResource(R.string.choose_animal_type), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BreedClassifier.AnimalType.entries.forEach { type ->
                        FilterChip(
                            selected = animalType == type.name,
                            onClick = { animalType = type.name },
                            enabled = !running,
                            label = { Text(stringResource(if (type == BreedClassifier.AnimalType.CAT)
                                R.string.species_cat else R.string.species_dog)) }
                        )
                    }
                }
                if (running) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.inference_running))
                }
                result?.let { prediction ->
                    Text(prediction.breed.replace('_', ' '), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.inference_confidence, prediction.confidence * 100f))
                }
                if (inferenceFailed) {
                    Text(stringResource(R.string.inference_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { animalType = null }) { Text(stringResource(R.string.inference_retry)) }
                }
            } else if (loading) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.photo_preview_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
