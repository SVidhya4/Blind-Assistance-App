# LISA (Live Intelligent Sight Assistant) 🚀

LISA is a smart-glass-based assistive technology application built on Android. It's designed to help visually impaired users understand their surroundings by providing real-time scene descriptions and text reading capabilities.

The system runs **entirely on a local network** (like a mobile hotspot) with no internet connection required, ensuring speed and privacy. All AI processing is handled by a local server running on a laptop.

## ✨ Features

* **Fully Offline & Local:** Runs on a private network (e.g., your phone's hotspot). No internet, no cloud.
* **Real-time Video:** Streams a live feed from a wearable ESP32-CAM.
* **AI Scene Description:** Uses a locally-hosted BLIP model to generate natural language descriptions of what the camera sees.
* **AI Text Reading (OCR):** Employs a locally-hosted EasyOCR model to find and read any text in the camera's view.
* **Hands-Free Voice Control:**
    * **On-Device Wake Word:** Activates instantly by saying "Hey Lisa."
    * **On-Device Command Recognition:** Understands commands like "describe scene," "read text," and natural variations.
* **Audio Feedback:** All results are spoken aloud using the Android Text-to-Speech (TTS) engine.
* **Manual Controls:** Includes on-screen buttons for all major functions as an alternative to voice.

---

## 🔧 Architecture & Tech Stack

LISA operates as a self-contained system on a local Wi-Fi network (typically a mobile hotspot).

1.  **The Network (Router):** Your **Android Phone's Mobile Hotspot** creates the private Wi-Fi network.
2.  **The AI Server (Laptop):** Your **Laptop** connects to the hotspot, gets a local IP (e.g., `192.168.43.100`), and runs the Flask server to process AI tasks.
3.  **The Camera (Hardware):** The **ESP32-CAM** connects to the hotspot and streams video to its own local IP (e.g., `192.168.43.123`).
4.  **The App (Client):** The **Android App (LISA)** running on the phone:
    * Pulls the video stream directly from the ESP32-CAM's IP.
    * Sends image frames directly to the Laptop's IP for captioning or OCR.



### Tech Stack

* **Hardware:** ESP32-CAM
* **Mobile App:** Kotlin, Android SDK, OkHttp, Picovoice (Porcupine & Rhino)
* **AI Backend:** Python, Flask, Transformers (`Salesforce/blip-image-captioning-base`), EasyOCR
* **Network:** Mobile Hotspot (as a local-only router)

---

## ⚙️ Local Setup & Run

To run the full system, you must start all three components on the same network.

### 1. Start the Network
* Enable the **Mobile Hotspot** on your Android phone.

### 2. Start the AI Server (Laptop)
1.  Connect your laptop to your phone's mobile hotspot.
2.  Find your laptop's local IP address on this network.
    * **Windows:** Open Command Prompt and type `ipconfig`. Look for the "IPv4 Address" under your Wi-Fi adapter. (e.g., `192.168.43.100`).
    * **Mac/Linux:** Open a terminal and type `ifconfig` or `ip a`.
3.  Navigate to your Flask server directory and install dependencies:
    ```bash
    pip install flask transformers torch easyocr Pillow numpy
    ```
4.  Run the Flask server:
    ```bash
    python server.py
    ```
    

### 3. Start the Hardware (ESP32-CAM)
1.  Ensure your ESP32-CAM Arduino code is configured to connect to your phone's hotspot SSID and password.
2.  Power on the ESP32-CAM.
3.  Find its IP address (e.g., by checking the Arduino IDE Serial Monitor when it boots). This is your `STREAM_URL`.

### 4. Configure & Run the Android App
1.  Open the Android project in Android Studio.
2.  Go to `app/src/main/assets/` and add your Picovoice model files:
    * `Hey-Lisa_android.ppn`
    * `lisa-commands_android.rhn`
3.  Open `MainActivity.kt` and update the constants:
    ```kotlin
    // Set to the ESP32-CAM's IP address (from step 3)
    private const val STREAM_URL = "[http://192.168.43.123:81/stream](http://192.168.43.123:81/stream)"
    
    // Set to your laptop's IP (from step 2) and the Flask port (e.g., 5000)
    private const val CAPTION_SERVER_URL = "[http://192.168.43.100:5000/caption](http://192.168.43.100:5000/caption)"
    private const val OCR_SERVER_URL = "[http://192.168.43.100:5000/read_text](http://192.168.43.100:5000/read_text)"
    ```
4.  In the `startVoiceService()` function, paste your Picovoice **Access Key**:
    ```kotlin
    .setAccessKey("YOUR_PICOVOICE_ACCESS_KEY_HERE")
    ```
5.  Run the app on your Android phone (which is already connected to its own hotspot).

---

## 🚀 Usage

1.  Make sure all three components (Hotspot, Laptop Server, ESP32) are on and connected.
2.  Open the LISA app.
3.  Say **"Hey Lisa"**. The logo on the screen will pulse to indicate it's listening.
4.  Give a command, such as **"describe scene"** or **"read text"**.
5.  LISA will process the request locally and speak the result.
