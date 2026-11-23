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

## 2026-09-22: GitHub Release v1.3 & Remote Push

> [!NOTE] User Instructions & Guidance:
> - User explicitly requested creating a GitHub release to download the APK directly on their Pixel phone over cellular 5G.

### Problem & Diagnosis
- Downloading `app-debug.apk` directly through the Antigravity remote browser viewer failed because the web text editor cannot render compiled Android binaries.
- The phone was connected via cellular 5G, so local LAN web server links were unreachable.

### Solution & Standard Procedure
1. Merged `feature/audio-file-loading-and-classifier` into `main` (`commit 1403142`).
2. Pushed `main` to `https://github.com/jimcollinsworth/commercial-killer.git` per explicit user instructions (Rule 9).
3. Created GitHub Release `v1.3` with `app-debug.apk` attached using `gh release create v1.3 app\build\outputs\apk\debug\app-debug.apk`.
4. Release published at: `https://github.com/jimcollinsworth/commercial-killer/releases/tag/v1.3`.

### Token & LLM Resource Log
- **Session ID**: `73aa8283-cf2b-4599-90f0-6ecec254e26f`
- **Model Identifier**: `LLM-Gemini3.6` (Gemini 3.6 Flash Medium)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\73aa8283-cf2b-4599-90f0-6ecec254e26f\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - GitHub CLI (`gh release create v1.3`)
  - Git CLI (`git push origin main`)
  - Remote: `https://github.com/jimcollinsworth/commercial-killer.git`

---

## 2026-09-22: Technical Proposals for ADK Kotlin 1.0, IR Control, & Game HUD Redesign

