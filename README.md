<div align="center">
  <h1>AURA SmartCane 🦯</h1>
  <p><strong>An intelligent, low-cost Edge-AI mobility system for the visually impaired.</strong></p>
</div>

<br />

## 📖 Overview
The **AURA SmartCane** is a comprehensive, low-cost assistive mobility system that merges custom ESP32-based hardware with an intelligent Android application. Traditional white canes only detect hazards upon direct physical contact; AURA extends a user's environmental awareness using real-time sensor arrays, Edge-AI vision models, and continuous Text-to-Speech (TTS) guidance.

*(Note: Drag and drop your final demo video `.mp4` here to embed it natively!)*

---

## ✨ Key Features

### 1. Advanced Hardware Sensing
- **Ultrasonic Obstacle Detection:** Provides early warnings for objects in the user's path, eliminating "detection delay."
- **Infrared (IR) Sensors:** Specifically calibrated to detect sudden drop-offs and descending stairs.
- **Water & Moisture Sensors:** Alerts users to wet floors, puddles, and slippery surfaces to prevent falls.

### 2. Edge-AI Vision Camera
Leverages the smartphone camera and on-device ML models to dynamically recognize complex environmental hazards beyond physical reach, such as doors, ascending/descending stairs, and irregular obstacles.

### 3. OCR Text Reader
A built-in Optical Character Recognition (OCR) scanner that reads physical text out loud. Supports multilingual scanning, empowering users to intuitively "read" signage, documents, and labels effortlessly.

### 4. Emergency Support System
Safety is the top priority. A dedicated hardware or software trigger instantly initiates an emergency sequence that captures the user's live GPS coordinates and dispatches an emergency SMS and automated call to registered contacts.

### 5. 100% Offline Processing & TTS
Designed for the real world, the entire system—including sensor processing, AI vision, and OCR—runs completely offline. Feedback is delivered through a low-latency, zero-dependency Text-to-Speech (TTS) engine.

---

## 🛠️ System Architecture

### Hardware (ESP32)
The hardware acts as the primary data acquisition layer. The ESP32 firmware continuously polls the ultrasonic, IR, and water sensors, formats the spatial data, and broadcasts it over a low-latency Bluetooth link.

### Mobile Application (Android)
Built in Java, the Android application acts as the "brain" of AURA:
- **Bluetooth Management**: Handles seamless, auto-reconnecting communication with the ESP32.
- **Vision & OCR**: Integrates Google ML Kit for high-speed, on-device object and text recognition.
- **Audio Routing (`TtsManager`)**: A centralized singleton managing all audio feedback channels with dynamic debouncing and prioritization, ensuring alerts are never overlapped or confusing.

---

## 🚀 Getting Started

### Prerequisites
- ESP32 Microcontroller and associated sensors (Ultrasonic, IR, Water)
- Android Studio (for app compilation)
- An Android device running Android 8.0 (Oreo) or higher.

### Hardware Setup
1. Flash the provided firmware to the ESP32.
2. Wire the sensors to the appropriate GPIO pins as defined in the configuration.
3. Power the ESP32 via a portable battery bank.

### App Installation
1. Clone this repository to your local machine.
2. Open the project in Android Studio.
3. Build and run the application on your physical Android device.
4. Grant the necessary permissions (Camera, Location, Bluetooth, SMS).
5. Pair the device with the ESP32 via the app's Bluetooth interface.

---

## 🤝 Contributing
Contributions, issues, and feature requests are welcome! Feel free to check the issues page if you want to contribute.

## 📝 License
This project is licensed under the MIT License.
