//package com.example.lisa
//
//import android.content.BroadcastReceiver
//import android.content.Context
//import android.content.Intent
//import android.content.IntentFilter
//import android.widget.Toast
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.os.Build
//import android.os.Bundle
//import android.speech.tts.TextToSpeech
//import android.util.Log
//import android.widget.Button
//import android.widget.TextView
//import android.widget.ToggleButton
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.content.ContextCompat
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.launch
//import java.io.BufferedInputStream
//import java.net.HttpURLConnection
//import java.net.URL
//import java.util.Locale
//import java.util.concurrent.Executors
//
//class MainActivity : AppCompatActivity(), Detector.DetectorListener {
//
//    private lateinit var detector: Detector
//    private lateinit var statusText: TextView
//    private lateinit var btnDescribe: Button
//    private lateinit var toggleGpu: ToggleButton
//    private lateinit var tts: TextToSpeech
//
//    private var detectionJob: Job? = null
//    private var isDescribing = false
//
//    private lateinit var voiceCommandReceiver: BroadcastReceiver
//
//    companion object {
//        private const val TAG = "MainActivity"
//        private const val STREAM_URL = "http://10.255.248.178:81/stream" // change to your stream
//    }
//
//    private val REQUEST_RECORD_AUDIO = 1
//
//    private fun checkAudioPermission() {
//        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
//            != PackageManager.PERMISSION_GRANTED) {
//            requestPermissions(
//                arrayOf(android.Manifest.permission.RECORD_AUDIO),
//                REQUEST_RECORD_AUDIO
//            )
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<out String>,
//        grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == REQUEST_RECORD_AUDIO) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // ✅ Start service if granted
//                startService(Intent(this, VoiceRecognitionService::class.java))
//            } else {
//                Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        statusText = findViewById(R.id.statusText)
//        btnDescribe = findViewById(R.id.btnDescribe)
//        toggleGpu = findViewById(R.id.isGpu)
//
//        // Init detector
//        detector = Detector(this, Constants.MODEL_PATH, Constants.LABELS_PATH, this)
//
//        // Init TTS
//        tts = TextToSpeech(this) {
//            if (it == TextToSpeech.SUCCESS) {
//                tts.language = Locale.US
//            }
//        }
//
//        checkAudioPermission()
//
//        // Describe button
//        btnDescribe.setOnClickListener {
//            if (!isDescribing) startDescribing() else stopDescribing()
//        }
//
//        // ✅ GPU toggle button
//        val executor = Executors.newSingleThreadExecutor()
//        toggleGpu.setOnCheckedChangeListener { buttonView, isChecked ->
//            executor.submit { detector.restart(isGpu = isChecked) }
//            if (isChecked) {
//                buttonView.setBackgroundColor(
//                    ContextCompat.getColor(this, android.R.color.holo_green_dark)
//                )
//            } else {
//                buttonView.setBackgroundColor(
//                    ContextCompat.getColor(this, android.R.color.darker_gray)
//                )
//            }
//        }
//        toggleGpu.isChecked = false
//        toggleGpu.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
//
//        // ✅ Register receiver for voice commands (no version check)
//        voiceCommandReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context?, intent: Intent?) {
//                val command = intent?.getStringExtra("command")
//                when (command) {
//                    "DESCRIBE_SCENE" -> if (!isDescribing) startDescribing()
//                    "READ_TEXT" -> tts.speak("Sorry, read text not yet implemented", TextToSpeech.QUEUE_FLUSH, null, null)
//                }
//            }
//        }
//        val filter = IntentFilter("VOICE_COMMAND")
//        registerReceiver(voiceCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
//
//    }
//
//
//    private fun startDescribing() {
//        isDescribing = true
//        runOnUiThread {
//            statusText.text = "Describing scene..."
//            btnDescribe.text = "Stop"
//        }
//
//        detectionJob = CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val url = URL(STREAM_URL)
//                val connection = url.openConnection() as HttpURLConnection
//                connection.doInput = true
//                connection.connect()
//                val inputStream = BufferedInputStream(connection.inputStream)
//                val mjpegStream = MjpegInputStream(inputStream)
//
//                while (isDescribing) {
//                    val bitmap: Bitmap? = mjpegStream.readMjpegFrame()
//                    if (bitmap != null) {
//                        // run detection on the same IO coroutine thread
//                        detector.detect(bitmap)
//                    } else {
//                        runOnUiThread {
//                            statusText.text = "No frame available"
//                        }
//                    }
//                }
//
//                inputStream.close()
//                connection.disconnect()
//            } catch (e: Exception) {
//                Log.e(TAG, "Error fetching stream: ${e.message}")
//                runOnUiThread {
//                    statusText.text = "Error: ${e.message}"
//                    speakOut("Error in stream")
//                }
//            }
//        }
//    }
//
//    private fun stopDescribing() {
//        isDescribing = false
//        detectionJob?.cancel()
//        runOnUiThread {
//            statusText.text = "Stopped"
//            btnDescribe.text = "Describe scene"
//        }
//    }
//
//    // Detector.DetectorListener callbacks (called from detector thread)
//    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
//        if (boundingBoxes.isEmpty()) return
//
//        val descriptions = boundingBoxes.take(3).map { box -> box.clsName }
//        val desc = descriptions.joinToString(", ")
//
//        runOnUiThread {
//            statusText.text = "Detected: $desc"
//            speakOut(desc)
//        }
//    }
//
//
//    override fun onEmptyDetect() {
//        runOnUiThread {
//            statusText.text = "No objects detected"
//        }
//    }
//
////    private fun getPosition(cx: Float, cy: Float): String {
////        val horizontal = when {
////            cx < 0.33f -> "left"
////            cx > 0.66f -> "right"
////            else -> "center"
////        }
////        val vertical = when {
////            cy < 0.33f -> "top"
////            cy > 0.66f -> "bottom"
////            else -> "middle"
////        }
////        return "$vertical-$horizontal"
////    }
//
//    private fun speakOut(text: String) {
//        if (text.isNotEmpty()) {
//            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LISA_SAY")
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        try { unregisterReceiver(voiceCommandReceiver) } catch (_: Exception) {}
//        stopDescribing()
//        try { detector.close() } catch (_: Exception) {}
//        tts.shutdown()
//        val stopIntent = Intent(this, VoiceRecognitionService::class.java)
//        stopService(stopIntent)
//    }
//}