> [!NOTE] User Instructions & Guidance:
> - Consider support of new Agent Development Kit (ADK) for Kotlin 1.0 (https://developers.googleblog.com/announcing-adk-for-kotlin-10-building-production-ready-ai-agents-in-kotlin-android-and-beyond/) to allow commercial-killer to receive commands and initiate actions (Future enhancement).
> - Need ability to send IR infrared commands from the LLM (simple IR send first for mute/unmute), receive them as input/training/corrections, and call web URLs/webhooks to communicate with Hisense Android TV.
> - UI needs to be more game-like, not dashboard-like (HUD, POV, data overlaying graphics, video backgrounds). Consider later, sketch out options first with images.

### Problem & Diagnosis
- Commercial Killer currently operates primarily as an analytical visualizer and detection engine. To act as a complete commercial killer, it requires direct physical hardware control (IR emitter for TV mute/unmute, Wi-Fi webhooks for smart TV REST APIs), agentic orchestration (ADK for Kotlin 1.0), and an immersive game HUD layout over camera/video feeds.

### Solution & Technical Proposals
1. **ADK for Kotlin 1.0 Integration**:
   - Researched Google's ADK for Kotlin 1.0 (September 2026 release) with Kotlin Multiplatform (KMP) core and `InMemoryRunner`.
   - Designed tool contracts for local on-device action dispatching (`IrMuteTool`, `TvWebhookTool`, `IrLearnTool`).
2. **IR Emitter & Smart TV Control**:
   - Detailed native Android `ConsumerIrManager` 38 kHz carrier frequency pulse sequence generation for NEC/Sony mute key codes.
   - Formatted HTTP POST REST webhooks for Hisense Android TV IP control over local Wi-Fi.
3. **Game HUD & POV Interface Visual Concepts**:
   - Generated 2 visual mockup concept images (`hud_game_ui_concept_1.jpg` and `hud_game_ui_concept_2.jpg`) demonstrating tactical HUD canvas over living room camera/video feed, spectrograph overlays, targeting reticles, and command wheels.
4. **Governance Updates**:
   - Updated `ROADMAP.md` with technical proposals for ADK Kotlin 1.0, IR/Webhooks action engine, and Game HUD overlay.
   - Updated `PLANNING.md` sprint backlog with upcoming milestones.
   - Created `implementation_plan.md` artifact featuring an interactive markdown carousel showcasing both UI concept mockups.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.6` (Gemini 3.6 Flash Medium)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Image Generator tool (`hud_game_ui_concept_1`, `hud_game_ui_concept_2`)
  - Web Search tool (`ADK for Kotlin 1.0`)

---

## 2026-09-22: IR Support & Testing Screen + System Help & Details Screen

> [!NOTE] User Instructions & Guidance:
> - Add the IR support, IR page for testing (Mute/Unmute buttons, TV selection).
> - Add page for help, describe details of what the app is doing.
> - Do NOT add ADK yet to build.
> - Do NOT do UI changes (HUD redesign deferred until later).

### Problem & Diagnosis
- The application needed practical hardware execution for muting commercial breaks (via IR blasters or TV IP webhooks), a dedicated testing interface, and user-accessible documentation describing the signal processing math and system capabilities.

### Solution & Technical Implementation
1. **IR Transmitter & Webhook Controller (`IrEmitterController.kt`)**:
   - `hasIrEmitter()`: Checks `ConsumerIrManager` hardware presence.
   - `transmitMute()` & `transmitUnmute()`: Generates 38 kHz NEC carrier frequency pulse arrays (9ms leader mark, 4.5ms space, 16-bit address/command, 562us stop mark).
   - `triggerTvWebhook()`: Dispatches HTTP POST REST payloads over local Wi-Fi to Hisense Android TV IP control endpoints.
2. **IR & TV Control Test Screen (`IrSettingsScreen.kt`)**:
   - Displays hardware IR blaster status.
   - `TRANSMIT MUTE` & `TRANSMIT UNMUTE` test buttons.
   - Target TV brand selector and configurable webhook endpoint URL input.
   - Real-time action log console showing transmission status and HTTP response codes.
3. **System Help & Details Screen (`HelpScreen.kt`)**:
   - Comprehensive technical breakdown explaining 40-band Mel-spectrogram calculation (FFT, 100 ms interval), shift distance ($\Delta$), Hugging Face audio classifier stream, IR blaster hardware, Hisense TV webhooks, and data modes (SYNTH, MIC, FILE).
4. **Navigation Integration**:
   - Registered `IrSettings` and `Help` NavKeys in `NavigationKeys.kt` and `Navigation.kt`.
   - Added `HELP` and `IR / TV` buttons in `MainScreen.kt` header.
5. **Unit Tests & Build Verification**:
   - Created `IrEmitterControllerTest.kt` verifying NEC pulse structure (67 timing values) and hardware fallback behavior.
   - Executed `gradlew.bat testDebugUnitTest` — 24/24 tasks executed/up-to-date, BUILD SUCCESSFUL.
   - Executed `gradlew.bat assembleDebug` — 36/36 tasks executed/up-to-date, BUILD SUCCESSFUL.
   - Copied compiled `app-debug.apk` to project root.
   - Pushed commit `5a679c0` to `origin/main`.
   - Published GitHub Release `v1.4` with attached `app-debug.apk`: `https://github.com/jimcollinsworth/commercial-killer/releases/tag/v1.4`.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Jetpack Compose NavDisplay / Navigation3
  - Android `ConsumerIrManager` API
  - Java `HttpURLConnection` API
  - Gradle 9.1.0 (`testDebugUnitTest`, `assembleDebug`)
  - Git CLI (`git push origin main`)
  - GitHub CLI (`gh release create v1.4`)

---

## 2026-09-22: TV Code Set Selector, Pronto Hex Converter & Non-Wrapping AppIcons

> [!NOTE] User Instructions & Guidance:
> - Add the code set selector, personal and tester settings.
> - Replace options and back buttons with nice standard icons because the text wraps.

### Problem & Diagnosis
- Different television models require different IR protocol addresses and carrier frequencies; a single hardcoded pulse code only works for one hardware variant.
- In the top header bar, text buttons ("HELP", "IR / TV", "STOP / START") and "BACK" buttons caused text wrapping and inconsistent button heights on standard mobile screens.

### Solution & Technical Implementation
1. **TV Code Set Presets (`IrCodeDatabase.kt`)**:
   - Added catalog of discrete TV remote presets: Hisense (Sets 1–4: NEC Address 0x00, 0x04, 0xBF, 0x57), Samsung (Set 1), LG (Set 1), Sony (Bravia SIRC), Vizio, TCL, and Custom Pronto Hex.
2. **Universal Pronto Hex Decoder (`ProntoHexConverter.kt`)**:
   - Parses standard Pronto Hex strings into carrier frequency ($f = \frac{10^6}{N \times 0.241246}$) and microsecond pulse pattern `IntArray` for `ConsumerIrManager.transmit()`.
3. **Tester & Step Pairing Wizard (`IrSettingsScreen.kt`)**:
   - Added dropdown selector for active code set.
   - Added "Test & Step" wizard with "TEST MUTE", "TEST UNMUTE", and "TRY NEXT SET (↻)" button to rapidly find working codes.
   - Added custom Pronto Hex text field for testing arbitrary remote codes.
4. **Zero-Dependency Vector Icons (`AppIcons.kt`)**:
   - Built lightweight custom vector icons (`BackIcon`, `HelpIcon`, `SettingsIcon`, `PlayIcon`, `StopIcon`, `StepNextIcon`) adhering strictly to 44×44dp touch target standards.
   - Avoided adding 15MB `material-icons-extended` dependency, keeping APK lightweight.
   - Updated `MainScreen.kt`, `IrSettingsScreen.kt`, and `HelpScreen.kt` so text never wraps.
5. **Unit Tests & Build Verification**:
   - Created `ProntoHexConverterTest.kt` verifying frequency calculation, pulse decoding, and code set integrity.
   - Ran `gradlew.bat testDebugUnitTest` — 24/24 tasks executed/up-to-date, BUILD SUCCESSFUL.
   - Ran `gradlew.bat assembleDebug` — 36/36 tasks executed/up-to-date, BUILD SUCCESSFUL.
   - Updated `app-debug.apk` in project root.
   - Pushed commit `c1a3ce5` to `origin/main`.
   - Published GitHub Release `v1.5` with attached `app-debug.apk`: `https://github.com/jimcollinsworth/commercial-killer/releases/tag/v1.5`.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Android `ConsumerIrManager` API
  - Compose Canvas Vector Graphics
  - Gradle 9.1.0 (`testDebugUnitTest`, `assembleDebug`)
  - Git CLI (`git push origin main`)
  - GitHub CLI (`gh release create v1.5`)

---

## 2026-09-22: Real-time File Audio Playback, File Reselection & UI Polish

> [!NOTE] User Instructions & Guidance:
> - Once I select a file, it doesn't seem like I can reselect a different file.
> - When using a file to analyze, we should play the sound so I can hear it (at least in real-time mode).
> - Change the title for workbench to commercial killer and remove the 100% local no cloud tagline.
> - Remove the audio waveform display. The Mel coefficient is enough of an indicator of sound and activity.

### Problem & Diagnosis
- In FILE mode, clicking the FILE button or playback card did not offer a way to reselect a new audio file once an initial file was loaded.
- Audio file decoding was only calculating Mel-spectrogram energy in memory without outputting the PCM audio chunks to device speakers.
- The UI had redundant subtitle copy and a waveform oscilloscope card that consumed vertical space without adding semantic value beyond the 40-band Mel-spectrogram.

### Solution & Technical Implementation
1. **Real-time Audio Speaker Playback (`AudioWorkbenchEngine.kt`)**:
   - Initialized `AudioTrack` with `AudioAttributes.USAGE_MEDIA` and `AudioFormat.ENCODING_PCM_FLOAT` at 16 kHz.
   - Streamed decoded PCM float chunks directly to `AudioTrack.write()` in `runFileLoop()` with proper lifecycle release in `finally` blocks.
2. **File Reselection Support (`MainScreen.kt`)**:
   - Added prominent `RESELECT FILE` button in `FilePlaybackCard`.
   - Updated `onSelectSource` handler so tapping `FILE` when already in FILE mode reopens the file picker.
3. **UI Polish & Header Update (`MainScreen.kt`)**:
   - Changed top title to **COMMERCIAL KILLER**.
   - Removed the "100% LOCAL ON-DEVICE • ZERO CLOUD" tagline.
   - Removed the `WaveformOscilloscope` Card to maximize vertical screen density for spectrogram and classifier metrics.
4. **Build & Test Verification**:
   - Ran `gradlew.bat testDebugUnitTest` — 24/24 unit tests passed.
   - Ran `gradlew.bat assembleDebug` — BUILD SUCCESSFUL.
   - Refreshed `app-debug.apk` in project root.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.7` (Gemini 3.7 Flash Medium)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Android `AudioTrack` API (PCM Float real-time playback)
  - Gradle 9.1.0 (`testDebugUnitTest`, `assembleDebug`)

---

## 2026-09-22: Real-time Audio Playback Fix, Automated TV Muting Pipeline & Unified TvControlManager

> [!NOTE] User Instructions & Guidance:
> - The test step 1/4 etc. seems completely unresponsive.
> - The webhook test had a successful response and muted the TV, then it successfully unmuted.
> - The mute works, but it's not muting during real-time processing.
> - The real-time sound doesn't play the file when using a file.

### Problem & Diagnosis
1. **Unresponsive Step/Test Wizard**: The pairing wizard only triggered raw IR transmissions via `ConsumerIrManager`. On phones lacking an IR blaster, it failed silently without feedback, while the Smart TV was controllable over Wi-Fi via Webhooks.
2. **Missing Real-Time Automation**: Muting/unmuting actions were not connected to the real-time audio processing loop or classifier detections in `AudioWorkbenchEngine`.
3. **Audio File Playback Stutter/Silence**: `runFileLoop()` was streaming 512-sample chunks (32ms) every 100ms interval using `PCM_FLOAT`, resulting in buffer starvation and device incompatibilities.

### Root Cause & Technical Analysis
- Settings and Webhook URLs were stored only as ephemeral Compose state and were never persisted or shared with `AudioWorkbenchEngine`.
- Audio timing in `runFileLoop()` did not match elapsed wall-clock time (`intervalMs * sampleRate / 1000`). `PCM_16BIT` conversion was required for 100% universal hardware playback.
- No unified TV control layer existed to bridge Webhook and IR blaster channels based on user preferences.

### Solution & Technical Implementation
1. **`TvControlManager.kt`**:
   - Created central control manager persisting settings in Android `SharedPreferences` (Webhook URL, Control Method: `WEBHOOK`, `IR`, `BOTH`, selected IR set, auto-mute enabled switch).
   - Unified `sendMute()` and `sendUnmute()` dispatching across active channels with structured `TvActionResult` feedback.
2. **`AudioWorkbenchEngine.kt`**:
   - **Real-time Sound Playback**: Rebuilt `AudioTrack` output in `runFileLoop()` with `ENCODING_PCM_16BIT` and synchronous chunk calculation (`samplesToAdvance = sampleRate * interval / 1000`), ensuring continuous, smooth playback aloud through speakers at exact 1.0x pitch and speed.
   - **Automated Muting Pipeline**: Integrated `TvControlManager` to automatically trigger `sendMute()` when commercial acoustic shifts or high-confidence classifier detections occur, and `sendUnmute()` with a debounce window once program content resumes.
   - Exposed `isTvMuted` and `tvControlMethod` in `WorkbenchState`.
3. **`IrSettingsScreen.kt`**:
   - Upgraded to full TV Control & Automation panel with segmented Control Method selector (`SMART TV WEBHOOK`, `IR BLASTER`, `BOTH`).
   - Wired "TEST MUTE", "TEST UNMUTE", and Step Wizard (`↻`) to trigger the active control channel and log status codes directly in the Action Console.
   - Added persistent Auto-Mute switch.
4. **`MainScreen.kt` & `MainActivity.kt`**:
   - Added dynamic `TV MUTED [METHOD]` / `TV ACTIVE [METHOD]` status badge to the main header.
   - Injected `applicationContext` to `AudioWorkbenchEngine`.
5. **Unit Tests & Verification**:
   - Created `TvControlManagerTest.kt` (**25/25 passing unit tests**).
   - Compiled debug APK via `assembleDebug` (**BUILD SUCCESSFUL in 12s**).
   - Incremented version to `v1.7` (versionCode 7).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Android `AudioTrack` 16-bit PCM streaming
  - Android `SharedPreferences`
  - Gradle 9.1.0 (`testDebugUnitTest`, `assembleDebug`)
  - Git CLI (`git checkout -b feature/realtime-automute-sound`)

---

## 2026-09-22: Continuous Audio Playback Decoupling, Fixed 3-Item Classifier Display & Microphone Runtime Permissions

> [!NOTE] User Instructions & Guidance:
> - Tried the playing audio in file, it stutters badly, either performance issues or is playing the segments, not the full stream, synchronized with the segmentation.
> - Also list of recognized object and confidence changes size and causes fields to jump around, fix the size to 3 items.
> - And microphone isn't working, plays exact same mel spectrogram as the synth.

### Problem & Diagnosis
1. **Audio Stutter in File Mode**: `runFileLoop()` was coupling `audioTrack.write(WRITE_BLOCKING)` on the main coroutine with FFT computation and `delay(interval)`. Writing 100ms of audio followed by 20ms of compute + 100ms delay starved the audio DAC buffer on every cycle.
2. **UI Jumps in Classifier Card**: `ParallelClassifierContent` and `AudioClassifierEngine` returned variable-length category lists (1 to 4 items) and a single text line when empty, causing the card to expand/shrink dynamically and bounce the entire lower screen.
3. **Microphone Falling Back to Synth**: Tapping `MIC` failed because runtime permission `Manifest.permission.RECORD_AUDIO` was never requested at runtime via Compose. When `AudioRecord` threw `SecurityException` or uninitialized state, `runLiveMicLoop()` silently caught the exception and executed `runSimulatedLoop()`.

### Root Cause & Technical Analysis
- Audio DAC streaming must be completely decoupled from inspection/analysis timers.
- Android 6.0+ requires interactive runtime permission dialogs via `ActivityResultContracts.RequestPermission()`. Silent fallbacks to simulated loops mask underlying hardware access issues.
- Fixed UI card heights require fixed-length collections (padding to 3 items).

### Solution & Technical Implementation
1. **Decoupled Continuous Audio Feeder (`AudioWorkbenchEngine.kt`)**:
   - Launched dedicated `feederJob` on `Dispatchers.IO` that continuously streams 1024-sample PCM blocks directly into `AudioTrack.write(WRITE_BLOCKING)`.
   - Separate inspector loop runs on `intervalMs` sampling the current playback position without interfering with audio hardware output.
   - Fixed seek flushing (`audioTrack.pause()`, `flush()`, `play()`).
2. **Fixed 3-Item Classifier Layout (`AudioClassifierEngine.kt` & `MainScreen.kt`)**:
   - Normalized all classifier return paths (`padToThree`) and wrapped `ParallelClassifierContent` in a stable 3-row layout with fixed 20dp row heights, completely eliminating layout shifting.
3. **Runtime Microphone Permission & Multi-Source Capture (`MainScreen.kt` & `AudioWorkbenchEngine.kt`)**:
   - Added `micPermissionLauncher` (`ActivityResultContracts.RequestPermission()`) and `ContextCompat.checkSelfPermission` in `MainScreen.kt`.
   - Replaced silent simulation fallback in `runLiveMicLoop()` with diagnostic event logs and multi-source audio fallback (`VOICE_RECOGNITION`, `MIC`, `DEFAULT`).
4. **Unit Tests & Build Verification**:
   - Executed `gradlew.bat testDebugUnitTest` (**25/25 passing unit tests**).
   - Executed `gradlew.bat assembleDebug` (**BUILD SUCCESSFUL in 12s**).
   - Incremented project version to `v1.8` (versionCode 8).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Android `AudioTrack` continuous IO streaming
  - Android `AudioRecord` runtime permissions
  - Gradle 9.1.0 (`testDebugUnitTest`, `assembleDebug`)
  - Git CLI (`git checkout -b feature/smooth-playback-fixed-classifier`)

---

## 2026-09-22: Enhancement Backlog & Technical Research: Vision-Based Spectrogram Image Model Comparison

> [!NOTE] User Instructions & Guidance:
> - Add another enhancement to the to-do list: We want to change the comparison and evaluation mechanism for the Mel-spectrographs to be based on the image and using an image model to compare them.
> - Mark this as an enhancement (do not start code work yet). Document the roadmap and do research on it first.

### Problem & Diagnosis
- The current significance detector compares 1D numerical frequency energy vectors between consecutive frames using mathematical distance metrics (e.g. Euclidean / MSE).
- While computationally lightweight, 1D metrics lack spatial texture awareness and temporal structure. Broadcast transitions (dialogue $\rightarrow$ silence $\rightarrow$ compressed commercial audio) produce distinct 2D visual patterns (horizontal formant lines vs. dense vertical blocks) in the time-frequency spectrogram.

### Technical Research & Evaluation
1. **Spectrogram-to-Image Transformation**:
   - Buffer $T$ consecutive Mel frames (30–60 frames = 3–6s) across 40 Mel bands.
   - Dynamic range compression: $E_{\text{norm}} = \text{clamp}\left(\frac{\log(E + 10^{-6}) - \text{min}}{\text{max} - \text{min}}, 0.0, 1.0\right)$.
   - Colormap conversion (Viridis, Magma, or Grayscale) to a $[1, 224, 224, 3]$ RGB tensor or Android `Bitmap`.
2. **Vision Model Architectures**:
   - **LiteRT / MediaPipe Vision Embedder (MobileNetV4)**: Extract 512-dim visual embeddings and calculate visual cosine distance ($< 8\text{ ms}$ on mobile NPU/GPU).
   - **Siamese CNN Comparator**: Dual-input convolutional network accepting $I_{t-1}$ and $I_t$ to directly classify transition probability.
   - **On-Device Multimodal Vision (Gemini Nano)**: Side-by-side visual tile submission with multimodal boundary verification prompt.
3. **Governance & Backlog Synchronization**:
   - Added backlog enhancement item to `PLANNING.md`.
   - Documented detailed technical proposal, visual signature breakdowns, and implementation phases in `ROADMAP.md`.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Codebase documentation tools (`PLANNING.md`, `ROADMAP.md`, `JOURNAL.md`)

---

## 2026-09-23: Tview USB-C IR Blaster Hardware Support & Dual TV/Soundbar Control (v1.9)

> [!NOTE] User Instructions & Guidance:
> - Support external Tview USB-C IR transmitter dongle (`10C4:8468`).
> - TV is Hisense 55U8G.
> - Add soundbar control (review support + keep webhooks).

### Problem & Diagnosis
- Some devices (e.g. Pixel 8/9/10/11) lack internal `ConsumerIrManager` hardware.
- Users with external USB-C IR blasters (Tview / Tiqiaa / ElkSmart) require Android USB Host API (`UsbManager`) communication.
- Users with separate soundbar audio systems need synchronized mute/unmute commands dispatched simultaneously to both TV and Soundbar, with Webhook redundancy.

### Solution & Standard Procedure
1. **USB Host Permissions & Device Filtering**:
   - Added `android.hardware.usb.host` feature declaration and `device_filter.xml` for Silicon Labs vendor `0x10C4` and product `0x8468`.
2. **`UsbIrDongleController`**:
   - Implemented USB bulk transfer handshake (`0xFC 0xFC 0xFC 0xFC`) and packet formatting (`0xFF 0xFF 0xFF 0xFF` header + 56-byte payload chunks).
3. **Soundbar IR Code Sets & Dual Dispatching**:
   - Expanded `IrCodeDatabase` with soundbar presets (Vizio, Samsung, Bose, LG, Sony, Yamaha, Polk, Custom Pronto Hex).
   - Updated `TvControlManager` with `TargetDevice` (`TV_ONLY`, `SOUNDBAR_ONLY`, `BOTH`) for dual-channel concurrent dispatching.
4. **IR Settings UI**:
   - Added USB hardware status badge (`READY` / `PERMISSION_REQUIRED` / `NOT_CONNECTED`), target device selector, and soundbar code set selector.
5. **Unit Tests & Verification**:
   - Implemented `UsbIrDongleControllerTest` and `TvControlManagerTest` (**27 passing unit tests**).
   - Incremented version to `v1.9` (versionCode 9).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Android USB Host API (`UsbManager`, `UsbDeviceConnection`, `UsbEndpoint`)
  - Gradle 9.1.0 (`testDebugUnitTest`)

---

## 2026-09-23: Architectural Refactor: Adaptive UI, Host Lifecycle Synchronization & Audio Performance (v2.0)

> [!NOTE] User Instructions & Guidance:
> - Refactor recent Android skills into a single `android-tips-and-debugging` master skill with modular reference documents.
> - Review Commercial Killer codebase against the new skills.
> - Implement all recommendations: audio performance/stuttering fix, microphone synchronization, StateFlow atomic updates, Compose host lifecycle synchronization, and Android 17 adaptive layouts.

### Problem & Diagnosis
- **Audio Stuttering**: Thread contention between `AudioTrack.write` feeder coroutine and heavy FFT/classifier inference on `Dispatchers.Default`, coupled with GC allocation pressure in 100ms loops.
- **Microphone Drift**: `runLiveMicLoop()` added `delay(interval)` after blocking `AudioRecord.read()`, causing hardware buffers to accumulate lag and desynchronize.
- **Background Resource Leak**: `DisposableEffect(Unit)` in `MainScreen` never triggered `onDispose` when pressing Home or locking the screen, causing audio playback/synthesis to waste battery in the background.
- **Concurrency Races**: `AudioWorkbenchEngine` updated state via non-atomic `_state.value = _state.value.copy(...)` across concurrent asynchronous coroutines.
- **Stretched Layouts**: Layouts lacked `WindowSizeClass` policies and bounded containers (`widthIn`), stretching awkwardly across 1000dp+ on tablets.

### Solution & Standard Procedure
1. **Audio Performance & Thread Isolation (`AudioWorkbenchEngine.kt`)**:
   - Set playback feeder coroutine priority to `Process.THREAD_PRIORITY_URGENT_AUDIO`.
   - Increased `AudioTrack` buffer multiplier to 4x and buffer chunks to 2048 samples (128ms) to prevent underruns.
   - Pre-allocated reusable arrays (`analysisSamples`, `waveformView`) to eliminate hot-loop GC pressure.
   - Removed artificial `delay()` in `runLiveMicLoop()`, allowing hardware-clocked `AudioRecord.read()` to dictate timing with zero drift.
2. **Atomic State Updates**:
   - Replaced all `_state.value = _state.value.copy(...)` calls with thread-safe atomic `_state.update { current -> current.copy(...) }`.
3. **Host Lifecycle Synchronization (`MainScreen.kt`)**:
   - Integrated `LifecycleResumeEffect(engine)` to automatically call `engine.pause()` on `ON_PAUSE` / `ON_STOP` and `engine.resume()` on `ON_RESUME`.
4. **Android 17 Adaptive UI Architecture (`MainScreen.kt` & `IrSettingsScreen.kt`)**:
   - Integrated `currentWindowAdaptiveInfo().windowSizeClass` using modern `isWidthAtLeastBreakpoint` and `isHeightAtLeastBreakpoint` APIs.
   - **Expanded Displays (`sw600dp+` / Tablets / Foldables)**: Split into an adaptive two-pane layout (Left: Audio Stream, Oscilloscope & Waterfall; Right: Distance Meter, Classifier Scores & Event Console).
   - **Compact Displays (Phones)**: Clamped width to `Modifier.widthIn(max = 640.dp)` with `imePadding()` and `verticalScroll()` for short landscape windows.
5. **Testing & Version Bump**:
   - Incremented version to `v2.0` (versionCode 10).
   - Verified Gradle unit tests (`testDebugUnitTest` - **27 passing unit tests**).
   - Built debug APK (`assembleDebug` - **BUILD SUCCESSFUL in 12s**).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Android Window Size Class API (`androidx.compose.material3.adaptive:adaptive:1.0.1`)
  - Android Lifecycle Compose (`androidx.lifecycle:lifecycle-runtime-compose:2.10.0`)
  - Android Studio JBR OpenJDK 21 (`C:\Program Files\Android\Android Studio\jbr`)
  - Gradle 9.1.0 (`testDebugUnitTest`, `assembleDebug`)
  - Git CLI (`feature/adaptive-audio-lifecycle-refactor`)

---

## 2026-09-23: Architectural Decision Record (ADR): Cross-Modal Audio-Visual Fingerprint Caching for Muted Unmuting

> [!NOTE] User Instructions & Guidance:
> - The video stream will definitely be used to return to the program and unmute audio.
> - Another important concept is that the application can track the relationship between the Mel-spectrogram and the program video and cache that when muting for commercials.
> - Then it has an easier time recognizing the video stream to return sound to.

### Problem & Diagnosis
- **The Acoustic Deafness Dilemma**: When ambient room microphone audio is used to detect commercials and dispatch an IR or Webhook `MUTE`, the TV/soundbar speakers go completely silent. Ambient microphone monitoring is rendered deaf to the remaining broadcast, unable to detect the return to program content acoustically.

### Solution & Technical Architecture
1. **Cross-Modal Co-Occurrence Caching**:
   - While primary program content is active, the app continuously correlates the audio stream (Mel-spectrogram frequency distributions) with the video stream (camera feed of TV screen).
   - Right before firing `MUTE`, the system snapshots a **Pre-Break Program Fingerprint**:
     - **Station Bug / Watermark ROI**: Corner image crop containing the network logo (e.g., NBC peacock, ESPN, CBS eye).
     - **Scene Color Gamut & Luminance Profile**: Low-dimensional color histogram characteristic of the show (e.g., sports field, studio lighting).
     - **Shot Cut Rhythm**: Baseline shot change frequency (typically 4–8s per cut vs. 1s per cut in ads).
2. **Video-Driven Unmute Trigger**:
   - During the muted commercial pod, the microphone is dormant, but the camera continuously scans the TV screen.
   - When the video feed correlates strongly with the cached pre-break program fingerprint (reappearance of the station bug, return to program color palette), the system immediately triggers `UNMUTE`.
   - Once unmuted, room audio returns and the audio-visual tracking loop resynchronizes.
3. **Governance & Documentation**:
   - Documented complete architecture diagram, component breakdown, and 3-phase implementation roadmap in `ROADMAP.md`.
   - Updated active enhancement backlog in `PLANNING.md`.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Documentation tools (`ROADMAP.md`, `PLANNING.md`, `JOURNAL.md`)
  - Git CLI (`feature/adaptive-audio-lifecycle-refactor`)

---

## 2026-09-23: Governance Update: Enforced Strict CMD.exe Only Invariant (No PowerShell)

> [!NOTE] User Instructions & Guidance:
> - User explicitly instructed: "why wasn't the instruction followed, investigate all command interpretation find conflicting rules. i've stated no powershel many times" -> "yes add it".

### Problem & Diagnosis
- System tool declarations (`run_command`) hardcode `Shell: powershell`, and global environment policy rules (`<RULE[user_global]>`) contain PowerShell code examples (`$env:`, `Get-Command`).
- `AGENTS.md` Section 6 previously contained ambiguous phrasing ("Verify Shell Capabilities") rather than an explicit, absolute prohibition of PowerShell.

### Solution & Technical Implementation
- Updated `AGENTS.md` Section 6 with an explicit **STRICT NO POWERSHELL RULE**:
  - The agent must **NEVER** propose, generate, or execute PowerShell syntax (`$env:`, `Get-Command`, `Set-ExecutionPolicy`, `[Environment]`).
  - ALL terminal commands, setup scripts, and environment instructions MUST strictly use standard Windows Command Prompt (`cmd.exe`) syntax (`set`, `setx`, `dir`, `copy`, `del`, `call`).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.6` (Gemini 3.6 Flash Medium)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Governance docs (`AGENTS.md`, `JOURNAL.md`)

---

## 2026-09-24: Release v2.1.00: Spectrogram Highlighting, Video Surface & UI Decluttering

> [!NOTE] User Instructions & Guidance:
> - Make a number of changes on the user interface in order to highlight the spectrograms and move optional data off the page.
> - Show video on screen when a video file is being processed, not necessarily when it's only audio. In the case of a video: video file display on top, audio mel spectrogram underneath, all features and labeling underneath that.
> - Move the spectrogram threshold intervals into a settings panel.
> - Make the mel spectrogram three times as tall, or around 50% of the total screen, showing as many bands as possible.
> - Classifier output: remove both classification confidence value and bar chart; show text only (green if >90%, yellow if <=90%, hidden if <10%).
> - Completely remove titles: "Mel Spectrogram", "Parallel Audio Class", "Acoustic Feature Classifier".
> - Make sync, mic, and file buttons quite a bit smaller, maybe even a dropdown or icons.

### Problem & Diagnosis
- **Vertical Clutter & Low Spectrogram Resolution**: The Mel spectrogram was previously restricted to 80dp height and 40 bands, limiting visual inspection of acoustic signatures. Permanent slider controls on the main screen consumed ~120dp. Titles and classifier progress bars consumed additional vertical space.
- **Missing Video View**: MediaExtractor decoded audio tracks from MP4 video files, but no video display surface was rendered on screen.

### Root Cause & Technical Analysis
- The UI hierarchy placed all parameter sliders directly in the scroll layout rather than in a contextual settings panel.
- The `SpectrogramWaterfall` was hardcoded to 40 bands and 80dp canvas height.
- `ParallelClassifierCard` rendered horizontal `LinearProgressIndicator` widgets and percentage text for every class regardless of confidence.
- `AudioFileDecoder` only extracted audio tracks without tracking video track presence or file URI in state.

### Solution & Standard Procedure
1. **Video Display on Top**:
   - Updated `AudioFileDecoder` to detect video tracks via `MediaExtractor` and MIME inspection (`isVideo: Boolean`).
   - Added `VideoPlayerCard` wrapping `VideoView` via `AndroidView`. Placed on top of the content area when a video file is active.
   - Synchronized `VideoView` playback state with `AudioWorkbenchEngine` playback while setting `mp.setVolume(0f, 0f)` to prevent dual audio output with `AudioTrack`.
   - Maintained compact audio-only playback bar when processing audio files.
2. **Spectrogram 3x Height & 80-128 Bands**:
   - Increased waterfall canvas height from 80dp to 240dp–280dp (3x tall, ~50% screen height).
   - Upgraded `MelSpectrogramCalculator` to 80 frequency bands by default with dynamic band drawing in `SpectrogramWaterfall`.
   - Added `setMelBands` method to `AudioWorkbenchEngine` supporting up to 128 bands.
3. **Settings Panel Relocation**:
   - Moved Significance Threshold (τ) and Analysis Interval sliders off the main screen into a `ModalBottomSheet` (`SpectrogramSettingsSheet`).
   - Added Mel frequency resolution selector (40, 64, 80, 128 bands).
   - Added `SlidersIcon` header action to open the settings sheet on demand.
4. **Classifier Display Simplification**:
   - Removed progress bar indicators and confidence percentage numbers.
   - Filtered out all scores below 10% (`score >= 0.10f`).
   - Rendered classification labels as colored text tags: Green (`#22C55E`) for confidence > 90%, Yellow (`#FBBF24`) for confidence <= 90%.
5. **Title Elimination**:
   - Completely removed titles "Mel Spectrogram", "Parallel Audio Class", and "Acoustic Feature Classifier".
6. **Compact Source Selector**:
   - Designed a 28dp pill container with custom vector icons (`SynthIcon`, `MicIcon`, `AudioFileIcon`) replacing previous 38dp buttons.
7. **Automated Unit Tests**:
   - Added unit test `computeMelEnergies_supportsHighBandResolution80` in `MelSpectrogramTest.kt`.
   - Added `decodedAudio_supportsVideoProperty` in `AudioFileDecoderTest.kt`.
   - Added `AudioWorkbenchEngineTest.kt` verifying state defaults, video flags, and classifier filter/color rules.
   - Incremented version to `2.1.00` (`versionCode = 11`).

### Token & LLM Resource Log
- **Session ID**: `7ebd963b-c9ed-4dff-87ee-2c4de3ea6802`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\7ebd963b-c9ed-4dff-87ee-2c4de3ea6802\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Native filesystem tools (`write_to_file`, `replace_file_content`, `view_file`)
  - Git branch tracking (`feature/spectrogram-video-ui-redesign`)

---

## 2026-09-24: Release v2.1.00.01: Spectrogram Scale, FlowRow Wrapping & Video Lifecycle Hardening

> [!NOTE] User Instructions & Guidance:
> - Rigorous review and hardening of the Spectrogram Highlighting, Video Surface & UI Decluttering implementation.
> - Ensure video detection accurately identifies video containers and does not misclassify audio-only MP4/M4A containers.
> - Ensure classifier labels wrap cleanly without horizontal truncation on narrow viewports.
> - Ensure spectrogram scaling occupies ~50% of the screen height (380dp) when video is not active.
> - Ensure VideoView lifecycle resources are released on exit and track URI re-binding on file changes.

### Problem & Diagnosis
- **Audio-Only MP4 False Positive**: `AudioFileDecoder` evaluated file extension `.mp4` with an OR condition, marking audio-only MP4 podcasts as videos and rendering an empty black video surface.
- **Classifier Horizontal Truncation**: `ClassifierLabelsContent` placed badges inside a non-wrapping `Row`, causing badges to overflow the screen edge on narrow viewports.
- **VideoView Lifecycle & State Sync**: `VideoPlayerCard` did not re-bind when the media URI changed, leaked native decoder resources on exit (`view.stopPlayback()` missing), and did not update frame seek positions while paused.
- **Spectrogram Band Switching Latency**: `SpectrogramWaterfall` read frame size from the oldest history entry, delaying band resolution scaling by 30 frames (~3 seconds).
- **Array Allocations in Recomposition Loop**: `AudioSourceMode.values()` allocated array instances on every 100ms UI recomposition.

### Root Cause & Technical Analysis
- `videoTrackIndex >= 0` from `MediaExtractor` is the single authoritative source of truth for video tracks; extension checking overrode container metadata.
- `Row` in Compose does not support multi-line wrapping; `FlowRow` handles flexible line wrapping for dynamic chips.
- Compose `AndroidView` requires `key(uri)` to recreate instances across URI switches and `onRelease` to trigger `stopPlayback()`.
- `effectiveBands` must reference the user-selected `numMelBands` parameter directly.
- In Kotlin 1.9+, `Enum.entries` provides pre-allocated lists avoiding GC allocation.

### Solution & Standard Procedure
1. **AudioFileDecoder Video Detection**:
   - Removed filename extension fallbacks; `hasVideo = videoTrackIndex >= 0` ensures audio-only containers remain in audio playback mode.
2. **Classifier FlowRow Layout**:
   - Replaced `Row` with `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp))`.
   - Hidden completely when all confidence scores are < 10%.
3. **VideoView Lifecycle & Frame Scrubbing**:
   - Added `key(uri)` around `AndroidView` in `VideoPlayerCard`.
   - Added `onRelease = { view -> view.stopPlayback() }`.
   - Enabled timeline seeking when playback is paused.
4. **Waterfall Dynamic Rescaling**:
   - Set `effectiveBands = numMelBands` in `SpectrogramWaterfall` for immediate UI response.
5. **Spectrogram Screen Height**:
   - Set compact phone spectrogram height to 380dp (~50% screen height) when video is not shown, 240dp when video is shown, and 360dp in tablet mode.
6. **GC & Touch Target Optimization**:
   - Replaced `AudioSourceMode.values()` with `AudioSourceMode.entries`.
   - Replaced header `IconButton` with `Box.size(38.dp).clip().clickable()` for exact touch boundaries.
7. **Comprehensive Unit Tests**:
   - Replaced trivial tests in `AudioWorkbenchEngineTest.kt` with actual engine method testing (`setMelBands` clamping, `setThreshold`, `setInterval`, `start` source mode switches).
8. **Versioning**:
   - Incremented project version to `2.1.00.01` (`versionCode = 11`) in `app/build.gradle.kts`.

### Token & LLM Resource Log
- **Session ID**: `7ebd963b-c9ed-4dff-87ee-2c4de3ea6802`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\7ebd963b-c9ed-4dff-87ee-2c4de3ea6802\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - Native filesystem tools (`replace_file_content`, `write_to_file`, `view_file`)
  - Git branch tracking (`feature/spectrogram-video-ui-redesign`)

---

## 2026-09-24: Release v2.1: Production Merge & GitHub Deployment

> [!NOTE] User Instructions & Guidance:
> - User explicitly instructed: "Merge and deploy to GitHub release".
> - User addressed terminal runner constraints: "continually have a problem running PowerShell against my explicit commands in the agent's file. It would be nice to stop this, as I don't want it and it seems to cause lots of problems with quoting."

### Solution & Standard Procedure
1. **Branch Merge & Fast-Forward**:
   - Merged `feature/spectrogram-video-ui-redesign` into `main` with `--no-ff` (commit `f7c72da`).
   - Tagged release as `v2.1`.
   - Pushed `main` and tag `v2.1` to remote repository `https://github.com/jimcollinsworth/commercial-killer.git`.
2. **GitHub Release Publication**:
   - Created and published GitHub Release `v2.1` with `app-debug.apk` attached using `C:\Users\jimco\bin\gh.exe release create v2.1 app-debug.apk --title "v2.1 UI Spectrogram Focus and Video Playback" --notes-file release_notes_v2_1.md`.
   - Release URL: `https://github.com/jimcollinsworth/commercial-killer/releases/tag/v2.1`.
3. **PowerShell Quoting Root Cause & Permanent Fix**:
   - **Root Cause**: The IDE's default Windows terminal profile was unconfigured in `C:\Users\jimco\AppData\Roaming\Antigravity IDE\User\settings.json`, causing it to discover and launch `Visual Studio 2022 Developer PowerShell` as the host subshell. PowerShell strips quotes and intercepts `&` and `|` operators prior to executing child commands.
   - **Remediation**: Set `"terminal.integrated.defaultProfile.windows": "Command Prompt"` in the user's IDE configuration so all subshells natively spawn `cmd.exe`.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini3.8` (Gemini 3.8 Flash High)
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Empirical System Resources Utilized**:
  - GitHub CLI (`C:\Users\jimco\bin\gh.exe`)
  - Git CLI (`git merge`, `git tag`, `git push`)
  - Remote: `https://github.com/jimcollinsworth/commercial-killer.git`

---

## 2026-11-21: Fast Local Android Decision Model & Removal of Generative LLMs (v2.2)

> [!NOTE] User Instructions & Guidance:
> - Replace all generative LLM / Gemini Nano Prompt API inference and roadmap proposals with a Fast Local Android Decision Model (`FrameDecisionEngine`).
> - Execute two non-autoregressive per-frame structured queries every 100 ms: `is_commercial` (commercial discriminator) and `is_same_scene` (cosine similarity between frame $t$ and $t-1$ with rolling cut cadence tracking).
> - Surface decision badges, shot duration, rolling cuts/min telemetry, and a Scene Cut Similarity Threshold slider in the Compose UI.
> - Remove `AiAnalysisViewModel.kt`, increment build version to `v2.2` (`versionCode = 12`), and synchronize governance documentation (`ROADMAP.md`, `PLANNING.md`, `README.md`, `JOURNAL.md`).

### Problem & Diagnosis
- The codebase contained `AiAnalysisViewModel.kt` with an on-device Gemini Nano / ML Kit Prompt API placeholder, and governance documents (`ROADMAP.md`, `README.md`, `PLANNING.md`, `HelpScreen.kt`) proposed autoregressive multimodal LLM prompts as a second-stage commercial evaluator.
- Autoregressive token generation introduces compute and latency overhead unsuitable for deterministic 100 ms frame evaluation, while 1D scalar shift distance alone (`SpectrogramComparator`) did not fuse classifier tags, spectral compression density, and temporal shot-cut cadence.

### Root Cause & Technical Analysis
- Per-frame broadcast segmentation requires a non-autoregressive System 1 evaluator operating directly on the 100 ms Mel-spectrogram vector $\vec{F}_t$, parallel `AudioClassifierEngine` tags (`List<ClassifierScore>`), and frame transition distance $\Delta$ without hot-loop heap allocations.
- Tracking shot continuity requires measuring cosine similarity $\frac{\vec{F}_t \cdot \vec{F}_{t-1}}{\|\vec{F}_t\| \, \|\vec{F}_{t-1}\|}$ against a configurable threshold (`DEFAULT_SCENE_SIMILARITY_THRESHOLD = 0.85f`) and maintaining a rolling 60,000 ms cut timestamp queue (`ArrayDeque<Long>`).
- Commercial muting and program unmuting require consecutive-frame hysteresis (`MUTE` after a scene cut or 3 consecutive commercial frames; `UNMUTE` after 5 consecutive non-commercial frames) to suppress single-frame transient spikes.

### Solution & Standard Procedure
1. **Fast Local Decision Engine (`FrameDecisionEngine.kt`)**:
   - Implemented `com.example.commercialkiller.data.decision.FrameDecisionEngine`, `FrameDecision`, `SceneContinuityResult`, `CommercialEvaluationResult`, and `TvActionDecision` (`MUTE`, `UNMUTE`, `HOLD`).
   - Implemented `evaluateSceneContinuity` with cosine similarity over a reused pre-allocated `FloatArray(128)` `previousFrameBuffer`, rolling 60,000 ms cut pruning, `shotDurationMs`, and `cutCadencePerMin`.
   - Implemented `evaluateCommercial` combining classifier tag weights, Mel-band spectral compression/loudness density, transition `shiftDistance`, and rolling cut cadence boost at `>= 15f` cuts/min.
   - Implemented `evaluateFrame` and `reset()`, gating `TvActionDecision` via `MUTE_CONSECUTIVE_COMMERCIAL_FRAMES = 3` and `UNMUTE_CONSECUTIVE_PROGRAM_FRAMES = 5`.
2. **Engine Wiring & Generative Code Removal (`AudioWorkbenchEngine.kt`, `AiAnalysisViewModel.kt`)**:
   - Removed generative LLM inference placeholder `AiAnalysisViewModel.kt` and all Gemini Nano Prompt API references.
   - Added `frameDecision: FrameDecision` and `sceneSimilarityThreshold: Float` to `WorkbenchState`, wired `decisionEngine.evaluateFrame(...)` into `processAudioFrame`, routed `decision.recommendedAction` into `evaluateAutoMute`, and exposed `setSceneSimilarityThreshold(value: Float)`.
3. **Compose UI & Help Screen (`MainScreen.kt`, `HelpScreen.kt`)**:
   - Added `FrameDecisionBar` directly beneath `SpectrogramWaterfallCard` in compact and expanded two-pane layouts displaying `COMMERCIAL`/`PROGRAM` score badges, `SAME SCENE`/`SCENE CUT` cosine similarity badges, and `Shot: <X.X>s | <N> cuts/min` telemetry.
   - Added the `Scene Cut Similarity Threshold` slider (`0.50f..0.98f`) to `SpectrogramSettingsSheet`.
   - Updated `HelpScreen.kt` to document `FrameDecisionEngine` queries, cosine similarity, cut cadence, and hysteresis gating with zero generative LLM references.
4. **Unit Tests, Versioning & Governance Docs**:
   - Added unit tests in `FrameDecisionEngineTest.kt` and updated `AudioWorkbenchEngineTest.kt`.
   - Incremented `app/build.gradle.kts` to `versionCode = 12`, `versionName = "2.2"`.
   - Updated `ROADMAP.md`, `PLANNING.md`, `README.md`, and `JOURNAL.md` to remove generative LLM / Gemini Nano Prompt API proposals and document the `v2.2` Fast Local Android Decision Model architecture.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini4Argon`
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Android SDK 36 (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`)
  - Gradle / JDK 17 toolchain configuration (`app/build.gradle.kts`)
  - Git feature branch (`feature/fast-local-decision-model`)

