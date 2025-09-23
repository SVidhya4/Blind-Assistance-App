//package com.example.lisa
//
//
//import android.app.*
//import android.content.Intent
//import android.os.*
//import android.util.Log
//import android.widget.Toast
//import androidx.core.app.NotificationCompat
//import org.vosk.*
//import org.vosk.android.RecognitionListener
//import org.vosk.android.SpeechService
//import org.vosk.android.StorageService
//
//class VoiceWakeupService : Service() {
//
//    private lateinit var model: Model
//    private lateinit var speechService: SpeechService
//
//    override fun onCreate() {
//        super.onCreate()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                "WakeupChannel",
//                "Voice Wakeup Service",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            val manager = getSystemService(NotificationManager::class.java)
//            manager.createNotificationChannel(channel)
//        }
//        Log.d("VoiceService", "Service onCreate()")
//
//        val notification = NotificationCompat.Builder(this, "WakeupChannel")
//            .setContentTitle("Voice Wakeup Active")
//            .setSmallIcon(R.drawable.ic_launcher_foreground)
//            .build()
//
//        startForeground(101, notification)
//        Log.d("VoiceService", "Foreground notification started")
//
//        Thread {
//            try {
//                StorageService.unpack(this, "model", "model",
//                    { model ->
//                        Log.d("VoiceService", "Model unpacked and ready!")
//                        this.model = model
//                        startListening()
//                    },
//                    { exception ->
//                        Log.e("VoiceService", "Error loading model: ${exception.message}")
//                    }
//                )
//            } catch (e: Exception) {
//                Log.e("VoiceService", "Model loading failed: ${e.message}")
//            }
//        }.start()
//    }
//
//    private fun startListening() {
//        val recognizer = Recognizer(model, 16000.0f)
//        speechService = SpeechService(recognizer, 16000.0f)
//
//        speechService.startListening(object : RecognitionListener {
//            override fun onPartialResult(hypothesis: String?) {
//                hypothesis?.let {
//                    Log.d("VoskPartial", it)
//                    if (it.contains("Listen", true)) {  // Wake word
//                        youraction()
//                    }
//                }
//            }
//
//            override fun onResult(hypothesis: String?) {}
//            override fun onFinalResult(hypothesis: String?) {}
//            override fun onError(e: Exception?) {
//                Log.e("VoskError", e.toString())
//            }
//
//            override fun onTimeout() {}
//        })
//    }
//
//    private fun youraction() {
//        Toast.makeText(this, "Voice Word Detected", Toast.LENGTH_SHORT).show()
//    }
//
//    override fun onDestroy() {
//        speechService.stop()
//        speechService.shutdown()
//        model.close()
//        super.onDestroy()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//}

package com.example.lisa

import android.app.*
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.vosk.*
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

class VoiceRecognitionService : Service() {

    private lateinit var model: Model
    private lateinit var speechService: SpeechService
    private var isListeningForCommand = false   // <-- new flag

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "WakeupChannel",
            "Voice Wakeup Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        Log.d("VoiceService", "Service onCreate()")

        val notification = NotificationCompat.Builder(this, "WakeupChannel")
            .setContentTitle("Voice Recognition Active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(101, notification)
        Log.d("VoiceService", "Foreground notification started")

        Thread {
            try {
                StorageService.unpack(this, "model", "model",
                    { model ->
                        Log.d("VoiceService", "Model unpacked and ready!")
                        this.model = model
                        startListening()
                    },
                    { exception ->
                        Log.e("VoiceService", "Error loading model: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e("VoiceService", "Model loading failed: ${e.message}")
            }
        }.start()
    }

    private fun startListening() {
        val recognizer = Recognizer(model, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f)

        speechService.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let {
                    Log.d("VoskPartial", it)

                    try {
                        val partial = org.json.JSONObject(it).optString("partial", "")
                        if (partial.isNotEmpty()) {

                            // 1. Wake word detection (flexible match)
                            if (!isListeningForCommand && partial.contains("hey lisa", ignoreCase = true)) {
                                isListeningForCommand = true
                                Toast.makeText(
                                    this@VoiceRecognitionService,
                                    "Wake word detected",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return
                            }

                            // 2. Command detection (after wake word)
                            if (isListeningForCommand) {
                                when {
                                    partial.contains("describe", ignoreCase = true) &&
                                            partial.contains("scene", ignoreCase = true) -> {
                                        sendCommandToActivity("DESCRIBE_SCENE")
                                        isListeningForCommand = false
                                    }
                                    partial.contains("read", ignoreCase = true) &&
                                            partial.contains("text", ignoreCase = true) -> {
                                        sendCommandToActivity("READ_TEXT")
                                        isListeningForCommand = false
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VoiceService", "JSON parse error: ${e.message}")
                    }
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

    private fun sendCommandToActivity(command: String) {
        val intent = Intent("VOICE_COMMAND")
        intent.putExtra("command", command)
        sendBroadcast(intent)
        Toast.makeText(this, "Command: $command", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        speechService.stop()
        speechService.shutdown()
        model.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
