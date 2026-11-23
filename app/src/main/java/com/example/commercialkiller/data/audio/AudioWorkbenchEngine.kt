// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.Uri
import android.os.Process
import com.example.commercialkiller.data.action.TvControlManager
import com.example.commercialkiller.data.decision.FrameDecision
import com.example.commercialkiller.data.decision.FrameDecisionEngine
import com.example.commercialkiller.data.decision.LiteRtLmDecisionVerifier
import com.example.commercialkiller.data.decision.SessionSegment
import com.example.commercialkiller.data.decision.SessionSegmentTracker
import com.example.commercialkiller.data.decision.Stage2Decision
import com.example.commercialkiller.data.decision.Stage2TransitionFeatures
import com.example.commercialkiller.data.decision.TvActionDecision
import com.example.commercialkiller.data.model.CatalogModelSpec
import com.example.commercialkiller.data.model.LocalModelEntry
import com.example.commercialkiller.data.model.ModelDownloadState
import com.example.commercialkiller.data.model.ModelKind
import com.example.commercialkiller.data.model.ModelRepository
import com.example.commercialkiller.data.model.PipelinePreferences
import com.example.commercialkiller.data.model.Stage2Mode
import com.example.commercialkiller.data.video.VideoFrameSnapshot
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AudioSourceMode {
    LIVE,
    FILE
}

data class WorkbenchEvent(
    val timestamp: String,
    val distance: Float,
    val threshold: Float,
    val description: String
)

data class WorkbenchState(
    val isRunning: Boolean = false,
    val sourceMode: AudioSourceMode = AudioSourceMode.LIVE,
    val isCameraEnabled: Boolean = false,
    val intervalMs: Long = 100L,
    val threshold: Float = 0.05f,
    val currentDistance: Float = 0f,
    val isEventTriggered: Boolean = false,
    val isTvMuted: Boolean = false,
    val tvControlMethod: String = "WEBHOOK",
    val waveform: FloatArray = FloatArray(256),
    val spectrogramHistory: List<FloatArray> = emptyList(),
    val eventLogs: List<WorkbenchEvent> = emptyList(),
    val classifierScores: List<ClassifierScore> = emptyList(),
    val activeClassifierModel: String = "Acoustic Feature Classifier (HF Fallback)",
    val loadedFileName: String? = null,
    val fileDurationMs: Long = 0L,
    val filePositionMs: Long = 0L,
    val fileProgress: Float = 0f,
    val isVideo: Boolean = false,
    val mediaUri: Uri? = null,
    val videoFrameCount: Long = 0L,
    val latestVisualDescriptor: VisualFrameDescriptor? = null,
    val latestVideoThumbnail: Bitmap? = null,
    val numMelBands: Int = 80,
    val frameDecision: FrameDecision = FrameDecision(),
    val sceneSimilarityThreshold: Float = FrameDecisionEngine.DEFAULT_SCENE_SIMILARITY_THRESHOLD,
    val mediaThreshold: Float = FrameDecisionEngine.DEFAULT_MEDIA_THRESHOLD,
    val sessionSegments: List<SessionSegment> = emptyList(),
    val simulateMutedBreak: Boolean = false,
    val stage2Status: String = LiteRtLmDecisionVerifier.STATUS_STAGE_1_ONLY,
    val stage2LastVerdict: String? = null,
    val stage2LastLatencyMs: Long? = null,
    val useNeuralAudioClassifier: Boolean = true,
    val selectedAudioModelPath: String? = null,
    val availableAudioModels: List<LocalModelEntry> = emptyList(),
    val videoFusionEnabled: Boolean = true,
    val stage2Mode: Stage2Mode = Stage2Mode.AUDIT,
    val selectedStage2ModelPath: String? = null,
    val availableStage2Models: List<LocalModelEntry> = emptyList(),
    val modelDownloadState: ModelDownloadState? = null
) {
    val isLiveMic: Boolean get() = sourceMode == AudioSourceMode.LIVE
}

/**
 * Core engine managing audio capture, file playback, Mel-spectrogram calculation,
 * parallel on-device audio classification, per-frame multimodal Jev decision evaluation,
 * equal-length session timeline segmentation, and automatic TV muting/unmuting.
 */