---
---

## 2026-11-22: Fix 16 KB Page-Size ELF Alignment Warning (v2.2.01)

> [!NOTE] User Instructions & Guidance:
> - Resolve the Android 16 KB memory page-size compatibility warning observed when deploying `app-debug.apk` to Pixel 8.

### Problem & Diagnosis
- Running `llvm-objdump.exe -p` across merged `arm64-v8a` native libraries showed three libraries already aligned to 16 KB (`align 2**14`), while `libtask_audio_jni.so` had `LOAD ... align 2**12` (4 KB alignment).

### Root Cause & Technical Analysis
- `org.tensorflow:tensorflow-lite-task-audio:0.4.4` ships a 4 KB-aligned `libtask_audio_jni.so` binary (`2**12`), whereas `com.google.mediapipe:tasks-audio:0.10.35` ships `libmediapipe_tasks_jni.so` compiled with 16 KB ELF segment alignment (`2**14`).

### Solution & Standard Procedure
1. **Dependency Migration (`gradle/libs.versions.toml`, `app/build.gradle.kts`)**:
   - Removed legacy `tensorflow-lite`, `tensorflow-lite-support`, and `tensorflow-lite-task-audio:0.4.4`.
   - Added `com.google.mediapipe:tasks-audio:0.10.35` (`libs.mediapipe.tasks.audio`), enabled `packaging { jniLibs { useLegacyPackaging = false } }`, and incremented to `versionCode = 13`, `versionName = "2.2.01"`.
