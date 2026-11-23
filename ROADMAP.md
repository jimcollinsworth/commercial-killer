# ROADMAP.md — Product Vision & Technical Proposals

## Product Vision

Commercial Killer is an **on-device experimentation workbench** for audio and video data. It enables developers to test real-time feature detectors, signal transforms, and local machine learning models directly on phone hardware without cloud latency or data privacy concerns.

## Technical Proposal: Significance Determination Alternatives

When comparing sequential Mel-spectrogram frames (calculated at a configurable 100 ms interval), two non-generative approaches evaluate whether an audio or scene transition requires action:

### Alternative A: 1D Matrix Distance Metric (`SpectrogramComparator`)
- **Algorithm**: Compute Mean Squared Error (MSE), Euclidean Distance, or Cosine Distance between consecutive Mel-spectrogram frequency bin vectors $S_t$ and $S_{t-1}$.
- **Characteristics**: Sub-millisecond execution ($< 1\text{ ms}$ on CPU/DSP), single scalar distance output ($\Delta$), deterministic thresholding ($\Delta > \tau$).
- **Limitations**: Measures raw spectral magnitude change without distinguishing commercial compression from speech volume shifts or tracking shot cadence over time.

### Alternative B: Fast Local Android Decision Model (`FrameDecisionEngine`, Non-Autoregressive System 1 Evaluator)
- **Algorithm**: Combine parallel on-device LiteRT / MediaPipe audio classification (`AudioClassifierEngine`) and synchronized `250 ms` video embeddings (`VideoFrameAnalyzer` / `VisualFrameDescriptor`) with deterministic, non-autoregressive per-frame decision queries in `FrameDecisionEngine` running on every `100 ms` frame.
- **Characteristics**: Zero autoregressive token generation, sub-millisecond per-frame vector scoring, multi-factor fusion (classifier tags, spectral energy compression/variance, shift distance, `72`-float System 1 visual descriptors using spatial L1 luma similarity + RGB histogram L1 distance, and rolling audio/visual cut cadence), and hysteresis-gated `MUTE` / `UNMUTE` / `HOLD` outputs, paired with an optional Stage 2 constrained single-token `litertlm-android:0.18.0` cascade verifier (`LiteRtLmDecisionVerifier`) using `Backend.GPU()` -> `Backend.CPU()` fallback when an external `.litertlm` model is present.
- **Status (`v2.5`)**: `VideoFrameAnalyzer` extracts `72`-float visual descriptors (`16` `lumaGrid`, `24` `8R+8G+8B` `colorHistogram`, `16` bottom-right `cornerRoiLuma`, `16` bottom-left `bottomLeftRoiLuma`, `isBlackFrame`, `visualChangeScore`) via reusable `ARGB_8888` `Bitmap` + `IntArray` buffers across CameraX `ImageAnalysis` (`MIC / CAM`) and `TextureView` + `MediaPlayer` (`FILE / VIDEO`). `ProgramFingerprintCache` maintains a `6`-angle intra-scene bank plus a `15 s` pre-break ring (`2 s` pre-mute rollback, `K = 5` diverse prototypes, dual-corner watermark stability) fused into 3-modality `FrameDecisionEngine` (`AUDIO_ONLY`, `AUDIO_VIDEO`, `VIDEO_ONLY_MUTED`), while `SessionSegmentTracker` and `SessionTimelineCard` retain fixed-height equal-width segment rows with `MiniMelStrip` + `64×36` thumbnails post-stop.

### Comparison: 1D Matrix Distance vs. On-Device Fast Decision Model

| Dimension | 1D Matrix Distance (`SpectrogramComparator`) | On-Device Fast Decision Model (LiteRT / MediaPipe + `FrameDecisionEngine`) |
| --- | --- | --- |
| **Inference Type** | Single-frame vector distance | Non-autoregressive discriminative System 1 evaluator |
| **Execution Interval** | 100 ms (configurable) | 100 ms per-frame evaluation |
| **Primary Inputs** | Current & previous Mel vectors ($S_t, S_{t-1}$) | Mel vector $\vec{F}_t$, `List<ClassifierScore>`, shift distance $\Delta$, cut cadence |
| **Structured Outputs** | Scalar shift distance ($\Delta$) | `is_media` (`MediaEnvironment`), `is_commercial`, `is_same_scene` cosine similarity, `shotDurationMs`, `cutCadencePerMin`, `TvActionDecision` |
| **Memory Allocation** | Scalar accumulator | Pre-allocated `previousFrame` `FloatArray` buffer + bounded 60,000 ms `ArrayDeque<Long>` |
| **Action Gating** | Single threshold crossing ($\Delta > \tau$) | First-gate media check (`!isMedia` forces `HOLD`) + frame-count hysteresis (`MUTE` after scene cut or 3 commercial frames; `UNMUTE` after 5 program frames) |

