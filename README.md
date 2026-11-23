# Commercial Killer — Audio/Video AI Workbench

An on-device experimentation workbench and real-time processing engine for Android (v2.4). Commercial Killer ingests live microphone + CameraX streams (`MIC / CAM`) or local video/audio files (`FILE / VIDEO`), computes real-time Mel-spectrograms (40–128 bands), samples `250 ms` visual frame descriptors, executes parallel MediaPipe / heuristic audio classification, and evaluates every 100 ms frame with a non-autoregressive Fast Local Android Decision Model (`FrameDecisionEngine`) to distinguish broadcast TV/radio media from silence/ambient environments (`is_media`), detect commercial breaks (`is_commercial`), track scene continuity (`is_same_scene`), and trigger TV muting with zero cloud dependencies.

## Core Features & Vision

- **100% Local On-Device Execution**: Runs entirely on phone hardware — zero external cloud APIs or network inference calls.
- **Flexible Data Inputs**:
  - Microphones & live camera streams (`camerax`)
  - Local audio & video files (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS, MP4)
  - Combined stream pipelines & future streaming input services
- **Signal Processing & Feature Detection**:
  - Configurable Mel-spectrogram calculations (40–128 bands, default 80; 100 ms default interval)
  - Mel-spectrogram matrix shift & change significance detection
  - Native integration with LiteRT-ML (TFLite), MediaPipe, and open-weights Hugging Face audio models
- **Fast Local Android Decision Model (`FrameDecisionEngine`)**:
  - Non-autoregressive per-frame evaluation every 100 ms with zero token generation latency
  - `is_media`: First-gate acoustic presence classifier distinguishing `BROADCAST_MEDIA` (TV/radio) from `SILENCE` and `AMBIENT_NOISE` (vehicle driving rumble or steady room/rain noise)
  - `is_commercial`: Discriminative scoring combining classifier tags, Mel spectral compression/loudness density, shift distance, and rolling cut cadence (gated on `is_media`)
  - `is_same_scene`: Cosine similarity comparison between consecutive Mel-spectrogram frames ($\vec{F}_t$ vs $\vec{F}_{t-1}$) tracking shot duration and cuts/min over a 60-second window
  - Hysteresis-gated `MUTE`, `UNMUTE`, and `HOLD` action outputs integrated into `TvControlManager`
- **Real-Time Workbench Dashboard**:
  - Top visual input viewport supporting live CameraX `Preview` + `ImageAnalysis` (`MIC / CAM`, with `CAM ON` / `CAM OFF` audio-only toggle) and `TextureView` + muted `MediaPlayer` video playback (`FILE / VIDEO`)
  - High-resolution graphical Mel-spectrogram waterfall / heatmap
  - Fixed-height HUD layout (`ShiftStatusBanner` 22dp, `FrameDecisionBar` 48dp with single-line `MEDIA`/`NO MEDIA: SILENCE`/`NO MEDIA: AMBIENT`, `COMMERCIAL`/`PROGRAM`/`HOLD (NO MEDIA)`, `SAME`/`CUT` badges, 3-slot `ClassifierLabelsContent` 24dp, `DistanceMeterCard` 34dp, and `EventConsoleCard` 110dp)
  - Live classifier tag display and execution log console

## Architecture & Directory Layout