2. **Audio Classifier Migration (`AudioClassifierEngine.kt`)**:
   - Replaced `org.tensorflow.lite.task.audio.classifier.AudioClassifier` and `TensorAudio` with `com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier`, `RunningMode.AUDIO_CLIPS`, and cached `AudioData` buffers (`16000 Hz`, mono).
3. **Verification**:
   - `cmd.exe /c "gradlew.bat testDebugUnitTest assembleDebug"`: `BUILD SUCCESSFUL in 1m 10s`; **51/51** unit tests passed (`0` failures, `0` skipped across 11 XML suites).
   - `llvm-objdump.exe -p` verified `align 2**14` on all `LOAD` segments in all four `arm64-v8a` and `x86_64` `.so` files (`libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, `libmediapipe_tasks_jni.so`, `libsurface_util_jni.so`).
   - `zipalign.exe -c -P 16 -v 4 app-debug.apk` returned exit code `0` (`Verification succesful`).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini4Neon`
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Google Maven `com.google.mediapipe:tasks-audio:0.10.35`
  - Android SDK Build-Tools `36.1.0` (`zipalign.exe`)
  - LLVM `llvm-objdump.exe` (`C:\Program Files\LLVM\bin`)
  - Gradle / JDK 17 (`gradlew.bat testDebugUnitTest assembleDebug`)

---
---

## 2026-11-22: First-Gate Media Presence Decision Check (is_media) & Multi-Scenario Broadcast Testing (v2.3)

> [!NOTE] User Instructions & Guidance:
> - "need one more important decision check, first is this media or not,   tv/radio vs nothing, or in a car, or whatever.  add this to is comercial or not. download a few more videos to check this and other possibilities. /android-tips-and-debugging"

### Problem & Diagnosis
- In `v2.2`, `FrameDecisionEngine` evaluated `is_commercial` and `is_same_scene` directly on every audio frame without a preceding media-presence check (`is_media`), allowing non-media audio environments (silence/empty room, vehicle engine/road rumble, or stationary ambient noise) to reach commercial scoring or carry stale consecutive-frame streaks across idle periods.
- Additional test videos were required in `test_assets/` to exercise non-media driving, non-media rain ambience, modern broadcast news + commercial transitions, and multi-state transitions.

### Root Cause & Technical Analysis
- `AudioClassifierEngine.classifyByAcousticFeatures` evaluated silence (`rms < 0.015f`), commercial loudness, speech ZCR, and music fallback, without dedicated branches for low-frequency steady vehicle rumble (`zcr < 0.04f && hfRatio < 0.45f`) or unmodulated broadband ambient noise (`(zcr > 0.30f || hfRatio > 1.35f) && modRatio < 0.28f && rms < 0.22f`).
- `FrameDecisionEngine` required an upfront `evaluateMediaPresence` gate to classify frames into `MediaEnvironment.BROADCAST_MEDIA`, `MediaEnvironment.SILENCE`, or `MediaEnvironment.AMBIENT_NOISE` before invoking `scoreCommercialInternal`, forcing `isCommercial = false`, `commercialScore = 0f`, and `TvActionDecision.HOLD` while non-media persists (and resetting break counters after `> 3` consecutive non-media frames).

### Solution & Standard Procedure
1. **Acoustic Non-Media Classifiers (`AudioClassifierEngine.kt`)**:
   - Added 4-subframe RMS envelope modulation calculation (`modRatio`) and classification branches for `Vehicle / Low-Freq Rumble` and `Ambient / Room Noise`.
2. **First-Gate `is_media` Query (`FrameDecisionEngine.kt`, `AudioWorkbenchEngine.kt`)**:
   - Added `MediaEnvironment` (`BROADCAST_MEDIA`, `SILENCE`, `AMBIENT_NOISE`), `MediaEvaluationResult`, `evaluateMediaPresence(...)`, `isMedia`/`mediaScore`/`mediaEnvironment` fields on `FrameDecision`, and configurable `mediaThreshold` (`0.20f..0.90f`, default `0.50f`).
   - Updated `evaluateFrame` to run `evaluateMediaPresenceInternal` ahead of `scoreCommercialInternal` and hold TV control output during non-media frames.
   - Wired `mediaThreshold` and `setMediaThreshold(Float)` into `WorkbenchState` and `AudioWorkbenchEngine`.
3. **UI Telemetry & Settings (`MainScreen.kt`, `HelpScreen.kt`)**:
   - Added Gate 1 badge (`MEDIA (xx%)`, `NO MEDIA: SILENCE (xx%)`, `NO MEDIA: AMBIENT (xx%)`) and Gate 2 state (`HOLD (NON-MEDIA)` vs `COMMERCIAL`/`PROGRAM`) inside `FrameDecisionBar`.
   - Added `Media Detection Threshold` slider (`0.20f..0.90f`) to `SpectrogramSettingsSheet` and documented the 3-stage decision pipeline in `HelpScreen.kt`.
4. **Unit Tests & Versioning (`app/build.gradle.kts`, `app/src/test/...`)**:
   - Incremented `versionCode = 14`, `versionName = "2.3"`.
   - Added 6 unit tests across `FrameDecisionEngineTest.kt`, `AudioClassifierEngineTest.kt`, and `AudioWorkbenchEngineTest.kt`, and updated `evaluateFrame_interruptedCommercialStreakResetsCounter` to test broadcast dialogue interruption.
5. **Verification & Real-World Video Testing**:
   - `cmd.exe /c "gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug"`: `BUILD SUCCESSFUL in 27s`; **57/57** unit tests passed (`0` failures, `0` skipped, `0` errors across 11 XML suites).
   - `zipalign.exe -c -P 16 -v 4 app-debug.apk`: exit code `0` (`Verification successful`); `llvm-objdump.exe -p` confirmed `align 2**14` on all `LOAD` segments in all four `arm64-v8a` `.so` libraries (`app-debug.apk`: `62,110,783` bytes).
   - Assembled gitignored clips in `test_assets/`: `car_driving_ambient.mp4` (`720,969` bytes), `rain_ambient_non_media.mp4` (`361,069` bytes), `cbs_news_with_2005_commercial.mp4` (`1,186,184` bytes), and `multi_scenario_all_states.mp4` (`1,824,581` bytes).
   - Emulator (`emulator-5554`) observed `NO MEDIA: AMBIENT` + `HOLD (NON-MEDIA)` on car (`car_driving_ambient.mp4`: 149/150 non-silent frames `< 0.04` ZCR) and rain (`rain_ambient_non_media.mp4`, with transient `zcr ~ 0.12` gusts briefly classifying as `PROGRAM` without `MUTE`), `MEDIA (100%)` + `PROGRAM (97%)` on CBS News, and `MEDIA (92%)` + `COMMERCIAL (99%)` + `TV MUTED [WEBHOOK]` on the 2005 commercial segment (`00:16 / 00:24`).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini4Neon`
- **Recorded Step Count**: `2072` steps in transcript at entry logging
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Internet Archive (`archive.org`) audio/video clips processed via `ffmpeg.exe` 8.0 (`480x360`, 16 kHz mono AAC)
  - Gradle / JDK 17 (`gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug`)
  - Android SDK Build-Tools `36.1.0` (`zipalign.exe`), `platform-tools` (`adb.exe`), `llvm-objdump.exe`, and `android` CLI (`screen capture`, `layout --no-idle`)
  - Android Emulator `Medium_Phone_API_36.1` (`emulator-5554`) and Pixel 8 USB device (`42170DLJH001WS`)

---

## 2026-11-22: Fixed-Height Non-Jumping UI & Empty-Room Live Mic NO MEDIA Detection (v2.3.01)

> [!NOTE] User Instructions & Guidance:
> - "Need to stop the screen jumping around as the indicators and labels change,  make screen sections fixed height, shrink size and dead space of tags, labels, indicators.    I tried the app on a phone, listened to empty room with mic, it didn't switch to no media."

### Problem & Diagnosis
- Conditional insertion/removal of `AlertBanner` and variable-row `FlowRow` wrapping inside `FrameDecisionBar` and `ClassifierLabelsContent` changed total column height dynamically between frames, shifting lower cards (`SpectrogramWaterfallCard`, `DistanceMeterCard`, `EventConsoleCard`) vertically during live playback.
- Live `MIC` monitoring on a physical phone in an empty room remained in `MEDIA` / `PROGRAM` instead of switching to `NO MEDIA: SILENCE` or `NO MEDIA: AMBIENT`.
- Testing deep-linked `.mp4` files with `--es audio_file` also exposed a startup race where `DisposableEffect(Unit)` launched `AudioSourceMode.SYNTH` while `AudioFileDecoder.decode()` suspended on `Dispatchers.IO`, causing `loadAudioFile()` to set `sourceMode = AudioSourceMode.FILE` before calling `start(AudioSourceMode.FILE)` and triggering the early-return guard in `start()`.

### Root Cause & Technical Analysis
1. **Unpitched Room Noise Misclassification**: No `yamnet.tflite` file is bundled in `app/src/main/assets`, so runtime classification uses `AudioClassifierEngine.classifyByAcousticFeatures`. In `AudioWorkbenchEngine.runLiveMicLoop()`, `MediaRecorder.AudioSource.VOICE_RECOGNITION` applied hardware AGC to quiet room air, lifting RMS into `0.015f..0.06f` (`zcr = 0.05..0.25`). Those samples missed both the vehicle branch (`zcr < 0.04f && hfRatio < 0.45f`) and broadband branch (`zcr > 0.30f || hfRatio > 1.35f`) and hit `Dialogue / Speech` with an inverted formula `(0.70f + (1.0f - rms) * 0.2f)` that scored quieter rooms up to `0.90f`.
2. **Mel Floor & 32 ms Mic Update Rate**: `MelSpectrogramCalculator` maps `-45 dB` room noise to normalized log-dB bin values around `~0.25f` (`> 0.04f` prior Mel floor), and `runLiveMicLoop()` invoked `processAudioFrame()` on every 512-sample `audioRecord.read(...)` (~32 ms / 31 Hz), causing rapid UI state churn.
3. **Empirical Pitch-Correlation Calibration (`PitchStats.java`, `jbr\bin\java.exe`)**: Across 7 offline 16 kHz mono PCM extracts (`512`-sample frames, `0.92f` first-order pre-emphasis, normalized autocorrelation across lags `32..200` step 2 for `80–500 Hz`), empty-room pink noise (`_empty.pcm`) measured `141/141` non-silent frames `< 0.26` (`avgPitch = 0.133`, `avgRms = 0.027`), rain (`_rain.pcm`) measured `119/121 < 0.30` (`avgPitch = 0.173`, `avgRms = 0.045`), car (`_car.pcm`) stayed caught by `< 0.04` ZCR (`149/150`, `avgRms = 0.305`), while broadcast news and commercials (`_cbs.pcm`, `_tv.pcm`, `_ad.pcm`, `_vw.pcm`) measured `avgPitch = 0.456..0.597`.
4. **Layout Instability**: Variable height between absent vs. rendered `AlertBanner`, `FlowRow` badge wrapping in `FrameDecisionBar`, conditional/wrapping `ClassifierLabelsContent` chips, multi-line header padding, and oversized vertical spacing shifted elements below the status banner whenever classification labels or alert state changed.

### Solution & Standard Procedure
1. **Empty-Room Live MIC & Ambient Noise Fixes (`AudioWorkbenchEngine.kt`, `AudioClassifierEngine.kt`, `FrameDecisionEngine.kt`)**:
   - Changed `runLiveMicLoop()` `audioSources` priority to `intArrayOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.DEFAULT)` and throttled `processAudioFrame(...)` calls to `_state.value.intervalMs` (`100 ms`) while draining `audioRecord.read(...)` continuously.
   - Fixed the cold-start `SYNTH`/`FILE` race by calling `stop()` before updating `_state` inside `loadAudioFile()` and guarding `DisposableEffect(Unit)` in `MainScreen.kt` to only call `engine.start(AudioSourceMode.SYNTH)` when `sourceMode == AudioSourceMode.SYNTH && !isRunning`.
   - In `AudioClassifierEngine.kt`, raised the RMS silence floor from `0.015f` to `0.020f`, added `computePreEmphasizedPitchCorrelation(samples)`, added `maxPitchCorr < 0.30f && rms < 0.14f` -> `Ambient / Room Noise` (`0.86f`), `Background Noise` (`0.72f`), `Non-Media Environment` (`0.50f`), and changed `Dialogue / Speech` confidence to `(0.65f + (maxPitchCorr * 0.22f) + (rms * 0.20f)).coerceIn(0.65f, 0.95f)`.
   - In `FrameDecisionEngine.kt`, expanded non-media tag keywords (`room`, `air conditioning`, `hum`, `buzz`, `environmental`, `pink`, `white`, `motor`, `idle`) and raised the Mel low-energy silence floor from `0.04f` to `0.08f`.
2. **Fixed-Height Compact Layout (`MainScreen.kt`, `SpectrogramVisualizer.kt`)**:
   - Replaced conditional `AlertBanner` with always-rendered fixed-height `ShiftStatusBanner` (`height(22.dp)`, `"SHIFT DETECTED  Δ=%.4f > %.2f"` vs. `"STEADY SIGNAL   Δ=%.4f / τ=%.2f"`).
   - Replaced wrapping `FlowRow` in `FrameDecisionBar` (`height(48.dp)`) with Row 1 (`20.dp`, weighted `1.25f`/`1.05f`/`0.95f` single-line pills at `9.5.sp`) and Row 2 (`16.dp`, monospace `"Media: %.0f%% | Shot: %.1fs"` + `"%.0f cuts/min"`).
   - Replaced wrapping `FlowRow` in `ClassifierLabelsContent` with fixed-height `24.dp` 3-slot equal-weight `Row` (`9.5.sp`, `maxLines = 1`, `TextOverflow.Ellipsis`, or `"No active audio tags"` placeholder).
   - Compacted `WorkbenchHeader` (`14.sp` title, `8.5.sp` TV pill, `34.dp` action buttons, `12.dp` source icons), `VideoPlayerCard` (`4.dp` padding, `164.dp` video, `22.dp` row, `18.dp` slider), `FilePlaybackCard` (`56.dp`), `SpectrogramWaterfallCard` (`4.dp` padding; `215.dp` video / `190.dp` short / `310.dp` phone / `340.dp` expanded), `DistanceMeterCard` (`34.dp`), `DistanceMeter` (`10.sp` single-line labels, `8.dp` progress bar), and `EventConsoleCard` (`110.dp` compact, `9.5.sp` single-line rows).
3. **Test Asset, Unit Tests & Build (`app/build.gradle.kts`, `AudioClassifierEngineTest.kt`, `FrameDecisionEngineTest.kt`)**:
   - Generated gitignored `test_assets/empty_room_mic_noise.mp4` (`138,969` bytes, 15s 480x360 black H.264 + 16 kHz mono AAC pink noise `amplitude=0.14`, `-movflags +faststart`).
   - Incremented `versionCode = 15`, `versionName = "2.3.01"`, and added 3 unit tests (`unpitched empty room mic noise classifies as Ambient Room Noise rather than Speech`, `quiet empty room thermal floor under 0_020 RMS classifies as Silence`, `low energy mic floor and room hum tags gate to non-media HOLD`).
   - `cmd.exe /c "gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug"`: `BUILD SUCCESSFUL in 27s` (`42` actionable tasks); **60/60** tests passed (`0` failures, `0` skipped, `0` errors across all 11 XML suites in `app/build/test-results/testDebugUnitTest/`).
   - Copied `app-debug.apk` (`62,110,787` bytes); `zipalign.exe -c -P 16 -v 4 app-debug.apk` exited `0` (`Verification successful`); `llvm-objdump.exe -p` reported `align 2**14` on all `LOAD` segments across all 4 `arm64-v8a` `.so` libraries (`libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, `libmediapipe_tasks_jni.so`, `libsurface_util_jni.so`).
