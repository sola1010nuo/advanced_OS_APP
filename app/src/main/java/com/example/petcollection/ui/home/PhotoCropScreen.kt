package com.example.petcollection.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.petcollection.R
import com.example.petcollection.data.CropSource
import com.example.petcollection.data.PhotoCropIO
import com.example.petcollection.data.SquareCrop
import com.example.petcollection.ui.collection.EncounterPhotoLayout

@Composable
fun PhotoCropHost(state: PhotoInputState) {
    if (state.source == null && !state.opening && !state.exporting && !state.error) return
    Dialog(onDismissRequest = state::discard, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            PhotoCropScreen(state.source, state.opening || state.exporting, state.error,
                state::discard, state::confirm, state::retry)
        }
    }
}

@Composable
internal fun PhotoCropScreen(source: CropSource?, busy: Boolean, error: Boolean, onCancel: () -> Unit,
    onConfirm: (SquareCrop) -> Unit, onRetry: () -> Unit = {}) {
    var centerX by rememberSaveable(source) { mutableFloatStateOf(.5f) }
    var centerY by rememberSaveable(source) { mutableFloatStateOf(.5f) }
    var zoom by rememberSaveable(source) { mutableFloatStateOf(1f) }
    val aspectRatio = EncounterPhotoLayout.aspectRatio
    val crop = SquareCrop(centerX, centerY, zoom, aspectRatio)
    fun change(next: SquareCrop) { centerX = next.centerX; centerY = next.centerY; zoom = next.zoom }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).testTag("photo-crop-page"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.crop_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.crop_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val frameWidth = minOf(maxWidth, maxHeight * aspectRatio)
            if (source != null) {
                Canvas(Modifier.size(frameWidth, frameWidth / aspectRatio).clipToBounds().testTag("photo-crop-frame")
                    .pointerInput(source, busy, aspectRatio) {
                        if (!busy) detectTransformGestures { centroid, pan, factor, _ ->
                            change(SquareCrop(centerX, centerY, zoom, aspectRatio).gesture(source.bitmap.width, source.bitmap.height,
                                size.width.toFloat(), centroid.x, centroid.y, pan.x, pan.y, factor))
                        }
                    }) {
                    drawIntoCanvas { PhotoCropIO.draw(it.nativeCanvas, source.bitmap, crop, size.width) }
                    for (fraction in listOf(1f / 3, 2f / 3)) {
                        drawLine(Color.White.copy(alpha = .5f), Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height))
                        drawLine(Color.White.copy(alpha = .5f), Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction))
                    }
                    drawRect(Color.White.copy(alpha = .8f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
            if (busy) CircularProgressIndicator()
        }
        if (source != null) {
            Text(stringResource(R.string.crop_zoom), style = MaterialTheme.typography.labelLarge)
            Slider(value = zoom, onValueChange = { value ->
                change(crop.gesture(source.bitmap.width, source.bitmap.height, 1f, .5f, .5f / aspectRatio, 0f, 0f, value / zoom))
            }, valueRange = 1f..8f, enabled = !busy, modifier = Modifier.testTag("photo-crop-zoom"))
        }
        if (error) {
            Text(stringResource(R.string.crop_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.crop_retry)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).testTag("photo-crop-cancel")) {
                Text(stringResource(R.string.crop_cancel))
            }
            Button(onClick = { onConfirm(crop) }, enabled = source != null && !busy,
                modifier = Modifier.weight(1f).testTag("photo-crop-confirm")) { Text(stringResource(R.string.crop_confirm)) }
        }
    }
}
