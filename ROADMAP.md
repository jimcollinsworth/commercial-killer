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

## Technical Proposal & Research: Vision-Based Spectrogram Image Comparison & Evaluation

### 1. Concept & Background
Traditional acoustic shift detection compares numerical 1D energy vectors between adjacent frames using mathematical distance metrics (e.g. Euclidean distance, Cosine distance). While fast, 1D vectors lack global temporal structure and texture awareness.

In audio signal processing, broadcast transitions (e.g., transition from quiet program dialogue to dynamic multi-harmonic commercial jingles) manifest as distinct **2D visual patterns** across the time-frequency domain:
- **Speech / Dialogue**: Sparse horizontal formant stripes modulating at 2–4 Hz syllable cadence.
- **Silence / Black Frame**: Dark horizontal bands with minimal energy.
- **Commercial Breaks**: Dense, high-contrast vertical blocks across all frequency bands due to dynamic range compression and loudness boosting (+6 to +10 dB).

By converting the Mel-spectrogram buffer into a standardized 2D image bitmap (e.g., $128 \times 128$ or $224 \times 224$ RGB image), we can leverage on-device **Computer Vision models and Visual Embedders** to evaluate transitions visually.

---

### 2. Spectrogram-to-Image Transformation Pipeline

```
[Audio PCM Stream]
       │
       ▼
[40-Band Mel-Spectrogram Calculation (100ms per frame)]
       │
       ▼
[Rolling Time Window Buffer (e.g., 30–60 frames = 3.0–6.0s)]
       │
       ▼
[Log-Magnitude Energy Normalization & Colormapping]
  • Log-scale dynamic range compression
  • Colormap: Viridis / Magma / Greyscale
       │
       ▼
[2D Bitmap / Tensor Generation (e.g., 224x224x3)]
       │
       ├───► [Approach 1: LiteRT Vision Embedder] ───► Cosine Similarity between Img(t) & Img(t-1)
       ├───► [Approach 2: Dual-Input Siamese CNN] ───► Transition Probability Score
       └───► [Approach 3: Local Gemini Nano Multimodal] ──► Semantic Boundary Evaluation
```

---

### 3. Evaluated Vision Architectures

#### Option A: Lightweight On-Device Vision Embedder (MediaPipe / LiteRT MobileNetV4)
- **Mechanism**: Passes 2D spectrogram image through a pretrained visual embedding network to generate a 512-dimensional visual feature embedding vector $\vec{v}_t$.
- **Comparison**: Computes Cosine Distance between consecutive embedding vectors:
  $$D_{\text{visual}}(t, t-1) = 1 - \frac{\vec{v}_t \cdot \vec{v}_{t-1}}{\|\vec{v}_t\| \|\vec{v}_{t-1}\|}$$
- **Performance**: $< 8\text{ ms}$ inference time on mobile GPU/NPU; low memory footprint.

#### Option B: Siamese Spectrogram Comparator CNN
- **Mechanism**: A dedicated two-branch convolutional network that accepts both spectrogram image patch $I_{t-1}$ (program baseline) and $I_t$ (candidate window), outputting a binary transition classification score $P(\text{commercial\_break})$.
- **Advantage**: Specifically trained on time-frequency spectrogram textures to ignore random mic noise while detecting commercial mastering characteristics.

#### Option C: Local Multimodal Vision LLM (Gemini Nano / AICore Image API)
- **Mechanism**: Renders consecutive spectrogram tiles side-by-side and submits the image directly to on-device Gemini Nano with multimodal prompt:
  *"Analyze these two consecutive audio spectrogram images. Has the broadcast transitioned from primary content into a commercial advertisement block?"*
- **Advantage**: Full semantic explanation and high boundary precision; ideal as a second-stage confirmation filter.

---

### 4. Implementation Phasing & Research Steps

1. **Phase 1 (Spectrogram Image Renderer)**:
   - Build `SpectrogramBitmapRenderer.kt` in Kotlin to convert `List<FloatArray>` rolling history buffers into Android `Bitmap` with normalized color palettes.