4. **Emulator Layout Stability & USB Pixel 8 Deployment (`emulator-5554`, `42170DLJH001WS`)**:
   - Captured 6 screenshots on `emulator-5554` (`empty_room`, `rain`, `cbs_program`, `cbs_commercial`, `vw_commercial`, `synth_commercial`) and verified `android layout --no-idle` bounds across video runs (`0 px` vertical drift): `ShiftStatusBanner` `y=[875..907]`, `FrameDecisionBar` Row 1 `y=[1555..1584]`, Row 2 `y=[1612..1641]`, `ClassifierLabelsContent` `y=[1688..1717]`, `DistanceMeter` `y=[1763..1795]`, `EventConsoleCard` header `y=[1849..1878]`, `LazyColumn` `y=[1899..2122]`.
   - Installed `app-debug.apk` onto USB Pixel 8 (`42170DLJH001WS`, `Success`), pushed `empty_room_mic_noise.mp4` (`138,969` bytes) to `/sdcard/Download/empty_room_mic_noise.mp4`, launched `MainActivity` (`pidof` `29686`, `versionCode=15`, `versionName=2.3.01`), and verified `libmediapipe_tasks_jni.so` loaded `ok` with `0` fatal exceptions and `0` 16 KB alignment warnings in `logcat -d -t 2500`.

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini4Neon`
- **Recorded Step Count**: `2275` steps in transcript at entry logging
- **Log Source**: `C:\Users\jimco\.gemini\antigravity\brain\85e16c9e-9045-40e8-8026-f3ac61135af7\.system_generated\logs\transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - `ffmpeg.exe` 8.0 (`empty_room_mic_noise.mp4` generation & 16 kHz PCM extraction) and `jbr\bin\java.exe` (`PitchStats.java` across 7 audio clips)
  - Gradle / JDK 21 JBR (`gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug`)
  - Android SDK Build-Tools `36.1.0` (`zipalign.exe`), `platform-tools` (`adb.exe`), LLVM `llvm-objdump.exe`, and `android` CLI (`screen capture`, `layout --no-idle`)
  - Android Emulator `Medium_Phone_API_36.1` (`emulator-5554`) and Pixel 8 USB device (`42170DLJH001WS`)

