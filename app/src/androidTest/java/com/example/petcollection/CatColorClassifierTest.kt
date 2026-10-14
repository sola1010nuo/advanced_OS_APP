package com.example.petcollection

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.petcollection.data.RecognitionPolicy
import org.junit.Assert.*
import org.junit.Test

class CatColorClassifierTest {
    @Test fun bundledModelHasExpectedLabelsAndProducesProbabilities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        try {
            CatColorClassifier(context).use { classifier ->
                for (color in listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)) {
                    bitmap.eraseColor(color)
                    val (label, confidence) = classifier.classify(bitmap)
                    assertTrue(label in RecognitionPolicy.colorAlbumIds)
                    assertTrue(confidence.isFinite() && confidence in 0f..1f)
                }
            }
        } finally { bitmap.recycle() }
    }
}
