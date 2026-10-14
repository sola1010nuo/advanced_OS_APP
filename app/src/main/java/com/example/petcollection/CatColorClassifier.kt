package com.example.petcollection

import android.content.Context
import android.graphics.Bitmap
import com.example.petcollection.data.RecognitionPolicy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exported graph already performs RGB / 127.5 - 1 and Softmax. */
class CatColorClassifier(context: Context) : Closeable {
    private val labels = context.assets.open("cat_color_labels.txt").bufferedReader(Charsets.UTF_8)
        .use { it.readLines().map(String::trim) }
    private val interpreter: Interpreter

    init {
        require(labels == RecognitionPolicy.colorAlbumIds.keys.toList()) { "Unexpected cat color label order" }
        val bytes = context.assets.open("cat_color.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes).rewind()
        val detector = Interpreter(model)
        try {
            require(detector.inputTensorCount == 1 && detector.outputTensorCount == 1)
            require(detector.getInputTensor(0).dataType() == DataType.FLOAT32)
            require(detector.getOutputTensor(0).dataType() == DataType.FLOAT32)
            require(detector.getInputTensor(0).shape().contentEquals(intArrayOf(1, 224, 224, 3)))
            require(detector.getOutputTensor(0).shape().contentEquals(intArrayOf(1, labels.size)))
            interpreter = detector
        } catch (error: Throwable) {
            detector.close()
            throw error
        }
    }

    fun classify(bitmap: Bitmap): Pair<String, Float> {
        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(BreedClassifier.rgbInput(bitmap), output)
        val scores = output[0]
        require(scores.all { it.isFinite() && it in 0f..1f })
        require(kotlin.math.abs(scores.sum() - 1f) < .01f)
        val best = scores.indices.maxBy { scores[it] }
        return labels[best] to scores[best]
    }

    override fun close() = interpreter.close()
}
