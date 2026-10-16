package com.example.petcollection.data

import kotlin.math.min

enum class CropMode { FREE, COVER }
enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
data class CropViewport(val scale: Float, val x: Float, val y: Float)

/** Source coordinates are independent of canvas size. Only the viewport changes while manipulating the photo. */
data class PhotoCropEditor(val selection: CropBounds, val zoom: Float = 1f,
    val shiftX: Float = 0f, val shiftY: Float = 0f) {
    companion object {
        fun initial(width: Int, height: Int, mode: CropMode, coverRatio: Float): PhotoCropEditor =
            PhotoCropEditor(if (mode == CropMode.FREE) CropBounds(0f, 0f, width.toFloat(), height.toFloat())
                else SquareCrop(aspectRatio = coverRatio).bounds(width, height))
    }

    fun viewport(width: Int, height: Int, viewWidth: Float, viewHeight: Float): CropViewport {
        val scale = min(viewWidth / width, viewHeight / height) * .9f * zoom
        return CropViewport(scale, (viewWidth - width * scale) / 2 + shiftX * scale,
            (viewHeight - height * scale) / 2 + shiftY * scale)
    }

    fun region(width: Int, height: Int) = CropRegion(selection.left / width, selection.top / height,
        selection.side / width, selection.height / height)
    fun crop(width: Int, height: Int) = SquareCrop.fromRegion(region(width, height), width, height)

    fun move(width: Int, height: Int, dx: Float, dy: Float): PhotoCropEditor = copy(selection = selection.copy(
        left = (selection.left + dx).coerceIn(0f, (width - selection.side).coerceAtLeast(0f)),
        top = (selection.top + dy).coerceIn(0f, (height - selection.height).coerceAtLeast(0f))))

    fun resize(width: Int, height: Int, corner: CropCorner, dx: Float, dy: Float): PhotoCropEditor {
        val right = selection.left + selection.side
        val bottom = selection.top + selection.height
        val minimumWidth = min(width / 64f, selection.side)
        val minimumHeight = min(height / 64f, selection.height)
        val leftEdge = corner == CropCorner.TOP_LEFT || corner == CropCorner.BOTTOM_LEFT
        val topEdge = corner == CropCorner.TOP_LEFT || corner == CropCorner.TOP_RIGHT
        val l = if (leftEdge) (selection.left + dx).coerceIn(0f, right - minimumWidth) else selection.left
        val t = if (topEdge) (selection.top + dy).coerceIn(0f, bottom - minimumHeight) else selection.top
        val r = if (!leftEdge) (right + dx).coerceIn(l + minimumWidth, width.toFloat()) else right
        val b = if (!topEdge) (bottom + dy).coerceIn(t + minimumHeight, height.toFloat()) else bottom
        return copy(selection = CropBounds(l, t, r - l, b - t))
    }

    /** Keep the frame stationary and move/scale the photo beneath it, clamped to cover every frame pixel. */
    fun transform(width: Int, height: Int, viewWidth: Float, viewHeight: Float,
        centroidX: Float, centroidY: Float, panX: Float, panY: Float, factor: Float): PhotoCropEditor {
        if (!factor.isFinite() || factor <= 0f) return this
        val old = viewport(width, height, viewWidth, viewHeight)
        val nextZoom = (zoom * factor).coerceIn(1f, 8f)
        val scale = old.scale * nextZoom / zoom
        val frameLeft = old.x + selection.left * old.scale
        val frameTop = old.y + selection.top * old.scale
        val frameWidth = selection.side * old.scale
        val frameHeight = selection.height * old.scale
        // A zoom-out cannot expose pixels outside the image behind the fixed frame.
        val safeScale = maxOf(scale, frameWidth / width, frameHeight / height)
        val x = (centroidX - (centroidX - old.x) * safeScale / old.scale + panX)
            .coerceIn(min(frameLeft, frameLeft + frameWidth - width * safeScale), frameLeft)
        val y = (centroidY - (centroidY - old.y) * safeScale / old.scale + panY)
            .coerceIn(min(frameTop, frameTop + frameHeight - height * safeScale), frameTop)
        val l = ((frameLeft - x) / safeScale).coerceIn(0f, width.toFloat())
        val t = ((frameTop - y) / safeScale).coerceIn(0f, height.toFloat())
        return PhotoCropEditor(CropBounds(l, t, min(frameWidth / safeScale, width - l),
            min(frameHeight / safeScale, height - t)), zoom * safeScale / old.scale,
            (x - (viewWidth - width * safeScale) / 2) / safeScale,
            (y - (viewHeight - height * safeScale) / 2) / safeScale)
    }
}
