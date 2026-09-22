# JOURNAL.md — Project Decision Records & Development Log

## 2026-09-21: Environment Discovery, Android CLI Setup & Project Scaffolding

> [!NOTE] User Instructions & Guidance:
> - Install Android CLI (`android.exe`) into `C:\Users\jimco\bin\android.exe`.
> - Use standard command runners via `cmd /c` (avoid PowerShell syntax).
> - Register project skills: `adaptive`, `android-cli`, `android-profiler`, `camerax`, `edge-to-edge`, `ml-kit-genai-prompt-api`, `testing-setup`.
> - Create initial project structure (`android create --name "Commercial Killer" empty-activity`).
> - Establish project governance document `AGENTS.md` and repository standards.

### Problem & Diagnosis
- Android CLI was missing from `C:\Users\jimco\bin`.
- Initial Kotlin compilation failed due to a missing coroutine import (`suspendCancellableCoroutine`) in `CameraScreen.kt`.

### Root Cause & Technical Analysis
- `suspendCancellableCoroutine` resides in `kotlinx.coroutines.suspendCancellableCoroutine`, not `kotlin.coroutines`.
- `JAVA_HOME` needed to point to Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`).

### Solution & Standard Procedure
1. Downloaded `android.exe` to `C:\Users\jimco\bin\android.exe` and ran `android init`.
2. Added `adb.cmd`, `emulator.cmd`, `fastboot.cmd` wrappers to `C:\Users\jimco\bin`.
3. Created project baseline via `android create` with Compose & AGP 9.
4. Integrated CameraX, Adaptive UI, and ML Kit Prompt API handlers.
5. Corrected import in `CameraScreen.kt` to `kotlinx.coroutines.suspendCancellableCoroutine`.
6. Verified Gradle build with `gradlew assembleDebug` (**BUILD SUCCESSFUL in 19s**).
7. Created project governance documents (`AGENTS.md`, `README.md`, `PLANNING.md`, `ROADMAP.md`, `JOURNAL.md`).

---

## 2026-09-21: Audio/Video AI Workbench Architecture & UI Verification

> [!NOTE] User Instructions & Guidance:
> - Transform project vision into an experimentation workbench for local audio/video data (microphones, cameras, local files, streaming).
> - Target native on-device Android APIs: LiteRT-ML (TFLite), MediaPipe, AICore / Gemini Nano, HuggingFace models, Librosa signal functions. 100% local execution on phone, zero cloud dependencies.
> - Incremental Step 1: Configurable Mel-spectrogram generator (100 ms interval) + significance change detection (matrix distance metrics & local multimodal LLM).
> - Capture emulator UI screenshots for walkthrough evidence.
> - Add Rule 19 to track token and LLM resource usage in `JOURNAL.md`.

### Problem & Diagnosis
- Baseline app display showed default template text ("Hello Android!").
- Needed real-time audio/video workbench layout, Mel-spectrogram visualizer components, and local AI significance event logging.

### Solution & Standard Procedure
1. Booted emulator `Medium_Phone_API_36.1` and deployed `app-debug.apk`.
2. Captured initial app UI screenshot via `adb shell screencap` to [app_screenshot.png](file:///C:/Users/jimco/.gemini/antigravity/brain/f53b2a75-c279-49f9-81c3-90b77fcbbd89/app_screenshot.png).
3. Added Rule 19 to `AGENTS.md` and established token & resource usage logging standard in `JOURNAL.md`.
4. Updated `README.md`, `PLANNING.md`, and `ROADMAP.md` to reflect the local-only Audio/Video AI Workbench architecture.

### Token & LLM Resource Log
- **Session ID**: `f53b2a75-c279-49f9-81c3-90b77fcbbd89`
- **Model Identifier**: `LLM-Gemini3.6` (Gemini 3.6 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\f53b2a75-c279-49f9-81c3-90b77fcbbd89\.system_generated\logs\transcript.jsonl` (280 steps recorded)
- **Token Accounting Status**: Raw per-step token counts are managed at the platform IDE host level; transcript logs track tool calls, timestamps, and step index.
- **Empirical System Resources Utilized**:
  - Android SDK 36 (`C:\Users\jimco\AppData\Local\Android\Sdk`)
  - ADB Daemon (`tcp:5037`)
  - JDK 21 OpenJDK (`C:\Program Files\Android\Android Studio\jbr`)
  - Emulator (`Medium_Phone_API_36.1`)
  - Android CLI (`C:\Users\jimco\bin\android.exe` v1.0.16261425)

---

## 2026-09-21: Real-Time Audio/Video AI Workbench Implementation & Verification

> [!NOTE] User Instructions & Guidance:
> - Build the complete on-device Audio/Video AI Workbench with real-time graphics indicating activity and actions.
> - Provide automated unit tests for signal processing and distance evaluation.
> - Follow `ml-best-practices` and `building-data-apps` guidelines.

