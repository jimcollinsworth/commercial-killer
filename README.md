# Commercial Killer — Audio/Video AI Workbench

An on-device experimentation workbench and real-time processing engine for Android. Commercial Killer ingests live camera/microphone streams or local files, computes real-time signal transformations (such as Mel-spectrograms), and runs on-device AI feature detectors and local LLMs (LiteRT-ML, MediaPipe, AICore / Gemini Nano) to detect content transitions and commercial breaks with **zero cloud dependencies**.

## Core Features & Vision

- **100% Local On-Device Execution**: Runs entirely on the phone hardware — no external cloud APIs or network calls.
- **Flexible Data Inputs**:
  - Microphones & live camera streams (`camerax`)
  - Local audio & video files
  - Combined stream pipelines & future streaming input services
- **Signal Processing & Feature Detection**:
  - Configurable Mel-spectrogram calculations (100 ms default interval)
  - Mel-spectrogram matrix shift & change significance detection
  - Native integration with LiteRT-ML (TFLite), MediaPipe, and HuggingFace models
- **Local Multimodal LLM Evaluation**:
  - Local Gemini Nano / AICore evaluations comparing current vs. previous spectrogram frames.
- **Real-Time Workbench Dashboard**:
  - Input stream video preview & live audio visualizer
  - Graphical Mel-spectrogram waterfall / heatmap
  - Live model execution logs and confidence metrics

## Architecture & Directory Layout

- `app/src/main/java/com/example/commercialkiller/`
  - `MainActivity.kt`: Entry activity with edge-to-edge UI scaffold (`edge-to-edge`).
  - `Navigation.kt`: Navigation graph and scene transitions.
  - `data/audio/AudioWorkbenchEngine.kt`: Core engine managing audio pipelines (SYNTH, MIC, FILE) and event triggers.
  - `data/audio/AudioFileDecoder.kt`: Multi-format audio file decoder (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS) and resampler.
  - `data/audio/AudioClassifierEngine.kt`: Parallel on-device audio classifier supporting open-weights Hugging Face models via LiteRT / TFLite.
  - `data/audio/MelSpectrogramCalculator.kt`: 40-band Mel-spectrogram calculation using Cooley-Tukey FFT.
  - `data/audio/SpectrogramComparator.kt`: Frame-to-frame matrix distance comparator with configurable thresholding.
  - `data/action/IrEmitterController.kt`: Hardware IR transmitter controller (`ConsumerIrManager` 38 kHz NEC pulses) and Hisense TV HTTP Webhook runner.
  - `ui/ir/IrSettingsScreen.kt`: Dedicated IR hardware test screen and smart TV webhook testing console.
  - `ui/help/HelpScreen.kt`: Comprehensive System Help & Technical Details screen explaining signal processing, Mel-spectrogram math, IR signals, webhooks, and workbench modes.
  - `ui/camera/CameraScreen.kt`: CameraX surface preview and frame capture pipeline (`camerax`).
  - `data/ai/AiAnalysisViewModel.kt`: On-device AI inference and frame change significance evaluator (`ml-kit-genai-prompt-api`).
  - `ui/main/MainScreen.kt`: Jetpack Compose adaptive workbench dashboard (`adaptive`).
  - `theme/`: Material Design 3 adaptive theme setup (`adaptive`, `styles`).
- `app/src/test/`: Automated unit tests for signal processing, decoding, and classification (`testing-setup`).
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
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.*
