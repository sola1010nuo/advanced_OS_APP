package com.example.petcollection.ui.collection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.petcollection.R
import com.example.petcollection.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun PhotoSortButton(mode: PhotoSortMode, enabled: Boolean, onSelect: (PhotoSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }, modifier = Modifier.testTag("photo-sort")) {
            Text(stringResource(R.string.photo_sort))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration,
                LocalResources provides context.resources) {
                PhotoSortMode.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(stringResource(when (option) {
                        PhotoSortMode.NEWEST -> R.string.photo_sort_newest
                        PhotoSortMode.OLDEST -> R.string.photo_sort_oldest
                        PhotoSortMode.CUSTOM -> R.string.photo_sort_custom
                    })) }, leadingIcon = { Text(if (option == mode) "✓" else "") },
                        modifier = Modifier.testTag("photo-sort-${option.name}").semantics { selected = option == mode },
                        onClick = { expanded = false; onSelect(option) })
                }
            }
        }
    }
}

/** One pointer handler on the grid survives cross-row item movement/recomposition. */
internal class PhotoGridDragState(private val grid: LazyGridState, private val scope: CoroutineScope,
    private val edgePx: Float, private val horizontalPaddingPx: Float) {
    var order by mutableStateOf(emptyList<String>())
        private set
    var draggingId by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    private var original = emptyList<String>()
    private var pointer by mutableStateOf(Offset.Zero)
    private var grabOffset = Offset.Zero
    private var scrollJob: Job? = null

    fun sync(ids: List<String>) {
        if (draggingId != null && ids.toSet() != original.toSet()) cancel()
        if (draggingId == null && !saving) order = ids
    }
    fun finishSaving() { saving = false }
    // LazyGridItemInfo offsets exclude the grid's visual content-padding offset.
    private fun contentPosition(position: Offset) = position - Offset(horizontalPaddingPx, grid.layoutInfo.beforeContentPadding.toFloat())
    private fun hit(position: Offset) = grid.layoutInfo.visibleItemsInfo.find {
        it.key in order && position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
            position.y >= it.offset.y && position.y < it.offset.y + it.size.height
    }
    private fun target() {
        val id = draggingId ?: return
        val item = hit(pointer) ?: return
        if (item.key == id) return
        val from = order.indexOf(id)
        val to = order.indexOf(item.key)
        if (from >= 0 && to >= 0) {
            // Keep the viewport stationary when moving its first visible stable key.
            // Otherwise LazyGrid follows that key and can repeatedly jump during a drag.
            grid.requestScrollToItem(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
            order = order.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
    private fun start(position: Offset) {
        if (saving) return
        val content = contentPosition(position)
        val item = hit(content) ?: return
        original = order
        draggingId = item.key as String
        pointer = content
        grabOffset = content - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
        scrollJob = scope.launch {
            snapshotFlow {
                val layout = grid.layoutInfo
                val top = layout.viewportStartOffset + edgePx
                val bottom = layout.viewportEndOffset - edgePx
                when {
                    pointer.y < top -> -((top - pointer.y) / edgePx).coerceIn(0f, 1f)
                    pointer.y > bottom -> ((pointer.y - bottom) / edgePx).coerceIn(0f, 1f)
                    else -> 0f
                }
            }.collectLatest { amount ->
                if (amount == 0f) return@collectLatest
                var previous = withFrameNanos { it }
                while (draggingId != null && if (amount < 0f) grid.canScrollBackward else grid.canScrollForward) {
                    val frame = withFrameNanos { it }
                    val seconds = ((frame - previous) / 1_000_000_000f).coerceAtMost(.05f)
                    previous = frame
                    grid.scrollBy(amount * edgePx * 8f * seconds)
                    target()
                }
            }
        }
    }
    fun cancel() {
        scrollJob?.cancel()
        if (draggingId != null) order = original
        draggingId = null
    }
    fun itemModifier(id: String): Modifier = Modifier.zIndex(if (draggingId == id) 1f else 0f).graphicsLayer {
        if (draggingId == id) {
            val item = grid.layoutInfo.visibleItemsInfo.find { it.key == id }
            if (item != null) {
                val translation = pointer - grabOffset - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
                translationX = translation.x
                translationY = translation.y
            }
            scaleX = 1.04f; scaleY = 1.04f
            shadowElevation = 12.dp.toPx()
        }
    }
    fun gestureModifier(enabled: Boolean, onDrop: (List<String>) -> Unit) = Modifier.pointerInput(this, enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // Album cards, headers and selection controls keep their own gestures.
            if (hit(contentPosition(down.position)) == null) return@awaitEachGesture
            try {
                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                start(longPress.position)
                if (drag(longPress.id) { change ->
                    pointer = contentPosition(change.position)
                    change.consume()
                    target()
                }) {
                    currentEvent.changes.forEach { it.consume() }
                    scrollJob?.cancel()
                    if (draggingId != null) {
                        draggingId = null
                        saving = true
                        onDrop(order.toList())
                    }
                }
            } finally {
                cancel()
            }
        }
    }
}

@Composable
internal fun rememberPhotoGridDragState(key: String, grid: LazyGridState, ids: List<String>): PhotoGridDragState {
    val scope = rememberCoroutineScope()
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val horizontalPadding = with(LocalDensity.current) { 24.dp.toPx() }
    val drag = remember(key, grid, scope, edge, horizontalPadding) { PhotoGridDragState(grid, scope, edge, horizontalPadding).apply { sync(ids) } }
    LaunchedEffect(drag, ids, drag.saving) { drag.sync(ids) }
    DisposableEffect(drag) { onDispose { drag.cancel() } }
    return drag
}