// MainActivity.kt
package com.example.lisa

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.ToggleButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_RECORD_AUDIO = 101
    }

    private lateinit var statusText: TextView
    private lateinit var btnDescribe: Button
    private lateinit var toggleGpu: ToggleButton

    private lateinit var captionHelper: CaptionHelper
    private lateinit var tts: TextToSpeech
    private lateinit var voiceCommandReceiver: BroadcastReceiver

    private var isDescribing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnDescribe = findViewById(R.id.btnDescribe)
        toggleGpu = findViewById(R.id.isGpu)

        // Init CaptionHelper (default: CPU)
        captionHelper = CaptionHelper(this)

        // Init TTS (modern init, no setAudioStreamType needed)
        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "TTS language not supported")
                }
            } else {
                Log.e(TAG, "TTS init failed")
            }
        }

        checkAudioPermission()

        // Describe button
        btnDescribe.setOnClickListener {
            if (!isDescribing) startDescribing() else stopDescribing()
        }

        // GPU toggle button (just cosmetic unless you implement GPU delegate in CaptionHelper)
        val executor = Executors.newSingleThreadExecutor()
        toggleGpu.setOnCheckedChangeListener { buttonView, isChecked ->
            executor.submit {
                captionHelper.restart(isChecked)   // 🔥 actually switch
            }
            runOnUiThread {
                buttonView.setBackgroundColor(
                    ContextCompat.getColor(
                        this,
                        if (isChecked) android.R.color.holo_green_dark else android.R.color.darker_gray
                    )
                )
            }
        }

        toggleGpu.isChecked = false
        toggleGpu.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        // ✅ Register receiver for voice commands
        voiceCommandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val command = intent?.getStringExtra("command")
                when (command) {
                    "DESCRIBE_SCENE" -> if (!isDescribing) startDescribing()
                    "READ_TEXT" -> speakOut("Sorry, read text not yet implemented")
                }
            }
        }
        val filter = IntentFilter("VOICE_COMMAND")
        registerReceiver(voiceCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(voiceCommandReceiver) } catch (_: Exception) {}
        stopDescribing()
        try { captionHelper.close() } catch (_: Exception) {}
        try { tts.stop(); tts.shutdown() } catch (_: Exception) {}
    }

    private fun startDescribing() {
        isDescribing = true
        statusText.text = "Describing scene..."

        // Example: capture a bitmap from camera / stream / placeholder
        val placeholderBitmap: Bitmap = Bitmap.createBitmap(299, 299, Bitmap.Config.ARGB_8888)
        val caption = captionHelper.generateCaption(placeholderBitmap)

        runOnUiThread {
            statusText.text = caption
            speakOut(caption)
            Toast.makeText(this, caption, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopDescribing() {
        isDescribing = false
        statusText.text = "Idle"
    }

    private fun speakOut(text: String) {
        if (text.isBlank()) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "CaptionUtteranceId")
    }

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQUEST_RECORD_AUDIO
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Audio permission required for voice features", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
