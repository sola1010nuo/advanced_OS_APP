package com.example.petcollection.data

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

data class PhotoMetadata(val capturedAt: LocalDateTime? = null, val timeZoneId: String? = null,
    val latitude: Double? = null, val longitude: Double? = null)
data class CropSource(val bitmap: Bitmap, val metadata: PhotoMetadata)
data class CroppedPhoto(val file: File, val metadata: PhotoMetadata) {
    val uri: Uri get() = Uri.fromFile(file)
}

/** Reads originals only; the sole written image is the confirmed crop in an app-cache session. */
object PhotoCropIO {
    private val initializedRoots = mutableSetOf<String>()
    @Synchronized fun sessionDirectory(context: Context): File {
        val root = File(context.cacheDir, "photo_crops").apply { mkdirs() }
        if (initializedRoots.add(root.canonicalPath)) {
            // Only our flat cache sessions from a previous process; never touch collection storage or the gallery.
            root.listFiles()?.filter { it.isDirectory && it.name.startsWith("crop-") }?.forEach(::deleteSession)
        }
        return File(root, "crop-${UUID.randomUUID()}").apply { mkdirs() }
    }
    fun deleteSession(directory: File) {
        directory.listFiles()?.filter { it.isFile && it.name.endsWith(".png") }?.forEach { it.delete() }
        directory.delete()
    }

    fun prepare(context: Context, uri: Uri): CropSource {
        val exif = runCatching { context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()
        val formatter = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)
        val capturedAt = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
            runCatching { LocalDateTime.parse(it.trim(), formatter) }.getOrNull()
        }
        val zone = exif?.getAttribute("OffsetTimeOriginal")?.let { runCatching { ZoneOffset.of(it.trim()).id }.getOrNull() }
        val coordinates = FloatArray(2)
        val hasGps = exif?.getLatLong(coordinates) == true
        val metadata = PhotoMetadata(capturedAt, zone, coordinates[0].toDouble().takeIf { hasGps }, coordinates[1].toDouble().takeIf { hasGps })
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(requireNotNull(it), null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported image" }
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2560) options.inSampleSize *= 2
        val decoded = requireNotNull(context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(requireNotNull(it), null, options) })
        val matrix = Matrix().apply {
            when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (oriented !== decoded) decoded.recycle()
        return CropSource(oriented, metadata)
    }

    /** Shared by the preview canvas and export: identical floating-point source rectangle, scale and translation. */
    fun draw(canvas: Canvas, bitmap: Bitmap, crop: SquareCrop, edge: Float) {
        val bounds = crop.bounds(bitmap.width, bitmap.height)
        val scale = edge / bounds.side
        // Cover the entire floating-point selection even when integer output dimensions round differently.
        val scaleY = canvas.height / bounds.height
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, Matrix().apply {
            setScale(scale, scaleY)
            postTranslate(-bounds.left * scale, -bounds.top * scaleY)
        }, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    fun render(bitmap: Bitmap, crop: SquareCrop): Bitmap {
        val bounds = crop.bounds(bitmap.width, bitmap.height)
        val scale = min(1f, 1024f / maxOf(bounds.side, bounds.height))
        // Keep the collection's 4:3 output exact even when the source crop has fractional dimensions.
        val isFourByThree = crop.aspectRatio == 4f / 3f
        val width = if (isFourByThree) ((bounds.side * scale).toInt() / 4).coerceAtLeast(1) * 4
            else (bounds.side * scale).toInt().coerceAtLeast(1)
        val height = if (isFourByThree) width / 4 * 3 else (width / crop.aspectRatio).roundToInt().coerceIn(1, 1024)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(result), bitmap, crop, width.toFloat())
        return result
    }

    fun export(source: CropSource, crop: SquareCrop, directory: File): CroppedPhoto {
        val result = render(source.bitmap, crop)
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(result.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return CroppedPhoto(file, source.metadata)
        } catch (error: Throwable) { file.delete(); throw error }
        finally { result.recycle() }
    }
}
