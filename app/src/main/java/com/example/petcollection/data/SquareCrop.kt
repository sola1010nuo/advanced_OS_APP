package com.example.petcollection.data

import kotlin.math.min

/** Coordinates are normalized to the EXIF-oriented source, so resizing the UI cannot change the crop. */
// The historical name is retained for existing callers; aspectRatio supports rectangular photos.
data class SquareCrop(val centerX: Float = .5f, val centerY: Float = .5f, val zoom: Float = 1f,
    val aspectRatio: Float = 1f) {
    init { require(aspectRatio.isFinite() && aspectRatio > 0f) }
    fun bounds(width: Int, height: Int): CropBounds {
        require(width > 0 && height > 0)
        val side = min(width.toFloat(), height * aspectRatio) / zoom.coerceIn(1f, 8f)
        val cropHeight = min(height.toFloat(), side / aspectRatio)
        val x = (centerX * width).coerceIn(side / 2, width - side / 2)
        val y = (centerY * height).coerceIn(cropHeight / 2, height - cropHeight / 2)
        return CropBounds(x - side / 2, y - cropHeight / 2, side, cropHeight)
    }

    /** Keep the point under the pinch centroid stable, then pan and constrain coverage to the frame. */
    fun gesture(width: Int, height: Int, frame: Float, centroidX: Float, centroidY: Float,
        panX: Float, panY: Float, zoomFactor: Float): SquareCrop {
        if (frame <= 0 || !zoomFactor.isFinite() || zoomFactor <= 0) return this
        val old = bounds(width, height)
        val nextZoom = (zoom * zoomFactor).coerceIn(1f, 8f)
        val side = min(width.toFloat(), height * aspectRatio) / nextZoom
        val cropHeight = min(height.toFloat(), side / aspectRatio)
        val frameHeight = frame / aspectRatio
        val x = old.left + old.side / 2 + (centroidX / frame - .5f) * (old.side - side) - panX / frame * side
        val y = old.top + old.height / 2 + (centroidY / frameHeight - .5f) * (old.height - cropHeight) - panY / frameHeight * cropHeight
        return SquareCrop(x.coerceIn(side / 2, width - side / 2) / width,
            y.coerceIn(cropHeight / 2, height - cropHeight / 2) / height, nextZoom, aspectRatio)
    }
}

data class CropBounds(val left: Float, val top: Float, val side: Float, val height: Float = side)
