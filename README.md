# Commercial Killer — Audio/Video AI Workbench

An on-device experimentation workbench and real-time processing engine for Android (v2.7). Commercial Killer ingests live microphone + CameraX streams (`MIC / CAM`) or local video/audio files (`FILE / VIDEO`), computes real-time Mel-spectrograms (40–128 bands), samples `250 ms` `72`-float visual frame descriptors plus `64`-float 4-corner edge masks and letterbox/pillarbox ratios (`VideoFrameAnalyzer`), executes parallel MediaPipe `yamnet.tflite` / DSP audio classification (`AudioClassifierEngine`), and evaluates every 100 ms frame with a non-autoregressive System 1 Fast Local Android Decision Model (`FrameDecisionEngine` + `ProgramFingerprintCache` + `RepeatAdFingerprintIndex` + `BroadcastCueSignals`) across `AUDIO_ONLY`, `AUDIO_VIDEO`, and muted `VIDEO_ONLY_MUTED` modalities. Macro broadcast boundaries aggregate into a retained equal-length Session Segment Timeline (`SessionSegmentTracker`), paired with runtime-configurable Stage 2 `litertlm-android:0.18.0` verification (`OFF`, `AUDIT`, `VETO`) and an in-app `ModelRepository` manager (`[ANALYSIS | MODELS]`) for external `.tflite` and `.litertlm` models stored outside the APK and Git tree.

## Core Features & Vision

- **100% Local On-Device Execution**: Runs entirely on phone hardware — zero external cloud APIs or network inference calls.
- **Flexible Data Inputs**:
  - Microphones & live camera streams (`camerax`)
  - Local audio & video files (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS, MP4)
  - Combined stream pipelines & future streaming input services
- **Signal Processing & Feature Detection**:
  - Configurable Mel-spectrogram calculations (40–128 bands, default 80; 100 ms default interval)
  - Mel-spectrogram matrix shift & change significance detection
  - Native integration with LiteRT-ML (TFLite), MediaPipe, optional `litertlm-android:0.18.0` GPU/CPU external model execution, and open-weights Hugging Face audio models
- **Fast Local Android Decision Model (`FrameDecisionEngine` + `ProgramFingerprintCache`)**:
  - Non-autoregressive per-frame System 1 evaluation every 100 ms with zero token generation latency across `AUDIO_ONLY`, `AUDIO_VIDEO`, and muted `VIDEO_ONLY_MUTED` modes
  - `is_media`: First-gate acoustic presence classifier distinguishing `BROADCAST_MEDIA` (TV/radio) from `SILENCE` and `AMBIENT_NOISE` (vehicle driving rumble or steady room/rain noise)
  - `is_same_scene`: Audio cosine similarity fused with `ProgramFingerprintCache` active `6`-angle bank (`KNOWN_ANGLE_SIMILARITY_THRESHOLD = 0.78f`) so intra-scene A/B camera angle edits remain recognized without fragmenting program segments
  - `is_commercial` & muted `VIDEO_ONLY_MUTED` return: Discriminative scoring combining classifier tags, Mel spectral compression density, shift distance, rolling cut cadence, dual-corner ROI watermark stability, frozen `15 s` pre-break baseline prototypes (`2 s` pre-mute rollback, `K = 5` diverse prototypes, `PROGRAM_RETURN_UNMUTE_THRESHOLD = 0.80f`), normalized `0..1` `BroadcastCueSignals` (`logoMaskStrength`, `logoDropoutScore`, `blackSilencePulse`, `aspectRatioShift`, `repeatAdDistance`, `repeatAdMatchScore`), and `RepeatAdFingerprintIndex` (`600` `1.0 s` non-program windows, `MIN_REPEAT_GAP_MS = 8_000L`)
  - Configurable Stage 2 Cascade (`LiteRtLmDecisionVerifier` + `ModelRepository`): Discovers external `.tflite` (`filesDir/models/audio/`, `filesDir/models/`, `/data/local/tmp/`) and `.litertlm` (`filesDir/models/lm/`, `filesDir/models/`, `/data/local/tmp/`) files, supports SAF `OpenDocument` import (`BROWSE DEVICE...`), includes a curated HTTPS download catalog (`yamnet.tflite`, `qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm`, `Qwen3-0.6B.litertlm`, `gemma-4-E2B-it.litertlm`), and initializes `Backend.CPU()` -> `Backend.GPU()` fallback off the main thread for single-token `COMMERCIAL`/`PROGRAM`/`NON_MEDIA` verification across `OFF`, `AUDIT` (passive `S2:` badge), and `VETO` (`1.5 s` hold on borderline mutes `0.75..0.85` with `3 s` cooldown) modes, injecting `logoDrop`, `blkSil`, `arShift`, and `repDist` cue telemetry into `Stage2TransitionFeatures` and `buildPrompt`
  - Hysteresis-gated `MUTE`, `UNMUTE`, and `HOLD` action outputs integrated into `TvControlManager`, clearing break cadence buffers upon unmute
