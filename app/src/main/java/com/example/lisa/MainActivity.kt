package com.example.lisa

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.animation.AnimatorSet
import androidx.appcompat.widget.Toolbar
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // Splash UI Views
    private lateinit var splashLogo: ImageView
    private lateinit var splashTitle: TextView
    private lateinit var splashUiGroup: List<View>

    // Main UI Views
    private lateinit var mainUiGroup: List<View>
    private lateinit var statusText: TextView
    private lateinit var btnDescribe: Button
    private lateinit var btnReadText: Button
    private lateinit var previewImage: ImageView

    // Other components
    private lateinit var tts: TextToSpeech
    private var detectionJob: Job? = null
    private var isProcessing = false
    private lateinit var voiceCommandReceiver: BroadcastReceiver
    private var isContinuouslyDescribing = false
    private val animationScope = CoroutineScope(Dispatchers.Main)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "MainActivity"
        private const val STREAM_URL = "http://10.136.195.178:81/stream"
        private const val CAPTION_SERVER_URL = "https://unnationally-gruffish-tama.ngrok-free.dev/caption"
        private const val OCR_SERVER_URL = "https://unnationally-gruffish-tama.ngrok-free.dev/read_text"
        private const val REQUEST_RECORD_AUDIO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // No installSplashScreen() needed for this custom animation
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Initialize ALL Views ---
        // Splash UI
        splashLogo = findViewById(R.id.splash_logo)
        splashTitle = findViewById(R.id.splash_title)
        splashUiGroup = listOf(splashLogo, splashTitle)

        // Main UI
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val controlsCard: View = findViewById(R.id.controls_card)
        previewImage = findViewById(R.id.previewImage)
        mainUiGroup = listOf(toolbar, previewImage, controlsCard)

        statusText = findViewById(R.id.statusText)
        btnDescribe = findViewById(R.id.btnDescribe)
        btnReadText = findViewById(R.id.btnReadText)

        // Start the custom animation sequence
        startCustomAnimation()

        // --- Standard Setup (runs in parallel to animation) ---
        tts = TextToSpeech(this) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) { stopProcessing() }
                    override fun onDone(utteranceId: String?) {
                        if (isContinuouslyDescribing && !isProcessing) {
                            mainScope.launch {
                                delay(3000)
                                if (isContinuouslyDescribing) startDescribing()
                            }
                        }
                    }
                })
            }
        }
        checkAudioPermission()
    }

    private fun startCustomAnimation() {
        val zoomAnimation = AnimationUtils.loadAnimation(this, R.anim.zoom_out)
        splashLogo.startAnimation(zoomAnimation)

        animationScope.launch {
            typeText("LISA")
            delay(1000)
            fadeOutSplashAndFadeInMain()
        }
    }

    private suspend fun typeText(text: String) {
        text.forEach { char ->
            splashTitle.append(char.toString())
            delay(2000)
        }
    }

    private fun fadeOutSplashAndFadeInMain() {
        // Create individual animators for the splash screen elements
        val logoFader = ObjectAnimator.ofFloat(splashLogo, "alpha", 1f, 0f)
        val titleFader = ObjectAnimator.ofFloat(splashTitle, "alpha", 1f, 0f)

        // Use an AnimatorSet to play them together
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(logoFader, titleFader)
        animatorSet.duration = 400 // Set duration for the whole set

        // Add the listener to the AnimatorSet
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // This will now run correctly!

                // 1. Hide the splash views completely
                splashUiGroup.forEach { it.visibility = View.GONE }

                // 2. Make the main UI views visible and fade them in
                mainUiGroup.forEach { view ->
                    view.alpha = 0f // Ensure it starts from transparent
                    // In your XML, only previewImage is GONE. The others are just transparent.
                    // We make them all visible before animating.
                    view.visibility = View.VISIBLE
                    view.animate().alpha(1f).setDuration(500).start()
                }
            }
        })

        // Start the entire animation set
        animatorSet.start()
    }

    private fun setupListeners() {
        btnDescribe.setOnClickListener {
            if (isProcessing) stopProcessing() else {
                isContinuouslyDescribing = false
                startDescribing()
            }
        }

        btnReadText.setOnClickListener {
            isContinuouslyDescribing = false
            if (!isProcessing) startReadingText()
        }

        voiceCommandReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isProcessing && intent?.getStringExtra("command") != "KEEP_DESCRIBING") return

                when (intent?.getStringExtra("command")) {
                    "DESCRIBE_SCENE" -> {
                        isContinuouslyDescribing = false
                        startDescribing()
                    }
                    "KEEP_DESCRIBING" -> {
                        if (!isContinuouslyDescribing) {
                            isContinuouslyDescribing = true
                            speakOut("Starting continuous description.")
                            startDescribing()
                        }
                    }
                    "READ_TEXT" -> {
                        isContinuouslyDescribing = false
                        startReadingText()
                    }
                }
            }
        }
        val filter = IntentFilter("VOICE_COMMAND")
        registerReceiver(voiceCommandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun startVoiceService() { /* Your Vosk/Picovoice service start logic here */ }

    private fun startReadingText() {
        isProcessing = true
        runOnUiThread {
            statusText.text = "Looking for text..."
            btnDescribe.isEnabled = false
            btnReadText.isEnabled = false
            previewImage.visibility = View.GONE
        }

        detectionJob = CoroutineScope(Dispatchers.IO).launch {
            val bitmap = captureFrame()
            if (bitmap != null) {
                runOnUiThread {
                    previewImage.visibility = View.VISIBLE
                    previewImage.setImageBitmap(bitmap)
                    statusText.text = "Frame captured. Reading text..."
                }
                sendImageToServer(bitmap, OCR_SERVER_URL, "text")
            } else {
                if (isProcessing) runOnUiThread { stopProcessing() }
            }
        }
    }

    private fun startDescribing() {
        isProcessing = true
        runOnUiThread {
            statusText.text = "Fetching frame..."
            btnDescribe.text = "Stop"
            btnReadText.isEnabled = false
            previewImage.visibility = View.GONE
        }
        detectionJob = CoroutineScope(Dispatchers.IO).launch {
            val bitmap = captureFrame()
            if (bitmap != null) {
                runOnUiThread {
                    previewImage.visibility = View.VISIBLE
                    previewImage.setImageBitmap(bitmap)
                    statusText.text = "Frame captured. Captioning..."
                }
                sendImageToServer(bitmap, CAPTION_SERVER_URL, "caption")
            } else {
                if (isProcessing) runOnUiThread { stopProcessing() }
            }
        }
    }

    private suspend fun captureFrame(): Bitmap? {
        return try {
            val url = URL(STREAM_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.connect()
            val mjpegStream = MjpegInputStream(BufferedInputStream(connection.inputStream))
            val bitmap = mjpegStream.readMjpegFrame()
            connection.disconnect()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing frame: ${e.message}")
            runOnUiThread {
                statusText.text = "Error: Could not connect to the camera."
                stopProcessing()
            }
            null
        }
    }

    private fun sendImageToServer(bitmap: Bitmap, serverUrl: String, responseKey: String) {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val imageBytes = outputStream.toByteArray()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "frame.jpg", imageBytes.toRequestBody("image/jpeg".toMediaType()))
            .build()

        val request = Request.Builder().url(serverUrl).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    statusText.text = "Error: Server call failed."
                    stopProcessing()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                try { // Add this
                    if (response.isSuccessful && responseBody != null) {
                        val resultText = JSONObject(responseBody).getString(responseKey)
                        runOnUiThread {
                            statusText.text = resultText
                            speakOut(resultText)
                            if (!isContinuouslyDescribing) {
                                stopProcessing()
                            } else {
                                isProcessing = false
                            }
                        }
                    } else {
                        runOnUiThread {
                            statusText.text = "Error: Server returned code ${response.code}"
                            stopProcessing()
                        }
                    }
                } catch (e: org.json.JSONException) { // And add this
                    // This block prevents the crash!
                    runOnUiThread {
                        statusText.text = "Error: Invalid response from server."
                        Log.e(TAG, "Failed to parse JSON. Received: $responseBody", e)
                        stopProcessing()
                    }
                }
            }
        })
    }

    private fun stopProcessing() {
        isContinuouslyDescribing = false
        isProcessing = false
        detectionJob?.cancel()
        runOnUiThread {
            btnDescribe.text = "Describe Scene"
            btnDescribe.isEnabled = true
            btnReadText.isEnabled = true
        }
    }

    private fun speakOut(text: String) {
        if (text.isNotEmpty()) {
            val utteranceId = UUID.randomUUID().toString()
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun checkAudioPermission() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        } else {
            setupListeners()
            startVoiceService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupListeners()
                startVoiceService()
            } else {
                Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_LONG).show()
                setupListeners()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animationScope.cancel()
        try { unregisterReceiver(voiceCommandReceiver) } catch (e: Exception) {}
        tts.shutdown()
        mainScope.cancel()
    }
}