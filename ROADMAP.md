# ROADMAP.md — Product Vision & Technical Proposals

## Product Vision

Commercial Killer is an **on-device experimentation workbench** for audio and video data. It enables developers to test real-time feature detectors, signal transforms, and local machine learning models directly on phone hardware without cloud latency or data privacy concerns.

## Technical Proposal: Significance Determination Alternatives

When comparing sequential Mel-spectrogram frames (calculated at a configurable 100 ms interval), we evaluate two primary approaches to determine whether a change is "significant" enough to trigger an event:

### Alternative A: Matrix Distance Metric (Lightweight / Low Energy)
- **Algorithm**: Compute Mean Squared Error (MSE), Fréchet Distance, or Cosine Distance between consecutive Mel-spectrogram frequency bin vectors $S_t$ and $S_{t-1}$.
- **Pros**: Extremely fast ($< 1\text{ ms}$ on CPU/DSP), minimal battery consumption, deterministic thresholding ($\Delta > \tau$).
- **Cons**: Sensitive to abrupt background noise or volume shifts without semantic understanding.

### Alternative B: Local Multimodal LLM Evaluation (Semantic Context)
- **Algorithm**: Pass consecutive spectrogram image frames or feature embeddings to an on-device local multimodal model (e.g. Gemini Nano via AICore / LiteRT-ML).
- **Prompt**: *"Compare the audio/video spectrogram at $t_{-1}$ and $t_0$. Has the broadcast transitioned from primary content to a commercial break?"*
- **Pros**: High semantic accuracy, contextual awareness of commercial transitions, immune to simple volume spikes.
- **Cons**: Higher latency and NPU/GPU compute requirements per 100 ms frame.

### Hybrid Architecture (Recommended)
Use **Alternative A** as a fast first-stage filter. When matrix distance $\Delta$ exceeds threshold $\tau_{\text{trigger}}$, invoke **Alternative B** (local multimodal LLM) to confirm the commercial transition event.

---

## Long-Term Engine Roadmap

1. **Native Framework Integration**:
   - LiteRT-ML (TFLite) & MediaPipe tasks
   - AICore / Gemini Nano Prompt API
   - HuggingFace local GGUF / ONNX models via NNAPI
2. **Multi-Input Stream Workbench**:
   - Camera & Microphone live capture
   - Local audio/video file playback & frame-by-frame analysis
   - Streaming service input integration
3. **Real-Time Instrumentation & Profiling**:
   - Live Mel-spectrogram heatmap display in Jetpack Compose
   - Real-time execution latency charts & log console
   - System trace analysis using `android-profiler`

---
*Author Attribution: Co-authored by Project Owner & LLM-Gemini3.6.*