### Problem & Diagnosis
- The app needed real-time visualizers (audio waveform oscilloscope, 40-band Mel-spectrogram waterfall, shift distance meter, and live event console) and automated tests.

### Solution & Standard Procedure
1. Implemented `MelSpectrogramCalculator.kt` computing Radix-2 Cooley-Tukey FFT and 40 triangular Mel filterbank energies.
2. Implemented `SpectrogramComparator.kt` calculating Euclidean, Cosine, and MSE distances with configurable significance thresholding.
3. Implemented `AudioWorkbenchEngine.kt` managing live `AudioRecord` PCM buffers and simulated benchmark streams.
4. Created hardware-accelerated Compose visualizers in `SpectrogramVisualizer.kt` (`WaveformOscilloscope`, `SpectrogramWaterfall`, `DistanceMeter`).
5. Updated `MainScreen.kt` with a responsive dashboard including threshold/interval sliders, real-time alert banner, and scrolling event console.
6. Created unit tests in `MelSpectrogramTest.kt` and `SpectrogramComparatorTest.kt` (**all passed, BUILD SUCCESSFUL in 36s**).
7. Re-deployed `app-debug.apk` to emulator `Medium_Phone_API_36.1` and captured live screenshots demonstrating real-time spectrogram shift and commercial event trigger.

### Token & LLM Resource Log
- **Session ID**: `f53b2a75-c279-49f9-81c3-90b77fcbbd89`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\f53b2a75-c279-49f9-81c3-90b77fcbbd89\.system_generated\logs\transcript.jsonl` (390+ steps recorded)
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Android SDK 36 Build-Tools 36.0.0
  - ADB Daemon (`tcp:5037`)
  - JDK 21 OpenJDK (`C:\Program Files\Android\Android Studio\jbr`)
  - Emulator (`Medium_Phone_API_36.1`)
  - Gradle 9.1.0 Daemon

---

## 2026-09-22: Remote Push to GitHub Repository

> [!NOTE] User Instructions & Guidance:
> - Push changes directly to `main` on the existing repository `jimcollinsworth/commercial-killer`.

### Problem & Diagnosis
- Local commit `0abdb7f` containing Step 1 implementation was ready on branch `main` and needed to be synchronized to GitHub remote.

### Solution & Standard Procedure
1. Executed `cmd.exe /c "git push origin main"`.
2. Verified remote update: `711a918..0abdb7f  main -> main` pushed to `https://github.com/jimcollinsworth/commercial-killer.git`.

### Token & LLM Resource Log
- **Session ID**: `f53b2a75-c279-49f9-81c3-90b77fcbbd89`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\f53b2a75-c279-49f9-81c3-90b77fcbbd89\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Git CLI (`git push origin main`)
  - Remote: `https://github.com/jimcollinsworth/commercial-killer.git`

---

## 2026-09-22: Multi-Format Audio File Loading, Parallel Hugging Face Classifier & Realistic Simulation

> [!NOTE] User Instructions & Guidance:
> - Add audio file loading supporting as many formats as possible.
> - Implement parallel audio classifier stream supporting open-weights Hugging Face models (no commercial classifiers).
> - Implement a realistic simulation approach (multi-formant speech, silence dips, loudness jumps, chordal musical beds).
> - Identify sources of actual broadcast recordings with commercials for testing.
> - Apply skills `/ml-kit-genai-prompt-api`, `/adaptive`, and `/testing-setup`.

### Problem & Diagnosis
- The workbench previously only supported synthetic 220 Hz vs 880 Hz pure tones and live microphone input.
- Audio file loading and parallel on-device classification were missing.
- In JVM unit testing, `android.util.Log` threw runtime exceptions, and an initial AGP manifest collision occurred between `tensorflow-lite-support` and `tensorflow-lite-task-audio`.

### Root Cause & Technical Analysis
- `MediaExtractor` and `MediaCodec` allow native decoding of WAV, MP3, AAC, M4A, FLAC, OGG, and OPUS formats on Android into PCM buffers.
- `org.tensorflow:tensorflow-lite-task-audio:0.4.4` encapsulates audio classification inference for open-weights models (like YAMNet or AST). Declaring both `tflite-support` and `tflite-task-audio` caused a duplicate namespace collision (`org.tensorflow.lite.support`) under AGP 9.0.
- Running unit tests on the JVM required `testOptions.unitTests.isReturnDefaultValues = true` and safe logging fallbacks for `android.util.Log`.