In `v2.5`, `AudioWorkbenchEngine` executes `100 ms` audio inspection alongside `250 ms` `VideoFrameAnalyzer` visual sampling across `MIC / CAM` (`AudioSourceMode.LIVE`) and `FILE / VIDEO` (`AudioSourceMode.FILE`), fusing `SpectrogramComparator`, `AudioClassifierEngine`, `ProgramFingerprintCache`, `FrameDecisionEngine`, and `SessionSegmentTracker` (with optional Stage 2 `LiteRtLmDecisionVerifier` cascade verification on macro transitions) to drive `TvControlManager`.

---

## Fast Local Android Decision Model Architecture

The v2.3.01 Fast Local Android Decision Model (`com.example.commercialkiller.data.decision.FrameDecisionEngine`) executes three non-autoregressive per-frame queries directly on Android hardware every 100 ms.

### 1. Per-Frame Structured Queries (100 ms Cadence)

Every 100 ms analysis tick produces a `FrameDecision` record by executing three structured queries:

1. **`is_media` (`evaluateMediaPresence`)**:
   - First-stage acoustic gate distinguishing broadcast TV/radio media from non-media states (`MediaEnvironment.BROADCAST_MEDIA`, `MediaEnvironment.SILENCE`, `MediaEnvironment.AMBIENT_NOISE`).
   - Evaluates total Mel energy (`meanEnergy < 0.08f` silence floor), `0.020f` RMS thermal floor, spectral tilt, mid-band formant ratio vs. steady low-frequency car rumble (`zcr < 0.04`, `hfRatio < 0.45`), flat broadband ambient noise (`(zcr > 0.30 || hfRatio > 1.35) && modRatio < 0.28 && rms < 0.22`), unpitched room/pink/HVAC/rain noise (`computePreEmphasizedPitchCorrelation(samples)` across lags `32..min(200, n/2)` step 2 with `0.92f` pre-emphasis: `maxPitchCorr < 0.30f && rms < 0.14f`), plus parallel classifier tag probabilities (`Dialogue`, `Speech`, `Commercial`, `Vehicle`, `Ambient`, `Silence`, `Room`, `Hum`, `Air Conditioning`). Live `MIC` capture prioritizes `MediaRecorder.AudioSource.MIC` / `UNPROCESSED` / `DEFAULT` without ASR speech-band AGC and throttles `processAudioFrame` ticks to `_state.value.intervalMs` (`100 ms`).
   - Clamps `mediaScore` to `0.0f..1.0f` and checks `isMedia = mediaScore >= mediaThreshold` (default `0.50f`, slider `0.20f..0.90f`). When `!isMedia`, `isCommercial = false`, `commercialScore = 0f`, and `recommendedAction = TvActionDecision.HOLD` (resetting break streaks after `> 3` consecutive non-media frames).

2. **`is_same_scene` (`evaluateSceneContinuity`)**:
   - Computes the cosine similarity between consecutive Mel-spectrogram feature vectors $\vec{F}_t$ (current frame) and $\vec{F}_{t-1}$ (pre-allocated previous frame buffer) across $B$ Mel bands (40–128 bands, default 80):
     $$\text{similarity}(\vec{F}_t, \vec{F}_{t-1}) = \frac{\vec{F}_t \cdot \vec{F}_{t-1}}{\|\vec{F}_t\| \, \|\vec{F}_{t-1}\|} = \frac{\sum_{i=1}^{B} F_{t,i} \, F_{t-1,i}}{\sqrt{\sum_{i=1}^{B} F_{t,i}^2} \sqrt{\sum_{i=1}^{B} F_{t-1,i}^2}}$$
   - **Boundary Handling**: On the first frame, when either vector norm is zero, or when the configured Mel band count $B$ changes, `similarity = 1.0f` and `isSameScene = true` (initializing `currentShotStartMs = timestampMs`).
   - **Scene Cut Detection & Cadence Tracking**:
     - `isSameScene = similarity >= sceneSimilarityThreshold` (default `0.85f`, engine range `0.50f..0.99f`, UI settings slider `0.50f..0.98f`).
     - When `!isSameScene` (scene cut), `timestampMs` is appended to `cutTimestampsMs` (`ArrayDeque<Long>`) and `currentShotStartMs` resets to `timestampMs`.
     - Timestamps older than `timestampMs - 60_000L` are pruned on every frame to maintain a rolling 60,000 ms window.
     - Emits `shotDurationMs = (timestampMs - currentShotStartMs).coerceAtLeast(0L)` and `cutCadencePerMin = cutTimestampsMs.size.toFloat()`.
   - **Hot-Loop Allocation Avoidance**: Copies `currentFeatures` into a reused `previousFrame` `FloatArray` in place without per-frame heap allocation.