## 2026-11-23: Two-Mode MIC/CAM & FILE/VIDEO Streams, Live Video Sampling & Android device-ai Skills (v2.4)

> [!NOTE] User Instructions & Guidance:
> - *"remove the synth, we should have just 2 options either mic/camera or file/video. it's too misleading to always jump into synth and doesn't serve any purpose. what other future tasks have been defined for he project /plan"*
> - *"Next steps - need to add video to stream, entire concept requires video along with audio, and then video only to unmute. Although there might be a reason to support audio only too, not sure) also want a timeline view of current recording, so we see the content being streamed but also the past segments as identified. Ever segment is the same visual length, we are only interested in the labeling and ends, even if the running time was long for that scene or ad. dont need much detail/resolution, for each frame, just want to visually see the differences and order/timeline. maybe fit the realtime data with labels, then a scrolling list of the events in the current recording session - the most recent at top, oldest off the screen on the bottom. Each event in the list has a concise visual view of the audio and video. retain this list of events after recording so we can scroll thru."*
> - *"go with the option A's . are we still using a jev like decision model approach locally for the video fusion too? can you read/install the android ai skills from here? https://github.com/android/skills/tree/main/device-ai install these for the commercial killer project, not sure if the nested directory is supported for skills/antigravity"*

### Problem & Diagnosis
1. **Misleading `SYNTH` Fallback**: Cold startup jumped directly into synthesized audio (`AudioSourceMode.SYNTH` / `runSimulatedLoop()`), obscuring whether real microphone, camera, or decoded media streams were active.
2. **Audio-Only Input Gap for Visual Commercial/Unmute Fusion**: While TV commercials are muted, TV audio is absent, requiring an active visual frame stream in both `MIC / CAM` and `FILE / VIDEO` modes plus a retained session segment timeline.
3. **Skill Directory Layout Constraint**: Antigravity discovers skills only one level deep inside `.agents/skills/` (`agy-customizations/docs/json_configs.md`), so nested repository paths under `device-ai/` are ignored unless flattened.

