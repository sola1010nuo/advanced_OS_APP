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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.petcollection.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SelectedPhotoPreview(uri: Uri) {
    val resolver = LocalContext.current.contentResolver
    var loading by remember(uri) { mutableStateOf(true) }
    val bitmap by produceState<Bitmap?>(null, uri, resolver) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
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
    OutlinedCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.selected_photo), style = MaterialTheme.typography.titleMedium)
            val photo = bitmap
            if (photo != null) {
                Image(photo.asImageBitmap(), contentDescription = stringResource(R.string.selected_photo),
                    modifier = Modifier.fillMaxWidth().height(240.dp), contentScale = ContentScale.Fit)
            } else if (loading) {
                CircularProgressIndicator()
            } else {
                Text(stringResource(R.string.photo_preview_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
