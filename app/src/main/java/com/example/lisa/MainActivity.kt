package com.example.lisa

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import ai.picovoice.picovoice.*

class MainActivity : AppCompatActivity() {

    // Signal to wait for the Text-to-Speech engine
    private val ttsInitialized = CompletableDeferred<Boolean>()

    // Main UI Views
    private lateinit var mainLayout: View
    private lateinit var statusText: TextView
    private lateinit var btnDescribe: Button
    private lateinit var btnReadText: Button
    private lateinit var previewImage: ImageView

    // Animation UI Views
    private lateinit var animationLayout: View
    private lateinit var animatedLogo: ImageView
    private lateinit var animatedText: TextView

    // Other components
    private lateinit var tts: TextToSpeech
    private var detectionJob: Job? = null
    private var isProcessing = false
    private var isContinuouslyDescribing = false
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var picovoiceManager: PicovoiceManager? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "MainActivity"
        private const val STREAM_URL = "http://10.227.159.178:81/stream"
        private const val CAPTION_SERVER_URL = "http://10.227.159.211:5000/caption"
        private const val OCR_SERVER_URL = "http://10.227.159.211:5000/read_text"
        private const val REQUEST_RECORD_AUDIO = 1
    }

    // Add this variable at the top of your MainActivity class
    private var listeningAnimator: ObjectAnimator? = null

    private fun startListeningAnimation() {
        // Stop any previous animation
        stopListeningAnimation()

        // Make the logo pulse by fading its alpha from 1.0 (visible) to 0.5 (faded) and back
        listeningAnimator = ObjectAnimator.ofFloat(animatedLogo, View.ALPHA, 1f, 0.5f).apply {
            duration = 700 // How long one pulse takes
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        listeningAnimator?.start()
    }

    private fun stopListeningAnimation() {
        listeningAnimator?.cancel()
        // Reset the logo to be fully visible
        animatedLogo.alpha = 1f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()

        // --- TTS Setup ---
        tts = TextToSpeech(this) { status: Int ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) { stopProcessing() }
                    override fun onDone(utteranceId: String?) {
                        if (isContinuouslyDescribing && !isProcessing) {
                            mainScope.launch { delay(3000); if (isContinuouslyDescribing) startDescribing() }
                        }
                    }
                })
                ttsInitialized.complete(true)
            } else {
                ttsInitialized.complete(false)
            }
        }
        // This now handles starting the voice service
        checkAudioPermission()

        // Splash Screen Animation (unchanged)
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val iconView = splashScreenView.iconView
            val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 0f)
            val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 0f)
            val alpha = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
            val moveUp = ObjectAnimator.ofFloat(iconView, View.TRANSLATION_Y, 0f, -200f)
            val animatorSet = AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha, moveUp)
                duration = 800L
                interpolator = android.view.animation.AccelerateInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        splashScreenView.remove()
                    }
                })
            }
            animatorSet.start()
        }

        // Intro Animation (unchanged)
        mainScope.launch {
            val ttsReady = ttsInitialized.await()
            runCustomIntroAnimation(ttsReady)
            animationLayout.animate().alpha(0f).setDuration(400).withEndAction {
                animationLayout.visibility = View.GONE
            }.start()
            mainLayout.alpha = 0f
            mainLayout.visibility = View.VISIBLE
            mainLayout.animate().alpha(1f).setDuration(400).start()
        }
    }

    private fun setupViews() {
        animationLayout = findViewById(R.id.animation_layout)
        animatedLogo = findViewById(R.id.animated_logo)
        animatedText = findViewById(R.id.animated_text)
        mainLayout = findViewById(R.id.main_layout)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        previewImage = findViewById(R.id.previewImage)
        statusText = findViewById(R.id.statusText)
        btnDescribe = findViewById(R.id.btnDescribe)
        btnReadText = findViewById(R.id.btnReadText)

        // This function now just sets up the button clicks
        setupListeners()
    }

    private suspend fun runCustomIntroAnimation(ttsReady: Boolean) {
        delay(400)
        animatedText.alpha = 0f
        animatedText.animate().alpha(1f).setDuration(500).start()
        delay(500)
        if (ttsReady) {
            val welcomeMessage = "Welcome to Lisa, Live Intelligent Sight Assistance."
            speakOut(welcomeMessage)
        }
        val fullText = "Live Intelligent Sight Assistant"
        animatedText.text = ""
        fullText.forEach { char ->
            animatedText.append(char.toString())
            delay(60)
        }
        delay(1500)
    }

    private fun speakOut(text: String, utteranceId: String = UUID.randomUUID().toString()) {
        if (text.isNotEmpty()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
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

        // No more BroadcastReceiver
    }

    private fun startVoiceService() {
        try {
            picovoiceManager = PicovoiceManager.Builder()
                .setAccessKey("UAnntQu8y2V8kQA5neIxny6B66TTiqjsC5wIenKMlngxm6KZgZyxug==")
                .setKeywordPath("Hey-Lisa_android.ppn")
                .setWakeWordCallback {
                    // This is called when "Hey Lisa" is detected
                    runOnUiThread {
                        statusText.text = "Listening..."
                        startListeningAnimation() // Start the pulse
                    }
                }
                .setContextPath("lisa-commands_android.rhn")
                .setInferenceCallback { inference ->
                    // This is called when a command is understood OR a timeout occurs
                    runOnUiThread {
                        stopListeningAnimation() // Stop the pulse in all cases

                        if (inference.isUnderstood) {
                            // A valid command was heard
                            if (isProcessing) return@runOnUiThread

                            when (inference.intent) {
                                "describeScene" -> {
                                    isContinuouslyDescribing = false
                                    startDescribing()
                                }
                                "readText" -> {
                                    isContinuouslyDescribing = false
                                    startReadingText()
                                }
                                else -> {
                                    statusText.text = "Sorry, I didn't understand that."
                                    speakOut("Sorry, I didn't understand that.")
                                }
                            }
                        } else {
                            // ✅ This is the "timeout" case
                            // No valid command was heard
                            statusText.text = "Say 'hey lisa' to start."
                        }
                    }
                }
                .build(applicationContext)

            picovoiceManager?.start()
            Log.i(TAG, "Picovoice service started successfully.")

        } catch (e: PicovoiceException) {
            Log.e(TAG, "Failed to start Picovoice: ${e.message}")
            Toast.makeText(this, "Voice command engine failed to start.", Toast.LENGTH_LONG).show()
        }
    }

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
                try {
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
                } catch (e: JSONException) {
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
            stopListeningAnimation()
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

    override fun onDestroy() {
        super.onDestroy()
        picovoiceManager?.stop()
        picovoiceManager?.delete() // Release resources

        // try { unregisterReceiver(voiceCommandReceiver) } catch (e: Exception) {} // No longer needed
        tts.shutdown()
        mainScope.cancel()
    }
}