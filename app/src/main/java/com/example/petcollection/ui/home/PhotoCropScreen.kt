package com.example.petcollection.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.positionChange
import com.example.petcollection.data.CropMode
import com.example.petcollection.data.CropCorner
import com.example.petcollection.data.PhotoCropEditor
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.petcollection.R
import com.example.petcollection.data.CropSource
import com.example.petcollection.data.SquareCrop
import com.example.petcollection.ui.collection.EncounterPhotoLayout

@Composable
fun PhotoCropHost(state: PhotoInputState) {
    if (state.source == null && !state.opening && !state.exporting && !state.error) return
    // Dialog installs its own Android locals. Retain the language selected in App settings.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    Dialog(onDismissRequest = state::discard, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
            LocalResources provides context.resources) {
            Surface(Modifier.fillMaxSize()) {
                PhotoCropScreen(state.source, state.opening || state.exporting, state.error,
                    state::discard, state::confirm, state::retry, state.mode)
            }
        }
    }
}

private val cropEditorSaver = androidx.compose.runtime.saveable.listSaver<com.example.petcollection.data.PhotoCropEditor, Float>(
    save = { listOf(it.selection.left, it.selection.top, it.selection.side, it.selection.height, it.zoom, it.shiftX, it.shiftY) },
    restore = { com.example.petcollection.data.PhotoCropEditor(com.example.petcollection.data.CropBounds(it[0], it[1], it[2], it[3]), it[4], it[5], it[6]) })

@Composable
internal fun PhotoCropScreen(source: CropSource?, busy: Boolean, error: Boolean, onCancel: () -> Unit,
    onConfirm: (SquareCrop) -> Unit, onRetry: () -> Unit = {}, mode: CropMode = CropMode.FREE) {
    val width = source?.bitmap?.width ?: 1
    val height = source?.bitmap?.height ?: 1
    val initial = PhotoCropEditor.initial(width, height, mode, EncounterPhotoLayout.aspectRatio)
    var editor by rememberSaveable(source, mode, stateSaver = cropEditorSaver) { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp).testTag("photo-crop-page"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.crop_title), style = MaterialTheme.typography.headlineMedium)
        if (mode == CropMode.COVER) Text(stringResource(R.string.crop_cover_ratio),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (source != null) CropCanvas(source, mode, busy, editor, { editor = it })
            if (busy) {
                val description = stringResource(R.string.crop_loading)
                CircularProgressIndicator(Modifier.semantics { contentDescription = description })
            }
        }
        if (source != null) {
            val label = stringResource(R.string.crop_zoom)
            // The slider is an optional accessible alternative to the two-finger photo gesture.
            Slider(value = editor.zoom.coerceIn(1f, 8f), onValueChange = { value ->
                // A unit viewport keeps this operation independent of display density and window size.
                val viewport = editor.viewport(width, height, 1f, 1f)
                val centerX = viewport.x + (editor.selection.left + editor.selection.side / 2) * viewport.scale
                val centerY = viewport.y + (editor.selection.top + editor.selection.height / 2) * viewport.scale
                editor = editor.transform(width, height, 1f, 1f, centerX, centerY, 0f, 0f, value / editor.zoom)
            }, valueRange = 1f..8f, enabled = !busy,
                modifier = Modifier.testTag("photo-crop-zoom").semantics { contentDescription = label })
        }
        if (error) {
            Text(stringResource(R.string.crop_error), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.crop_retry)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).testTag("photo-crop-cancel")) {
                Text(stringResource(R.string.crop_cancel))
            }
            OutlinedButton(onClick = { editor = initial }, enabled = source != null && !busy,
                modifier = Modifier.weight(1f).testTag("photo-crop-reset")) { Text(stringResource(R.string.crop_reset)) }
            Button(onClick = { onConfirm(editor.crop(width, height)) }, enabled = source != null && !busy,
                modifier = Modifier.weight(1f).testTag("photo-crop-confirm")) { Text(stringResource(R.string.crop_confirm)) }
        }
    }
}

