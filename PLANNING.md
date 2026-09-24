# PLANNING.md — Active Backlog & Sprint Milestones

## Incremental Step 1: Mel-Spectrogram & Significance Event Trigger (Current)

- [x] Establish project baseline & governance files (`AGENTS.md`, `README.md`, `PLANNING.md`, `ROADMAP.md`, `JOURNAL.md`)
- [x] Register 7 Android CLI skills (`adaptive`, `camerax`, `edge-to-edge`, `ml-kit-genai-prompt-api`, `testing-setup`, `android-profiler`, `android-cli`)
- [x] Configure token & resource usage logging rule (Rule 19) in `AGENTS.md` and `JOURNAL.md`
- [x] Verify Gradle debug build (`assembleDebug` - SUCCESS)
- [x] Deploy to emulator `Medium_Phone_API_36.1` and capture baseline UI screenshot
- [x] Add Audio Recording & Mel-Spectrogram calculation engine (100 ms configurable interval)
- [x] Add Spectrogram matrix distance comparison (Euclidean / Cosine / MSE) for significance thresholding
- [x] Implement local Multimodal LLM evaluator fallback (`AiAnalysisViewModel`)
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
- [ ] Cross-Modal Audio-Visual Fingerprint Caching (Learn and snapshot pre-break program signature [Mel-spectrogram + camera frame palette & station bug] prior to muting, enabling high-confidence visual recognition of program return to trigger unmute)
- [ ] Vision-Based Spectrogram Image Comparison & Evaluation (Convert Mel-spectrograms to 2D image tensors/bitmaps and evaluate transitions using an on-device vision/image model)
- [ ] IR signal acquisition & calibration interface (receiving raw IR signals for training and user corrections)
- [ ] ADK for Kotlin 1.0 integration (deferred until later release)
- [ ] MediaPipe & LiteRT-ML video model execution pipeline integration
- [ ] Game-Style HUD & POV UI redesign (deferred for future iteration)

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.*