2. **Phase 2 (LiteRT Vision Embedder Integration)**:
   - Integrate LiteRT Image Embedder task to extract 2D feature representations.
   - Benchmark visual similarity distances against 1D matrix distance metrics across the `test_assets/` broadcast clips (`gunsmoke_broadcast_with_ad.mp3`, `frosted_flakes_1976_ad.mp4`).
3. **Phase 3 (Workbench Visualizer & Action Trigger)**:
   - Display the visual embedding distance curve in Compose UI.
   - Wire vision model transition thresholding directly to `TvControlManager.sendMute()`.

---

## Technical Proposal: Cross-Modal Audio-Visual Fingerprint Caching for Unmuting & Program Return

### 1. The Core Architecture & Acoustic Paradox Resolution
When the application mutes physical television speakers or soundbars via IR or Webhook, any ambient room microphone immediately goes deaf to the broadcast audio.

By combining the **Camera Video Stream** with **Audio Mel-Spectrogram Tracking**, the application solves this paradox through **Cross-Modal Co-Occurrence Caching**:

```
[Normal Program Playing]
       │
       ├──► Audio Pipeline ──► Continuous Mel-Spectrogram Analysis
       └──► Camera Pipeline ─► TV Screen Scene Recognition (Palette, Channel Bug, Motion)
       │
       ▼
[Joint Audio-Visual Program Baseline Cached]
 (Locks pre-break scene palette, channel logo presence, and acoustic profile)
       │
       ▼ [Commercial Detected]
 1. Send MUTE (IR / Webhook) ──► TV/Soundbar goes silent
 2. Freeze "Pre-Break Program Fingerprint" in Fast Memory Cache
       │
       ▼ [Muted Commercial Pod]
 • Microphone is silent (no audio data available)
 • Camera continues monitoring muted TV screen:
     - Detects commercial characteristics (rapid scene cuts, missing station bug, banner ads)
     - Continuously computes visual distance to Cached Program Fingerprint
       │
       ▼ [Program Returns on Video]
 Visual frame matches Cached Program Fingerprint:
   ✓ Station watermark / logo reappears
   ✓ Color palette & scene cadence match pre-break baseline
       │
       ▼
 1. Send UNMUTE (IR / Webhook) ──► TV/Soundbar audio restored
 2. Re-engage Live Microphone & Spectrogram Loop
```

---

### 2. Key Components of the Cached Program Fingerprint

1. **Station Bug / Watermark Anchor**:
   - During normal broadcast, networks anchor a transparent or semi-opaque watermark logo in a screen corner (e.g., lower-right).
   - The camera pipeline caches the corner ROI (Region of Interest) patch.
   - Commercials almost universally strip this watermark. When the exact template/feature reappears in that ROI, it acts as an immediate trigger for program resumption.
2. **Visual Color Distribution & Luminance Signature**:
   - Pre-break program scenes (e.g., sports green field, sitcom interior, newsroom lighting) maintain a characteristic color histogram.
   - Ads introduce distinct saturated palettes and high-contrast color shifts.
3. **Temporal Cut Cadence**:
   - Program content typically averages 4–8 seconds per camera shot.
   - Commercials pack 10–25 cuts into a 30-second spot (0.8–1.5s per shot). When cut frequency drops back to program baseline, confidence increases.

---

### 3. Implementation Phasing

- **Phase 1 (Program Signature Cache Module)**:
  - Create `ProgramFingerprintCache.kt` storing visual ROI patches, color gamut statistics, and pre-break Mel-spectrogram descriptors.
- **Phase 2 (Video Stream Mute-Watcher Engine)**:
  - While `isTvCurrentlyMuted == true`, switch detection driver from `AudioWorkbenchEngine` to `CameraScreen` / video frame evaluator.
  - Calculate frame similarity against `ProgramFingerprintCache`.
- **Phase 3 (Unified Multimodal State Machine)**:
  - Tie transition detection to automatic `TvControlManager.sendMute()` and return detection to `TvControlManager.sendUnmute()`.

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.*

