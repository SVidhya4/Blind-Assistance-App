package com.example.lisa

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

class CaptionHelper(
    private val context: Context,
    private val cnnModelAssetName: String = "preprocessed_model_cnn.tflite",
    private val rnnModelAssetName: String = "model_nlp.tflite",
    private val inputImageSize: Int = 299
) {
    companion object {
        private const val TAG = "CaptionHelper"
    }

    private var cnnInterpreter: Interpreter? = null
    private var rnnInterpreter: Interpreter? = null

    private var wToI: JSONObject? = null
    private var iToW: JSONObject? = null

    init {
        try {
            SingletonJSONS.init(context.applicationContext)
            wToI = SingletonJSONS.w_to_i_jObj
            iToW = SingletonJSONS.i_to_w_jObj
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load mapping JSONs: ${e.message}")
        }

        cnnInterpreter = Interpreter(loadModelFile(context, cnnModelAssetName))
        rnnInterpreter = Interpreter(loadModelFile(context, rnnModelAssetName))
    }

    fun close() {
        cnnInterpreter?.close()
        rnnInterpreter?.close()
        cnnInterpreter = null
        rnnInterpreter = null
    }

    fun restart(useGpu: Boolean) {
        // Close existing interpreters & delegate
        close()

        val options = Interpreter.Options().apply {
            if (useGpu) {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    gpuDelegate = GpuDelegate(delegateOptions)
                    addDelegate(gpuDelegate)
                    Log.i(TAG, "Restarted with GPU delegate")
                } else {
                    setNumThreads(numThreads)
                    Log.i(TAG, "GPU not supported, falling back to CPU")
                }
            } else {
                setNumThreads(numThreads)
                Log.i(TAG, "Restarted with CPU ($numThreads threads)")
            }
        }

        try {
            cnnInterpreter = Interpreter(loadModelFile(context, cnnModelAssetName), options)
            rnnInterpreter = Interpreter(loadModelFile(context, rnnModelAssetName), options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recreate interpreters: ${e.message}")
        }
    }


    fun generateCaption(bitmap: Bitmap): String {
        val cnn = cnnInterpreter ?: return ""
        val rnn = rnnInterpreter ?: return ""

        // Step 1: Run CNN
        val resized = Bitmap.createScaledBitmap(bitmap, inputImageSize, inputImageSize, true)
        val tensorImage = TensorImage(DataType.UINT8)
        tensorImage.load(resized)

        val cnnInput = TensorBuffer.createFixedSize(intArrayOf(1, inputImageSize, inputImageSize, 3), DataType.UINT8)
        cnnInput.loadBuffer(tensorImage.buffer)

        val cnnOutputShape = cnn.getOutputTensor(0).shape()
        val cnnOut = TensorBuffer.createFixedSize(cnnOutputShape, DataType.FLOAT32)
        cnn.run(cnnInput.buffer, cnnOut.buffer.rewind())

        // Step 2: Prepare sequence buffer
        val seqLen = 33
        val sequence = FloatArray(seqLen) { 0f }
        val resultWords = mutableListOf<String>()

        // Step 3: Iteratively predict
        for (step in 0 until 20) {
            val seqBuffer = ByteBuffer.allocateDirect(seqLen * 4).order(ByteOrder.nativeOrder())
            sequence.forEach { seqBuffer.putFloat(it) }

            val inputSeq = TensorBuffer.createFixedSize(intArrayOf(1, seqLen), DataType.FLOAT32)
            inputSeq.loadBuffer(seqBuffer)

            val inputs = arrayOf(inputSeq.buffer, cnnOut.buffer)

            val rnnOutputShape = rnn.getOutputTensor(0).shape()
            val outTensor = TensorBuffer.createFixedSize(rnnOutputShape, DataType.FLOAT32)

            rnn.run(inputs, outTensor.buffer.rewind())

            val probs = outTensor.floatArray
            val bestIdx = probs.indices.maxByOrNull { probs[it] } ?: 0

            // Stop if <end> token
            if (bestIdx == 13) break

            // Map index → word
            val word = iToW?.optString(bestIdx.toString(), "<unk>") ?: "<unk>"
            resultWords.add(word)

            // Shift sequence left, append new token
            for (i in 0 until seqLen - 1) sequence[i] = sequence[i + 1]
            sequence[seqLen - 1] = bestIdx.toFloat()
        }

        return resultWords.joinToString(" ")
    }

    private fun loadModelFile(context: Context, assetFileName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(assetFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val mapped = fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
        inputStream.close()
        return mapped
    }
}
