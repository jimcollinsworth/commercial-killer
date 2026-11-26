# Graph Report - commercial-killer  (2026-11-25)

## Corpus Check
- 18 files · ~108,879 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 983 nodes · 2314 edges · 42 communities (24 shown, 18 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 96 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Program Fingerprint Cache
- Frame Decision Engine
- Pronto Hex & IR Tests
- TV Control & IR Database
- LiteRT Runtime & Verifier
- Session Segment Timeline
- Workbench HUD & Controls
- Audio Classifier Engine
- Broadcast Cues & Repeat Ad
- USB IR Dongle Controller
- Model Repository & Downloads
- MainActivity & Launcher Icons
- MainScreen ViewModel & Repo
- CameraX Preview & Analysis
- Audio File Decoder
- Audio Workbench Controls
- Spectrogram Waterfall Visualizers
- Mel Spectrogram Calculator
- Spectrogram Distance Comparator
- Compose UI & Theme
- Navigation & Help Screens
- Navigation & Vector Icons
- Broadcast Clip Evaluation
- Audio Workbench Unit Tests
- Workbench State & Mode
- AppFunctions & Agent Skills
- Audio Frame Processing Loop
- Audio Source Mode Controls
- ML Kit Prompt API Skill
- Navigation Keys
- Video TextureView Surface Listener
- AGENTS.md Governance Rules

## God Nodes (most connected - your core abstractions)
1. `AudioWorkbenchEngine` - 76 edges
2. `FrameDecisionEngine` - 60 edges
3. `VisualFrameDescriptor` - 49 edges
4. `FrameDecisionEngineTest` - 32 edges
5. `ProgramFingerprintCache` - 29 edges
6. `AudioClassifierEngine` - 26 edges
7. `LiteRtLmDecisionVerifier` - 26 edges
8. `SessionSegmentTracker` - 25 edges
9. `TvControlManager` - 22 edges
10. `FrameDecision` - 22 edges

## Surprising Connections (you probably didn't know these)
- `LiteRT-LM Community Model Selection` --conceptually_related_to--> `ModelRepository`  [INFERRED]
  .agents/skills/litert-lm/references/get-a-model.md → app/src/main/java/com/example/commercialkiller/data/model/ModelRepository.kt
- `16 KB Page-Size ELF Alignment Migration` --rationale_for--> `AudioClassifierEngine`  [EXTRACTED]
  JOURNAL.md → app/src/main/java/com/example/commercialkiller/data/audio/AudioClassifierEngine.kt
- `Fast Local Android Decision Model (System 1)` --references--> `FrameDecisionEngine`  [EXTRACTED]
  ROADMAP.md → app/src/main/java/com/example/commercialkiller/data/decision/FrameDecisionEngine.kt
- `Stage 2 CPU Backend & Prompt Calibration (v2.6)` --rationale_for--> `LiteRtLmDecisionVerifier`  [EXTRACTED]
  JOURNAL.md → app/src/main/java/com/example/commercialkiller/data/decision/LiteRtLmDecisionVerifier.kt
- `Broadcast Clip Evaluation Benchmark (v2.7)` --references--> `BroadcastClipEvaluationTest`  [EXTRACTED]
  JOURNAL.md → app/src/test/java/com/example/commercialkiller/data/decision/BroadcastClipEvaluationTest.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Android AppFunctions Skill Documentation Suite** — agents_skills_appfunctions_skill, agents_skills_appfunctions_references_implementation_configuration_service_entrypoint, agents_skills_appfunctions_references_kdoc_refinement_optimization_kdoc, agents_skills_appfunctions_references_adb_interaction_testing_adb [EXTRACTED 1.00]
- **Commercial Killer Governance & Planning Corpus** — agents_universal_operating_rules, readme_workbench_overview, planning_sprint_backlog, roadmap_technical_proposals, journal_decision_log [EXTRACTED 1.00]
- **LiteRT-LM and CompiledModel Runtime Skills** — agents_skills_litert_lm_skill, agents_skills_litert_runtime_skill, agents_skills_litert_lm_references_get_a_model_litert_community [EXTRACTED 1.00]
- **ML Kit GenAI Prompt and Agent Skills** — agents_skills_ml_kit_genai_prompt_api_skill, agents_skills_mlkit_agent_api_skill, agents_skills_mlkit_agent_api_references_tools_and_functions_tools [EXTRACTED 1.00]
- **Stage 1 & Stage 2 Multimodal Decision Pipeline** — app_src_main_java_com_example_commercialkiller_data_decision_broadcastcuesignals_broadcastcuesignals, app_src_main_java_com_example_commercialkiller_data_decision_repeatadfingerprintindex_repeatadfingerprintindex, app_src_main_java_com_example_commercialkiller_data_decision_framedecisionengine_framedecisionengine, app_src_main_java_com_example_commercialkiller_data_decision_programfingerprintcache_programfingerprintcache, app_src_main_java_com_example_commercialkiller_data_decision_litertlmdecisionverifier_litertlmdecisionverifier, roadmap_system1_decision_model [EXTRACTED 1.00]

## Communities (42 total, 18 thin omitted)

### Community 0 - "Program Fingerprint Cache"
Cohesion: 0.07
Nodes (10): ActiveSceneObservation, CornerBugStats, EdgeAspectScore, ProgramFingerprintCache, VideoFrameAnalyzer, VideoFrameSnapshot, VisualFrameDescriptor, ProgramFingerprintCacheTest (+2 more)

### Community 1 - "Frame Decision Engine"
Cohesion: 0.09
Nodes (11): CommercialEvaluationResult, FrameDecisionEngine, MediaEnvironment, AMBIENT_NOISE, BROADCAST_MEDIA, SILENCE, MediaEvaluationResult, SceneContinuityResult (+3 more)

### Community 2 - "Pronto Hex & IR Tests"
Cohesion: 0.06
Nodes (8): DecodedPronto, ProntoHexConverter, IrEmitterControllerTest, ProntoHexConverterTest, TvControlManagerTest, UsbIrDongleControllerTest, AudioClassifierEngineTest, ModelRepositoryTest

### Community 3 - "TV Control & IR Database"
Cohesion: 0.07
Nodes (16): DeviceCategory, SOUNDBAR, TV, IrCodeDatabase, TvCodeSet, IrEmitterController, ControlMethod, BOTH (+8 more)

### Community 4 - "LiteRT Runtime & Verifier"
Cohesion: 0.06
Nodes (15): Play for On-Device AI (LiteRT-LM Copy), LiteRT-LM Community Model Selection, LiteRT-LM On-Device LLM Skill, Play for On-Device AI (LiteRT Runtime Copy), Migration from Legacy TFLite Interpreter, LiteRT in Google Play Services, LiteRT Physical Device Verification, LiteRT CompiledModel Runtime Skill (+7 more)

### Community 5 - "Session Segment Timeline"
Cohesion: 0.12
Nodes (18): DecisionModality, AUDIO_ONLY, AUDIO_VIDEO, VIDEO_ONLY_MUTED, FrameDecision, TvActionDecision, HOLD, MUTE (+10 more)

### Community 6 - "Workbench HUD & Controls"
Cohesion: 0.08
Nodes (19): ModelDownloadState, ActiveMediaInputSlot(), DistanceMeterCard(), FilePlaybackCard(), formatMmSs(), formatSizeBytes(), FrameDecisionBar(), LiveCameraCard() (+11 more)

### Community 8 - "Audio Classifier Engine"
Cohesion: 0.09
Nodes (3): AudioClassifierEngine, ClassifierScore, ClassifierLabelsContent()

### Community 9 - "Broadcast Cues & Repeat Ad"
Cohesion: 0.11
Nodes (15): BroadcastCueSignals, RepeatAdFingerprintIndex, WindowFingerprint, Broadcast Clip Evaluation Benchmark (v2.7), JOURNAL.md Architecture Decision Log, Comskip/adhush Generalized Broadcast Cues (v2.7), Codebase Knowledge Graph (graphify-out), PLANNING.md Sprint Backlog (+7 more)

### Community 10 - "USB IR Dongle Controller"
Cohesion: 0.10
Nodes (6): UsbDongleStatus, NOT_CONNECTED, PERMISSION_REQUIRED, READY, UsbIrDongleController, BroadcastReceiver

### Community 11 - "Model Repository & Downloads"
Cohesion: 0.14
Nodes (11): CatalogModelSpec, LocalModelEntry, ModelKind, AUDIO_TFLITE, STAGE2_LITERTLM, ModelRepository, PipelinePreferences, Stage2Mode (+3 more)

### Community 12 - "MainActivity & Launcher Icons"
Cohesion: 0.09
Nodes (12): MainScreenTest, MainActivity, HDPI Square Launcher Icon (Green Grid Android Robot), HDPI Round Launcher Icon (Green Grid Android Robot), MDPI Square Launcher Icon (Green Grid Android Robot), MDPI Round Launcher Icon (Green Grid Android Robot), XHDPI Square Launcher Icon (Green Grid Android Robot), XHDPI Round Launcher Icon (Green Grid Android Robot) (+4 more)

### Community 13 - "MainScreen ViewModel & Repo"
Cohesion: 0.11
Nodes (9): DataRepository, DefaultDataRepository, Error, Loading, MainScreenUiState, MainScreenViewModel, Success, FakeMyModelRepository (+1 more)

### Community 15 - "Audio File Decoder"
Cohesion: 0.17
Nodes (3): AudioFileDecoder, DecodedAudio, AudioFileDecoderTest

### Community 17 - "Spectrogram Waterfall Visualizers"
Cohesion: 0.15
Nodes (7): DistanceMeter(), getThermalArgb(), MiniMelStrip(), SpectrogramWaterfall(), WaterfallPingPongBuffer, WaterfallSlot, WaveformOscilloscope()

### Community 19 - "Spectrogram Distance Comparator"
Cohesion: 0.19
Nodes (7): ComparisonResult, DistanceMetric, COSINE_DISTANCE, EUCLIDEAN_DISTANCE, MEAN_SQUARED_ERROR, SpectrogramComparator, SpectrogramComparatorTest

### Community 22 - "Navigation & Help Screens"
Cohesion: 0.15
Nodes (4): MainNavigation(), HelpScreen(), HelpSubItem(), IrSettingsScreen()

### Community 23 - "Navigation & Vector Icons"
Cohesion: 0.42
Nodes (11): AudioFileIcon(), BackIcon(), CameraIcon(), HelpIcon(), MicIcon(), PlayIcon(), SettingsIcon(), SlidersIcon() (+3 more)

### Community 24 - "Broadcast Clip Evaluation"
Cohesion: 0.23
Nodes (4): BroadcastClipEvaluationTest, DecodedClip, PassResult, StateAccumulator

### Community 26 - "Workbench State & Mode"
Cohesion: 0.24
Nodes (5): WorkbenchState, handleSelectSource(), handleToggleCamera(), handleToggleRunning(), launchMediaPicker()

### Community 28 - "AppFunctions & Agent Skills"
Cohesion: 0.20
Nodes (10): AppFunctions ADB Interaction & Testing, AppFunctions Context & Terminology, AppFunctions Feature Discovery Analysis, AppFunctionServiceEntryPoint & KSP Config, AppFunctions KDoc & app_metadata Refinement, AppFunctions Migration to Service Entry Point, AppFunctions Skill Guide, ML Kit Agent API Session Lifecycle (+2 more)

### Community 30 - "Audio Source Mode Controls"
Cohesion: 0.25
Nodes (3): AudioSourceMode, FILE, LIVE

### Community 31 - "ML Kit Prompt API Skill"
Cohesion: 0.29
Nodes (7): ML Kit Prompt API Get Started, ML Kit Prompt API Prefix Caching, ML Kit Prompt API Prompt Design, ML Kit Prompt API Optimization Rules, ML Kit Prompt API Structured Output (@Generable), ML Kit Prompt API System Instructions, ML Kit GenAI Prompt API Skill

### Community 32 - "Navigation Keys"
Cohesion: 0.73
Nodes (3): Help, IrSettings, Main

## Knowledge Gaps
- **65 isolated node(s):** `Error`, `Loading`, `Success`, `NOT_CONNECTED`, `PERMISSION_REQUIRED` (+60 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 230 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AudioWorkbenchEngine` connect `Audio Workbench Controls` to `Program Fingerprint Cache`, `Frame Decision Engine`, `LiteRT Runtime & Verifier`, `Workbench HUD & Controls`, `Broadcast Cues & Repeat Ad`, `MainActivity & Launcher Icons`, `Audio Workbench Engine`, `Compose UI & Theme`, `Navigation & Help Screens`, `Audio Workbench Unit Tests`, `Workbench State & Mode`, `Model Manager Methods`, `Audio Frame Processing Loop`, `Audio Source Mode Controls`?**
  _High betweenness centrality (0.154) - this node is a cross-community bridge._
- **Why does `VisualFrameDescriptor` connect `Program Fingerprint Cache` to `Frame Decision Engine`, `Pronto Hex & IR Tests`, `Workbench HUD & Controls`, `Audio Classifier Engine`, `Broadcast Cues & Repeat Ad`, `CameraX Preview & Analysis`, `Audio Workbench Controls`, `Audio Workbench Engine`, `Broadcast Clip Evaluation`, `Audio Workbench Unit Tests`, `Workbench State & Mode`?**
  _High betweenness centrality (0.151) - this node is a cross-community bridge._
- **Why does `FrameDecisionEngine` connect `Frame Decision Engine` to `Program Fingerprint Cache`, `Pronto Hex & IR Tests`, `Workbench HUD & Controls`, `Broadcast Cues & Repeat Ad`, `Audio Workbench Controls`, `Audio Workbench Engine`, `Broadcast Clip Evaluation`?**
  _High betweenness centrality (0.134) - this node is a cross-community bridge._
- **Are the 11 inferred relationships involving `AudioWorkbenchEngine` (e.g. with `.`dynamic Stage 1 and Stage 2 toggles update state without requiring device context`()` and `.initialState_hasDefaultSpectrogramResolutionOf80Bands()`) actually correct?**
  _`AudioWorkbenchEngine` has 11 INFERRED edges - model-reasoned connections that need verification._
- **Are the 27 inferred relationships involving `FrameDecisionEngine` (e.g. with `ActiveSceneObservation` and `ProgramFingerprintCache`) actually correct?**
  _`FrameDecisionEngine` has 27 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `ProgramFingerprintCache` (e.g. with `FrameDecisionEngine` and `.`active scene angle bank recognizes alternating A and B camera shots without novelty spikes`()`) actually correct?**
  _`ProgramFingerprintCache` has 8 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Error`, `Loading`, `Success` to the rest of the system?**
  _65 weakly-connected nodes found - possible documentation gaps or missing edges._