- `app/src/main/java/com/example/commercialkiller/`
  - `MainActivity.kt`: Entry activity with edge-to-edge UI scaffold (`edge-to-edge`).
  - `Navigation.kt` & `NavigationKeys.kt`: Navigation graph and scene transitions.
  - `data/audio/AudioWorkbenchEngine.kt`: Core engine managing dual audio+video pipelines (`MIC / CAM` as `AudioSourceMode.LIVE` and `FILE / VIDEO` as `AudioSourceMode.FILE`), `100 ms` audio inspection loops (`MIC`/`UNPROCESSED` live priority and `intervalMs` throttling), `250 ms` `VisualFrameDescriptor` state, and automated muting triggers.
  - `data/video/VideoFrameSampler.kt`: Non-autoregressive visual descriptor extractor computing a `56`-float `VisualFrameDescriptor` (`16` luma grid, `24` RGB histogram, `16` bottom-right ROI luma) plus a `64×36` `RGB_565` `4,608`-byte thumbnail from CameraX YUV frames and `TextureView` video bitmaps.
  - `data/audio/AudioFileDecoder.kt`: Multi-format audio/video file decoder (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS, MP4) and 16 kHz mono resampler.
  - `data/audio/AudioClassifierEngine.kt`: Parallel on-device audio classifier supporting open-weights Hugging Face models via LiteRT / TFLite, plus acoustic fallback with pre-emphasized pitch-correlation room/ambient noise rejection (`maxPitchCorr < 0.30f && rms < 0.14f`).
  - `data/audio/MelSpectrogramCalculator.kt`: Configurable 40–128 band Mel-spectrogram calculation (default 80) using Cooley-Tukey FFT.
  - `data/audio/SpectrogramComparator.kt`: Frame-to-frame matrix distance comparator with configurable thresholding.
  - `data/decision/FrameDecisionEngine.kt`: Non-autoregressive per-frame decision model evaluating `is_media` (`MediaEnvironment`), `is_commercial`, `is_same_scene` cosine similarity, shot duration, rolling cut cadence, and `MUTE`/`UNMUTE`/`HOLD` gating.
  - `data/action/TvControlManager.kt`: Unified TV/Soundbar control manager handling persistent settings (SharedPreferences) and multi-channel MUTE/UNMUTE dispatch (Webhook / IR / USB IR).
  - `data/action/IrEmitterController.kt`: Hardware IR transmitter controller (`ConsumerIrManager` 38 kHz NEC pulses) and Hisense TV HTTP Webhook runner.
  - `data/action/UsbIrDongleController.kt`: External USB-C IR transmitter controller (`UsbManager` for `10C4:8468` Tview / Tiqiaa dongles).
  - `data/action/IrCodeDatabase.kt`: Predefined TV & Soundbar Code Set presets (Hisense Sets 1-4, Samsung, LG, Sony, Vizio, TCL, Bose, Yamaha, Polk, Custom Pronto).
  - `data/action/ProntoHexConverter.kt`: Universal Pronto Hex to ConsumerIrManager carrier frequency and microsecond pulse timing decoder.
  - `ui/components/AppIcons.kt`: Zero-dependency custom vector icons for navigation, settings, source selection, and transport actions.
  - `ui/components/SpectrogramVisualizer.kt`: Canvas visualizers for oscilloscope waveforms, Mel-spectrogram waterfall, and distance meter.
  - `ui/ir/IrSettingsScreen.kt`: Dedicated IR hardware & Webhook test screen with Code Set selector, Test & Step pairing wizard, and smart TV automation console.
  - `ui/help/HelpScreen.kt`: System Help & Details screen explaining signal processing, Mel-spectrogram math, `FrameDecisionEngine` queries, IR signals, webhooks, and workbench modes.
  - `ui/camera/CameraScreen.kt`: CameraX `Preview` and `ImageAnalysis` (`Size(320, 240)`, `250 ms` interval, `STRATEGY_KEEP_ONLY_LATEST`) capture pipeline (`camerax`).
  - `ui/main/MainScreen.kt`: Jetpack Compose adaptive workbench dashboard (`adaptive`) featuring two-mode `MIC / CAM` and `FILE / VIDEO` selection, `LiveCameraCard` (`CAM ON`/`CAM OFF` toggle), `TextureView` + `MediaPlayer` `VideoPlayerCard`, `FrameDecisionBar` HUD badges, and `SpectrogramSettingsSheet`.
  - `theme/`: Material Design 3 adaptive theme setup (`adaptive`, `styles`).
  - `.agents/skills/`: Installed Antigravity project skills (`appfunctions`, `litert-lm`, `litert-runtime`, `ml-kit-genai-prompt-api`, `mlkit-agent-api`).
- `app/src/test/`: Automated unit tests for signal processing, decoding, classification, TV/IR control, and `FrameDecisionEngine` (`testing-setup`).
- `gradle/libs.versions.toml`: Version catalog.

## Development & Build Setup

### Prerequisites
- Android SDK (API 36, Build-Tools 36.0.0)
- JDK 21 (Android Studio JBR)
- Android CLI (`android.exe`)

### Build Commands

```cmd
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set PATH=%JAVA_HOME%\bin;%PATH%
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug
```

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8, LLM-Gemini4Argon, LLM-Gemini4Neon.*
