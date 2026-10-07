package com.example.petcollection

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the shipped native models rather than mocking classifier output. */
class BreedClassifierInferenceTest {
    @Test fun bothModelsProduceLabelAndProbabilityOffMainThread() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        photo.eraseColor(Color.rgb(180, 120, 60))
        try {
            for (type in BreedClassifier.AnimalType.entries) {
                val result = withContext(Dispatchers.Default) {
                    assertTrue(android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
                    val classifier = BreedClassifier(context, type)
                    try { classifier.classify(photo) } finally { classifier.close() }
                }
                val labelsFile = if (type == BreedClassifier.AnimalType.CAT) "cat_labels.txt" else "dog_labels.txt"
                val labels = context.assets.open(labelsFile).bufferedReader().use { it.readLines() }
                assertTrue(result.breed in labels)
                assertTrue(result.confidence.isFinite() && result.confidence in 0f..1f)
            }
        } finally {
            photo.recycle()
        }
    }
}