3. **`is_commercial` (`evaluateCommercial`)**:
   - Executed when `isMedia` is true; evaluates a bounded discriminative commercial likelihood score (`commercialScore` clamped to `0.0f..1.0f`) by combining three signal groups:
     - **Classifier Tag Probabilities**: Weighted sum over parallel LiteRT / MediaPipe `List<ClassifierScore>` tags, applying positive weights for commercial, jingle, music, singing, and advertisement classes and offsetting weights for program speech, dialogue, and silence classes.
     - **Spectral Compression & Loudness Density**: Evaluates elevated mean Mel energy, low band-to-band variance (full-spectrum dynamic range compression), and the 1D frame transition `shiftDistance`.
     - **Rolling Cut Cadence Boost**: Adds positive commercial weight when `cutCadencePerMin >= 15f` cuts/min.
   - Outputs `isCommercial = commercialScore >= commercialThreshold` (default `0.75f`).

### 2. Telemetry & Action Gating (`TvActionDecision`)

`FrameDecisionEngine.evaluateFrame(...)` evaluates Gate 1 (`isMedia`) first, then tracks consecutive state counters (`consecutiveCommercialFrames` and `consecutiveProgramFrames`) to emit `TvActionDecision`:
- **`TvActionDecision.HOLD` (Non-Media Gate)**: Forced when `!isMedia` (`SILENCE` or `AMBIENT_NOISE`); resets break counters after `> 3` consecutive non-media frames (`NON_MEDIA_STREAK_RESET_FRAMES`).
- **`TvActionDecision.MUTE`**: Triggered when `isMedia && isCommercial && (!isSameScene || consecutiveCommercialFrames >= 3)` (requires either a simultaneous scene cut or 3 consecutive commercial frames, >= 300 ms at 100 ms cadence).
- **`TvActionDecision.UNMUTE`**: Triggered when `isMedia && !isCommercial && consecutiveProgramFrames >= 5` (5 consecutive non-commercial program frames, >= 500 ms hysteresis).
- **`TvActionDecision.HOLD` (Hysteresis)**: Emitted on all intermediate frames.

### 3. UI Telemetry & Settings Integration

- **`MainScreen.kt` Fixed-Height Telemetry Layout**: Uses fixed-height, single-line ellipsized slots (`ShiftStatusBanner` `22.dp`, `FrameDecisionBar` `48.dp`, 3-slot `ClassifierLabelsContent` `24.dp`, `DistanceMeterCard` `34.dp`, `EventConsoleCard` `110.dp`) to eliminate vertical UI shift (`0 px` drift across `STEADY SIGNAL` / `SHIFT DETECTED` and `MEDIA` / `NO MEDIA` transitions).
- **`FrameDecisionBar` (`MainScreen.kt`)**: Displays fixed-weight single-line badges underneath `SpectrogramWaterfallCard`: `MEDIA <score>%` / `NO MEDIA: SILENCE` / `NO MEDIA: AMBIENT`, `COMMERCIAL <score>%` / `PROGRAM <score>%` / `HOLD (NO MEDIA)`, `SAME <sim>` / `CUT <sim>`, and monospace telemetry (`Media: <score>% | Shot: <X.X>s` and `<N> cuts/min`).
- **Threshold Controls (`SpectrogramSettingsSheet`)**: Exposes `Media Detection Threshold` (`0.20f..0.90f`) and `Scene Cut Similarity Threshold` (`0.50f..0.98f`) sliders bound to `AudioWorkbenchEngine`.

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
   - Fast Local Android Decision Model (`FrameDecisionEngine`) executing non-autoregressive 100 ms per-frame queries
   - Local ONNX / LiteRT classification models via NNAPI
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

