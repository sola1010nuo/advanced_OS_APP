package com.example.petcollection.ui.collection

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import com.example.petcollection.data.Breed
import com.example.petcollection.data.BreedPhoto
import com.example.petcollection.data.Species
import com.example.petcollection.data.PhotoCropIO
import com.example.petcollection.data.SquareCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared cover/gallery area. Always preserve the complete confirmed crop, including legacy aspect ratios.
 * Locked entries never load or display a collected photo.
 */
@Composable
internal fun BreedImageArea(
    breed: Breed,
    photo: BreedPhoto?,
    locked: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photoUri = if (locked) null else photo?.uri
    val cropRegion = photo?.cropRegion
    val bitmap by produceState<ImageBitmap?>(null, photoUri, cropRegion, context) {
        value = null
        value = withContext(Dispatchers.IO) {
            val uri = photoUri?.let(Uri::parse)
            if (uri == null || uri.scheme !in listOf("content", "file", "android.resource")) {
                null
            } else {
                // Use two streams for bounds and decoding; sample large camera images.
                runCatching {
                    if (cropRegion != null) {
                        // Use the identical EXIF-oriented source and renderer as the cover editor.
                        val source = PhotoCropIO.prepare(context, uri).bitmap
                        return@runCatching try {
                            PhotoCropIO.render(source, SquareCrop.fromRegion(cropRegion, source.width, source.height)).asImageBitmap()
                        } finally { source.recycle() }
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    }
                    var sampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 1024) {
                        sampleSize *= 2
                    }
                    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    val colors = MaterialTheme.colorScheme
    val isCat = breed.species == Species.CAT
    val background = if (locked) colors.surfaceContainerHighest
        else if (isCat) colors.secondaryContainer else colors.tertiaryContainer
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (!locked && image != null) {
            Image(image, contentDescription = stringResource(R.string.breed_photo_description, breed.name),
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else if (breed.species == null) {
            Text(stringResource(R.string.breed_photo_placeholder), color = colors.onSurfaceVariant)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val fur = if (locked) colors.outline else if (isCat) colors.secondary else colors.tertiary
                Canvas(Modifier.size(64.dp)) {
                    val w = size.width
                    val h = size.height
                    if (isCat) {
                        drawPath(Path().apply {
                            moveTo(w * .12f, h * .48f)
                            lineTo(w * .12f, h * .08f)
                            lineTo(w * .45f, h * .32f)
                            close()
                            moveTo(w * .55f, h * .32f)
                            lineTo(w * .88f, h * .08f)
                            lineTo(w * .88f, h * .48f)
                            close()
                        }, fur)
                    } else {
                        drawOval(fur, Offset(0f, h * .16f), Size(w * .3f, h * .64f))
                        drawOval(fur, Offset(w * .7f, h * .16f), Size(w * .3f, h * .64f))
                    }
                    drawOval(fur, Offset(w * .12f, h * .23f), Size(w * .76f, h * .68f))
                    if (!locked) {
                        drawCircle(colors.surface, w * .035f, Offset(w * .35f, h * .53f))
                        drawCircle(colors.surface, w * .035f, Offset(w * .65f, h * .53f))
                        drawOval(colors.surface, Offset(w * .36f, h * .62f), Size(w * .28f, h * .2f))
                    }
                }
                Text(stringResource(if (locked) R.string.breed_undiscovered else R.string.breed_photo_placeholder),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
