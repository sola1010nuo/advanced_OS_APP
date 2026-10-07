package com.example.petcollection.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import com.example.petcollection.BreedClassifier
import com.example.petcollection.data.Breed
import com.example.petcollection.data.ModelBreedMapping
import com.example.petcollection.data.Species
import com.example.petcollection.ui.collection.CollectionUnlockState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-scoped UI session; tab changes do not repeat recognition or create another draft. */
class PhotoRecognitionState {
    private var request = 0
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var breedId by mutableStateOf<String?>(null)
        private set
    var confidence by mutableStateOf<Float?>(null)
        private set
    var processing by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    suspend fun recognize(context: Context, uri: Uri, catalog: List<Breed>, unlockState: CollectionUnlockState) {
        val currentRequest = ++request
        bitmap = null
        breedId = null
        confidence = null
        failed = false
        processing = true
        try {
            val photo = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
            bitmap = photo
            val (species, prediction) = withContext(Dispatchers.Default) {
                val type = BreedClassifier.detectAnimal(context, photo)
                val classifier = BreedClassifier(context, type)
                try {
                    (if (type == BreedClassifier.AnimalType.CAT) Species.CAT else Species.DOG) to classifier.classify(photo)
                } finally {
                    classifier.close()
                }
            }
            val breed = requireNotNull(ModelBreedMapping.resolve(prediction.breed, species, catalog)) {
                "Model label has no catalog entry: ${prediction.breed}"
            }
            check(unlockState.onBreedRecognized(breed, uri.toString())) { "Another encounter is pending" }
            breedId = breed.id
            confidence = prediction.confidence
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        } finally {
            if (request == currentRequest) processing = false
        }
    }
}

private fun decodePhoto(context: Context, uri: Uri): Bitmap {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= 28) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = (1024f / maxOf(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1))
        }
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    options.inJustDecodeBounds = false
    options.inSampleSize = 1
    while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024) options.inSampleSize *= 2
    return requireNotNull(resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) })
}
