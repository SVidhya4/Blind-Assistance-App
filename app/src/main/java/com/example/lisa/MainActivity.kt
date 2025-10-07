package com.example.lisa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnDescribe: Button
    private lateinit var tts: TextToSpeech
    private var detectionJob: Job? = null
    private var isDescribing = false
    private lateinit var voiceCommandReceiver: BroadcastReceiver

    // NEW: Add a variable for the ImageView
    private lateinit var previewImage: ImageView

    companion object {
        private const val TAG = "MainActivity"
        private const val STREAM_URL = "http://10.21.65.178:81/stream"
        private const val REQUEST_RECORD_AUDIO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnDescribe = findViewById(R.id.btnDescribe)
        // NEW: Initialize the ImageView
        previewImage = findViewById(R.id.previewImage)

        // REMOVED: All code for the GPU toggle has been deleted.

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
            }
        }

        checkAudioPermission()

        btnDescribe.setOnClickListener {
            if (!isDescribing) startDescribing() else stopDescribing()
        }

        voiceCommandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val command = intent?.getStringExtra("command")
                if (command == "DESCRIBE_SCENE" && !isDescribing) startDescribing()
            }
        }
        val filter = IntentFilter("VOICE_COMMAND")
        registerReceiver(voiceCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun startDescribing() {
        isDescribing = true
        runOnUiThread {
            statusText.text = "Fetching frame..."
            btnDescribe.text = "Stop"
            btnDescribe.isEnabled = false
        }

        detectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(STREAM_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                val mjpegStream = MjpegInputStream(BufferedInputStream(connection.inputStream))
                val bitmap: Bitmap? = mjpegStream.readMjpegFrame()
                connection.disconnect()

                if (bitmap != null) {
                    runOnUiThread {
                        // NEW: Display the captured frame in the ImageView
                        previewImage.setImageBitmap(bitmap)
                        statusText.text = "Frame captured. Captioning..."
                    }

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    val imageBytes = outputStream.toByteArray()

                    val client = OkHttpClient()
                    val requestBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("image", "frame.jpg", imageBytes.toRequestBody("image/jpeg".toMediaType()))
                        .build()

                    val serverUrl = "https://unnationally-gruffish-tama.ngrok-free.dev/caption"

                    val request = Request.Builder()
                        .url(serverUrl)
                        .post(requestBody)
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            Log.e(TAG, "API call failed: ${e.message}")
                            runOnUiThread { statusText.text = "Error: Network call failed." }
                            runOnUiThread { stopDescribing() }
                        }

                        override fun onResponse(call: Call, response: Response) {
                            val responseBody = response.body?.string()
                            if (response.isSuccessful && responseBody != null) {
                                val jsonObject = JSONObject(responseBody)
                                val caption = jsonObject.getString("caption")
                                runOnUiThread {
                                    statusText.text = caption // Caption stays visible
                                    speakOut(caption)
                                    // Optionally re-enable button without resetting statusText
                                    btnDescribe.isEnabled = true
                                    btnDescribe.text = "Describe Scene"
                                }
                            } else {
                                Log.e(TAG, "API call unsuccessful: ${response.code} ${response.message}")
                                runOnUiThread {
                                    statusText.text = "Error: ${response.code} ${response.message}"
                                    stopDescribing() // Reset UI only on error
                                }
                            }
                        }

                    })
                } else {
                    runOnUiThread {
                        statusText.text = "Could not fetch frame."
                        stopDescribing()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
                runOnUiThread {
                    statusText.text = "Error: ${e.message}"
                    stopDescribing()
                }
            }
        }
    }

    private fun stopDescribing() {
        isDescribing = false
        detectionJob?.cancel()
        runOnUiThread {
            statusText.text = "Tap 'Describe Scene' to start."
            btnDescribe.text = "Describe Scene"
            btnDescribe.isEnabled = true
        }
    }

    private fun speakOut(text: String) {
        if (text.isNotEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LISA_SAY")
        }
    }

    private fun checkAudioPermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(voiceCommandReceiver) } catch (_: Exception) {}
        tts.shutdown()
    }
}