package com.example.lisa

import android.content.Context
import android.util.Log
import android.widget.Toast
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

object VoiceRecognition {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isRunning = false
    private val triggerWords= mutableListOf<String>()  //Helps when you want to reload from room database otherwise make a static list

// Use it to initialize the object's first instance
    fun start(context: Context) {
        if (isRunning) return
        val appContext = context.applicationContext
        StorageService.unpack(context, "model", "model",
            { unpackedModel ->
                model = unpackedModel
                isRunning = true
                Log.d("VoiceService", "Model unpacked and ready!")
                startListening(appContext)

            },
            { exception ->
                Log.e("VoiceRecognizer", "Model error: ${exception.message}")
            })
    }

    private fun startListening(context: Context) {

        val recognizer = Recognizer(model, 16000.0f)

        speechService = SpeechService(recognizer, 16000.0f)

        speechService?.startListening(object : RecognitionListener {

            override fun onPartialResult(hypothesis: String?) {

                Log.d("Vosk Partial", hypothesis ?: "")

                // Execute your code inside the check
                if (triggerWords.any{ hypothesis?.contains(it, ignoreCase = true) == true })
                {
                    Toast.makeText(context, " Request Word detected", Toast.LENGTH_SHORT).show()
                    pause()
                }
            }

            override fun onResult(hypothesis: String?) {}
            override fun onFinalResult(hypothesis: String?) {}
            override fun onError(e: Exception?) {
                Log.e("VoskError", e.toString())
            }

            override fun onTimeout() {}
        })
    }

    // pause it to free resources which needed to be used by another task
    fun pause() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        isRunning = false
        Log.d("Voice Recognition", "Paused")
    }


    // Use it to resume the listening after manually paused but in case of App restarts Use start
    fun resume(context: Context ) {
        if (!isRunning && model != null) {
            startListening(context)
            isRunning = true
            Log.d("Voice Recognition", "Resumed")
        }
    }

    // Use it to stop
    fun stop() {
        isRunning = false
        speechService?.stop()
        speechService?.shutdown()
        model?.close()
        speechService = null
        model = null
        Log.d("Voice Recognition", "Stopped")
    }
}