class AudioWorkbenchEngine(
    private val context: Context? = null
) : AutoCloseable {
    companion object {
        const val STAGE2_BORDERLINE_CEILING = 0.85f
        const val STAGE2_VETO_HOLD_TIMEOUT_MS = 1_500L
        const val STAGE2_VETO_COOLDOWN_MS = 3_000L
    }

    private var calculator = MelSpectrogramCalculator(numMelBands = 80)
    private val comparator = SpectrogramComparator()
    private val classifier = AudioClassifierEngine(context)
    private val decisionEngine = FrameDecisionEngine()
    private val segmentTracker = SessionSegmentTracker { decisionEngine.resetActiveSceneAngles() }
    private val stage2Verifier = LiteRtLmDecisionVerifier(context)
    private val fileDecoder = AudioFileDecoder()
    val tvControlManager = TvControlManager(context)

    private var loadedAudio: DecodedAudio? = null
    @Volatile
    private var filePlaybackOffset = 0
    @Volatile
    private var isFileSeeking = false
    @Volatile
    private var lastVisualDescriptor: VisualFrameDescriptor? = null
    @Volatile
    private var latestVideoThumbForSegment: Bitmap? = null
    @Volatile
    private var simulateMutedBreak = false
    @Volatile
    private var sessionElapsedMs = 0L
    @Volatile
    private var lastSessionWallMs = 0L
    @Volatile
    private var pipelinePreferences: PipelinePreferences = ModelRepository.loadPreferences(context)
    @Volatile
    private var pendingVetoMuteStartMs: Long? = null
    @Volatile
    private var pendingVetoApproved = false
    @Volatile
    private var pendingApprovedVerdict: Pair<Stage2Decision, Long>? = null
    @Volatile
    private var vetoCooldownUntilMs = 0L
    private var downloadJob: Job? = null
    private var mutedSimSilenceBuffer = FloatArray(0)
    private var isTvCurrentlyMuted = false
    private var isPaused = false

    private val _state = MutableStateFlow(
        WorkbenchState(
            activeClassifierModel = classifier.getActiveModelName(),
            tvControlMethod = tvControlManager.controlMethod.name,
            stage2Status = stage2Verifier.getStatusText()
        )
    )
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    init {
        val prefs = pipelinePreferences
        if (context != null) {
            val (audioModels, lmModels) = ModelRepository.scanModels(context)
            val preferredAudio = prefs.selectedAudioModelPath
                ?.let { File(it) }
                ?.takeIf { it.exists() && it.isFile }
            if (!prefs.useNeuralAudio) {
                classifier.setUseNeuralModel(false)
            } else if (preferredAudio != null && preferredAudio.absolutePath != classifier.getSelectedModelPath()) {
                classifier.selectModelFile(preferredAudio)
            }
            val initialAudioPath = classifier.getSelectedModelPath() ?: prefs.selectedAudioModelPath
            val initialStage2Path = prefs.selectedStage2ModelPath
                ?.takeIf { File(it).exists() }
                ?: lmModels.firstOrNull()?.absolutePath
            _state.update { current ->
                current.copy(
                    activeClassifierModel = classifier.getActiveModelName(),
                    useNeuralAudioClassifier = prefs.useNeuralAudio,
                    selectedAudioModelPath = initialAudioPath,
                    availableAudioModels = audioModels,
                    videoFusionEnabled = prefs.videoFusionEnabled,
                    stage2Mode = prefs.stage2Mode,
                    selectedStage2ModelPath = initialStage2Path,
                    availableStage2Models = lmModels,
                    stage2Status = if (prefs.stage2Mode == Stage2Mode.OFF) {
                        LiteRtLmDecisionVerifier.STATUS_DISABLED
                    } else {
                        current.stage2Status
                    }
                )
            }
            if (prefs.stage2Mode != Stage2Mode.OFF) {
                val lmFile = initialStage2Path?.let { File(it) }?.takeIf { it.exists() && it.isFile }
                stage2Verifier.configureAsync(enabled = true, modelFile = lmFile) { status, activePath ->
                    _state.update { current ->
                        current.copy(
                            stage2Status = status,
                            selectedStage2ModelPath = activePath ?: current.selectedStage2ModelPath
                        )
                    }
                }
            }
        } else {
            _state.update { current ->
                current.copy(
                    useNeuralAudioClassifier = prefs.useNeuralAudio,
                    videoFusionEnabled = prefs.videoFusionEnabled,
                    stage2Mode = prefs.stage2Mode
                )
            }
        }
    }

    private var engineJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start(sourceMode: AudioSourceMode = _state.value.sourceMode) {
        startInternal(sourceMode = sourceMode, resetTimeline = true)
    }

    private fun startInternal(sourceMode: AudioSourceMode, resetTimeline: Boolean) {
        if (sourceMode == AudioSourceMode.FILE && loadedAudio == null) return
        if (_state.value.isRunning && _state.value.sourceMode == sourceMode) return
        stopInternal(resetAnchorAndUnmute = resetTimeline)

        if (resetTimeline) {
            comparator.reset()
            classifier.resetBuffer()
            decisionEngine.reset()
            segmentTracker.reset()
            sessionElapsedMs = 0L
            lastSessionWallMs = 0L
            lastVisualDescriptor = null
            latestVideoThumbForSegment = null
            isTvCurrentlyMuted = false
            pendingVetoMuteStartMs = null
            pendingVetoApproved = false
            pendingApprovedVerdict = null
            vetoCooldownUntilMs = 0L
        } else {
            lastSessionWallMs = System.currentTimeMillis()
        }
        isPaused = false

        _state.update { current ->
            current.copy(
                isRunning = true,
                sourceMode = sourceMode,
                activeClassifierModel = classifier.getActiveModelName(),
                tvControlMethod = tvControlManager.controlMethod.name,
                isTvMuted = if (resetTimeline) false else isTvCurrentlyMuted,
                frameDecision = if (resetTimeline) FrameDecision() else current.frameDecision,
                sceneSimilarityThreshold = decisionEngine.getSceneSimilarityThreshold(),
                mediaThreshold = decisionEngine.getMediaThreshold(),
                isVideo = if (sourceMode == AudioSourceMode.FILE) (loadedAudio?.isVideo ?: current.isVideo) else false,
                videoFrameCount = if (resetTimeline) 0L else current.videoFrameCount,
                latestVisualDescriptor = if (resetTimeline) null else current.latestVisualDescriptor,
                latestVideoThumbnail = if (resetTimeline) null else current.latestVideoThumbnail,
                sessionSegments = if (resetTimeline) emptyList() else current.sessionSegments,
                stage2LastVerdict = if (resetTimeline) null else current.stage2LastVerdict,
                stage2LastLatencyMs = if (resetTimeline) null else current.stage2LastLatencyMs
            )
        }

        engineJob = scope.launch {
            when (sourceMode) {
                AudioSourceMode.LIVE -> runLiveMicLoop()
                AudioSourceMode.FILE -> runFileLoop()
            }
        }
    }

    /**
     * Pauses active audio capture or file playback when the host Activity is STOPPED.
     * Preserves state, playback position, and session timeline so it can resume.
     */
    fun pause() {
        if (!_state.value.isRunning || isPaused) return
        isPaused = true
        engineJob?.cancel()
        engineJob = null
        _state.update { it.copy(isRunning = false) }
    }

    /**
     * Resumes playback/capture when host Activity returns to STARTED/RESUMED.
     */
    fun resume() {
        if (!isPaused) return
        isPaused = false
        startInternal(_state.value.sourceMode, resetTimeline = false)
    }

    fun stop() {
        isPaused = false
        stopInternal(resetAnchorAndUnmute = true)
    }

    private fun stopInternal(resetAnchorAndUnmute: Boolean) {
        engineJob?.cancel()
        engineJob = null
        if (resetAnchorAndUnmute) {
            if (isTvCurrentlyMuted && tvControlManager.isAutoMuteEnabled) {
                scope.launch {
                    tvControlManager.sendUnmute()
                }
            }
            isTvCurrentlyMuted = false
            decisionEngine.onManualUnmute()
            val retainedSegments = segmentTracker.finalizeActiveSegment(sessionElapsedMs)
            _state.update {
                it.copy(
                    isRunning = false,
                    isEventTriggered = false,
                    isTvMuted = false,
                    sessionSegments = retainedSegments
                )
            }
        } else {
            _state.update { it.copy(isRunning = false, isEventTriggered = false) }
        }
    }

    override fun close() {
        downloadJob?.cancel()
        downloadJob = null
        stop()
        stage2Verifier.close()
        classifier.close()
    }

    fun setUseNeuralAudioClassifier(enabled: Boolean) {
        classifier.setUseNeuralModel(enabled)
        val activeName = classifier.getActiveModelName()
        val selectedPath = classifier.getSelectedModelPath() ?: pipelinePreferences.selectedAudioModelPath
        updatePreferences {
            it.copy(
                useNeuralAudio = enabled,
                selectedAudioModelPath = selectedPath
            )
        }
        _state.update {
            it.copy(
                useNeuralAudioClassifier = enabled,
                activeClassifierModel = activeName,
                selectedAudioModelPath = selectedPath
            )
        }
    }

    fun selectAudioModel(path: String?) {
        val file = path?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        if (file == null) {
            classifier.selectModelFile(null)
            classifier.setUseNeuralModel(false)
            val activeName = classifier.getActiveModelName()
            updatePreferences { it.copy(useNeuralAudio = false, selectedAudioModelPath = null) }
            _state.update {
                it.copy(
                    useNeuralAudioClassifier = false,
                    selectedAudioModelPath = null,
                    activeClassifierModel = activeName
                )
            }
        } else {
            classifier.setUseNeuralModel(true)
            classifier.selectModelFile(file)
            val activeName = classifier.getActiveModelName()
            updatePreferences {
                it.copy(
                    useNeuralAudio = true,
                    selectedAudioModelPath = file.absolutePath
                )
            }
            _state.update {
                it.copy(
                    useNeuralAudioClassifier = true,
                    selectedAudioModelPath = file.absolutePath,
                    activeClassifierModel = activeName
                )
            }
        }
    }

    fun setVideoFusionEnabled(enabled: Boolean) {
        updatePreferences { it.copy(videoFusionEnabled = enabled) }
        _state.update { it.copy(videoFusionEnabled = enabled) }
    }

    fun setStage2Mode(mode: Stage2Mode) {
        pendingVetoMuteStartMs = null
        pendingVetoApproved = false
        pendingApprovedVerdict = null
        vetoCooldownUntilMs = 0L
        val targetPath = pipelinePreferences.selectedStage2ModelPath
        updatePreferences { it.copy(stage2Mode = mode) }
        _state.update {
            it.copy(
                stage2Mode = mode,
                stage2Status = if (mode == Stage2Mode.OFF) {
                    LiteRtLmDecisionVerifier.STATUS_DISABLED
                } else {
                    it.stage2Status
                }
            )
        }
        if (context != null) {
            val file = targetPath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
            stage2Verifier.configureAsync(enabled = mode != Stage2Mode.OFF, modelFile = file) { status, activePath ->
                _state.update { current ->
                    current.copy(
                        stage2Status = status,
                        selectedStage2ModelPath = activePath ?: current.selectedStage2ModelPath
                    )
                }
            }
        }
    }

    fun selectStage2Model(path: String?) {
        val file = path?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        pendingVetoMuteStartMs = null
        pendingVetoApproved = false
        pendingApprovedVerdict = null
        if (file == null) {
            updatePreferences { it.copy(stage2Mode = Stage2Mode.OFF, selectedStage2ModelPath = null) }
            _state.update {
                it.copy(
                    stage2Mode = Stage2Mode.OFF,
                    selectedStage2ModelPath = null,
                    stage2Status = LiteRtLmDecisionVerifier.STATUS_DISABLED
                )
            }
            stage2Verifier.configureAsync(enabled = false, modelFile = null) { status, _ ->
                _state.update { current -> current.copy(stage2Status = status) }
            }
        } else {
            val nextMode = if (pipelinePreferences.stage2Mode == Stage2Mode.OFF) {
                Stage2Mode.AUDIT
            } else {
                pipelinePreferences.stage2Mode
            }
            updatePreferences { it.copy(stage2Mode = nextMode, selectedStage2ModelPath = file.absolutePath) }
            _state.update {
                it.copy(
                    stage2Mode = nextMode,
                    selectedStage2ModelPath = file.absolutePath
                )
            }
            stage2Verifier.configureAsync(enabled = true, modelFile = file) { status, activePath ->
                _state.update { current ->
                    current.copy(
                        stage2Status = status,
                        selectedStage2ModelPath = activePath ?: file.absolutePath
                    )
                }
            }
        }
    }

    fun refreshAvailableModels() {
        val appContext = context ?: return
        val (audioModels, lmModels) = ModelRepository.scanModels(appContext)
        _state.update {
            it.copy(
                availableAudioModels = audioModels,
                availableStage2Models = lmModels
            )
        }
    }

    fun importModelFromUri(uri: Uri, kind: ModelKind) {
        val appContext = context ?: return
        scope.launch {
            val result = ModelRepository.importModelFromUri(appContext, uri, kind)
            result.onSuccess { entry ->
                refreshAvailableModels()
                when (kind) {
                    ModelKind.AUDIO_TFLITE -> selectAudioModel(entry.absolutePath)
                    ModelKind.STAGE2_LITERTLM -> selectStage2Model(entry.absolutePath)
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        modelDownloadState = ModelDownloadState(
                            modelId = "import_${kind.name}",
                            fileName = kind.name,
                            isDownloading = false,
                            errorMessage = err.message ?: "Import failed"
                        )
                    )
                }
            }
        }
    }

    fun downloadCatalogModel(spec: CatalogModelSpec) {
        val appContext = context ?: return
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.update {
                it.copy(
                    modelDownloadState = ModelDownloadState(
                        modelId = spec.id,
                        fileName = spec.fileName,
                        bytesDownloaded = 0L,
                        totalBytes = spec.expectedSizeBytes,
                        isDownloading = true,
                        errorMessage = null
                    )
                )
            }
            val result = ModelRepository.downloadCatalogModel(appContext, spec) { read, total ->
                _state.update { current ->
                    current.copy(
                        modelDownloadState = ModelDownloadState(
                            modelId = spec.id,
                            fileName = spec.fileName,
                            bytesDownloaded = read,
                            totalBytes = total,
                            isDownloading = true,
                            errorMessage = null
                        )
                    )
                }
            }
            result.onSuccess { entry ->
                refreshAvailableModels()
                _state.update { it.copy(modelDownloadState = null) }
                when (spec.kind) {
                    ModelKind.AUDIO_TFLITE -> selectAudioModel(entry.absolutePath)
                    ModelKind.STAGE2_LITERTLM -> selectStage2Model(entry.absolutePath)
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        modelDownloadState = ModelDownloadState(
                            modelId = spec.id,
                            fileName = spec.fileName,
                            bytesDownloaded = 0L,
                            totalBytes = spec.expectedSizeBytes,
                            isDownloading = false,
                            errorMessage = err.message ?: "Download failed"
                        )
                    )
                }
            }
        }
    }

    fun cancelModelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _state.update { it.copy(modelDownloadState = null) }
    }

    fun deleteManagedModel(entry: LocalModelEntry) {
        val appContext = context ?: return
        if (!ModelRepository.deleteManagedModel(appContext, entry)) return
        refreshAvailableModels()
        when (entry.kind) {
            ModelKind.AUDIO_TFLITE -> if (_state.value.selectedAudioModelPath == entry.absolutePath) {
                val fallback = _state.value.availableAudioModels.firstOrNull { it.absolutePath != entry.absolutePath }
                selectAudioModel(fallback?.absolutePath)
            }
            ModelKind.STAGE2_LITERTLM -> if (_state.value.selectedStage2ModelPath == entry.absolutePath) {
                val fallback = _state.value.availableStage2Models.firstOrNull { it.absolutePath != entry.absolutePath }
                selectStage2Model(fallback?.absolutePath)
            }
        }
    }

    private fun updatePreferences(transform: (PipelinePreferences) -> PipelinePreferences) {
        val updated = transform(pipelinePreferences)
        pipelinePreferences = updated
        ModelRepository.savePreferences(context, updated)
    }

    fun setThreshold(newThreshold: Float) {
        comparator.threshold = newThreshold
        _state.update { it.copy(threshold = newThreshold) }
    }

    fun setInterval(newIntervalMs: Long) {
        _state.update { it.copy(intervalMs = newIntervalMs) }
    }

    fun setMelBands(bands: Int) {
        val clamped = bands.coerceIn(20, 128)
        calculator = MelSpectrogramCalculator(numMelBands = clamped)
        _state.update { it.copy(numMelBands = clamped) }
    }

    fun setSceneSimilarityThreshold(value: Float) {
        decisionEngine.setSceneSimilarityThreshold(value)
        val clamped = decisionEngine.getSceneSimilarityThreshold()
        _state.update { current -> current.copy(sceneSimilarityThreshold = clamped) }
    }

    fun setMediaThreshold(value: Float) {
        decisionEngine.setMediaThreshold(value)
        val clamped = decisionEngine.getMediaThreshold()
        _state.update { current -> current.copy(mediaThreshold = clamped) }
    }

    fun setCameraEnabled(enabled: Boolean) {
        if (!enabled) {
            lastVisualDescriptor = null
            latestVideoThumbForSegment = null
        }
        _state.update { current ->
            current.copy(
                isCameraEnabled = enabled,
                latestVisualDescriptor = if (enabled) current.latestVisualDescriptor else null,
                latestVideoThumbnail = if (enabled) current.latestVideoThumbnail else null
            )
        }
    }

    fun setSimulateMutedBreak(enabled: Boolean) {
        simulateMutedBreak = enabled
        _state.update { it.copy(simulateMutedBreak = enabled) }
    }

    fun setAutoMuteEnabled(enabled: Boolean) {
        tvControlManager.isAutoMuteEnabled = enabled
    }

    fun manualUnmute() {
        decisionEngine.onManualUnmute()
        isTvCurrentlyMuted = false
        _state.update { it.copy(isTvMuted = false) }
        if (tvControlManager.isAutoMuteEnabled) {
            scope.launch {
                tvControlManager.sendUnmute()
            }
        }
    }

    fun getLastVisualDescriptor(): VisualFrameDescriptor? = lastVisualDescriptor

    fun onVideoFrameSampled(snapshot: VideoFrameSnapshot) {
        lastVisualDescriptor = snapshot.descriptor
        if (snapshot.thumbnail != null) {
            latestVideoThumbForSegment = snapshot.thumbnail
        }
        _state.update { current ->
            current.copy(
                latestVisualDescriptor = snapshot.descriptor,
                latestVideoThumbnail = snapshot.thumbnail ?: current.latestVideoThumbnail,
                videoFrameCount = current.videoFrameCount + 1L
            )
        }
    }

    fun prepareForExternalFileIntent() {
        stop()
        _state.update { current -> current.copy(sourceMode = AudioSourceMode.FILE) }
    }

    suspend fun loadAudioFile(appContext: Context, uri: Uri): Result<DecodedAudio> {
        return try {
            val decoded = fileDecoder.decode(appContext, uri)
            stop()
            loadedAudio = decoded
            filePlaybackOffset = 0
            isFileSeeking = false

            _state.update { current ->
                current.copy(
                    loadedFileName = decoded.fileName,
                    fileDurationMs = decoded.durationMs,
                    filePositionMs = 0L,
                    fileProgress = 0f,
                    sourceMode = AudioSourceMode.FILE,
                    isVideo = decoded.isVideo,
                    mediaUri = uri
                )
            }

            // Auto-start playback of loaded file
            start(AudioSourceMode.FILE)
            Result.success(decoded)
        } catch (e: Exception) {
            e.printStackTrace()
            if (loadedAudio == null) {
                _state.update { current -> current.copy(sourceMode = AudioSourceMode.LIVE) }
            }
            Result.failure(e)
        }
    }

    fun seekFile(progress: Float) {
        val audio = loadedAudio ?: return
        val clampedProgress = progress.coerceIn(0f, 1f)
        filePlaybackOffset = (audio.samples.size * clampedProgress).toInt()
        isFileSeeking = true
        classifier.resetBuffer()
        val posMs = (audio.durationMs * clampedProgress).toLong()
        _state.update { current ->
            current.copy(
                fileProgress = clampedProgress,
                filePositionMs = posMs
            )
        }
    }

    private fun formatTimestamp(dateFormat: SimpleDateFormat): String {
        return synchronized(dateFormat) {
            dateFormat.format(Date())
        }
    }

    private fun recordEvent(logs: ArrayDeque<WorkbenchEvent>, event: WorkbenchEvent) {
        synchronized(logs) {
            if (logs.size >= 50) logs.removeFirst()
            logs.addLast(event)
        }
    }

    private fun snapshotLogs(logs: ArrayDeque<WorkbenchEvent>): List<WorkbenchEvent> {
        return synchronized(logs) {
            logs.toList().reversed()
        }
    }

    private fun downsampleWaveform(source: FloatArray, target: FloatArray) {
        val step = (source.size / target.size).coerceAtLeast(1)
        for (i in target.indices) {
            val idx = i * step
            target[i] = if (idx < source.size) source[idx] else 0f
        }
    }

    private fun evaluateAutoMute(
        recommendedAction: TvActionDecision,
        logs: ArrayDeque<WorkbenchEvent>,
        dateFormat: SimpleDateFormat,
        distance: Float,
        threshold: Float
    ) {
        if (!tvControlManager.isAutoMuteEnabled) return

        when (recommendedAction) {
            TvActionDecision.MUTE -> {
                if (!isTvCurrentlyMuted) {
                    isTvCurrentlyMuted = true
                    scope.launch {
                        val result = tvControlManager.sendMute()
                        recordEvent(
                            logs,
                            WorkbenchEvent(
                                timestamp = formatTimestamp(dateFormat),
                                distance = distance,
                                threshold = threshold,
                                description = "⚡ AUTO-MUTE TRIGGERED (${result.method.name}: ${result.message})"
                            )
                        )
                    }
                }
            }
            TvActionDecision.UNMUTE -> {
                if (isTvCurrentlyMuted) {
                    isTvCurrentlyMuted = false
                    scope.launch {
                        val result = tvControlManager.sendUnmute()
                        recordEvent(
                            logs,
                            WorkbenchEvent(
                                timestamp = formatTimestamp(dateFormat),
                                distance = distance,
                                threshold = threshold,
                                description = "🔊 AUTO-UNMUTE TRIGGERED (${result.method.name}: ${result.message})"
                            )
                        )
                    }
                }
            }
            TvActionDecision.HOLD -> Unit
        }
    }

    private suspend fun processAudioFrame(
        samples: FloatArray,
        history: ArrayDeque<FloatArray>,
        logs: ArrayDeque<WorkbenchEvent>,
        dateFormat: SimpleDateFormat,
        waveformView: FloatArray,
        significantDescription: String?,
        filePositionMs: Long? = null,
        fileProgress: Float? = null
    ) {
        val nowMs = System.currentTimeMillis()
        val prevWall = lastSessionWallMs
        if (prevWall > 0L) {
            sessionElapsedMs += (nowMs - prevWall).coerceIn(0L, 1_000L)
        }
        lastSessionWallMs = nowMs
        val elapsedMs = sessionElapsedMs

        val analysisInput = if (simulateMutedBreak && isTvCurrentlyMuted) {
            if (mutedSimSilenceBuffer.size != samples.size) {
                mutedSimSilenceBuffer = FloatArray(samples.size)
            }
            mutedSimSilenceBuffer
        } else {
            samples
        }

        val melEnergiesDeferred = scope.async { calculator.computeMelEnergies(analysisInput) }
        val classificationDeferred = scope.async { classifier.classify(analysisInput) }

        val melEnergies = melEnergiesDeferred.await()
        val classificationScores = classificationDeferred.await()

        if (history.size >= 30) history.removeFirst()
        history.addLast(melEnergies)

        val compResult = comparator.compare(melEnergies)
        if (compResult.isSignificant) {
            val eventDescription = significantDescription
                ?: if (filePositionMs != null) {
                    "File Transition Detected (t=${filePositionMs / 1000}s)"
                } else {
                    "Acoustic Shift Exceeded Threshold"
                }
            recordEvent(
                logs,
                WorkbenchEvent(
                    timestamp = formatTimestamp(dateFormat),
                    distance = compResult.distance,
                    threshold = compResult.threshold,
                    description = eventDescription
                )
            )
        }

        val prefs = pipelinePreferences
        val visualSnapshot = if (prefs.videoFusionEnabled) lastVisualDescriptor else null
        val mutedBeforeEval = isTvCurrentlyMuted
        val decision = decisionEngine.evaluateFrame(
            melEnergies = melEnergies,
            classifierTags = classificationScores,
            shiftDistance = compResult.distance,
            timestampMs = nowMs,
            visualDescriptor = visualSnapshot,
            isTvMuted = mutedBeforeEval
        )

        var effectiveAction = decision.recommendedAction
        if (prefs.stage2Mode == Stage2Mode.VETO &&
            stage2Verifier.isModelReady() &&
            effectiveAction == TvActionDecision.MUTE &&
            !isTvCurrentlyMuted &&
            decision.commercialScore < STAGE2_BORDERLINE_CEILING
        ) {
            if (nowMs < vetoCooldownUntilMs) {
                effectiveAction = TvActionDecision.HOLD
            } else if (pendingVetoApproved) {
                pendingVetoApproved = false
                pendingVetoMuteStartMs = null
            } else {
                val pendingStart = pendingVetoMuteStartMs
                if (pendingStart != null) {
                    if (nowMs - pendingStart >= STAGE2_VETO_HOLD_TIMEOUT_MS) {
                        pendingVetoMuteStartMs = null
                        pendingVetoApproved = false
                    } else {
                        effectiveAction = TvActionDecision.HOLD
                    }
                } else {
                    pendingVetoMuteStartMs = nowMs
                    pendingVetoApproved = false
                    effectiveAction = TvActionDecision.HOLD
                    val tags = classificationScores.filter { it.score >= 0.10f }.map { it.label }
                    val match = visualSnapshot?.let { decisionEngine.programCache.scoreAgainstProgram(it) }
                    stage2Verifier.verifyTransitionAsync(
                        features = Stage2TransitionFeatures(
                            mediaScore = decision.mediaScore,
                            commercialScore = decision.commercialScore,
                            visualChangeScore = decision.visualChangeScore,
                            prototypeSimilarity = match?.prototypeSimilarity ?: decision.bestAngleSimilarity,
                            hasStableBug = match?.hasStableBug ?: false,
                            bugSimilarity = match?.bugSimilarity ?: 0f,
                            cutsPerMin = maxOf(decision.cutCadencePerMin, decision.visualCutCadencePerMin),
                            topTags = tags,
                            isTvMuted = isTvCurrentlyMuted
                        ),
                        onErrorOrEmpty = { latencyMs ->
                            pendingVetoMuteStartMs = null
                            pendingVetoApproved = true
                            _state.update { it.copy(stage2LastLatencyMs = latencyMs) }
                        }
                    ) { verdict, latencyMs ->
                        _state.update {
                            it.copy(
                                stage2LastVerdict = verdict.name,
                                stage2LastLatencyMs = latencyMs
                            )
                        }
                        if (verdict == Stage2Decision.COMMERCIAL) {
                            pendingVetoApproved = true
                            pendingApprovedVerdict = verdict to latencyMs
                            recordEvent(
                                logs,
                                WorkbenchEvent(
                                    timestamp = formatTimestamp(dateFormat),
                                    distance = compResult.distance,
                                    threshold = compResult.threshold,
                                    description = "Stage 2 Confirmed Borderline Commercial (${latencyMs}ms)"
                                )
                            )
                        } else {
                            pendingVetoMuteStartMs = null
                            pendingVetoApproved = false
                            pendingApprovedVerdict = null
                            vetoCooldownUntilMs = System.currentTimeMillis() + STAGE2_VETO_COOLDOWN_MS
                            recordEvent(
                                logs,
                                WorkbenchEvent(
                                    timestamp = formatTimestamp(dateFormat),
                                    distance = compResult.distance,
                                    threshold = compResult.threshold,
                                    description = "Stage 2 Vetoed Borderline Mute (${verdict.name}, ${latencyMs}ms)"
                                )
                            )
                        }
                    }
                }
            }
        } else if (effectiveAction != TvActionDecision.MUTE && pendingVetoMuteStartMs != null) {
            pendingVetoMuteStartMs = null
            pendingVetoApproved = false
        }

        val effectiveDecision = if (effectiveAction == decision.recommendedAction) {
            decision
        } else {
            decision.copy(recommendedAction = effectiveAction)
        }

        evaluateAutoMute(
            recommendedAction = effectiveDecision.recommendedAction,
            logs = logs,
            dateFormat = dateFormat,
            distance = compResult.distance,
            threshold = compResult.threshold
        )

        var (updatedSegments, openedSegment) = segmentTracker.onFrame(
            timestampMs = elapsedMs,
            decision = effectiveDecision,
            melEnergies = melEnergies,
            thumbnail = latestVideoThumbForSegment
        )

        if (openedSegment != null) {
            val preVerdict = pendingApprovedVerdict
            if (preVerdict != null) {
                pendingApprovedVerdict = null
                updatedSegments = segmentTracker.attachStage2Verdict(openedSegment.id, preVerdict.first.name)
                _state.update {
                    it.copy(
                        sessionSegments = updatedSegments,
                        stage2LastVerdict = preVerdict.first.name,
                        stage2LastLatencyMs = preVerdict.second
                    )
                }
            } else if (prefs.stage2Mode != Stage2Mode.OFF && stage2Verifier.isModelReady()) {
                val tags = classificationScores.filter { it.score >= 0.10f }.map { it.label }
                val match = visualSnapshot?.let { decisionEngine.programCache.scoreAgainstProgram(it) }
                stage2Verifier.verifyTransitionAsync(
                    Stage2TransitionFeatures(
                        mediaScore = effectiveDecision.mediaScore,
                        commercialScore = effectiveDecision.commercialScore,
                        visualChangeScore = effectiveDecision.visualChangeScore,
                        prototypeSimilarity = match?.prototypeSimilarity ?: effectiveDecision.bestAngleSimilarity,
                        hasStableBug = match?.hasStableBug ?: false,
                        bugSimilarity = match?.bugSimilarity ?: 0f,
                        cutsPerMin = maxOf(effectiveDecision.cutCadencePerMin, effectiveDecision.visualCutCadencePerMin),
                        topTags = tags,
                        isTvMuted = mutedBeforeEval
                    )
                ) { verdict, latencyMs ->
                    val withVerdict = segmentTracker.attachStage2Verdict(openedSegment.id, verdict.name)
                    _state.update {
                        it.copy(
                            sessionSegments = withVerdict,
                            stage2LastVerdict = verdict.name,
                            stage2LastLatencyMs = latencyMs
                        )
                    }
                }
            }
        }

        downsampleWaveform(analysisInput, waveformView)

        _state.update { current ->
            current.copy(
                waveform = waveformView.clone(),
                spectrogramHistory = history.toList(),
                currentDistance = compResult.distance,
                isEventTriggered = compResult.isSignificant,
                isTvMuted = isTvCurrentlyMuted,
                tvControlMethod = tvControlManager.controlMethod.name,
                eventLogs = snapshotLogs(logs),
                classifierScores = classificationScores,
                frameDecision = effectiveDecision,
                sessionSegments = updatedSegments,
                filePositionMs = filePositionMs ?: current.filePositionMs,
                fileProgress = fileProgress ?: current.fileProgress
            )
        }
    }

    private suspend fun runFileLoop() {
        val audio = loadedAudio ?: return
        val chunkSize = 512
        val sampleRate = 16000
        val history = ArrayDeque<FloatArray>(30)
        val logs = ArrayDeque<WorkbenchEvent>(50)
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        var audioTrack: AudioTrack? = null
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(8192)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize * 4) // Quadruple buffer to prevent playback underruns
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.setVolume(1.0f)
            audioTrack.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. Dedicated Audio Streaming Coroutine (Feeder - URGENT_AUDIO priority without buffer underruns)
        val feederJob = scope.launch(Dispatchers.IO) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (ignored: Exception) {}

            val streamBufferChunk = 2048 // 128ms chunks prevent audio jitter
            val shortBuffer = ShortArray(streamBufferChunk)

            while (isActive && audioTrack != null) {
                if (isFileSeeking) {
                    try {
                        audioTrack.pause()
                        audioTrack.flush()
                        audioTrack.play()
                    } catch (ignored: Exception) {}
                    isFileSeeking = false
                }

                val total = audio.samples.size
                var offset = filePlaybackOffset
                if (offset >= total) {
                    offset = 0
                    filePlaybackOffset = 0
                }

                val remaining = total - offset
                val count = streamBufferChunk.coerceAtMost(remaining)
                if (count > 0) {
                    for (i in 0 until count) {
                        val sample = audio.samples[offset + i]
                        shortBuffer[i] = (sample * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    }
                    val written = audioTrack.write(shortBuffer, 0, count, AudioTrack.WRITE_BLOCKING)
                    if (written > 0) {
                        filePlaybackOffset += written
                    }
                } else {
                    delay(5)
                }
            }
        }

        val analysisSamples = FloatArray(chunkSize)
        val waveformView = FloatArray(256)

        // 2. Spectrogram & Classification Inspector Loop
        try {
            while (scope.isActive) {
                val interval = _state.value.intervalMs
                val totalSamples = audio.samples.size
                val currentOffset = filePlaybackOffset.coerceIn(0, totalSamples.coerceAtLeast(1) - 1)

                val progress = currentOffset.toFloat() / totalSamples.coerceAtLeast(1)
                val currentPosMs = (audio.durationMs * progress).toLong()

                // Extract 512-sample analysis window from current audio stream playback position
                val available = (totalSamples - currentOffset).coerceAtLeast(0)
                val analysisCount = chunkSize.coerceAtMost(available)
                if (analysisCount > 0) {
                    System.arraycopy(audio.samples, currentOffset, analysisSamples, 0, analysisCount)
                }
                for (i in analysisCount until chunkSize) {
                    analysisSamples[i] = 0f
                }

                processAudioFrame(
                    samples = analysisSamples,
                    history = history,
                    logs = logs,
                    dateFormat = dateFormat,
                    waveformView = waveformView,
                    significantDescription = null,
                    filePositionMs = currentPosMs,
                    fileProgress = progress
                )

                delay(interval)
            }
        } finally {
            feederJob.cancel()
            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (ignored: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runLiveMicLoop() {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val logs = ArrayDeque<WorkbenchEvent>(50)
        val history = ArrayDeque<FloatArray>(30)
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        var audioRecord: AudioRecord? = null
        val audioSources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.DEFAULT
        )

        for (source in audioSources) {
            try {
                val record = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    break
                } else {
                    record.release()
                }
            } catch (ignored: Exception) {}
        }

        if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            recordEvent(
                logs,
                WorkbenchEvent(
                    timestamp = formatTimestamp(dateFormat),
                    distance = 0f,
                    threshold = _state.value.threshold,
                    description = "[MIC ERROR] Microphone not initialized. Verify RECORD_AUDIO permission in Android settings."
                )
            )
            _state.update { current ->
                current.copy(
                    eventLogs = snapshotLogs(logs),
                    spectrogramHistory = emptyList(),
                    currentDistance = 0f
                )
            }
            return
        }

        try {
            audioRecord.startRecording()
            val chunkSize = 512
            val shortBuffer = ShortArray(chunkSize)
            val floatSamples = FloatArray(chunkSize)
            val waveformView = FloatArray(256)
            var lastProcessTimeMs = 0L

            while (scope.isActive) {
                // AudioRecord.read keeps HAL input drained at 32ms; throttle UI/decision ticks to intervalMs.
                val readCount = audioRecord.read(shortBuffer, 0, chunkSize)

                if (readCount > 0) {
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastProcessTimeMs >= _state.value.intervalMs) {
                        lastProcessTimeMs = nowMs
                        for (i in 0 until readCount) {
                            floatSamples[i] = (shortBuffer[i] / 32768.0f).coerceIn(-1f, 1f)
                        }
                        for (i in readCount until chunkSize) {
                            floatSamples[i] = 0f
                        }

                        processAudioFrame(
                            samples = floatSamples,
                            history = history,
                            logs = logs,
                            dateFormat = dateFormat,
                            waveformView = waveformView,
                            significantDescription = "Live Acoustic Shift Exceeded Threshold"
                        )
                    }
                } else if (readCount < 0) {
                    delay(50)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            recordEvent(
                logs,
                WorkbenchEvent(
                    timestamp = formatTimestamp(dateFormat),
                    distance = 0f,
                    threshold = _state.value.threshold,
                    description = "[MIC EXCEPTION] ${e.message ?: "AudioRecord error"}"
                )
            )
            _state.update { it.copy(eventLogs = snapshotLogs(logs)) }
        } finally {
            try {
                audioRecord.stop()
                audioRecord.release()
            } catch (ignored: Exception) {}
        }
    }
}
