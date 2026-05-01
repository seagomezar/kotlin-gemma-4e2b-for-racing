# Sonoma Racing Coach (Edge Integration)

<div align="center">
  <img src="images/dashboard_waiting.png" width="30%" alt="Dashboard Waiting for Telemetry">
  <img src="images/dashboard_active.png" width="30%" alt="Dashboard Active Coaching">
</div>

A Kotlin-based Android edge application powered by the **Gemma 4:E2B** model running entirely on-device to deliver predictive, actionable insights to a professional racing driver in real-time. 

This app ingests live high-frequency `.vbo` telemetry (steering, speed, etc.) from the **ApexAI** racing simulator backend over WebSockets, analyzes driving behavior, and uses LLM-powered inference to generate context-aware coaching feedback through text-to-speech.

## Features

- **Telemetry Dashboard:** Live speed and steering tracking built with Jetpack Compose (Material 3).
- **Gated Inference Engine:** Real-time steering variance monitoring to ensure LLM inference is only triggered during straightaways, maintaining thermal stability.
- **On-device Coaching:** Fully offline inference using Google's LiteRT-LM and Gemma 4:E2B.
- **Audio Delivery:** Strict JSON-formatted instructions mapped into prioritized TextToSpeech cues for the driver.
- **ApexAI Integration:** Connects automatically to local Racelogic VBOX simulator backends.

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/seagomezar/kotlin-gemma-4e2b-for-racing.git
```

Open the project in Android Studio.

### 2. Prepare the LLM

Download the `gemma-4-E2B-it.litertlm` model and push it to the device/emulator:

```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/gemma-4-E2B-it.litertlm
```

### 3. Start Telemetry Simulator

The app connects to `ws://10.0.2.2:8000/ws/telemetry`. Start the `apexai` backend on your local host:

```bash
cd path/to/apexai
python -m uv run apexai-server --vbo-file data/session.vbo --loop --autostart
```

### 4. Run the app
- Run the Android app via Android Studio or ADB.
- The Dashboard will connect to the telemetry server and automatically start guiding the driver.

## Tech Stack
- Kotlin & Jetpack Compose
- LiteRT-LM & MediaPipe
- OkHttp (WebSockets)
- Android TextToSpeech

## License
MIT