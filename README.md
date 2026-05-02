# Sonoma Racing Coach (Edge Integration)

![Dashboard Waiting for Telemetry](https://raw.githubusercontent.com/seagomezar/sonoma-racing-coach/main/images/dashboard_waiting.png)
![Dashboard Active Coaching](https://raw.githubusercontent.com/seagomezar/sonoma-racing-coach/main/images/dashboard_active.png)

A Kotlin-based Android edge application powered by the **Gemma 4:E2B** model running entirely on-device to deliver predictive, actionable insights to a professional racing driver in real-time. 

This app ingests live high-frequency `.vbo` telemetry (steering, speed, etc.) from the **ApexAI** racing simulator backend over WebSockets, analyzes driving behavior, and uses LLM-powered inference to generate context-aware coaching feedback through text-to-speech.

---

## Readiness Assessment & Addressed Gaps

This repository serves as the unified edge architecture intended to pass the *Final Architecture & Readiness Assessment* for Team 3's Sonoma Raceway field test. It directly addresses the pending blockers identified by the review committee:

### 1. Architectural Consolidation
- **Deprecated `flutter_gemma_test`:** We entirely removed the fragmented Flutter implementation in favor of a strictly native Android (Kotlin + Jetpack Compose) stack using Google's `LiteRT-LM`. This solves the severe architectural fragmentation and instability seen in the earlier cross-platform modules.
- **Defined API Contracts:** Core logic is unified. Telemetry ingestion, inference triggering, and UI display are cleanly separated via strict Kotlin data contracts (`CoachingPayload` and `TelemetryData`).

### 2. Thermal & Compute Load Optimization
- **Gated Inference Engine:** Operating the Pixel 10 at 10Hz within a high-heat cabin environment previously risked thermal throttling. We introduced a `GatedInferenceEngine` that constantly evaluates steering variance. The Gemma LLM is now strictly prevented from executing mid-corner; inference compute cycles only trigger during stable straightaway segments.

### 3. Pedagogical Tuning (The Pro Driver / T-Rod)
- **Straightaway Delivery Windows:** Mid-corner audio cues are a severe safety risk. By triggering inference immediately upon detecting corner exit, Text-to-Speech instructions are formulated and delivered 2-3 seconds prior to the *next* corner entry, honoring the driver's cognitive bandwidth.
- **Hyper-Specific Micro-Adjustments:** The `Gemma4Manager` system prompt explicitly enforces structured JSON generation that provides exact measurements (e.g., "Apply 0.05 bar throttle") rather than generic advice.

### 4. End-to-End Latency Validation
- **Latency Tracker:** We introduced a dedicated `LatencyTracker` logging module. It captures the exact delta between receiving the WebSocket packet from `apexai` to the moment the audio buffer is released to the TTS engine, ensuring the pipeline remains within the strict 2-3 second latency budget required for a 150+ mph field test.

### 5. Extensibility & Wired Integration
- Standardized the schema (`CoachingPayload`) so that the racing heuristics remain decoupled from the UI framework. The `TelemetryManager` relies on a standard HTTP WebSocket implementation, keeping the edge app ready to swap from the local `apexai` simulator stream to a raw wired USB-C CANbus serial stream on the day of the Sonoma field test.

---

## Features

- **Telemetry Dashboard:** Live speed and steering tracking built with Jetpack Compose (Material 3).
- **Gated Inference Engine:** Real-time steering variance monitoring to ensure LLM inference is only triggered during straightaways, maintaining thermal stability.
- **On-device Coaching:** Fully offline inference using Google's LiteRT-LM and Gemma 4:E2B.
- **Audio Delivery:** Strict JSON-formatted instructions mapped into prioritized TextToSpeech cues for the driver.
- **ApexAI Integration:** Connects automatically to local Racelogic VBOX simulator backends.

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/seagomezar/sonoma-racing-coach.git
```

Open the project in Android Studio.

### 2. Prepare the LLM

Download the `gemma-4-E2B-it.litertlm` model and push it to the device/emulator:

```bash
wget -O gemma-4-E2B-it.litertlm \
https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/gemma-4-E2B-it.litertlm
```

### 3. Choose Telemetry Source

The dashboard has a telemetry settings panel with two source modes:

- **WebSocket**: testing mode. The app receives normalized JSON packets from the
  ApexAI server at `/ws/telemetry`.
- **USB-C CAN**: realtime mode. The app detects attached USB devices and is the
  integration point for direct CAN adapter frame decoding.

For emulator testing, keep WebSocket set to:

```text
ws://10.0.2.2:8000/ws/telemetry
```

For a physical Android device on the same network as your laptop, use:

```text
ws://YOUR_LAPTOP_IP:8000/ws/telemetry
```

USB-C CAN mode currently discovers the attached adapter and reports status in the
dashboard. Full direct CAN ingestion still needs the adapter-specific USB
protocol parser that turns USB payloads into CAN frames, then maps those frames
into `TelemetryPacket`.

### 4. Start Telemetry Simulator

The app connects to `ws://10.0.2.2:8000/ws/telemetry`. Start the `apexai` backend on your local host:

```bash
cd path/to/apexai
uv run apexai-server --source vbo --vbo-file data/session.vbo --loop --autostart
```

To test CAN-derived telemetry through the same WebSocket path:

```bash
uv run apexai-server \
  --source can \
  --dbc-file data/vehicle.dbc \
  --can-interface virtual \
  --can-channel test \
  --stream-interval 0.1 \
  --autostart
```

### 5. Run the app
- Run the Android app via Android Studio or ADB.
- The Dashboard will connect to the telemetry server and automatically start guiding the driver.

## Tech Stack
- Kotlin & Jetpack Compose
- LiteRT-LM & MediaPipe
- OkHttp (WebSockets)
- Android TextToSpeech

## License
MIT