- **Real-Time Workbench Dashboard & Retained Timeline**:
  - Top visual input viewport supporting live CameraX `Preview` + `ImageAnalysis` (`MIC / CAM`, with `CAM ON` / `CAM OFF` audio-only toggle) and `TextureView` + muted `MediaPlayer` video playback (`FILE / VIDEO`) with reusable `ARGB_8888` `Bitmap` + `IntArray` sampling
  - High-resolution Mel-spectrogram waterfall rendered via ping-pong 2-slot `Bitmap` buffers
  - Fixed-height HUD layout (`ShiftStatusBanner` 22dp, `FrameDecisionBar` 48dp showing modality tag `A+V`/`VID-ONLY`/`AUD-ONLY`, `MEDIA`/`NO MEDIA` / `VID-ONLY (MUTED)`, `COMMERCIAL`/`PROGRAM`/`RET %`, `SAME`/`CUT`, `DecisionModality` telemetry; 3-slot `ClassifierLabelsContent` 24dp; `DistanceMeterCard` 34dp; `SessionTimelineCard` 150dp with `[TIMELINE | LOG]` toggle)
  - Equal-length Session Segment Timeline (`SessionSegmentTracker`) displaying newest segments at top (`44.dp` rows with `56×34.dp` thumbnail, `50×34.dp` `MiniMelStrip`, `PROGRAM`/`COMMERCIAL`/`NO MEDIA` label, timestamps, reason, and Stage 2 badge), retained after `STOP` for post-session scrolling (`Simulate TV Mute` testing toggle in `SpectrogramSettingsSheet`)

## Architecture & Directory Layout