@Composable
private fun CropCanvas(source: CropSource, mode: CropMode, busy: Boolean, editor: PhotoCropEditor,
    onChange: (PhotoCropEditor) -> Unit) {
    val latest by rememberUpdatedState(editor)
    val width = source.bitmap.width
    val height = source.bitmap.height
    val density = androidx.compose.ui.platform.LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val viewWidth = with(density) { maxWidth.toPx() }
        val viewHeight = with(density) { maxHeight.toPx() }
        val hitRadius = with(density) { 28.dp.toPx() }
        val viewport = editor.viewport(width, height, viewWidth, viewHeight)
        val rect = editor.selection
        val left = viewport.x + rect.left * viewport.scale
        val top = viewport.y + rect.top * viewport.scale
        val frameWidth = rect.side * viewport.scale
        val frameHeight = rect.height * viewport.scale
        Canvas(Modifier.fillMaxSize().testTag("photo-crop-canvas").pointerInput(source, mode, busy, viewWidth, viewHeight) {
            if (!busy) awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = latest
                var working = start
                fun update(next: PhotoCropEditor) { working = next; onChange(next) }
                val vp = start.viewport(width, height, viewWidth, viewHeight)
                val selected = start.selection
                val l = vp.x + selected.left * vp.scale
                val t = vp.y + selected.top * vp.scale
                val r = l + selected.side * vp.scale
                val b = t + selected.height * vp.scale
                val corners = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))
                val nearest = corners.indices.minBy { (corners[it] - down.position).getDistance() }
                val corner = if (mode == CropMode.FREE && (corners[nearest] - down.position).getDistance() <= hitRadius)
                    CropCorner.entries[nearest] else null
                val moveFrame = mode == CropMode.FREE && down.position.x in l..r && down.position.y in t..b
                var transformPhoto = mode == CropMode.COVER || !moveFrame
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val active = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!active.pressed) break
                    val current = working
                    val currentVp = current.viewport(width, height, viewWidth, viewHeight)
                    val delta = active.positionChange()
                    if (corner != null) {
                        // Lock the initial corner for the entire drag; never fall through to photo panning.
                        val cr = current.selection
                        val x = cr.left + if (corner == CropCorner.TOP_RIGHT || corner == CropCorner.BOTTOM_RIGHT) cr.side else 0f
                        val y = cr.top + if (corner == CropCorner.BOTTOM_LEFT || corner == CropCorner.BOTTOM_RIGHT) cr.height else 0f
                        val nextX = (x + delta.x / currentVp.scale).coerceIn(maxOf(0f, -currentVp.x / currentVp.scale),
                            minOf(width.toFloat(), (viewWidth - currentVp.x) / currentVp.scale))
                        val nextY = (y + delta.y / currentVp.scale).coerceIn(maxOf(0f, -currentVp.y / currentVp.scale),
                            minOf(height.toFloat(), (viewHeight - currentVp.y) / currentVp.scale))
                        update(current.resize(width, height, corner, nextX - x, nextY - y))
                    } else {
                        if (event.changes.count { it.pressed } > 1) transformPhoto = true
                        if (transformPhoto) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val pan = event.calculatePan()
                            if (centroid.x.isFinite() && centroid.y.isFinite()) update(current.transform(width, height,
                                viewWidth, viewHeight, centroid.x, centroid.y, pan.x, pan.y, event.calculateZoom()))
                        } else {
                            val cr = current.selection
                            val minX = maxOf(0f, -currentVp.x / currentVp.scale)
                            val minY = maxOf(0f, -currentVp.y / currentVp.scale)
                            val maxX = minOf(width.toFloat(), (viewWidth - currentVp.x) / currentVp.scale) - cr.side
                            val maxY = minOf(height.toFloat(), (viewHeight - currentVp.y) / currentVp.scale) - cr.height
                            update(current.move(width, height,
                                (cr.left + delta.x / currentVp.scale).coerceIn(minX, maxOf(minX, maxX)) - cr.left,
                                (cr.top + delta.y / currentVp.scale).coerceIn(minY, maxOf(minY, maxY)) - cr.top))
                        }
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }) {
            drawIntoCanvas {
                val matrix = android.graphics.Matrix().apply {
                    setScale(viewport.scale, viewport.scale)
                    postTranslate(viewport.x, viewport.y)
                }
                it.nativeCanvas.drawBitmap(source.bitmap, matrix,
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
            }
            val right = left + frameWidth
            val bottom = top + frameHeight
            val shade = Color.Black.copy(alpha = .55f)
            drawRect(shade, Offset.Zero, androidx.compose.ui.geometry.Size(size.width, top.coerceAtLeast(0f)))
            drawRect(shade, Offset(0f, bottom), androidx.compose.ui.geometry.Size(size.width, (size.height - bottom).coerceAtLeast(0f)))
            drawRect(shade, Offset(0f, top), androidx.compose.ui.geometry.Size(left.coerceAtLeast(0f), frameHeight))
            drawRect(shade, Offset(right, top), androidx.compose.ui.geometry.Size((size.width - right).coerceAtLeast(0f), frameHeight))
            for ((color, stroke) in listOf(Color.Black to 4.dp.toPx(), Color.White to 2.dp.toPx())) {
                drawRect(color, Offset(left, top), androidx.compose.ui.geometry.Size(frameWidth, frameHeight),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            }
            if (mode == CropMode.FREE) {
                val length = minOf(20.dp.toPx(), frameWidth / 3, frameHeight / 3)
                for ((color, stroke) in listOf(Color.Black to 8.dp.toPx(), Color.White to 4.dp.toPx())) {
                    for ((point, direction) in listOf(Offset(left, top) to Offset(1f, 1f), Offset(right, top) to Offset(-1f, 1f),
                        Offset(left, bottom) to Offset(1f, -1f), Offset(right, bottom) to Offset(-1f, -1f))) {
                        drawLine(color, point, point + Offset(direction.x * length, 0f), stroke)
                        drawLine(color, point, point + Offset(0f, direction.y * length), stroke)
                    }
                }
            }
        }
        // Semantic bounds follow the exact selected area, so accessibility/tests can target it independently of the mask.
        Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()) }
            .size(with(density) { frameWidth.toDp() }, with(density) { frameHeight.toDp() }).testTag("photo-crop-frame"))
        if (mode == CropMode.FREE) {
            for ((index, point) in listOf(Offset(left, top), Offset(left + frameWidth, top),
                Offset(left, top + frameHeight), Offset(left + frameWidth, top + frameHeight)).withIndex()) {
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(point.x.toInt() - 8, point.y.toInt() - 8) }
                    .size(with(density) { 16.toDp() }).testTag("photo-crop-handle-$index"))
            }
        }
    }
}
