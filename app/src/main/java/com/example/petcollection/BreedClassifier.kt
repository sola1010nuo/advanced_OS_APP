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
        interpreter = Interpreter(loadModelFile(modelFileName))

        labels = context.assets
            .open(labelFileName)
            .bufferedReader()
            .readLines()
    }

    fun classify(bitmap: Bitmap): Result {

        // 模型訓練時使用 224 x 224
        val resizedBitmap =
            Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        val inputBuffer =
            ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
                .order(ByteOrder.nativeOrder())

        // 將圖片轉成模型輸入
        for (y in 0 until 224) {
            for (x in 0 until 224) {

                val pixel = resizedBitmap.getPixel(x, y)

                val r = ((pixel shr 16) and 0xFF).toFloat()
                val g = ((pixel shr 8) and 0xFF).toFloat()
                val b = (pixel and 0xFF).toFloat()

                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }

        inputBuffer.rewind()

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

        val fileDescriptor =
            context.assets.openFd(fileName)

        FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->

            val fileChannel = inputStream.channel

            return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.declaredLength
            )
        }
    }

    fun close() {
        interpreter.close()
    }
}