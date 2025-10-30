🦾 LISA (Live Intelligent Sight Assistant) 🚀

LISA is a smart-glass-based assistive technology application built on Android.
It's designed to help visually impaired users understand their surroundings by providing real-time scene descriptions and text reading capabilities.

The system runs entirely on a local network (like a mobile hotspot) with no internet connection required, ensuring speed and privacy.
All AI processing is handled by a local server running on a laptop.

✨ Features

Fully Offline & Local: Runs on a private network (e.g., your phone's hotspot). No internet, no cloud.

Real-time Video: Streams a live feed from a wearable ESP32-CAM.

AI Scene Description: Uses a locally-hosted BLIP model to generate natural language descriptions of what the camera sees.

AI Text Reading (OCR): Employs a locally-hosted EasyOCR model to find and read any text in the camera's view.

Hands-Free Voice Control:

On-Device Wake Word: Activates instantly by saying "Hey Lisa."

On-Device Command Recognition: Understands commands like "describe scene", "read text", and natural variations.

Audio Feedback: All results are spoken aloud using the Android Text-to-Speech (TTS) engine.

Manual Controls: Includes on-screen buttons for all major functions as an alternative to voice.

🔧 Architecture & Tech Stack

LISA operates as a self-contained system on a local Wi-Fi network (typically a mobile hotspot).

🧩 The Network (Router)

Your Android Phone's Mobile Hotspot creates the private Wi-Fi network.

💻 The AI Server (Laptop)

Your Laptop connects to the hotspot, gets a local IP (e.g., 192.168.43.100), and runs the Flask server to process AI tasks.

📷 The Camera (Hardware)

The ESP32-CAM connects to the hotspot and streams video to its own local IP (e.g., 192.168.43.123).

📱 The App (Client)

The Android App (LISA) running on the phone:

Pulls the video stream directly from the ESP32-CAM's IP.

Sends image frames directly to the Laptop's IP for captioning or OCR.

🧠 Tech Stack
Component	Technology
Hardware	ESP32-CAM
Mobile App	Kotlin, Android SDK, OkHttp, Picovoice (Porcupine & Rhino)
AI Backend	Python, Flask, Transformers (Salesforce/blip-image-captioning-base), EasyOCR
Network	Mobile Hotspot (as a local-only router)
⚙️ Local Setup & Run

To run the full system, start all three components on the same network.

1️⃣ Start the Network

Enable the Mobile Hotspot on your Android phone.

2️⃣ Start the AI Server (Laptop)

Connect your laptop to your phone's mobile hotspot.

Find your laptop's local IP address on this network.

Windows:

ipconfig


Look for the “IPv4 Address” under your Wi-Fi adapter (e.g., 192.168.43.100).

Mac/Linux:

ifconfig


or

ip a


Navigate to your Flask server directory and install dependencies:

pip install flask transformers torch easyocr Pillow numpy


Run the Flask server:

python server.py


The server will start listening on:

0.0.0.0:5000

3️⃣ Start the Hardware (ESP32-CAM)

Ensure your ESP32-CAM Arduino code is configured to connect to your phone's hotspot SSID and password.

Power on the ESP32-CAM.

Find its IP address (e.g., by checking the Arduino IDE Serial Monitor when it boots).
This will be your STREAM_URL.

4️⃣ Configure & Run the Android App

Open the Android project in Android Studio.

Go to:

app/src/main/assets/


and add your Picovoice model files:

Hey-Lisa_android.ppn

lisa-commands_android.rhn

Open MainActivity.kt and update the constants:

private const val STREAM_URL = "http://192.168.43.123:81/stream"
private const val CAPTION_SERVER_URL = "http://192.168.43.100:5000/caption"
private const val OCR_SERVER_URL = "http://192.168.43.100:5000/read_text"


In the startVoiceService() function, paste your Picovoice Access Key:

.setAccessKey("YOUR_PICOVOICE_ACCESS_KEY_HERE")


Run the app on your Android phone (which is already connected to its own hotspot).

🚀 Usage

Make sure all three components (Hotspot, Laptop Server, ESP32-CAM) are on and connected.

Open the LISA app.

Say "Hey Lisa" — the logo on the screen will pulse to indicate it's listening.

Give a command, such as:

"describe scene"

"read text"

LISA will process the request locally and speak the result.
