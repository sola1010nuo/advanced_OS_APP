package com.example.petcollection.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.petcollection.CatColorClassifier
import androidx.compose.runtime.*
import com.example.petcollection.BreedClassifier
import com.example.petcollection.AnimalClassifier
import com.example.petcollection.data.RecognitionPolicy
import com.example.petcollection.data.Breed
import com.example.petcollection.data.Species
import com.example.petcollection.data.PhotoMetadata
import com.example.petcollection.ui.collection.CollectionUnlockState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    fun reset() {
        request++
        bitmap = null
        breedId = null
        confidence = null
        processing = false
        failed = false
    }

    suspend fun recognize(context: Context, uri: Uri, catalog: List<Breed>, unlockState: CollectionUnlockState,
        metadata: PhotoMetadata? = null) {
        val currentRequest = ++request
        unlockState.recognitionEffects.clear()
        bitmap = null
        breedId = null
        confidence = null
        failed = false
        processing = true
        try {
            val photo = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
            bitmap = photo
            val classification = withContext(Dispatchers.Default) {
                val animal = AnimalClassifier(context).use { it.classify(photo) }
                Log.d("PetRecognition", "animal top=${animal.category} confidence=${animal.confidence}")
                RecognitionPolicy.route(animal, catalog,
                    classifyColor = { CatColorClassifier(context).use { it.classify(photo) } },
                    trace = { Log.d("PetRecognition", it) }) { species ->
                    val type = if (species == Species.CAT) BreedClassifier.AnimalType.CAT else BreedClassifier.AnimalType.DOG
                    BreedClassifier(context, type).use { classifier ->
                        val result = classifier.classify(photo)
                        result.breed to result.confidence
                    }
                }
            }
            unlockState.collection.first { it.loaded }
            check(unlockState.onClassificationResult(classification.breedId, catalog, uri.toString(), classification.confidence, metadata)) { "Another encounter is pending" }
            breedId = classification.breedId
            confidence = classification.confidence
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
