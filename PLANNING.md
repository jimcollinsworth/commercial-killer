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
- [ ] MediaPipe & LiteRT-ML video model execution pipeline integration
- [ ] Audio muting & automated component triggers upon commercial detection

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.*
