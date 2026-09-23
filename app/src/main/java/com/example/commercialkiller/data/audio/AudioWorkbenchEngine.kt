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
import com.example.commercialkiller.data.action.TvControlManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val fileProgress: Float = 0f
) {
    val isLiveMic: Boolean get() = sourceMode == AudioSourceMode.MIC
}

/**
 * Core engine managing audio capture, file playback, Mel-spectrogram calculation,
 * parallel on-device audio classification, and automatic TV muting/unmuting.
 */
class AudioWorkbenchEngine(
    private val context: Context? = null
) {
    private val calculator = MelSpectrogramCalculator()
    private val comparator = SpectrogramComparator()
    private val classifier = AudioClassifierEngine(context)
    private val fileDecoder = AudioFileDecoder()
    val tvControlManager = TvControlManager(context)

    private var loadedAudio: DecodedAudio? = null
    @Volatile
    private var filePlaybackOffset = 0
    @Volatile
    private var isFileSeeking = false
    private var isTvCurrentlyMuted = false
    private var programConsecutiveFrames = 0

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
        isTvCurrentlyMuted = false
        programConsecutiveFrames = 0

        _state.value = _state.value.copy(
            isRunning = true,
            sourceMode = sourceMode,
            activeClassifierModel = classifier.getActiveModelName(),
            tvControlMethod = tvControlManager.controlMethod.name,
            isTvMuted = false
        )

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

    fun stop() {
        engineJob?.cancel()
        engineJob = null
        if (isTvCurrentlyMuted && tvControlManager.isAutoMuteEnabled) {
            scope.launch {
                tvControlManager.sendUnmute()
            }
        }
        isTvCurrentlyMuted = false
        _state.value = _state.value.copy(isRunning = false, isEventTriggered = false, isTvMuted = false)
    }

    fun setThreshold(newThreshold: Float) {
        comparator.threshold = newThreshold
        _state.value = _state.value.copy(threshold = newThreshold)
    }

    fun setInterval(newIntervalMs: Long) {
        _state.value = _state.value.copy(intervalMs = newIntervalMs)
    }

    suspend fun loadAudioFile(appContext: Context, uri: Uri): Result<DecodedAudio> {
        return try {
            val decoded = fileDecoder.decode(appContext, uri)
            loadedAudio = decoded
            filePlaybackOffset = 0
            isFileSeeking = false

            _state.value = _state.value.copy(
                loadedFileName = decoded.fileName,
                fileDurationMs = decoded.durationMs,
                filePositionMs = 0L,
                fileProgress = 0f,
                sourceMode = AudioSourceMode.FILE
            )

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
        _state.value = _state.value.copy(
            fileProgress = clampedProgress,
            filePositionMs = posMs
        )
    }

    private fun handleAutoMuting(
        isCommercialActive: Boolean,
        logs: ArrayDeque<WorkbenchEvent>,
        dateFormat: SimpleDateFormat,
        distance: Float,
        threshold: Float
    ) {
        if (!tvControlManager.isAutoMuteEnabled) return

        if (isCommercialActive) {
            programConsecutiveFrames = 0
            if (!isTvCurrentlyMuted) {
                isTvCurrentlyMuted = true
                scope.launch {
                    val result = tvControlManager.sendMute()
                    val event = WorkbenchEvent(
                        timestamp = dateFormat.format(Date()),
                        distance = distance,
                        threshold = threshold,
                        description = "⚡ AUTO-MUTE TRIGGERED (${result.method.name}: ${result.message})"
                    )
                    if (logs.size >= 50) logs.removeFirst()
                    logs.addLast(event)
                }
            }
        } else {
            // Require 10 consecutive non-commercial frames (~1 sec) before unmuting
            if (isTvCurrentlyMuted) {
                programConsecutiveFrames++
                if (programConsecutiveFrames >= 10) {
                    isTvCurrentlyMuted = false
                    programConsecutiveFrames = 0
                    scope.launch {
                        val result = tvControlManager.sendUnmute()
                        val event = WorkbenchEvent(
                            timestamp = dateFormat.format(Date()),
                            distance = distance,
                            threshold = threshold,
                            description = "🔊 AUTO-UNMUTE TRIGGERED (${result.method.name}: ${result.message})"
                        )
                        if (logs.size >= 50) logs.removeFirst()
                        logs.addLast(event)
                    }
                }
            }
        }
    }

    /**
     * Realistic acoustic broadcast simulation:
     * - Multi-formant speech simulation (F1=500Hz, F2=1500Hz, F3=2500Hz) with natural cadence
     * - 200 ms silence gap (commercial pod boundary)
     * - High-compression commercial segment (loudness jump + multi-harmonic music/jingle bed)
     */
    private suspend fun runSimulatedLoop() {
        var tick = 0
        val sampleRate = 16000
        val chunkSize = 512
        val history = ArrayDeque<FloatArray>(30)
        val logs = ArrayDeque<WorkbenchEvent>(50)
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

        while (scope.isActive) {
            val interval = _state.value.intervalMs
            tick++

            // 60-tick cycle (6.0s at 100ms interval)
            val phase = tick % 60
            val samples = FloatArray(chunkSize)

            val isSilenceGap = phase in 35..36
            val isCommercialSegment = phase in 37..59

            if (isSilenceGap) {
                // Silence dip / black frame transition
                for (i in 0 until chunkSize) {
                    samples[i] = (Random.nextFloat() - 0.5f) * 0.002f
                }
            } else if (isCommercialSegment) {
                // Commercial break: Loudness boost (+8 dB), chordal musical bed (440, 554, 659 Hz) + rhythm
                for (i in 0 until chunkSize) {
                    val sampleIdx = tick * chunkSize + i
                    val t = sampleIdx.toDouble() / sampleRate
                    val chord = sin(2.0 * PI * 440.0 * t) * 0.35 +
                            sin(2.0 * PI * 554.37 * t) * 0.25 +
                            sin(2.0 * PI * 659.25 * t) * 0.20
                    val beat = sin(2.0 * PI * 4.0 * t) // 4 Hz rhythm pulse
                    val percussion = (Random.nextFloat() - 0.5f) * (if (beat > 0.7) 0.35f else 0.08f)
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

            // Parallel Execution: 1. Mel-Spectrogram & 2. Audio Classifier
            val melEnergiesDeferred = scope.async { calculator.computeMelEnergies(samples) }
            val classificationDeferred = scope.async { classifier.classify(samples) }

            val melEnergies = melEnergiesDeferred.await()
            val classificationScores = classificationDeferred.await()

            // Update waterfall history
            if (history.size >= 30) history.removeFirst()
            history.addLast(melEnergies)

            // Compare with previous frame
            val compResult = comparator.compare(melEnergies)

            if (compResult.isSignificant) {
                val event = WorkbenchEvent(
                    timestamp = dateFormat.format(Date()),
                    distance = compResult.distance,
                    threshold = compResult.threshold,
                    description = when {
                        isSilenceGap -> "Broadcast Silence / Black Frame Gap"
                        isCommercialSegment -> "Commercial Break Transition (Acoustic Shift)"
                        else -> "Program Return Transition"
                    }
                )
                if (logs.size >= 50) logs.removeFirst()
                logs.addLast(event)
            }

            // Automatic TV Mute / Unmute Handler
            handleAutoMuting(
                isCommercialActive = isCommercialSegment,
                logs = logs,
                dateFormat = dateFormat,
                distance = compResult.distance,
                threshold = compResult.threshold
            )

            // Downsampled waveform for oscilloscope (256 samples)
            val waveformView = FloatArray(256)
            for (i in 0 until 256) {
                waveformView[i] = samples[i * (chunkSize / 256)]
            }

            _state.value = _state.value.copy(
                waveform = waveformView,
                spectrogramHistory = history.toList(),
                currentDistance = compResult.distance,
                isEventTriggered = compResult.isSignificant,
                isTvMuted = isTvCurrentlyMuted,
                tvControlMethod = tvControlManager.controlMethod.name,
                eventLogs = logs.toList().reversed(),
                classifierScores = classificationScores
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
                .setBufferSizeInBytes(minBufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.setVolume(1.0f)
            audioTrack.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 1. Dedicated Audio Streaming Coroutine (Feeder - continuous playback without buffer underruns)
        val feederJob = scope.launch(Dispatchers.IO) {
            val streamBufferChunk = 1024
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
                    delay(10)
                }
            }
        }

        // 2. Spectrogram & Classification Inspector Loop
        try {
            while (scope.isActive) {
                val interval = _state.value.intervalMs
                val totalSamples = audio.samples.size
                val currentOffset = filePlaybackOffset.coerceIn(0, totalSamples.coerceAtLeast(1) - 1)

                val progress = currentOffset.toFloat() / totalSamples.coerceAtLeast(1)
                val currentPosMs = (audio.durationMs * progress).toLong()

                // Extract 512-sample analysis window from current audio stream playback position
                val analysisSamples = FloatArray(chunkSize)
                val available = (totalSamples - currentOffset).coerceAtLeast(0)
                val analysisCount = chunkSize.coerceAtMost(available)
                if (analysisCount > 0) {
                    System.arraycopy(audio.samples, currentOffset, analysisSamples, 0, analysisCount)
                }

                // Parallel Execution: Mel-Spectrogram + Audio Classifier
                val melEnergiesDeferred = scope.async { calculator.computeMelEnergies(analysisSamples) }
                val classificationDeferred = scope.async { classifier.classify(analysisSamples) }

                val melEnergies = melEnergiesDeferred.await()
                val classificationScores = classificationDeferred.await()

                if (history.size >= 30) history.removeFirst()
                history.addLast(melEnergies)

                val compResult = comparator.compare(melEnergies)
                if (compResult.isSignificant) {
                    val event = WorkbenchEvent(
                        timestamp = dateFormat.format(Date()),
                        distance = compResult.distance,
                        threshold = compResult.threshold,
                        description = "File Transition Detected (t=${currentPosMs / 1000}s)"
                    )
                    if (logs.size >= 50) logs.removeFirst()
                    logs.addLast(event)
                }

                // Detect commercial from classifier scores or shift
                val isCommercialScore = classificationScores.any { 
                    it.label.contains("Commercial", ignoreCase = true) && it.score >= 0.35f
                }
                val isCommercialActive = isCommercialScore || compResult.isSignificant

                handleAutoMuting(
                    isCommercialActive = isCommercialActive,
                    logs = logs,
                    dateFormat = dateFormat,
                    distance = compResult.distance,
                    threshold = compResult.threshold
                )

                val waveformView = FloatArray(256)
                for (i in 0 until 256) {
                    waveformView[i] = analysisSamples[i * (chunkSize / 256)]
                }

                _state.value = _state.value.copy(
                    waveform = waveformView,
                    spectrogramHistory = history.toList(),
                    currentDistance = compResult.distance,
                    isEventTriggered = compResult.isSignificant,
                    isTvMuted = isTvCurrentlyMuted,
                    tvControlMethod = tvControlManager.controlMethod.name,
                    eventLogs = logs.toList().reversed(),
                    classifierScores = classificationScores,
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
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
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
            val event = WorkbenchEvent(
                timestamp = dateFormat.format(Date()),
                distance = 0f,
                threshold = _state.value.threshold,
                description = "[MIC ERROR] Microphone not initialized. Verify RECORD_AUDIO permission in Android settings."
            )
            logs.addLast(event)
            _state.value = _state.value.copy(
                eventLogs = logs.toList().reversed(),
                spectrogramHistory = emptyList(),
                currentDistance = 0f
            )
            return
        }

        try {
            audioRecord.startRecording()
            val shortBuffer = ShortArray(512)
            val floatSamples = FloatArray(512)

            while (scope.isActive) {
                val interval = _state.value.intervalMs
                val readCount = audioRecord.read(shortBuffer, 0, shortBuffer.size)

                if (readCount > 0) {
                    for (i in 0 until readCount) {
                        floatSamples[i] = (shortBuffer[i] / 32768.0f).coerceIn(-1f, 1f)
                    }

                    // Parallel: Mel-spectrogram & Classifier
                    val melDeferred = scope.async { calculator.computeMelEnergies(floatSamples) }
                    val classDeferred = scope.async { classifier.classify(floatSamples) }

                    val melEnergies = melDeferred.await()
                    val classificationScores = classDeferred.await()

                    if (history.size >= 30) history.removeFirst()
                    history.addLast(melEnergies)

                    val compResult = comparator.compare(melEnergies)
                    if (compResult.isSignificant) {
                        val event = WorkbenchEvent(
                            timestamp = dateFormat.format(Date()),
                            distance = compResult.distance,
                            threshold = compResult.threshold,
                            description = "Live Acoustic Shift Exceeded Threshold"
                        )
                        if (logs.size >= 50) logs.removeFirst()
                        logs.addLast(event)
                    }

                    // Check live commercial probability
                    val isCommercialScore = classificationScores.any { 
                        it.label.contains("Commercial", ignoreCase = true) && it.score >= 0.35f
                    }
                    val isCommercialActive = isCommercialScore || compResult.isSignificant

                    handleAutoMuting(
                        isCommercialActive = isCommercialActive,
                        logs = logs,
                        dateFormat = dateFormat,
                        distance = compResult.distance,
                        threshold = compResult.threshold
                    )

                    val waveformView = FloatArray(256)
                    for (i in 0 until 256) {
                        waveformView[i] = floatSamples[i * 2]
                    }

                    _state.value = _state.value.copy(
                        waveform = waveformView,
                        spectrogramHistory = history.toList(),
                        currentDistance = compResult.distance,
                        isEventTriggered = compResult.isSignificant,
                        isTvMuted = isTvCurrentlyMuted,
                        tvControlMethod = tvControlManager.controlMethod.name,
                        eventLogs = logs.toList().reversed(),
                        classifierScores = classificationScores
                    )
                }
                delay(interval)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val event = WorkbenchEvent(
                timestamp = dateFormat.format(Date()),
                distance = 0f,
                threshold = _state.value.threshold,
                description = "[MIC EXCEPTION] ${e.message ?: "AudioRecord error"}"
            )
            logs.addLast(event)
            _state.value = _state.value.copy(eventLogs = logs.toList().reversed())
        } finally {
            try {
                audioRecord.stop()
                audioRecord.release()
            } catch (ignored: Exception) {}
        }
    }
}