- `app/src/main/java/com/example/commercialkiller/`
  - `MainActivity.kt`: Entry activity with edge-to-edge UI scaffold (`edge-to-edge`).
  - `Navigation.kt` & `NavigationKeys.kt`: Navigation graph and scene transitions.
  - `data/audio/AudioWorkbenchEngine.kt`: Core engine managing dual audio+video pipelines (`MIC / CAM` as `AudioSourceMode.LIVE` and `FILE / VIDEO` as `AudioSourceMode.FILE`), `100 ms` audio inspection loops (`MIC`/`UNPROCESSED` live priority and `intervalMs` throttling), `250 ms` `VisualFrameDescriptor` state, `SessionSegmentTracker`, `LiteRtLmDecisionVerifier`, `simulateMutedBreak`, and automated muting triggers with `SessionSegment` retention on `stop()`.
  - `data/video/VideoFrameSampler.kt`: Non-autoregressive visual descriptor extractor computing a `72`-float `VisualFrameDescriptor` (`16` luma grid, `24` `8R+8G+8B` RGB histogram, `16` bottom-right `cornerRoiLuma`, `16` bottom-left `bottomLeftRoiLuma`, `isBlackFrame`, `visualChangeScore` via spatial L1 luma + histogram L1 distances) plus `64`-float `cornerEdgeMasks`, `letterboxRatio`, and `pillarboxRatio`, `sampleIntoReusableBuffer`, and `64×36` `RGB_565` `4,608`-byte thumbnails from CameraX YUV frames and `TextureView` video bitmaps.
  - `data/audio/AudioFileDecoder.kt`: Multi-format audio/video file decoder (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS, MP4) and 16 kHz mono resampler.
  - `data/audio/AudioClassifierEngine.kt`: Parallel on-device audio classifier supporting open-weights Hugging Face models via LiteRT / TFLite, plus acoustic fallback with pre-emphasized pitch-correlation room/ambient noise rejection (`maxPitchCorr < 0.30f && rms < 0.14f`).
  - `data/audio/MelSpectrogramCalculator.kt`: Configurable 40–128 band Mel-spectrogram calculation (default 80) using Cooley-Tukey FFT.
  - `data/audio/SpectrogramComparator.kt`: Frame-to-frame matrix distance comparator with configurable thresholding.
  - `data/decision/BroadcastCueSignals.kt`: Normalized (`0f..1f`) continuous container for Cues 1–4 (`logoMaskStrength`, `logoDropoutScore`, `blackSilencePulse`, `aspectRatioShift`, `repeatAdDistance`, `repeatAdMatchScore`).
  - `data/decision/RepeatAdFingerprintIndex.kt`: Bounded `600`-window (`1.0 s`) short-window repeat-ad A+V fingerprint distance index (`MIN_REPEAT_GAP_MS = 8_000L`, `REPEAT_MATCH_DISTANCE_THRESHOLD = 0.25f`).
  - `data/decision/FrameDecisionEngine.kt`: Three-modality non-autoregressive per-frame decision model (`AUDIO_ONLY`, `AUDIO_VIDEO`, `VIDEO_ONLY_MUTED`) evaluating `is_media`, `is_same_scene`, `is_commercial`, `programReturnScore`, and `BroadcastCueSignals` fusion (including `blackSilencePulse`) with `MUTE`/`UNMUTE`/`HOLD` gating and post-unmute break cadence cleanup.
  - `data/decision/ProgramFingerprintCache.kt`: Maintains the active `6`-angle intra-scene bank (`KNOWN_ANGLE_SIMILARITY_THRESHOLD = 0.78f`), `15 s` pre-break ring with `2 s` rollback, `K = 5` diverse frozen prototypes, dual-corner ROI stability templates, 4-corner static edge logo mask EMA/variance (`logoMaskStrength`, `logoDropoutScore`), and `aspectRatioShift` baseline.
  - `data/decision/SessionSegmentTracker.kt`: Aggregates `100 ms` `FrameDecision` ticks into equal-width macro timeline segments (`PROGRAM`, `COMMERCIAL`, `NO_MEDIA`) with `16×16` Mel strips and `64×36` settled thumbnails retained after stop.
  - `data/decision/LiteRtLmDecisionVerifier.kt`: Stage 2 `litertlm-android:0.18.0` verifier loading external `.litertlm` models (`Backend.CPU()` -> `Backend.GPU()` fallback) with `<think>` stripping, `[video-only]` muted tag filtering, `Stage2TransitionFeatures` (`logoDrop`, `blkSil`, `arShift`, `repDist`), and constrained `COMMERCIAL`/`PROGRAM`/`NON_MEDIA` verdicts plus latency measurement.
  - `data/model/ModelRepository.kt`: External `.tflite` / `.litertlm` model discovery, SAF atomic file import, curated HTTPS download catalog with redirect/cancellation handling, managed model deletion, and `SharedPreferences` persistence for pipeline stage toggles and model selections.
  - `data/action/TvControlManager.kt`: Unified TV/Soundbar control manager handling persistent settings (SharedPreferences) and multi-channel MUTE/UNMUTE dispatch (Webhook / IR / USB IR).
  - `data/action/IrEmitterController.kt`: Hardware IR transmitter controller (`ConsumerIrManager` 38 kHz NEC pulses) and Hisense TV HTTP Webhook runner.
  - `data/action/UsbIrDongleController.kt`: External USB-C IR transmitter controller (`UsbManager` for `10C4:8468` Tview / Tiqiaa dongles).
  - `data/action/IrCodeDatabase.kt`: Predefined TV & Soundbar Code Set presets (Hisense Sets 1-4, Samsung, LG, Sony, Vizio, TCL, Bose, Yamaha, Polk, Custom Pronto).
  - `data/action/ProntoHexConverter.kt`: Universal Pronto Hex to ConsumerIrManager carrier frequency and microsecond pulse timing decoder.
  - `ui/components/AppIcons.kt`: Zero-dependency custom vector icons for navigation, settings, source selection, and transport actions.
  - `ui/components/SpectrogramVisualizer.kt`: Ping-pong 2-slot `Bitmap` Mel-spectrogram waterfall, oscilloscope waveform, distance meter, and `MiniMelStrip` segment strip visualizer.
  - `ui/ir/IrSettingsScreen.kt`: Dedicated IR hardware & Webhook test screen with Code Set selector, Test & Step pairing wizard, and smart TV automation console.
  - `ui/help/HelpScreen.kt`: System Help & Details screen explaining signal processing, Mel-spectrogram math, `FrameDecisionEngine` queries, IR signals, webhooks, and workbench modes.
  - `ui/camera/CameraScreen.kt`: CameraX `Preview` and `ImageAnalysis` (`Size(320, 240)`, `250 ms` interval, `STRATEGY_KEEP_ONLY_LATEST`) capture pipeline (`camerax`).
  - `ui/main/MainScreen.kt`: Jetpack Compose adaptive workbench dashboard (`adaptive`) featuring two-mode `MIC / CAM` and `FILE / VIDEO` selection, `LiveCameraCard` (`CAM ON`/`CAM OFF` toggle), `TextureView` + `MediaPlayer` `VideoPlayerCard` with reusable `ARGB_8888` `Bitmap` sampling, `FrameDecisionBar` HUD badges, `SessionTimelineCard` (`[TIMELINE | LOG]` toggle, `LazyListState` pinning), and tabbed `SpectrogramSettingsSheet` (`[ANALYSIS | MODELS]`) hosting `Simulate TV Mute`, Stage 1/Stage 2 pipeline toggles, on-device model pickers, SAF import, and catalog downloads.
  - `theme/`: Material Design 3 adaptive theme setup (`adaptive`, `styles`).
  - `.agents/skills/`: Installed Antigravity project skills (`appfunctions`, `litert-lm`, `litert-runtime`, `ml-kit-genai-prompt-api`, `mlkit-agent-api`).
- `app/src/test/`: `105` JVM unit tests covering signal processing, decoding, classification, TV/IR control, `BroadcastCueSignals` / `RepeatAdFingerprintIndex`, and `FrameDecisionEngine`, plus `BroadcastClipEvaluationTest` (`-DrunBroadcastEval=true`, `ffmpeg`-piped clip runner) (`testing-setup`).
- `gradle/libs.versions.toml`: Version catalog.
- `graphify-out/`: Codebase knowledge graph (`GRAPH_REPORT.md`, `graph.html`, `manifest.json`, `cost.json`, `.graphify_labels.json`, all `<= 500 KB`; `graph.json` and `cache/` ignored via `.gitignore`).

## Development & Build Setup

### Prerequisites
- Android SDK (API 36, Build-Tools 36.1.0)
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
