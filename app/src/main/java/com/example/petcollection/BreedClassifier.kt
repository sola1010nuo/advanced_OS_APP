package com.example.petcollection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class BreedClassifier(
    private val context: Context,
    private val animalType: AnimalType
) {
    companion object {
        fun detectAnimal(context: Context, bitmap: Bitmap): AnimalType {
            val labels = context.assets.open("cat_dog_labels.txt").bufferedReader().use { it.readLines() }
            val bytes = context.assets.open("cat_dog_model.tflite").use { it.readBytes() }
            val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            model.put(bytes).rewind()
            Interpreter(model).use { detector ->
                require(detector.getInputTensor(0).shape().contentEquals(intArrayOf(1, 224, 224, 3)))
                require(detector.getOutputTensor(0).shape().contentEquals(intArrayOf(1, labels.size)))
                val output = Array(1) { FloatArray(labels.size) }
                detector.run(rgbInput(bitmap), output)
                val best = output[0].indices.maxBy { output[0][it] }
                return when (labels[best].trim().lowercase(java.util.Locale.ROOT)) {
                    "cat" -> AnimalType.CAT
                    "dog" -> AnimalType.DOG
                    else -> error("Unexpected animal label")
                }
            }
        }

        /** Preserve the exported model contract: 224x224 RGB floats in the 0–255 range. */
        private fun rgbInput(bitmap: Bitmap): ByteBuffer {
            val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
            try {
                val buffer = ByteBuffer.allocateDirect(224 * 224 * 3 * 4).order(ByteOrder.nativeOrder())
                for (y in 0 until 224) for (x in 0 until 224) {
                    val pixel = resized.getPixel(x, y)
                    buffer.putFloat(((pixel shr 16) and 0xFF).toFloat())
                    buffer.putFloat(((pixel shr 8) and 0xFF).toFloat())
                    buffer.putFloat((pixel and 0xFF).toFloat())
                }
                buffer.rewind()
                return buffer
            } finally {
                if (resized !== bitmap) resized.recycle()
            }
        }
    }

    enum class AnimalType {
        CAT, DOG
    }

    data class Result(
        val breed: String,
        val confidence: Float
    )

    private val modelFileName =
        if (animalType == AnimalType.CAT) "cat_model.tflite"
        else "dog_model.tflite"

    private val labelFileName =
        if (animalType == AnimalType.CAT) "cat_labels.txt"
        else "dog_labels.txt"

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        labels = context.assets
            .open(labelFileName)
            .bufferedReader()
            .use { it.readLines() }

        interpreter = Interpreter(loadModelFile(modelFileName))
    }

    fun classify(bitmap: Bitmap): Result {
        val inputBuffer = rgbInput(bitmap)

        val output =
            Array(1) { FloatArray(labels.size) }

        interpreter.run(inputBuffer, output)

        val probabilities = output[0]

        var bestIndex = 0

        for (i in probabilities.indices) {
            if (probabilities[i] > probabilities[bestIndex]) {
                bestIndex = i
            }
        }

        return Result(
            breed = labels[bestIndex],
            confidence = probabilities[bestIndex]
        )
    }

    private fun loadModelFile(fileName: String): ByteBuffer {

        context.assets.openFd(fileName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->

                val fileChannel = inputStream.channel

                return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    fun close() {
        interpreter.close()
    }
}