### Solution & Standard Procedure
1. Created feature branch `feature/audio-file-loading-and-classifier` (Rule 9).
2. Implemented `AudioFileDecoder.kt` using `MediaExtractor` and `MediaCodec` with automatic channel downmixing to mono and resampling to 16 kHz.
3. Implemented `AudioClassifierEngine.kt` executing open-weights Hugging Face TFLite models in parallel with the Mel-spectrogram engine, with an acoustic feature fallback classifier (Speech, Music, Commercial / Jingle, Silence).
4. Upgraded `runSimulatedLoop()` in `AudioWorkbenchEngine.kt` to synthesize multi-formant speech cadence ($F_1=500\text{ Hz}, F_2=1500\text{ Hz}, F_3=2500\text{ Hz}$), a 200 ms silence dip, and compressed commercial beds (+8 dB loudness, 440/554/659 Hz chords with rhythm).
5. Updated `MainScreen.kt` with a 3-way source selector (SYNTH, MIC, FILE), storage access framework file picker launcher, file playback card, and parallel audio classifier card with animated confidence bars.
6. Implemented unit tests in `AudioFileDecoderTest.kt` and `AudioClassifierEngineTest.kt` (**15 passing tests, BUILD SUCCESSFUL in 22s**).
7. Successfully assembled debug APK (`assembleDebug`) and verified on emulator `Medium_Phone_API_36.1` with live visual verification screenshots.
8. Documented curated broadcast test material sources from Internet Archive in `ROADMAP.md`.

### Token & LLM Resource Log
- **Session ID**: `73aa8283-cf2b-4599-90f0-6ecec254e26f`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\73aa8283-cf2b-4599-90f0-6ecec254e26f\.system_generated\logs\transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Android SDK 36 Build-Tools 36.0.0
  - ADB Daemon (`tcp:5037`)
  - JDK 21 OpenJDK (`C:\Program Files\Android\Android Studio\jbr`)
  - Gradle 9.1.0 Daemon
  - Android Emulator (`Medium_Phone_API_36.1`)
  - Python 3.12.10

---

## 2026-09-22: Intent-Based ADB Audio Loading & Broadcast Test Clips Verification

> [!NOTE] User Instructions & Guidance:
> - Kill the emulator.
> - Implement intent-based ADB file loading so files can be injected via `adb shell am start ...`.
> - Download and verify broadcast test clips from Internet Archive.
> - Apply skills `/android-profiler`, `/android-cli`, `/antigravity-guide`, and `/tool-discovery-policy`.

### Problem & Diagnosis
- Automating file benchmarking via the Android system file picker GUI required manual user clicks or fragile touch coordinate scripts.
- Actual broadcast recordings with commercial pods were needed locally for testing without committing large binary assets to Git.

### Root Cause & Technical Analysis
- `MainActivity` did not inspect incoming `Intent` extras or implement `onNewIntent`, preventing external injection of media paths via ADB.
- `AudioFileDecoder.kt` only accepted `content://` URIs; raw file paths and `file://` schemes required `ParcelFileDescriptor.open(file, MODE_READ_ONLY)`.
- Rule 17 mandates that large media files (> 500 KB) must not be committed to Git.

### Solution & Standard Procedure
1. Terminated emulator process cleanly via `adb emu kill`.
2. Updated `MainActivity.kt` to inspect `intent.getStringExtra("audio_file")` and handle `onNewIntent` to pass URIs to `AudioWorkbenchEngine.loadAudioFile()`.
3. Updated `Navigation.kt` to pass `AudioWorkbenchEngine` into `MainScreen`.
4. Updated `AndroidManifest.xml` with `singleTop` launch mode and media storage permissions.
5. Updated `AudioFileDecoder.kt` to support both `file://` and `content://` URIs via `ParcelFileDescriptor`.
6. Added `test_assets/` and media file extensions (`*.wav`, `*.mp3`, `*.mp4`, `*.ogg`, `*.aac`, `*.flac`) to `.gitignore`.
7. Downloaded and verified two real broadcast clips from Internet Archive into `test_assets/`:
   - `gunsmoke_broadcast_with_ad.mp3` (1.5 MB)
   - `frosted_flakes_1976_ad.mp4` (2.0 MB)
8. Incremented project version to `versionCode = 4`, `versionName = "1.3"`.
9. Verified build via `gradlew.bat testDebugUnitTest assembleDebug` (**BUILD SUCCESSFUL in 14s**).

### Token & LLM Resource Log
- **Session ID**: `73aa8283-cf2b-4599-90f0-6ecec254e26f`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\73aa8283-cf2b-4599-90f0-6ecec254e26f\.system_generated\logs\transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Android SDK 36 Build-Tools 36.0.0
  - ADB CLI (`adb emu kill`, `adb devices`)
  - JDK 21 OpenJDK (`C:\Program Files\Android\Android Studio\jbr`)
  - Gradle 9.1.0 Daemon
  - Python 3.12.10

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.*