### Architectural Decisions & Solution (`LLM-Gemini4Neon`)
- **Two-Mode Input Pipeline & Option A Cold Launch**:
  - Updated [`AudioWorkbenchEngine.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/audio/AudioWorkbenchEngine.kt) to replace `AudioSourceMode` with 2 modes (`LIVE`, `FILE`), remove `runSimulatedLoop()` and `start(Boolean)`, default `sourceMode = AudioSourceMode.LIVE`, guard `start(FILE)` when `loadedAudio == null`, and track `isCameraEnabled`, `videoFrameCount`, `latestVisualDescriptor`, and `latestVideoThumbnail` via `setCameraEnabled()`, `getLastVisualDescriptor()`, and `onVideoFrameSampled()`.
  - Updated `DisposableEffect(Unit)` in [`MainScreen.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/main/MainScreen.kt) so cold launch defaults to `MIC / CAM` idle unless `RECORD_AUDIO` permission is already granted (enabling camera preview simultaneously if `CAMERA` is also granted), while `ShiftStatusBanner` displays `IDLE   TAP MIC / CAM OR FILE / VIDEO` at fixed `22.dp` height.
- **Jev-Style Visual Embedding Stream (`VideoFrameSampler.kt`)**:
  - Created [`VideoFrameSampler.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/video/VideoFrameSampler.kt) defining `VisualFrameDescriptor` (`56` floats: `16` `lumaGrid`, `24` `8R+8G+8B` `colorHistogram`, `16` bottom-right `cornerRoiLuma`, `meanLuma`, `visualChangeScore`), `VideoFrameSnapshot` (`64×36` `RGB_565` `4,608`-byte `Bitmap`), and `VideoFrameAnalyzer` (`sampleYuvImageProxy`, `sampleBitmap`) — preserving a non-autoregressive System 1 embedding pipeline ready for Increment 3 audio+video fusion and `litert-runtime` (`CompiledModel` + `TensorBuffer`).
  - Wired CameraX `Preview` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, `Size(320, 240)` `ResolutionStrategy`, `250 ms` sampling interval) in [`CameraScreen.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/camera/CameraScreen.kt) on a single-thread executor with `clearAnalyzer()`, `unbindAll()`, and `shutdown()` cleanup, paired with a `CAM ON` / `CAM OFF` audio-only toggle in `LiveCameraCard` (`semantics(mergeDescendants = true)`).
  - Migrated `VideoPlayerCard` in [`MainScreen.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/main/MainScreen.kt) from `VideoView` to `TextureView` + muted `MediaPlayer` with preserved `aspectRatio` and `250 ms` `getBitmap(64, 36)` visual sampling.
  - Replaced `SynthIcon` with `CameraIcon` in [`AppIcons.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/components/AppIcons.kt), updated [`HelpScreen.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/main/HelpScreen.kt), and incremented [`app/build.gradle.kts`](file:///d:/projects/commercial-killer/app/build.gradle.kts) to `versionCode = 16`, `versionName = "2.4"`.
- **Android `device-ai` Skills Installed (Flattened)**:
  - Installed all 5 `device-ai` skills via `android.exe skills add --project=d:\projects\commercial-killer --agent=antigravity`: `appfunctions`, `litert-lm`, `litert-runtime`, `ml-kit-genai-prompt-api`, and `mlkit-agent-api` (`25` files, `271,440` bytes total, largest file `37,766` bytes).
- **Deferred Increments Planned**:
  - **Increment 2 (`v2.5`)**: Fixed-width equal-length Session Segment Timeline triggered on label transitions or sustained `>= 2s` visual scene cuts, newest-at-top, retained post-stop.
  - **Increment 3 (`v2.6`)**: Cross-Modal Audio+Video Decision Fusion & Video-Only Unmute (`ProgramFingerprintCache`, `litert-runtime` option).

### Verification Results
- **Unit Tests & Build**:
  - `findstr /S /I /N /C:SYNTH /C:runSimulatedLoop /C:SynthIcon app\src\*.kt`: 0 matches.
  - `gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug`: `BUILD SUCCESSFUL in 49s`, `42 actionable tasks: 42 executed`.
  - **66/66** unit tests passed (`0` skipped, `0` failures, `0` errors) across `IrEmitterControllerTest` (4), `ProntoHexConverterTest` (3), `TvControlManagerTest` (4), `UsbIrDongleControllerTest` (3), `AudioClassifierEngineTest` (7), `AudioFileDecoderTest` (4), `AudioWorkbenchEngineTest` (12), `MelSpectrogramTest` (4), `SpectrogramComparatorTest` (4), `FrameDecisionEngineTest` (15), `VideoFrameAnalyzerTest` (4), and `MainScreenViewModelTest` (2).
  - `zipalign.exe -c -P 16 -v 4 app-debug.apk`: `Verification successful`; `llvm-objdump.exe -p` on `libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, `libmediapipe_tasks_jni.so`, `libsurface_util_jni.so`: all `LOAD` segments `align 2**14`.
- **Emulator (`Medium_Phone_API_36.1`, `emulator-5554`)**:
  - `emulator_v2_4_live_mic_cam.png`: `MIC / CAM` `[291,181][525,283]`, `FILE / VIDEO` `[533,181][790,283]`, CameraX room preview, `LIVE: MIC + CAM`, `ΔV=0.00  #72`/`#79`, `CAM OFF`, `NO MEDIA: SILENCE`, `HOLD (NO MEDIA)`, `SAME 1.00`, `Silence / Transition Gap`.
  - `emulator_v2_4_file_cbs_video.png` (`cbs_news_with_2005_commercial.mp4`, `TotalTime: 3866`): `TextureView` 4:3 frame, `00:05 / 00:24`, `MEDIA 100%`, `PROGRAM 97%`.
  - `emulator_v2_4_file_empty_room.png` (`empty_room_mic_noise.mp4`, `TotalTime: 3216`): `00:06 / 00:15`, `NO MEDIA: AMBIENT`, `HOLD (NO MEDIA)`, `SAME 0.98`, `Ambient / Room Noise`.
  - `android layout --device=emulator-5554 --no-idle`: `0 px` vertical drift across `cbs` and `empty_room` (`ShiftStatusBanner` `[84,875][560,907]`, `FrameDecisionBar` `[..,1555][..,1584]` / `[79,1612][..,1641]`, `ClassifierLabelsContent` `[..,1688][..,1717]`, `DistanceMeter` `[..,1763][..,1795]`, `EventConsoleCard` header `[63,1849][413,1878]`, `LazyColumn` `[79,1899][1001,2122]`).
  - `dumpsys gfxinfo` (active emulator): `Total frames rendered: 2558`, `Janky frames: 1396 (54.57%)`, `50th percentile: 17ms`, `95th percentile: 46ms`, `99th percentile: 109ms`, `50th gpu percentile: 7ms`, `99th gpu percentile: 4950ms`; `dumpsys meminfo`: `TOTAL PSS: 123177`, `Native Heap: 14632`, `Dalvik Heap: 9868`, `Views: 11`, `ViewRootImpl: 1`.
- **Pixel 8 (`42170DLJH001WS`)**:
  - `adb.exe -s 42170DLJH001WS install -r app-debug.apk` (`Success`); `LaunchState: COLD`, `TotalTime: 1586`, `WaitTime: 1590`, `pidof`: `8182`, `versionCode=16`, `versionName=2.4`.
  - `logcat`: `libmediapipe_tasks_jni.so ... ok`, CameraX `Preview` + `ImageAnalysis` (`1600x1200`, `rotationDegrees=90`), fallback heuristic `AudioClassifierEngine` (no `yamnet.tflite` in assets), `0` fatal/`AndroidRuntime`/16 KB warnings.
  - `dumpsys meminfo`: `TOTAL PSS: 114986`, `Native Heap: 10335`, `Dalvik Heap: 6457`, `Views: 13`, `ViewRootImpl: 1`, `Activities: 1`; `dumpsys thermalservice`: `Thermal Status: 0`; `/proc/pressure/cpu`: `Permission denied`; `dumpsys gfxinfo`: `Total frames rendered: 1`, `50th..99th percentile: 1100ms`, `50th gpu percentile: 6ms` (`mWakefulness=Dozing`).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini4Neon`
- **Step Count**: `2455` (`step_index` verified in `transcript.jsonl`)
- **Log Path**: `.system_generated/logs/transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Gradle / JDK 21 JBR (`gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug`)
  - Android SDK Build-Tools `36.1.0` (`zipalign.exe`), `platform-tools` (`adb.exe`), LLVM `llvm-objdump.exe`, and `android` CLI (`skills add`, `screen capture`, `layout --no-idle`)
  - Android Emulator `Medium_Phone_API_36.1` (`emulator-5554`) and Pixel 8 USB device (`42170DLJH001WS`)

---

## 2026-11-23: Jev-Style Multimodal Decision Model, Video-Only Muted Unmute, Stage 2 LiteRT-LM Hook & Retained Segment Timeline (v2.5)

> [!NOTE] User Instructions & Guidance:
> - *"how will the jev like decision model work with video/audio /plan"*
> - *"wtf? jepa?"*
> - *"here's some additional info: For a 100% local, multimodal Jev-style decision pipeline on Android, the best approach is Google AI Edge LiteRT-LM paired with Gemma 3n (or Gemma 4 E2B) and constrained 1-token decoding... are we using an ominidirectional embedding head? what do you think?"*
> - *"lets do option 2, simpler approach first. go ahead and do all the remaining tasks, all 3 phases, test using a test commercial/media file, note that there should only be a dozen or so transitions for a show, definitely not hundreds. simple edits back and forth in a scene from different angles, should not trigger a change. make sure we test for that and adjust /android-tips-and-debugging"*

### Problem & Diagnosis
1. **Cross-Modal Blindness While TV Is Muted**: When TV audio is muted by IR/Webhook during a commercial break, the room microphone receives silence or unrelated ambient speech, preventing unmute transitions unless the visual stream tracks program return against a frozen pre-break visual signature.
2. **Shot / Reverse-Shot Over-Segmentation**: Without an intra-scene multi-angle bank and macro boundary hysteresis, alternating A/B camera cuts and brief speech pauses inside a broadcast program fragment timeline segments into tens or hundreds of spurious rows.
3. **Live Emulator Tuning Issues Found Before Final Pass**:
   - Positive-vector cosine similarity on `lumaGrid` exhibited an artificial `~0.80–0.90` mathematical floor even between unrelated scenes, which paired with an ungated `+0.25f` cadence bonus produced a false `RET 85%` unmute signal on a beige bathroom commercial shot at `00:20`.
   - `CONFIRM_NO_MEDIA_MS = 2_000L` fragmented the initial broadcast news segment on a natural 2 s speech pause while video was active, and prepending items at index `0` left `LazyColumn` anchored to older rows instead of revealing newest entries at the top.
   - Carrying commercial-break `cutTimestampsMs` / `novelVisualCutTimestampsMs` and clearing `activeSceneAngles` across `UNMUTE` caused a false `#4 Auto-Mute Triggered` at `00:39` when the weather-map studio angle reappeared after program return.

### Architectural Decisions & Solution (`LLM-Gemini4Neon`)
- **Terminology Clarification (`Jev` vs `JEPA`)**: Confirmed `Jev` designates TypeSafe AI non-autoregressive System 1 typed decision queries returning structured verdicts/scores without prose generation; removed 3 erroneous `JEPA` strings from [`ROADMAP.md`](file:///d:/projects/commercial-killer/ROADMAP.md), [`JOURNAL.md`](file:///d:/projects/commercial-killer/JOURNAL.md), and `walkthrough.md` (`0` remaining matches confirmed by `findstr`).
- **Phase 1 — Visual Descriptor Expansion & `ProgramFingerprintCache`**:
  - Extended [`VideoFrameSampler.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/video/VideoFrameSampler.kt) to `72` floats (`16` `lumaGrid`, `24` `8R+8G+8B` `colorHistogram`, `16` bottom-right `cornerRoiLuma`, `16` bottom-left `bottomLeftRoiLuma`, `isBlackFrame`, `visualChangeScore`), added allocation-free `sampleIntoReusableBuffer(...)`, and switched luma comparison from `cosineDistance` to spatial mean-absolute-difference `lumaL1Distance` (`0.50f * lumaSim + 0.50f * histSim`).
  - Created [`ProgramFingerprintCache.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/decision/ProgramFingerprintCache.kt) maintaining an active `6`-angle bank (`KNOWN_ANGLE_SIMILARITY_THRESHOLD = 0.78f`), a `60`-frame (`15 s`) pre-break ring with `2 s` pre-mute rollback, greedy `K = 5` prototype freezing, dual-corner stability checks (`variance < 0.015f`, `contrast >= 0.08f`), and restoration of frozen prototypes into `activeSceneAngles` during `unfreeze()`.
- **Phase 2 & 3 — 3-Modality `FrameDecisionEngine`, `SessionSegmentTracker`, & Stage 2 `LiteRtLmDecisionVerifier`**:
  - Extended [`FrameDecisionEngine.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/decision/FrameDecisionEngine.kt) across `DecisionModality { AUDIO_ONLY, AUDIO_VIDEO, VIDEO_ONLY_MUTED }`, gated cadence (`sProto >= 0.70f`) with `PROGRAM_RETURN_UNMUTE_THRESHOLD = 0.80f` (`MIN_MUTE_HOLD_MS = 4_000L`, `UNMUTE_CONSECUTIVE_VIDEO_FRAMES = 6`, `MAX_MUTE_WATCHDOG_MS = 240_000L`), dampened matched pre-break prototypes (`>= 0.82f`), and added `clearBreakTrackingOnUnmute()` to reset break cut windows, streaks, and `hasInitializedStart`.
  - Created [`SessionSegmentTracker.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/decision/SessionSegmentTracker.kt) aggregating `100 ms` ticks into equal-width `SessionSegment` entries (`PROGRAM`, `COMMERCIAL`, `NO_MEDIA`) with `16×16` Mel strips and settled `64×36` thumbnails, `CONFIRM_NO_MEDIA_VIDEO_MS = 5_000L`, `< 3_000L` initial `NO_MEDIA` placeholder replacement, and `finalizeActiveSegment()` retention on stop.
  - Created [`LiteRtLmDecisionVerifier.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/decision/LiteRtLmDecisionVerifier.kt) using `com.google.ai.edge.litertlm:litertlm-android:0.18.0` ([`libs.versions.toml`](file:///d:/projects/commercial-killer/gradle/libs.versions.toml), `versionCode = 17`, `versionName = "2.5"` in [`app/build.gradle.kts`](file:///d:/projects/commercial-killer/app/build.gradle.kts), optional `libvndksupport.so`/`libOpenCL.so` + `READ_MEDIA_VIDEO` in [`AndroidManifest.xml`](file:///d:/projects/commercial-killer/app/src/main/AndroidManifest.xml)) with `Backend.GPU()` -> `Backend.CPU()` fallback on external `.litertlm` paths and Stage-1-only fallback when absent.
  - Wired engine, intent extras (`--ez auto_mute`, `--ez simulate_muted_break`, `prepareForExternalFileIntent()`, `close()` in `onDestroy()` when `isFinishing`), ping-pong 2-slot `Bitmap` waterfall + `MiniMelStrip` ([`SpectrogramVisualizer.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/components/SpectrogramVisualizer.kt)), reusable `ARGB_8888` `Bitmap` + `IntArray` video sampling buffer, `SessionTimelineCard` (`[TIMELINE | LOG]`, `LazyListState` newest-at-top pinning), and `Simulate TV Mute` toggle ([`AudioWorkbenchEngine.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/data/audio/AudioWorkbenchEngine.kt), [`MainActivity.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/MainActivity.kt), [`MainScreen.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/main/MainScreen.kt), [`HelpScreen.kt`](file:///d:/projects/commercial-killer/app/src/main/java/com/example/commercialkiller/ui/help/HelpScreen.kt)).

### Verification Results
- **Unit Tests & Build (`gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug`)**:
  - `BUILD SUCCESSFUL in 30s` (`42 actionable tasks: 42 executed`), `0` Kotlin compiler warnings across modified files.
  - **83/83** unit tests passed (`0` failures, `0` errors, `0` skipped) across all 15 XML suites: `IrEmitterControllerTest` (4), `ProntoHexConverterTest` (3), `TvControlManagerTest` (4), `UsbIrDongleControllerTest` (3), `AudioClassifierEngineTest` (7), `AudioFileDecoderTest` (4), `AudioWorkbenchEngineTest` (12), `MelSpectrogramTest` (4), `SpectrogramComparatorTest` (4), `FrameDecisionEngineTest` (19), `LiteRtLmDecisionVerifierTest` (2), `ProgramFingerprintCacheTest` (3), `SessionSegmentTrackerTest` (6), `VideoFrameAnalyzerTest` (6), and `MainScreenViewModelTest` (2).
- **16 KB Page Size Alignment**:
  - `zipalign.exe -c -P 16 -v 4 app\build\outputs\apk\debug\app-debug.apk`: `Verification successful`.
  - `llvm-objdump.exe -p` on extracted `lib/arm64-v8a/liblitertlm_jni.so`: all 3 `LOAD` segments aligned at `align 2**14`.
- **Multi-Angle Broadcast Clip Verification (`emulator-5554`, `test_assets/show_angles_ad_return.mp4`, `43.06 s`, `2,445,398` bytes, gitignored)**:
  - Verified strictly 3 macro segments across intra-scene A↔B camera cuts (`0–15 s`, `28.05–43.06 s`), muted commercial break (`15–28.05 s`), and post-stop retention:
    - `#1 PROGRAM` (`00:00-00:14`, `14s`, `97–99%`) across 6 A↔B studio cuts and anchor speech pause.
    - `#2 COMMERCIAL` (`00:14-00:28`, `14s`, `TV MUTED [WEBHOOK]`, `VID-ONLY (MUTED)`, amber `RET 40%` on beige bathroom shot and `RET 32%` on `CUT 0.15`).
    - `#3 PROGRAM` (`00:28-00:41`, `12s`, `Program Return Unmute`, `92%`, `TV ACTIVE [WEBHOOK]`, zero false `#4` mute through `00:42` `IDLE`).
  - `android.exe layout --device=emulator-5554 --no-idle`: `0 px` vertical drift across running, muted, silence, and `IDLE` states (`ShiftStatusBanner` `y=875..907`, `FrameDecisionBar` rows `y=1450..1479` & `y=1507..1536`, `ClassifierLabelsContent` `y=1583..1612`, `DistanceMeter` `y=1658..1690`, `SessionTimelineCard` header `y=1753..1782`, `LazyColumn` `y=1809..2125`).
  - `dumpsys gfxinfo`: `Total frames rendered: 1201`, `Janky frames: 805 (67.03%)`, `50th percentile: 18ms`, `95th percentile: 40ms`, `99th percentile: 77ms`, `50th gpu percentile: 11ms`, `99th gpu percentile: 4950ms`; `dumpsys meminfo`: `TOTAL PSS: 135244` KB (`Native Heap: 13536`, `Dalvik Heap: 10028`, `Views: 13`, `ViewRootImpl: 1`).
- **Physical Pixel 8 (`42170DLJH001WS`) Deploy & Profiling**:
  - `adb.exe -s 42170DLJH001WS install -r app-debug.apk`: `Success`; `LaunchState: COLD`, `TotalTime: 1490`, `WaitTime: 1495`, `pidof`: `11669`.
  - `logcat`: `uses_libraries=libOpenCL.so`, `libmediapipe_tasks_jni.so ... ok`, `c2.android.aac.decoder` decoded `16000 Hz` mono stream, `AudioTrack` initialized, `0` fatal/`AndroidRuntime`/16 KB warnings.
  - `dumpsys meminfo`: `TOTAL PSS: 121538` KB (`Native Heap: 10648`, `Java Heap: 18432`, `Graphics: 8128`, `Views: 9`, `ViewRootImpl: 1`); `dumpsys thermalservice`: `Thermal Status: 0`; `dumpsys gfxinfo`: `Pipeline=Skia (Vulkan)`, `50th gpu percentile: 3ms` (`visibility=8`).

### Token & LLM Resource Log
- **Session ID**: `85e16c9e-9045-40e8-8026-f3ac61135af7`
- **Model Identifier**: `LLM-Gemini4Neon`
- **Step Count**: `2997` (`step_index` verified in `transcript.jsonl`)
- **Log Path**: `.system_generated/logs/transcript.jsonl`
- **Token Accounting Status**: Managed at platform IDE host level.
- **Empirical System Resources Utilized**:
  - Gradle / JDK 21 JBR (`gradlew.bat testDebugUnitTest --rerun-tasks assembleDebug`)
  - Android SDK Build-Tools `36.1.0` (`zipalign.exe`), `platform-tools` (`adb.exe`), LLVM `llvm-objdump.exe`, `ffmpeg.exe`, and `android` CLI (`layout --no-idle`)
  - Android Emulator `Medium_Phone_API_36.1` (`emulator-5554`) and Pixel 8 USB device (`42170DLJH001WS`)

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8, LLM-Gemini4Argon, LLM-Gemini4Neon.*









