# PLANNING.md — Active Backlog & Sprint Milestones

## Incremental Step 1: Mel-Spectrogram & Significance Event Trigger (Current)

- [x] Establish project baseline & governance files (`AGENTS.md`, `README.md`, `PLANNING.md`, `ROADMAP.md`, `JOURNAL.md`)
- [x] Register 7 Android CLI skills (`adaptive`, `camerax`, `edge-to-edge`, `ml-kit-genai-prompt-api`, `testing-setup`, `android-profiler`, `android-cli`) *(Note: `ml-kit-genai-prompt-api` dropped/superseded in v2.2)*
- [x] Configure token & resource usage logging rule (Rule 19) in `AGENTS.md` and `JOURNAL.md`
- [x] Verify Gradle debug build (`assembleDebug` - SUCCESS)
- [x] Deploy to emulator `Medium_Phone_API_36.1` and capture baseline UI screenshot
- [x] Add Audio Recording & Mel-Spectrogram calculation engine (100 ms configurable interval)
- [x] Add Spectrogram matrix distance comparison (Euclidean / Cosine / MSE) for significance thresholding
- [x] ~~Implement local Multimodal LLM evaluator fallback (`AiAnalysisViewModel`)~~ *(Deleted and superseded in v2.2 by `FrameDecisionEngine`)*
- [x] Add real-time visual Mel-spectrogram rendering & event log overlay in Compose UI
- [x] Implement automated unit test suite (`MelSpectrogramTest.kt`, `SpectrogramComparatorTest.kt`)
- [x] Deploy and capture live emulator verification screenshots

## Future Increments