Integration of Google's **Agent Development Kit (ADK) for Kotlin 1.0** to provide deterministic action dispatching and bidirectional state machine communication:

1. **Architecture & Scope**:
   - Utilize KMP-ready ADK Kotlin core with `InMemoryRunner` for on-device agent state machine management.
   - Define structured action tools in Kotlin: `MuteAudioAction`, `UnmuteAudioAction`, `SendIrCommandAction`, and `TriggerTvWebhookAction`.
2. **Deterministic Decision Action Routing**:
   - Route structured `FrameDecision` payloads (`isCommercial`, `isSameScene`, `shotDurationMs`, `cutCadencePerMin`, `recommendedAction`) from `FrameDecisionEngine` directly into ADK Kotlin state transitions.
   - Execute hardware IR and TV webhook action tools without autoregressive token orchestration.

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
       └───► [Approach 2: Dual-Input Siamese CNN] ───► Transition Probability Score
```

---

### 3. Evaluated Vision Architectures

#### Option A: On-Device Vision Embedder (MediaPipe / LiteRT MobileNetV4)
- **Mechanism**: Passes 2D spectrogram image through a pretrained visual embedding network to generate a 512-dimensional visual feature embedding vector $\vec{v}_t$.
- **Comparison**: Computes Cosine Distance between consecutive embedding vectors:
  $$D_{\text{visual}}(t, t-1) = 1 - \frac{\vec{v}_t \cdot \vec{v}_{t-1}}{\|\vec{v}_t\| \|\vec{v}_{t-1}\|}$$
- **Performance**: $< 8\text{ ms}$ inference time on mobile GPU/NPU; low memory footprint.

#### Option B: Siamese Spectrogram Comparator CNN
- **Mechanism**: A dedicated two-branch convolutional network that accepts both spectrogram image patch $I_{t-1}$ (program baseline) and $I_t$ (candidate window), outputting a binary transition classification score $P(\text{commercial\_break})$.
- **Mechanism Details**: Trained on time-frequency spectrogram textures to separate ambient mic noise from full-spectrum commercial mastering profiles.

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

### 3. Implemented Phasing (`v2.5`)

- **Phase 1 (Program Signature Cache Module — Implemented)**:
  - `ProgramFingerprintCache.kt` (`v2.5`): maintains a `6`-angle active scene bank (`KNOWN_ANGLE_SIMILARITY_THRESHOLD = 0.78f`), a `60`-frame (`15 s`) pre-break ring with `2 s` pre-mute rollback, `K = 5` diverse frozen prototypes, dual-corner (`cornerRoiLuma`, `bottomLeftRoiLuma`) stability templates (`variance < 0.015f`, `contrast >= 0.08f`), and pre-break prototype seeding back into `activeSceneAngles` on `unfreeze()`.
- **Phase 2 (Video Stream Mute-Watcher Engine — Implemented)**:
  - `FrameDecisionEngine.kt` (`v2.5`): while `isTvMuted && activeVisual != null`, switches to `DecisionModality.VIDEO_ONLY_MUTED`, suppresses room silence/ambient drift, scores return similarity (`0.45*sProto + 0.35*sBug + 0.20*gatedCadence` when bug stable, or `0.80*sProto + 0.20*gatedCadence` otherwise; `PROGRAM_RETURN_UNMUTE_THRESHOLD = 0.80f`), and clears break cadence windows/buffers via `clearBreakTrackingOnUnmute()`.
- **Phase 3 (Unified Multimodal State Machine, Timeline & Stage 2 Hook — Implemented)**:
  - `SessionSegmentTracker.kt`, `LiteRtLmDecisionVerifier.kt`, `AudioWorkbenchEngine.kt`, and `SessionTimelineCard` (`MainScreen.kt`) aggregate ticks into equal-width retained macro segments (`PROGRAM`, `COMMERCIAL`, `NO_MEDIA`), wire `TvControlManager.sendMute()` / `sendUnmute()` (`Simulate TV Mute` test mode included), and dispatch asynchronous constrained `COMMERCIAL`/`PROGRAM`/`UNSURE` Stage 2 verification on macro transitions when a `.litertlm` model is staged on disk.

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8, LLM-Gemini4Argon, LLM-Gemini4Neon.*

