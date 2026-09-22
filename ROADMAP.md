# ROADMAP.md — Product Vision & Technical Proposals

## Product Vision

Commercial Killer is an **on-device experimentation workbench** for audio and video data. It enables developers to test real-time feature detectors, signal transforms, and local machine learning models directly on phone hardware without cloud latency or data privacy concerns.

## Technical Proposal: Significance Determination Alternatives

When comparing sequential Mel-spectrogram frames (calculated at a configurable 100 ms interval), we evaluate two primary approaches to determine whether a change is "significant" enough to trigger an event:

### Alternative A: Matrix Distance Metric (Lightweight / Low Energy)
- **Algorithm**: Compute Mean Squared Error (MSE), Fréchet Distance, or Cosine Distance between consecutive Mel-spectrogram frequency bin vectors $S_t$ and $S_{t-1}$.
- **Pros**: Extremely fast ($< 1\text{ ms}$ on CPU/DSP), minimal battery consumption, deterministic thresholding ($\Delta > \tau$).
- **Cons**: Sensitive to abrupt background noise or volume shifts without semantic understanding.

### Alternative B: Local Multimodal LLM Evaluation (Semantic Context)
- **Algorithm**: Pass consecutive spectrogram image frames or feature embeddings to an on-device local multimodal model (e.g. Gemini Nano via AICore / LiteRT-ML).
- **Prompt**: *"Compare the audio/video spectrogram at $t_{-1}$ and $t_0$. Has the broadcast transitioned from primary content to a commercial break?"*
- **Pros**: High semantic accuracy, contextual awareness of commercial transitions, immune to simple volume spikes.
- **Cons**: Higher latency and NPU/GPU compute requirements per 100 ms frame.

### Hybrid Architecture (Recommended)
Use **Alternative A** as a fast first-stage filter. When matrix distance $\Delta$ exceeds threshold $\tau_{\text{trigger}}$, invoke **Alternative B** (local multimodal LLM) to confirm the commercial transition event.

---

## Broadcast Test Material Sources

For benchmarking commercial detection on real broadcast media, public domain and fair-use archival sources provide complete broadcasts with intact commercial pods:

1. **Internet Archive TV News Archive** (`https://archive.org/details/tv`):
   - Continuous 24/7 recordings of US TV broadcasts (CNN, MSNBC, Fox News, local network affiliates) with complete commercial breaks.
   - Formats available for direct download: MP4, MP3.
2. **Internet Archive Classic TV Commercials Vault** (`https://archive.org/details/classic_tv_commercials` and `https://archive.org/details/vhs-vault`):
   - Thousands of complete off-air TV recordings with intact commercial pods from the 1980s, 1990s, 2000s, and 2010s.
   - Formats: MP4, MP3, OGG.
3. **Internet Archive Old-Time Radio (OTR) Broadcasts** (`https://archive.org/details/oldtimeradio`):
   - Full original radio programs with original sponsor commercials.
   - Formats: MP3, OGG, WAV.
4. **TRECVID / TV Ad Detection Research Benchmarks**:
   - Open datasets containing annotated commercial boundaries for broadcast television.

---

## Long-Term Engine Roadmap

1. **Native Framework Integration**:
   - LiteRT-ML (TFLite) & MediaPipe tasks (Audio & Video)
   - Open-weights Hugging Face models (YAMNet, Audio Spectrogram Transformer / AST, Wav2Vec2)
   - AICore / Gemini Nano Prompt API
   - Local GGUF / ONNX models via NNAPI
2. **Multi-Input Stream Workbench**:
   - Camera & Microphone live capture
   - Local audio file decoding (`MediaExtractor` / `MediaCodec` for WAV, MP3, AAC, M4A, FLAC, OGG, OPUS)
   - Local video file playback & frame-by-frame analysis
   - Streaming service input integration
3. **Real-Time Instrumentation & Profiling**:
   - Live Mel-spectrogram heatmap display in Jetpack Compose
   - Real-time parallel classifier confidence visualizers
   - Real-time execution latency charts & log console
   - System trace analysis using `android-profiler`

---

## Technical Proposal: ADK for Kotlin 1.0 Agent Integration

Integration of Google's **Agent Development Kit (ADK) for Kotlin 1.0** to provide autonomous action dispatching and bidirectional agentic communication:

1. **Architecture & Scope**:
   - Utilize KMP-ready ADK Kotlin core with `InMemoryRunner` for on-device agent state machine management.
   - Define structured action tools in Kotlin: `MuteAudioAction`, `UnmuteAudioAction`, `SendIrCommandAction`, and `TriggerTvWebhookAction`.
2. **LLM Orchestration**:
   - Couple local Gemini Nano / LiteRT-LM model with ADK tool calling capabilities.
   - When a commercial transition event is flagged by the spectrogram comparator, the ADK agent receives the event payload, determines intent, and dispatches hardware/network actions.

---

## Technical Proposal: IR Emitter & TV Webhook Action Engine

Direct hardware control capabilities to act upon commercial detection:

1. **Simple IR Send (Mute / Unmute)**:
   - Interface with Android `ConsumerIrManager` (`getSystemService(CONSUMER_IR_SERVICE)`).
   - Transmit standard consumer IR carrier frequency patterns (e.g., 38 kHz NEC / Sony / Philips RC6 protocol pulse bursts) for `MUTE` and `UNMUTE`.
2. **IR Signal Acquisition & Calibration**:
   - Receive incoming IR raw frequency samples (via IR receiver hardware or learning dongle) to register custom remote controls.
   - Store signal timing vectors for user correction and training.
3. **Hisense Android TV IP Control & Webhooks**:
   - Dispatch HTTP POST/REST webhooks over local Wi-Fi to Hisense Android TV control endpoints (e.g., MQTT / HTTP remote services).
   - Configurable webhook URL schema: `http://<tv-ip>:8080/api/v1/remote/mute`.

---

## Technical Proposal: Game-Style HUD & POV Interface Redesign

Transition from standard static dashboard layout to an immersive, game-like Heads-Up Display (HUD):

1. **POV Camera & Video Background**:
   - Fullscreen live camera feed (targeting TV in living room) or video playback backdrop.
2. **Data Overlay Graphics**:
   - Floating audio Mel-spectrogram waterfall / spectrograph widgets overlaid on the video stream.
   - Tactical targeting reticle locked onto the TV broadcast zone with real-time commercial confidence indicators.
   - Dynamic HUD status panels for IR emitter state, ADK agent telemetry, and active webhooks.
3. **Action Wheels & Tactical Controls**:
   - Circular command wheel overlay for quick IR mute, unmute, and manual override triggers.

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.*