- [x] File selector for local audio/video file benchmarking (multi-format audio loader: WAV, MP3, AAC, M4A, FLAC, OGG, OPUS)
- [x] Intent-based direct ADB audio file loading (`adb shell am start ... --es audio_file <path>`)
- [x] Download & verification of real broadcast test clips with commercials (`test_assets/`)
- [x] MediaPipe & LiteRT-ML / TFLite parallel audio classifier stream (Hugging Face open-weights models)
- [x] Realistic acoustic broadcast simulation (multi-formant speech, silence dips, loudness compression)
- [x] IR Infrared command sender (`ConsumerIrManager` for simple Mute/Unmute IR transmission)
- [x] IR & TV Control test screen (`IrSettingsScreen.kt` with Mute/Unmute buttons, carrier frequency config, and action console)
- [x] TV Code Set Selector & Presets (`IrCodeDatabase.kt` for Hisense Sets 1-4, Samsung, LG, Sony, Vizio, TCL)
- [x] Universal Pronto Hex decoder & converter (`ProntoHexConverter.kt` for custom TV codes)
- [x] Test & Step pairing wizard in `IrSettingsScreen.kt`
- [x] Lightweight AppIcons custom vector icons (non-wrapping 44×44dp touch targets)
- [x] Hisense Android TV IP control & webhook runner (HTTP POST / REST integration)
- [x] Comprehensive System Help & Details screen (`HelpScreen.kt` detailing signal processing, 40-band Mel calculation, IR signals, webhooks, and input modes)
- [x] Automated Mute / Unmute pipeline integration during real-time processing & commercial detection
- [x] Tview USB-C IR Blaster Dongle (`10C4:8468`) support via Android USB Host API (`UsbManager`) (v1.9)
- [x] Soundbar IR control presets & dual-channel simultaneous TV/Soundbar muting with Webhooks (v1.9)
- [x] Modern Android Tips & Debugging Skill Refactoring (`android-tips-and-debugging`) with modular references (v2.0)
- [x] AudioTrack dedicated playback thread with `THREAD_PRIORITY_URGENT_AUDIO` priority & quad-buffering (v2.0)
- [x] Real-time microphone capture synchronization without buffer drift or artificial delays (v2.0)
- [x] Thread-safe atomic StateFlow updates (`_state.update`) eliminating read-modify-write races (v2.0)
- [x] Host lifecycle awareness (`LifecycleResumeEffect`) auto-pausing synthesis and playback in background (v2.0)
- [x] Android 17 (API 37) Adaptive UI: Two-Pane layout for tablets/foldables (`sw600dp+`) and bounded phone containers (v2.0)
- [x] Video on-screen display during video file processing with audio sync and top layout placement (v2.1.00)
- [x] High-resolution Mel-Spectrogram 3x height expansion (~50% screen height) with up to 80-128 Mel bands (v2.1.00)
- [x] Spectrogram & Analysis Settings Panel (`SpectrogramSettingsSheet`) moving threshold/intervals off page (v2.1.00)
- [x] Text-only audio classifier display with color thresholding (>90% green, <=90% yellow, <10% hidden) (v2.1.00)
- [x] Removal of redundant titles ("Mel Spectrogram", "Parallel Audio Class", "Acoustic Feature Classifier") (v2.1.00)
- [x] Compact source selector (SYNTH, MIC, FILE) with custom vector icons in 28dp pill container (v2.1.00)
- [x] FlowRow wrapping for classifier labels preventing horizontal truncation on narrow screens (v2.1.00.01)
- [x] VideoView lifecycle cleanup (`onRelease`), `key(uri)` re-binding, and scrub-while-paused frame sync (v2.1.00.01)
- [x] Strict video container track validation in AudioFileDecoder preventing audio-only MP4/M4A false positives (v2.1.00.01)
- [x] Immediate waterfall frequency grid rescaling on band resolution changes in SpectrogramWaterfall (v2.1.00.01)
- [x] Non-allocating `AudioSourceMode.entries` in Compose loop and bounded 38dp header action touch targets (v2.1.00.01)
- [x] Comprehensive unit tests for AudioWorkbenchEngine state mutations, clamping, and mode switches (v2.1.00.01)
- [x] Fast Local Android Decision Model (`FrameDecisionEngine`) executing non-autoregressive 100 ms per-frame `is_commercial` and `is_same_scene` queries with cosine similarity cut cadence tracking and `MUTE`/`UNMUTE`/`HOLD` action gating (v2.2)
- [x] Deletion of `AiAnalysisViewModel.kt` and removal of all generative LLM / Gemini Nano Prompt API proposals across codebase and documentation (v2.2)
- [x] `FrameDecisionBar` HUD badges in `MainScreen.kt` (COMMERCIAL/PROGRAM score, SAME SCENE/SCENE CUT cosine similarity, and Shot duration / cuts per minute telemetry) across compact and expanded two-pane layouts (v2.2)
- [x] Scene Cut Similarity Threshold slider (`0.50f..0.98f`) in `SpectrogramSettingsSheet` wired to `AudioWorkbenchEngine.setSceneSimilarityThreshold` (v2.2)
- [x] Automated unit tests covering `is_commercial`, `is_same_scene`, scene cuts, shot duration, rolling cut cadence pruning, threshold clamping, `MUTE`/`UNMUTE`/`HOLD` gating, and `AudioWorkbenchEngine.setSceneSimilarityThreshold` (`FrameDecisionEngineTest.kt` and `AudioWorkbenchEngineTest.kt`) (v2.2)
- [x] First-gate Per-Frame Media Presence Query (`is_media`) in `FrameDecisionEngine` distinguishing `BROADCAST_MEDIA`, `SILENCE`, and `AMBIENT_NOISE` (car driving low-frequency rumble & steady broadband noise) before `is_commercial` evaluation (v2.3)
- [x] Acoustic non-media classifiers in `AudioClassifierEngine` (Vehicle Engine/Road Rumble via low ZCR/HF energy + Stationary Broadband Ambient Noise via high ZCR/HF energy and low subframe RMS modulation) (v2.3)
- [x] First-gate `MEDIA` / `NO MEDIA: SILENCE` / `NO MEDIA: AMBIENT` + `HOLD (NON-MEDIA)` badges in `FrameDecisionBar` and `Media Detection Threshold` slider (`0.20f..0.90f`) in `SpectrogramSettingsSheet` (v2.3)
- [x] Fixed-height non-jumping dashboard sections (`ShiftStatusBanner` 22dp, 2-row `FrameDecisionBar` 48dp, 3-slot `ClassifierLabelsContent` 24dp, `DistanceMeterCard` 34dp, `EventConsoleCard` 110dp) eliminating vertical UI shift across Compact and Expanded panes (v2.3.01)
- [x] Empty-room live `MIC` `NO MEDIA` detection using pre-emphasized (`0.92f`) pitch autocorrelation (`maxPitchCorr < 0.30f && rms < 0.14f`), `MIC`/`UNPROCESSED` live source priority, `100 ms` mic processing throttle, `0.020f` RMS silence gate, and `0.08f` Mel silence floor (v2.3.01)
- [x] Fixed `loadAudioFile()` vs `SYNTH` cold-start race by calling `stop()` before updating `_state` to `AudioSourceMode.FILE` and guarding `DisposableEffect(Unit)` in `MainScreen.kt` (v2.3.01)
- [ ] Cross-Modal Audio-Visual Fingerprint Caching (Learn and snapshot pre-break program signature [Mel-spectrogram + camera frame palette & station bug] prior to muting, enabling visual recognition of program return to trigger unmute)
- [ ] Vision-Based Spectrogram Image Comparison & Evaluation (Convert Mel-spectrograms to 2D image tensors/bitmaps and evaluate transitions using an on-device vision/image model)
- [ ] IR signal acquisition & calibration interface (receiving raw IR signals for training and user corrections)
- [ ] ADK for Kotlin 1.0 integration (deferred until later release)
- [ ] MediaPipe & LiteRT-ML video model execution pipeline integration
- [ ] Game-Style HUD & POV UI redesign (deferred for future iteration)

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8, LLM-Gemini4Argon, LLM-Gemini4Neon.*
