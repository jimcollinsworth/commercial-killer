# Commercial Killer — Audio/Video AI Workbench

An on-device experimentation workbench and real-time processing engine for Android (v2.3). Commercial Killer ingests live camera/microphone streams or local files, computes real-time Mel-spectrograms (40–128 bands), executes parallel MediaPipe / heuristic audio classification, and evaluates every 100 ms frame with a non-autoregressive Fast Local Android Decision Model (`FrameDecisionEngine`) to distinguish broadcast TV/radio media from silence/ambient environments (`is_media`), detect commercial breaks (`is_commercial`), track scene continuity (`is_same_scene`), and trigger TV muting with zero cloud dependencies.

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
  - Synchronized top video display when processing video files
  - High-resolution graphical Mel-spectrogram waterfall / heatmap
  - `FrameDecisionBar` HUD badges (`MEDIA`/`NO MEDIA: SILENCE`/`NO MEDIA: AMBIENT`, `COMMERCIAL`/`PROGRAM`/`HOLD (NON-MEDIA)`, `SAME SCENE`/`SCENE CUT`, shot duration, and cuts/min)
  - Live classifier tag display and execution log console

## Architecture & Directory Layout

- `app/src/main/java/com/example/commercialkiller/`
  - `MainActivity.kt`: Entry activity with edge-to-edge UI scaffold (`edge-to-edge`).
  - `Navigation.kt` & `NavigationKeys.kt`: Navigation graph and scene transitions.
  - `data/audio/AudioWorkbenchEngine.kt`: Core engine managing audio pipelines (SYNTH, MIC, FILE), 100 ms inspection loops, and automated muting triggers.
  - `data/audio/AudioFileDecoder.kt`: Multi-format audio/video file decoder (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS, MP4) and 16 kHz mono resampler.
  - `data/audio/AudioClassifierEngine.kt`: Parallel on-device audio classifier supporting open-weights Hugging Face models via LiteRT / TFLite.
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
  - `ui/camera/CameraScreen.kt`: CameraX surface preview and frame capture pipeline (`camerax`).
  - `ui/main/MainScreen.kt`: Jetpack Compose adaptive workbench dashboard (`adaptive`) featuring `FrameDecisionBar` HUD badges and `SpectrogramSettingsSheet`.
  - `theme/`: Material Design 3 adaptive theme setup (`adaptive`, `styles`).
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
