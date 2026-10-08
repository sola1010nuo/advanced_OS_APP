package com.example.petcollection

import android.content.Context
import android.graphics.Bitmap
import com.example.petcollection.data.AnimalCategory
import com.example.petcollection.data.AnimalPrediction
import com.example.petcollection.data.RecognitionPolicy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** First-stage classifier only. The exported graph contains its own RGB rescaling and Softmax. */
class AnimalClassifier(context: Context) : Closeable {
    companion object {
        const val MODEL_ASSET = "cat_dog_monkey_model.tflite"
        const val LABELS_ASSET = "cat_dog_monkey_labels.txt"
    }
    private val labels = context.assets.open(LABELS_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readLines().map { it.trim().lowercase(Locale.ROOT) }
    }
    private val interpreter: Interpreter
    private val width: Int
    private val height: Int

    init {
        require(labels == AnimalCategory.entries.map { it.modelLabel })
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        model.put(bytes).rewind()
        val detector = Interpreter(model)
        try {
            require(detector.inputTensorCount == 1 && detector.outputTensorCount == 1)
            val input = detector.getInputTensor(0)
            val output = detector.getOutputTensor(0)
            require(input.dataType() == DataType.FLOAT32 && output.dataType() == DataType.FLOAT32)
            val shape = input.shape()
            require(shape.contentEquals(intArrayOf(1, 224, 224, 3))) { "Unexpected animal model input shape" }
            require(output.shape().contentEquals(intArrayOf(1, labels.size))) { "Unexpected animal model output shape" }
            height = shape[1]
            width = shape[2]
            interpreter = detector
        } catch (error: Throwable) {
            detector.close()
            throw error
        }
    }

    fun classify(bitmap: Bitmap): AnimalPrediction {
        val probabilities = Array(1) { FloatArray(labels.size) }
        // Supply RGB pixels in [0, 255]. The graph performs x / 127.5 - 1 internally.
        interpreter.run(BreedClassifier.rgbInput(bitmap, width, height), probabilities)
        return RecognitionPolicy.fromProbabilities(labels, probabilities[0])
    }

    override fun close() = interpreter.close()
}