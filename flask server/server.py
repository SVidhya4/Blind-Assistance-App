from flask import Flask, request, jsonify
from transformers import pipeline
from PIL import Image
import io
import easyocr
import numpy as np

# Initialize the Flask application
app = Flask(__name__)

# --- Load AI Models ---
# Load the image captioning model (BLIP)
# nlpconnect/vit-gpt2-image-captioning
# Salesforce/blip-image-captioning-large
print("Loading captioning model...")
captioner = pipeline("image-to-text", model="Salesforce/blip-image-captioning-base")
print("Captioning model loaded!")

# Load the OCR model (EasyOCR)
print("Loading OCR model... This will be slow on the first run.")
ocr_reader = easyocr.Reader(['en'])  # Specify English
print("OCR model loaded! Server is ready.")

# --- Helper function to clean captions ---
def clean_caption(caption_text):
    """Removes unwanted introductory phrases from the generated caption."""
    phrases_to_remove = [
        "a picture of ", "an image of ", "a photo of ", "arafed of ", "a close up of "
    ]
    lower_caption = caption_text.lower()
    for phrase in phrases_to_remove:
        if lower_caption.startswith(phrase):
            cleaned_text = caption_text[len(phrase):].strip()
            return cleaned_text.capitalize()
    return caption_text

# --- ENDPOINT 1: For Describing the Scene ---
@app.route('/caption', methods=['POST'])
def caption_image():
    if 'image' not in request.files:
        return jsonify({'error': 'No image file provided'}), 400

    file = request.files['image']
    try:
        image = Image.open(io.BytesIO(file.read())).convert("RGB")
        raw_caption = captioner(image)[0]['generated_text']
        cleaned_caption = clean_caption(raw_caption)
        print(f"Generated caption: {cleaned_caption}")
        return jsonify({'caption': cleaned_caption})
    except Exception as e:
        print(f"An error occurred during captioning: {e}")
        return jsonify({'error': str(e)}), 500

# --- NEW ENDPOINT 2: For Reading Text (OCR) ---
@app.route('/read_text', methods=['POST'])
def read_text_from_image():
    if 'image' not in request.files:
        return jsonify({'error': 'No image file provided'}), 400

    file = request.files['image']
    try:
        image_bytes = file.read()
        # EasyOCR works with a format called a NumPy array
        np_image = np.array(Image.open(io.BytesIO(image_bytes)))
        
        # Run OCR on the image to find text
        results = ocr_reader.readtext(np_image)
        
        # Combine all the detected text pieces into a single string
        detected_text = ' '.join([text for bbox, text, prob in results])
        
        if not detected_text:
            detected_text = "No text was found in the image."

        print(f"Detected text: {detected_text}")
        return jsonify({'text': detected_text})
    except Exception as e:
        print(f"An error occurred during OCR: {e}")
        return jsonify({'error': str(e)}), 500

# --- Start the server ---
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)