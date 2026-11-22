// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.audio

import android.annotation.SuppressLint
import android.content.Context
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
import com.example.commercialkiller.data.decision.TvActionDecision
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class AudioSourceMode {
    SYNTH,
    MIC,
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
    val sourceMode: AudioSourceMode = AudioSourceMode.SYNTH,
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
    val numMelBands: Int = 80,
    val frameDecision: FrameDecision = FrameDecision(),
    val sceneSimilarityThreshold: Float = FrameDecisionEngine.DEFAULT_SCENE_SIMILARITY_THRESHOLD,
    val mediaThreshold: Float = FrameDecisionEngine.DEFAULT_MEDIA_THRESHOLD
) {
    val isLiveMic: Boolean get() = sourceMode == AudioSourceMode.MIC
}

/**
 * Core engine managing audio capture, file playback, Mel-spectrogram calculation,
 * parallel on-device audio classification, per-frame decision evaluation, and automatic TV muting/unmuting.
 */
class AudioWorkbenchEngine(
    private val context: Context? = null
) {
    private var calculator = MelSpectrogramCalculator(numMelBands = 80)
    private val comparator = SpectrogramComparator()
    private val classifier = AudioClassifierEngine(context)
    private val decisionEngine = FrameDecisionEngine()
    private val fileDecoder = AudioFileDecoder()
    val tvControlManager = TvControlManager(context)

    private var loadedAudio: DecodedAudio? = null
    @Volatile
    private var filePlaybackOffset = 0
    @Volatile
    private var isFileSeeking = false
    private var isTvCurrentlyMuted = false
    private var isPaused = false

    private val _state = MutableStateFlow(
        WorkbenchState(
            activeClassifierModel = classifier.getActiveModelName(),
            tvControlMethod = tvControlManager.controlMethod.name
        )
    )
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    private var engineJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start(sourceMode: AudioSourceMode = _state.value.sourceMode) {
        if (_state.value.isRunning && _state.value.sourceMode == sourceMode) return
        stop()

        comparator.reset()
        decisionEngine.reset()
        isTvCurrentlyMuted = false
        isPaused = false

        _state.update { current ->
            current.copy(
                isRunning = true,
                sourceMode = sourceMode,
                activeClassifierModel = classifier.getActiveModelName(),
                tvControlMethod = tvControlManager.controlMethod.name,
                isTvMuted = false,
                frameDecision = FrameDecision(),
                sceneSimilarityThreshold = decisionEngine.getSceneSimilarityThreshold(),
                mediaThreshold = decisionEngine.getMediaThreshold(),
                isVideo = if (sourceMode == AudioSourceMode.FILE) (loadedAudio?.isVideo ?: current.isVideo) else false
            )
        }

        engineJob = scope.launch {
            when (sourceMode) {
                AudioSourceMode.MIC -> runLiveMicLoop()
                AudioSourceMode.FILE -> runFileLoop()
                AudioSourceMode.SYNTH -> runSimulatedLoop()
            }
        }
    }

    fun start(useLiveMic: Boolean) {
        start(if (useLiveMic) AudioSourceMode.MIC else AudioSourceMode.SYNTH)
    }

    /**
     * Pauses active audio synthesis, playback, or mic processing when the host Activity is STOPPED.
     * Preserves state and playback position so it can resume.
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
        start(_state.value.sourceMode)
    }

    fun stop() {
        isPaused = false
        engineJob?.cancel()
        engineJob = null
        if (isTvCurrentlyMuted && tvControlManager.isAutoMuteEnabled) {
            scope.launch {
                tvControlManager.sendUnmute()
            }
        }
        isTvCurrentlyMuted = false
        _state.update { it.copy(isRunning = false, isEventTriggered = false, isTvMuted = false) }
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
            Result.failure(e)
        }
    }

    fun seekFile(progress: Float) {
        val audio = loadedAudio ?: return
        val clampedProgress = progress.coerceIn(0f, 1f)
        filePlaybackOffset = (audio.samples.size * clampedProgress).toInt()
        isFileSeeking = true
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
        val melEnergiesDeferred = scope.async { calculator.computeMelEnergies(samples) }
        val classificationDeferred = scope.async { classifier.classify(samples) }

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

        val decision = decisionEngine.evaluateFrame(
            melEnergies = melEnergies,
            classifierTags = classificationScores,
            shiftDistance = compResult.distance,
            timestampMs = System.currentTimeMillis()
        )

        evaluateAutoMute(
            recommendedAction = decision.recommendedAction,
            logs = logs,
            dateFormat = dateFormat,
            distance = compResult.distance,
            threshold = compResult.threshold
        )

        downsampleWaveform(samples, waveformView)

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
                frameDecision = decision,
                filePositionMs = filePositionMs ?: current.filePositionMs,
                fileProgress = fileProgress ?: current.fileProgress
            )
        }
    }

    /**
     * Broadcast simulation cycle:
     * - Multi-formant speech simulation (F1=500Hz, F2=1500Hz, F3=2500Hz)
     * - 200 ms silence gap (commercial pod boundary)
     * - High-compression commercial segment (loudness increase + multi-harmonic jingle bed)
     */
    private suspend fun runSimulatedLoop() {
        var tick = 0
        val sampleRate = 16000
        val chunkSize = 512
        val history = ArrayDeque<FloatArray>(30)
        val logs = ArrayDeque<WorkbenchEvent>(50)
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val waveformView = FloatArray(256)
        val samples = FloatArray(chunkSize)

        while (scope.isActive) {
            val interval = _state.value.intervalMs
            tick++

            // 60-tick cycle (6.0s at 100ms interval)
            val phase = tick % 60

            val isSilenceGap = phase in 35..36
            val isCommercialSegment = phase in 37..59

            if (isSilenceGap) {
                // Silence dip / black frame transition
                for (i in 0 until chunkSize) {
                    samples[i] = (Random.nextFloat() - 0.5f) * 0.002f
                }
            } else if (isCommercialSegment) {
                // Commercial break: Loudness boost, multi-harmonic jingle bed + high-frequency percussion
                for (i in 0 until chunkSize) {
                    val sampleIdx = tick * chunkSize + i
                    val t = sampleIdx.toDouble() / sampleRate
                    val chord = sin(2.0 * PI * 440.0 * t) * 0.18 +
                            sin(2.0 * PI * 554.37 * t) * 0.14 +
                            sin(2.0 * PI * 659.25 * t) * 0.12 +
                            sin(2.0 * PI * 5200.0 * t) * 0.38
                    val beat = sin(2.0 * PI * 4.0 * t) // 4 Hz rhythm pulse
                    val percussion = (Random.nextFloat() - 0.5f) * (if (beat > 0.7) 0.50f else 0.42f)
                    samples[i] = ((chord * 0.7 + percussion) * 0.85).toFloat().coerceIn(-1.0f, 1.0f)
                }
            } else {
                // Program segment: Multi-formant speech/dialogue simulation
                for (i in 0 until chunkSize) {
                    val sampleIdx = tick * chunkSize + i
                    val t = sampleIdx.toDouble() / sampleRate
                    val speechCadence = (sin(2.0 * PI * 2.5 * t) + 1.0) * 0.5
                    val pitch = sin(2.0 * PI * 130.0 * t)
                    val f1 = sin(2.0 * PI * 500.0 * t) * 0.4
                    val f2 = sin(2.0 * PI * 1500.0 * t) * 0.25
                    val f3 = sin(2.0 * PI * 2500.0 * t) * 0.15
                    val voice = (pitch * 0.2 + f1 + f2 + f3) * speechCadence
                    val ambient = (Random.nextFloat() - 0.5f) * 0.04f
                    samples[i] = ((voice * 0.6 + ambient) * 0.45).toFloat().coerceIn(-1.0f, 1.0f)
                }
            }

            val significantDesc = when {
                isSilenceGap -> "Broadcast Silence / Black Frame Gap"
                isCommercialSegment -> "Commercial Break Transition (Acoustic Shift)"
                else -> "Program Return Transition"
            }

            processAudioFrame(
                samples = samples,
                history = history,
                logs = logs,
                dateFormat = dateFormat,
                waveformView = waveformView,
                significantDescription = significantDesc
            )

            delay(interval)
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
