# Graph Report - commercial-killer  (2026-11-25)

## Corpus Check
- 91 files · ~97,236 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 17 file(s) not represented in the graph (top: .xml 10, (none) 2, .properties 2)

## Summary
- 894 nodes · 2065 edges · 31 communities (20 shown, 11 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 86 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Audio Workbench Engine
- Program Fingerprint Cache
- Frame Decision Engine
- Model Repository & Downloads
- LiteRT Runtime & Verifier
- Pronto Hex & IR Tests
- Session Segment Timeline
- MainActivity & Launcher Icons
- Audio Classifier Engine
- Navigation & Vector Icons
- USB IR Dongle Controller
- Mel Spectrogram Calculator
- TV Control & IR Database
- Workbench HUD Cards
- CameraX Preview & Analysis
- MainScreen ViewModel & Repo
- Audio File Decoder
- Spectrogram Distance Comparator
- IR Emitter & NEC Pulses
- Spectrogram Waterfall Visualizers
- AppFunctions & Agent Skills
- ML Kit Prompt API Skill
- Navigation Keys
- Video TextureView Surface Listener
- AGENTS.md Governance Rules

## God Nodes (most connected - your core abstractions)
1. `AudioWorkbenchEngine` - 83 edges
2. `FrameDecisionEngine` - 49 edges
3. `VisualFrameDescriptor` - 35 edges
4. `AudioClassifierEngine` - 27 edges
5. `SessionSegmentTracker` - 27 edges
6. `FrameDecisionEngineTest` - 27 edges
7. `TvControlManager` - 24 edges
8. `LiteRtLmDecisionVerifier` - 24 edges
9. `WorkbenchState` - 21 edges
10. `ModelRepository` - 21 edges

## Surprising Connections (you probably didn't know these)
- `LiteRT-LM Community Model Selection` --conceptually_related_to--> `ModelRepository`  [INFERRED]
  .agents/skills/litert-lm/references/get-a-model.md → app/src/main/java/com/example/commercialkiller/data/model/ModelRepository.kt
- `Fast Local Android Decision Model (System 1)` --references--> `FrameDecisionEngine`  [EXTRACTED]
  ROADMAP.md → app/src/main/java/com/example/commercialkiller/data/decision/FrameDecisionEngine.kt
- `Stage 2 CPU Backend & Prompt Calibration (v2.6)` --rationale_for--> `LiteRtLmDecisionVerifier`  [EXTRACTED]
  JOURNAL.md → app/src/main/java/com/example/commercialkiller/data/decision/LiteRtLmDecisionVerifier.kt
- `16 KB Page-Size ELF Alignment Migration` --rationale_for--> `AudioClassifierEngine`  [EXTRACTED]
  JOURNAL.md → app/src/main/java/com/example/commercialkiller/data/audio/AudioClassifierEngine.kt
- `Commercial Killer Audio/Video AI Workbench` --references--> `AudioWorkbenchEngine`  [EXTRACTED]
  README.md → app/src/main/java/com/example/commercialkiller/data/audio/AudioWorkbenchEngine.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Commercial Killer Governance & Planning Corpus** — agents_universal_operating_rules, readme_workbench_overview, planning_sprint_backlog, roadmap_technical_proposals, journal_decision_log [EXTRACTED 1.00]
- **Stage 1 & Stage 2 Multimodal Decision Pipeline** — app_src_main_java_com_example_commercialkiller_data_decision_framedecisionengine_framedecisionengine, app_src_main_java_com_example_commercialkiller_data_decision_programfingerprintcache_programfingerprintcache, app_src_main_java_com_example_commercialkiller_data_decision_litertlmdecisionverifier_litertlmdecisionverifier, roadmap_system1_decision_model [EXTRACTED 1.00]
- **Android AppFunctions Skill Documentation Suite** — agents_skills_appfunctions_skill, agents_skills_appfunctions_references_implementation_configuration_service_entrypoint, agents_skills_appfunctions_references_kdoc_refinement_optimization_kdoc, agents_skills_appfunctions_references_adb_interaction_testing_adb [EXTRACTED 1.00]
- **LiteRT-LM and CompiledModel Runtime Skills** — agents_skills_litert_lm_skill, agents_skills_litert_runtime_skill, agents_skills_litert_lm_references_get_a_model_litert_community [EXTRACTED 1.00]
- **ML Kit GenAI Prompt and Agent Skills** — agents_skills_ml_kit_genai_prompt_api_skill, agents_skills_mlkit_agent_api_skill, agents_skills_mlkit_agent_api_references_tools_and_functions_tools [EXTRACTED 1.00]

## Communities (31 total, 11 thin omitted)

### Community 0 - "Audio Workbench Engine"
Cohesion: 0.06
Nodes (6): AudioSourceMode, FILE, LIVE, AudioWorkbenchEngine, WorkbenchEvent, AudioWorkbenchEngineTest

### Community 1 - "Program Fingerprint Cache"
Cohesion: 0.07
Nodes (17): ActiveSceneObservation, CornerBugStats, ProgramFingerprintCache, ProgramMatchResult, VideoFrameAnalyzer, VideoFrameSnapshot, VisualFrameDescriptor, VideoPlayerCard() (+9 more)

### Community 2 - "Frame Decision Engine"
Cohesion: 0.09
Nodes (10): ClassifierScore, CommercialEvaluationResult, FrameDecisionEngine, MediaEnvironment, AMBIENT_NOISE, BROADCAST_MEDIA, SILENCE, MediaEvaluationResult (+2 more)

### Community 4 - "Model Repository & Downloads"
Cohesion: 0.07
Nodes (12): CatalogModelSpec, LocalModelEntry, ModelDownloadState, ModelKind, AUDIO_TFLITE, STAGE2_LITERTLM, ModelRepository, PipelinePreferences (+4 more)

### Community 5 - "LiteRT Runtime & Verifier"
Cohesion: 0.06
Nodes (15): Play for On-Device AI (LiteRT-LM Copy), LiteRT-LM Community Model Selection, LiteRT-LM On-Device LLM Skill, Play for On-Device AI (LiteRT Runtime Copy), Migration from Legacy TFLite Interpreter, LiteRT in Google Play Services, LiteRT Physical Device Verification, LiteRT CompiledModel Runtime Skill (+7 more)

### Community 6 - "Pronto Hex & IR Tests"
Cohesion: 0.07
Nodes (7): DecodedPronto, ProntoHexConverter, IrEmitterControllerTest, ProntoHexConverterTest, TvControlManagerTest, UsbIrDongleControllerTest, ModelRepositoryTest

### Community 7 - "Session Segment Timeline"
Cohesion: 0.12
Nodes (18): DecisionModality, AUDIO_ONLY, AUDIO_VIDEO, VIDEO_ONLY_MUTED, FrameDecision, TvActionDecision, HOLD, MUTE (+10 more)

### Community 8 - "MainActivity & Launcher Icons"
Cohesion: 0.06
Nodes (13): MainScreenTest, MainActivity, CommercialKillerTheme(), HDPI Square Launcher Icon (Green Grid Android Robot), HDPI Round Launcher Icon (Green Grid Android Robot), MDPI Square Launcher Icon (Green Grid Android Robot), MDPI Round Launcher Icon (Green Grid Android Robot), XHDPI Square Launcher Icon (Green Grid Android Robot) (+5 more)

### Community 10 - "Navigation & Vector Icons"
Cohesion: 0.13
Nodes (15): MainNavigation(), AudioFileIcon(), BackIcon(), CameraIcon(), HelpIcon(), MicIcon(), PlayIcon(), SettingsIcon() (+7 more)

### Community 11 - "USB IR Dongle Controller"
Cohesion: 0.10
Nodes (6): UsbDongleStatus, NOT_CONNECTED, PERMISSION_REQUIRED, READY, UsbIrDongleController, BroadcastReceiver

### Community 12 - "Mel Spectrogram Calculator"
Cohesion: 0.10
Nodes (3): MelSpectrogramCalculator, AudioClassifierEngineTest, MelSpectrogramTest

### Community 13 - "TV Control & IR Database"
Cohesion: 0.11
Nodes (15): DeviceCategory, SOUNDBAR, TV, IrCodeDatabase, TvCodeSet, ControlMethod, BOTH, IR (+7 more)

### Community 14 - "Workbench HUD Cards"
Cohesion: 0.14
Nodes (23): WorkbenchState, ActiveMediaInputSlot(), ClassifierLabelsContent(), DistanceMeterCard(), FilePlaybackCard(), formatMmSs(), formatSizeBytes(), FrameDecisionBar() (+15 more)

### Community 16 - "MainScreen ViewModel & Repo"
Cohesion: 0.11
Nodes (9): DataRepository, DefaultDataRepository, Error, Loading, MainScreenUiState, MainScreenViewModel, Success, FakeMyModelRepository (+1 more)

### Community 18 - "Audio File Decoder"
Cohesion: 0.16
Nodes (3): AudioFileDecoder, DecodedAudio, AudioFileDecoderTest

### Community 19 - "Spectrogram Distance Comparator"
Cohesion: 0.18
Nodes (7): ComparisonResult, DistanceMetric, COSINE_DISTANCE, EUCLIDEAN_DISTANCE, MEAN_SQUARED_ERROR, SpectrogramComparator, SpectrogramComparatorTest

### Community 21 - "Spectrogram Waterfall Visualizers"
Cohesion: 0.22
Nodes (7): DistanceMeter(), getThermalArgb(), MiniMelStrip(), SpectrogramWaterfall(), WaterfallPingPongBuffer, WaterfallSlot, WaveformOscilloscope()

### Community 22 - "AppFunctions & Agent Skills"
Cohesion: 0.20
Nodes (10): AppFunctions ADB Interaction & Testing, AppFunctions Context & Terminology, AppFunctions Feature Discovery Analysis, AppFunctionServiceEntryPoint & KSP Config, AppFunctions KDoc & app_metadata Refinement, AppFunctions Migration to Service Entry Point, AppFunctions Skill Guide, ML Kit Agent API Session Lifecycle (+2 more)

### Community 23 - "ML Kit Prompt API Skill"
Cohesion: 0.29
Nodes (7): ML Kit Prompt API Get Started, ML Kit Prompt API Prefix Caching, ML Kit Prompt API Prompt Design, ML Kit Prompt API Optimization Rules, ML Kit Prompt API Structured Output (@Generable), ML Kit Prompt API System Instructions, ML Kit GenAI Prompt API Skill

### Community 24 - "Navigation Keys"
Cohesion: 0.73
Nodes (3): Help, IrSettings, Main

## Knowledge Gaps
- **65 isolated node(s):** `TV`, `SOUNDBAR`, `WEBHOOK`, `IR`, `BOTH` (+60 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 222 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AudioWorkbenchEngine` connect `Audio Workbench Engine` to `Program Fingerprint Cache`, `Frame Decision Engine`, `Model Repository & Downloads`, `LiteRT Runtime & Verifier`, `Session Segment Timeline`, `MainActivity & Launcher Icons`, `Audio Classifier Engine`, `Navigation & Vector Icons`, `Mel Spectrogram Calculator`, `TV Control & IR Database`, `Workbench HUD Cards`, `MainScreen Adaptive Layout`, `Audio File Decoder`, `Spectrogram Distance Comparator`?**
  _High betweenness centrality (0.339) - this node is a cross-community bridge._
- **Why does `TvControlManager` connect `TV Control & IR Database` to `Audio Workbench Engine`, `Compose UI & Theme`, `Model Repository & Downloads`, `Pronto Hex & IR Tests`, `Navigation & Vector Icons`, `USB IR Dongle Controller`, `IR Emitter & NEC Pulses`?**
  _High betweenness centrality (0.130) - this node is a cross-community bridge._
- **Why does `FrameDecisionEngine` connect `Frame Decision Engine` to `Audio Workbench Engine`, `Program Fingerprint Cache`, `Model Repository & Downloads`, `Pronto Hex & IR Tests`, `MainScreen Adaptive Layout`?**
  _High betweenness centrality (0.114) - this node is a cross-community bridge._
- **Are the 15 inferred relationships involving `AudioWorkbenchEngine` (e.g. with `AudioClassifierEngine` and `AudioFileDecoder`) actually correct?**
  _`AudioWorkbenchEngine` has 15 INFERRED edges - model-reasoned connections that need verification._
- **Are the 21 inferred relationships involving `FrameDecisionEngine` (e.g. with `ActiveSceneObservation` and `ProgramFingerprintCache`) actually correct?**
  _`FrameDecisionEngine` has 21 INFERRED edges - model-reasoned connections that need verification._
- **Are the 2 inferred relationships involving `AudioClassifierEngine` (e.g. with `AudioWorkbenchEngine` and `AudioClassifierEngineTest`) actually correct?**
  _`AudioClassifierEngine` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 6 inferred relationships involving `SessionSegmentTracker` (e.g. with `.`20 rapid A-B camera angle edits within a show remain 1 single PROGRAM segment`()` and `.`2_5s silence pause with active video does not split PROGRAM while 5_5s gap transitions`()`) actually correct?**
  _`SessionSegmentTracker` has 6 INFERRED edges - model-reasoned connections that need verification._