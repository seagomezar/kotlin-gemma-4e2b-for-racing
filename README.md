# Sonoma Racing Coach (Edge Integration)

A Kotlin-based Android edge application powered by the **Gemma 4:E2B** model running entirely on-device to deliver predictive, actionable insights to a professional racing driver in real-time. 

This app ingests live high-frequency `.vbo` telemetry (steering, speed, etc.) from the **ApexAI** racing simulator backend over WebSockets, analyzes driving behavior, and uses LLM-powered inference to generate context-aware coaching feedback through text-to-speech.

---

## 🏎️ AI Coach Mode (Gemma Inference)
The AI Coach relies on real-time LLM inference (Gemma 4:E2B) to analyze driving behavior and dynamically predict optimal adjustments.

<div style="display: flex; flex-direction: row; gap: 10px;">
  <img src="https://raw.githubusercontent.com/seagomezar/sonoma-racing-coach/main/images/dashboard_waiting.png" width="30%" />
  <img src="https://raw.githubusercontent.com/seagomezar/sonoma-racing-coach/main/images/dashboard_active.png" width="30%" />
  <img src="https://raw.githubusercontent.com/seagomezar/sonoma-racing-coach/main/images/Screenshot_1777831770.png" width="30%" />
</div>

## 🧠 Memory Bank Mode
The Memory Bank uses a deterministic rule-based engine. It continuously tracks sector boundary telemetry (min speed, coasting time) and triggers alerts based on a driver's customized `.csv` profile.

<div style="display: flex; flex-direction: row; gap: 10px;">
  <img src="https://raw.githubusercontent.com/seagomezar/sonoma-racing-coach/main/images/Screenshot_1777832922.png" width="30%" />
  <img src="https://raw.githubusercontent.com/seagomezar/sonoma-racing-coach/main/images/Screenshot_1777832944.png" width="30%" />
</div>

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

- **Dual-Tab Dashboard:** Switch between "AI Coach" (predictive LLM) and "Memory Bank" (deterministic rule-based) coaching modes.
- **Memory Bank Coaching:** Upload customized `.csv` rule sets to trigger deterministic coaching advice based on sector boundary telemetry metrics (e.g. minimum speed, coasting time).
- **Session Control:** Explicit "Start/Stop Coaching Session" controls to manage telemetry processing and active coaching.
- **Telemetry Dashboard:** Live speed and steering tracking built with Jetpack Compose (Material 3).
- **Gated Inference Engine:** Real-time steering variance monitoring to ensure LLM inference is only triggered during straightaways, maintaining thermal stability.
- **On-device Coaching:** Fully offline inference using Google's LiteRT-LM and Gemma 4:E2B.
- **Audio Delivery:** Strict JSON-formatted instructions mapped into prioritized TextToSpeech cues for the driver.
- **ApexAI Integration:** Connects automatically to cloud Server-Sent Events (SSE) telemetry backends.

## Setup

### 1. Clone the repository

```bash
git clone https://github.com/seagomezar/sonoma-racing-coach.git
```

Open the project in Android Studio.

### 2. Prepare the LLM

Download the `gemma-4-E2B-it.litertlm` model and push it to the device/emulator:

```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma-4-E2B-it.litertlm /data/local/tmp/llm/gemma-4-E2B-it.litertlm
```

### 3. Start Telemetry Simulator

The app connects to the ApexAI Server-Sent Events (SSE) endpoint at `https://apexai-812524149286.us-central1.run.app/events/telemetry`. If you are running a local simulator, you can update the `startListening()` URL in `TelemetryManager.kt`.

### 4. Load Memory Bank Rules (Optional)

To use the deterministic Memory Bank mode, push your `coaching_recommendations.csv` to your device:

```bash
adb push "coaching_recommendations (1).csv" /sdcard/Download/coaching_recommendations.csv
```
Inside the app, switch to the **Memory Bank** tab, click **Upload/Change CSV**, and select the file to load the rule set.

### 5. Run the app
- Run the Android app via Android Studio or ADB.
- Click **Start Coaching Session** to begin receiving real-time racing advice.

## Tech Stack
- Kotlin & Jetpack Compose
- LiteRT-LM & MediaPipe
- OkHttp (WebSockets)
- Android TextToSpeech

## License
MIT