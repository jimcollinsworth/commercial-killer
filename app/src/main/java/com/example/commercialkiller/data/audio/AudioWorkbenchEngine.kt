package com.example.commercialkiller.data.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlin.random.Random

data class WorkbenchEvent(
    val timestamp: String,
    val distance: Float,
    val threshold: Float,
    val description: String
)

data class WorkbenchState(
    val isRunning: Boolean = false,
    val isLiveMic: Boolean = false,
    val intervalMs: Long = 100L,
    val threshold: Float = 0.05f,
    val currentDistance: Float = 0f,
    val isEventTriggered: Boolean = false,
    val waveform: FloatArray = FloatArray(256),
    val spectrogramHistory: List<FloatArray> = emptyList(),
    val eventLogs: List<WorkbenchEvent> = emptyList()
)

/**
 * Core engine managing audio capture, Mel-spectrogram calculation, and change significance triggers.
 */
class AudioWorkbenchEngine(
    private val context: Context? = null
) {
    private val calculator = MelSpectrogramCalculator()
    private val comparator = SpectrogramComparator()

    private val _state = MutableStateFlow(WorkbenchState())
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    private var engineJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start(useLiveMic: Boolean = false) {
        if (_state.value.isRunning) return
        comparator.reset()
        _state.value = _state.value.copy(isRunning = true, isLiveMic = useLiveMic)

        engineJob = scope.launch {
            if (useLiveMic) {
                runLiveMicLoop()
            } else {
                runSimulatedLoop()
            }
        }
    }

    fun stop() {
        engineJob?.cancel()
        engineJob = null
        _state.value = _state.value.copy(isRunning = false, isEventTriggered = false)
    }

    fun setThreshold(newThreshold: Float) {
        comparator.threshold = newThreshold
        _state.value = _state.value.copy(threshold = newThreshold)
    }

    fun setInterval(newIntervalMs: Long) {
        _state.value = _state.value.copy(intervalMs = newIntervalMs)
    }

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

            // Simulate baseline broadcast (low tone 220Hz) vs Commercial Break (abrupt noisy 880Hz + noise)
            val isCommercialSegment = (tick / 30) % 2 == 1
            val baseFreq = if (isCommercialSegment) 880.0 else 220.0

            val samples = FloatArray(chunkSize)
            for (i in 0 until chunkSize) {
                val tone = sin(2.0 * Math.PI * baseFreq * i / sampleRate).toFloat()
                val noise = (Random.nextFloat() - 0.5f) * (if (isCommercialSegment) 0.8f else 0.1f)
                samples[i] = (tone * 0.7f + noise * 0.3f).coerceIn(-1.0f, 1.0f)
            }

            // 1. Calculate Mel Spectrogram
            val melEnergies = calculator.computeMelEnergies(samples)

            // 2. Maintain waterfall history (keep last 30 frames)
            if (history.size >= 30) history.removeFirst()
            history.addLast(melEnergies)

            // 3. Compare with previous frame
            val compResult = comparator.compare(melEnergies)

            if (compResult.isSignificant) {
                val event = WorkbenchEvent(
                    timestamp = dateFormat.format(Date()),
                    distance = compResult.distance,
                    threshold = compResult.threshold,
                    description = if (isCommercialSegment) "Commercial Transition Detected" else "Program Return Detected"
                )
                if (logs.size >= 50) logs.removeFirst()
                logs.addLast(event)
            }

            // Extract downsampled waveform for UI oscilloscope (256 samples)
            val waveformView = FloatArray(256)
            for (i in 0 until 256) {
                waveformView[i] = samples[i * (chunkSize / 256)]
            }

            _state.value = _state.value.copy(
                waveform = waveformView,
                spectrogramHistory = history.toList(),
                currentDistance = compResult.distance,
                isEventTriggered = compResult.isSignificant,
                eventLogs = logs.toList().reversed()
            )

            delay(interval)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun runLiveMicLoop() {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                // Fall back to simulation if mic is unavailable
                runSimulatedLoop()
                return
            }

            audioRecord.startRecording()
            val shortBuffer = ShortArray(512)
            val floatSamples = FloatArray(512)
            val history = ArrayDeque<FloatArray>(30)
            val logs = ArrayDeque<WorkbenchEvent>(50)
            val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

            while (scope.isActive) {
                val interval = _state.value.intervalMs
                val readCount = audioRecord.read(shortBuffer, 0, shortBuffer.size)

                if (readCount > 0) {
                    for (i in 0 until readCount) {
                        floatSamples[i] = (shortBuffer[i] / 32768.0f).coerceIn(-1f, 1f)
                    }

                    val melEnergies = calculator.computeMelEnergies(floatSamples)
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

                    val waveformView = FloatArray(256)
                    for (i in 0 until 256) {
                        waveformView[i] = floatSamples[i * 2]
                    }

                    _state.value = _state.value.copy(
                        waveform = waveformView,
                        spectrogramHistory = history.toList(),
                        currentDistance = compResult.distance,
                        isEventTriggered = compResult.isSignificant,
                        eventLogs = logs.toList().reversed()
                    )
                }
                delay(interval)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            runSimulatedLoop()
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (ignored: Exception) {}
        }
    }
}
