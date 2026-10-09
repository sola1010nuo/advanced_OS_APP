package com.example.petcollection.ui.collection

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Only custom album keys participate; headers, photos and the monkey cannot be targets. */
internal class AlbumGridDragState(private val grid: LazyGridState, private val scope: CoroutineScope) {
    var order by mutableStateOf(emptyList<String>())
        private set
    var draggingId by mutableStateOf<String?>(null)
        private set
    private var topLeft by mutableStateOf(Offset.Zero)
    private var pointer = Offset.Zero
    private var grabOffset = Offset.Zero
    private var scrollJob: Job? = null
    private var original = emptyList<String>()
    var saving by mutableStateOf(false)
        private set

    fun sync(ids: List<String>) { if (draggingId == null && !saving) order = ids }
    fun translation(id: String): Offset {
        if (draggingId != id) return Offset.Zero
        val item = grid.layoutInfo.visibleItemsInfo.find { it.key == id } ?: return Offset.Zero
        return topLeft - Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
    }
    private fun target() {
        val id = draggingId ?: return
        val item = grid.layoutInfo.visibleItemsInfo.find {
            it.key in order && it.key != id && pointer.x >= it.offset.x && pointer.x < it.offset.x + it.size.width &&
                pointer.y >= it.offset.y && pointer.y < it.offset.y + it.size.height
        } ?: return
        val from = order.indexOf(id)
        val to = order.indexOf(item.key)
        if (from >= 0 && to >= 0) order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    private fun start(id: String, position: Offset) {
        val item = grid.layoutInfo.visibleItemsInfo.find { it.key == id && id in order } ?: return
        original = order
        draggingId = item.key as String
        topLeft = Offset(item.offset.x.toFloat(), item.offset.y.toFloat())
        grabOffset = position
        pointer = topLeft + position
        scrollJob = scope.launch {
            while (draggingId != null) {
                withFrameNanos { }
                val layout = grid.layoutInfo
                val edge = 72f
                val index = order.indexOf(draggingId)
                val delta = when {
                    index > 0 && pointer.y < layout.viewportStartOffset + edge -> -12f
                    index < order.lastIndex && pointer.y > layout.viewportEndOffset - edge -> 12f
                    else -> 0f
                }
                if (delta != 0f) { grid.scrollBy(delta); target() }
            }
        }
    }
    fun cancel() {
        scrollJob?.cancel()
        if (draggingId != null) order = original
        draggingId = null
    }
    fun finishSaving() { saving = false }
    fun modifier(id: String, enabled: Boolean, onDrop: (List<String>) -> Unit): Modifier = Modifier.pointerInput(this, id, enabled) {
        if (!enabled) return@pointerInput
        detectDragGesturesAfterLongPress(
            onDragStart = { position -> start(id, position) },
            onDragCancel = ::cancel,
            onDragEnd = {
                scrollJob?.cancel()
                val changed = draggingId != null && order != original
                draggingId = null
                if (changed) { saving = true; onDrop(order) }
            },
            onDrag = { change, _ ->
                if (draggingId != null) {
                    change.consume()
                    val item = grid.layoutInfo.visibleItemsInfo.find { it.key == id }
                    if (item != null) {
                        pointer = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()) + change.position
                        topLeft = pointer - grabOffset
                    }
                    target()
                }
            }
        )
    }
}
