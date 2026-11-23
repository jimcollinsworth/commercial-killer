// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.audio

import android.content.Context
import android.util.Log
import com.example.commercialkiller.data.model.ModelRepository
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

data class ClassifierScore(
    val label: String,
    val score: Float
)

/**
 * On-device audio classifier running MediaPipe Tasks Audio models (16 KB aligned)
 * in parallel with the Mel-spectrogram signal pipeline.
 *
 * Supports loading compatible audio classification models (e.g., YAMNet or custom
 * Audio Spectrogram Transformer / AST models) with an acoustic spectral feature fallback.
 */
class AudioClassifierEngine(
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "AudioClassifierEngine"
        const val DEFAULT_MODEL_PATH = "yamnet.tflite"
        private const val FALLBACK_MODEL_NAME = "Acoustic Feature Classifier (HF Fallback)"
        private const val FALLBACK_MANUAL_MODEL_NAME = "DSP Acoustic Heuristic (Manual)"
        private const val SAMPLE_RATE_HZ = 16000f
        private const val NEURAL_WINDOW_SAMPLES = 16_000
    }

    private val classifierLock = Any()
    private val neuralLock = Any()
    private val neuralRing = FloatArray(NEURAL_WINDOW_SAMPLES)
    private val neuralWindow = FloatArray(NEURAL_WINDOW_SAMPLES)
    private var neuralRingPos = 0
    private var neuralRingFilled = false

    @Volatile
    private var useNeuralModel: Boolean = true
    @Volatile
    private var selectedModelPath: String? = null

    private var mediaPipeClassifier: AudioClassifier? = null
    private var cachedAudioData: AudioData? = null
    private var cachedAudioDataLength: Int = -1
    private val audioFormat: AudioData.AudioDataFormat by lazy {
        AudioData.AudioDataFormat.builder()
            .setNumOfChannels(1)
            .setSampleRate(SAMPLE_RATE_HZ)
            .build()
    }
    private var activeModelName: String = FALLBACK_MODEL_NAME

    private fun logD(message: String) {
        try {
            Log.d(TAG, message)
        } catch (e: Exception) {
            println("[$TAG] $message")
        }
    }

    private fun logW(message: String) {
        try {
            Log.w(TAG, message)
        } catch (e: Exception) {
            System.err.println("[$TAG WARN] $message")
        }
    }

    private fun logE(message: String, throwable: Throwable? = null) {
        try {
            Log.e(TAG, message, throwable)
        } catch (e: Exception) {
            System.err.println("[$TAG ERROR] $message ${throwable?.message ?: ""}")
        }
    }

    init {
        initializeClassifier()
    }

    fun setUseNeuralModel(enabled: Boolean) {
        useNeuralModel = enabled
        val explicit = selectedModelPath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        initializeClassifier(explicit)
    }

    fun selectModelFile(file: File?) {
        selectedModelPath = file?.takeIf { it.exists() && it.isFile }?.absolutePath
        if (useNeuralModel) {
            initializeClassifier(file)
        } else {
            activeModelName = FALLBACK_MANUAL_MODEL_NAME
        }
    }

    fun isNeuralClassifierActive(): Boolean = useNeuralModel && mediaPipeClassifier != null

    fun getSelectedModelPath(): String? = selectedModelPath

    fun resetBuffer() {
        synchronized(neuralLock) {
            neuralRing.fill(0f)
            neuralWindow.fill(0f)
            neuralRingPos = 0
            neuralRingFilled = false
        }
    }

    /**
     * Initializes the MediaPipe AudioClassifier using an explicit file, discovered local
     * `.tflite` model, or bundled asset, falling back to spectral feature classification when absent
     * or manually disabled.
     */
    fun initializeClassifier(customModelFile: File? = null) {
        if (!useNeuralModel) {
            close()
            activeModelName = FALLBACK_MANUAL_MODEL_NAME
            return
        }
        if (context == null) {
            logD("Context not provided; using acoustic spectral feature classifier.")
            activeModelName = FALLBACK_MODEL_NAME
            return
        }

        synchronized(classifierLock) {
            closeInternalLocked()

            try {
                val baseOptionsBuilder = BaseOptions.builder()
                val pendingModelName: String

                val chosenFile = customModelFile?.takeIf { it.exists() && it.isFile }
                    ?: selectedModelPath?.let { File(it) }?.takeIf { it.exists() && it.isFile }
                    ?: discoverLocalTfliteModel(context)

                if (chosenFile != null) {
                    val modelBuffer = readFileToDirectBuffer(chosenFile)
                    baseOptionsBuilder.setModelAssetBuffer(modelBuffer)
                    selectedModelPath = chosenFile.absolutePath
                    pendingModelName = chosenFile.name
                } else {
                    baseOptionsBuilder.setModelAssetPath(DEFAULT_MODEL_PATH)
                    pendingModelName = DEFAULT_MODEL_PATH
                }

                val options = AudioClassifier.AudioClassifierOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setRunningMode(RunningMode.AUDIO_CLIPS)
                    .setMaxResults(5)
                    .build()

                mediaPipeClassifier = AudioClassifier.createFromOptions(context, options)
                activeModelName = pendingModelName
                resetBuffer()
            } catch (e: Exception) {
                logW("Failed to initialize MediaPipe AudioClassifier: ${e.message}. Using fallback classifier.")
                mediaPipeClassifier = null
                cachedAudioData = null
                cachedAudioDataLength = -1
                activeModelName = FALLBACK_MODEL_NAME
            }
        }
    }

    private fun discoverLocalTfliteModel(ctx: Context): File? {
        val audioEntries = ModelRepository.scanModels(ctx).first
        val preferred = audioEntries.firstOrNull {
            it.fileName.equals(DEFAULT_MODEL_PATH, ignoreCase = true)
        } ?: audioEntries.firstOrNull()
        return preferred?.let { File(it.absolutePath) }?.takeIf { it.exists() && it.isFile }
    }

    private fun readFileToDirectBuffer(file: File): ByteBuffer {
        return FileInputStream(file).use { fis ->
            val channel = fis.channel
            channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }
    }

    /**
     * Classifies a 16 kHz PCM audio buffer (mono).
     */
    suspend fun classify(samples: FloatArray): List<ClassifierScore> = withContext(Dispatchers.Default) {
        if (samples.isEmpty()) return@withContext emptyList()

        if (useNeuralModel) {
            val neuralResult = synchronized(classifierLock) {
                val classifier = mediaPipeClassifier
                if (classifier != null) {
                    try {
                        val windowSamples = appendAndSnapshotNeuralWindow(samples)
                        val audioData = if (cachedAudioData != null && cachedAudioDataLength == windowSamples.size) {
                            cachedAudioData!!
                        } else {
                            AudioData.create(audioFormat, windowSamples.size).also {
                                cachedAudioData = it
                                cachedAudioDataLength = windowSamples.size
                            }
                        }
                        audioData.load(windowSamples)

                        val result = classifier.classify(audioData)
                        val categories = result.classificationResults()
                            .firstOrNull()
                            ?.classifications()
                            ?.firstOrNull()
                            ?.categories()
                        if (!categories.isNullOrEmpty()) {
                            val items = categories.take(3).map {
                                ClassifierScore(label = it.categoryName(), score = it.score())
                            }
                            padToThree(items)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        logE("Error running MediaPipe audio classification", e)
                        null
                    }
                } else {
                    null
                }
            }
            if (neuralResult != null) {
                return@withContext neuralResult
            }
        }

        // Fallback Classifier: Acoustic Feature Analysis
        // Classifies audio into Speech, Music, Commercial / Jingle, or Silence based on spectral characteristics
        classifyByAcousticFeatures(samples)
    }

    private fun appendAndSnapshotNeuralWindow(samples: FloatArray): FloatArray {
        synchronized(neuralLock) {
            if (samples.size >= NEURAL_WINDOW_SAMPLES) {
                val offset = samples.size - NEURAL_WINDOW_SAMPLES
                System.arraycopy(samples, offset, neuralRing, 0, NEURAL_WINDOW_SAMPLES)
                neuralRingPos = 0
                neuralRingFilled = true
            } else {
                var i = 0
                while (i < samples.size) {
                    val space = NEURAL_WINDOW_SAMPLES - neuralRingPos
                    val copyLen = minOf(space, samples.size - i)
                    System.arraycopy(samples, i, neuralRing, neuralRingPos, copyLen)
                    neuralRingPos += copyLen
                    if (neuralRingPos >= NEURAL_WINDOW_SAMPLES) {
                        neuralRingPos = 0
                        neuralRingFilled = true
                    }
                    i += copyLen
                }
            }

            if (!neuralRingFilled && neuralRingPos == 0) {
                neuralWindow.fill(0f)
                return neuralWindow
            }
            val headLen = NEURAL_WINDOW_SAMPLES - neuralRingPos
            if (headLen > 0) {
                System.arraycopy(neuralRing, neuralRingPos, neuralWindow, 0, headLen)
            }
            if (neuralRingPos > 0) {
                System.arraycopy(neuralRing, 0, neuralWindow, headLen, neuralRingPos)
            }
            return neuralWindow
        }
    }

    /**
     * Acoustic feature classifier that computes Zero Crossing Rate (ZCR), RMS energy,
     * and high-frequency content to classify audio frames into broadcast categories.
     * Always returns exactly 3 items to maintain stable, non-jumping UI layout.
     */
    fun classifyByAcousticFeatures(samples: FloatArray): List<ClassifierScore> {
        if (samples.isEmpty()) {
            return listOf(
                ClassifierScore("Silence", 1.0f),
                ClassifierScore("Background Noise", 0.0f),
                ClassifierScore("Broadcast Content", 0.0f)
            )
        }

        // 1. RMS Energy
        var sumSquare = 0.0
        for (s in samples) {
            sumSquare += s * s
        }
        val rms = sqrt(sumSquare / samples.size).toFloat()

        if (rms < 0.020f) {
            return listOf(
                ClassifierScore("Silence / Transition Gap", 0.95f),
                ClassifierScore("Background Noise", 0.05f),
                ClassifierScore("Broadcast Content", 0.00f)
            )
        }

        // 2. Zero-Crossing Rate (ZCR)
        var zeroCrossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        val zcr = zeroCrossings.toFloat() / samples.size

        // 3. High-frequency energy estimate (first difference)
        var highFreqEnergy = 0.0
        for (i in 1 until samples.size) {
            val diff = samples[i] - samples[i - 1]
            highFreqEnergy += diff * diff
        }
        val hfRatio = (sqrt(highFreqEnergy / samples.size) / (rms + 1e-6f)).toFloat()

        // 4. Temporal envelope modulation ratio across 4 subframes (distinguishes speech/ads from steady ambient noise)
        val quarter = (samples.size / 4).coerceAtLeast(1)
        var minSubRms = Float.MAX_VALUE
        var maxSubRms = 0f
        for (q in 0 until 4) {
            val start = q * quarter
            val end = if (q == 3) samples.size else ((q + 1) * quarter).coerceAtMost(samples.size)
            val len = (end - start).coerceAtLeast(1)
            var qSq = 0.0
            for (idx in start until end) {
                val v = samples[idx]
                qSq += v * v
            }
            val subRms = sqrt(qSq / len).toFloat()
            if (subRms < minSubRms) minSubRms = subRms
            if (subRms > maxSubRms) maxSubRms = subRms
        }
        val modRatio = (maxSubRms - minSubRms) / (rms + 1e-6f)
        val maxPitchCorr = computePreEmphasizedPitchCorrelation(samples)

        // Distinguish Non-Media (Vehicle Rumble / Steady Ambient / Unpitched Room Noise) vs Commercial vs Speech vs Music:
        // Car cabin / engine rumble is concentrated below ~300 Hz (very low ZCR and low HF ratio).
        // Rain / HVAC / road hiss is broadband, unmodulated over short frames (low modRatio), and below ad loudness.
        // Empty room ambient noise lacks periodic vocal harmonics (low pre-emphasized autocorrelation).
        val isHighLoudness = rms > 0.18f
        val isHighFrequencyBed = hfRatio > 1.2f

        val rawList = if (zcr < 0.04f && hfRatio < 0.45f) {
            listOf(
                ClassifierScore("Vehicle / Low-Freq Rumble", 0.86f),
                ClassifierScore("Ambient / Non-Media", 0.72f),
                ClassifierScore("Background Noise", 0.45f)
            )
        } else if ((zcr > 0.30f || hfRatio > 1.35f) && modRatio < 0.28f && rms < 0.22f) {
            listOf(
                ClassifierScore("Ambient / Room Noise", 0.84f),
                ClassifierScore("Background Noise", 0.70f),
                ClassifierScore("Non-Media Environment", 0.45f)
            )
        } else if (maxPitchCorr < 0.30f && rms < 0.14f) {
            listOf(
                ClassifierScore("Ambient / Room Noise", 0.86f),
                ClassifierScore("Background Noise", 0.72f),
                ClassifierScore("Non-Media Environment", 0.50f)
            )
        } else if (isHighLoudness && isHighFrequencyBed) {
            listOf(
                ClassifierScore("Commercial / Jingle", (0.75f + (rms * 0.2f)).coerceAtMost(0.98f)),
                ClassifierScore("Music Bed", 0.65f),
                ClassifierScore("Voiceover / Speech", 0.40f)
            )
        } else if (zcr in 0.04f..0.25f && !isHighLoudness) {
            listOf(
                ClassifierScore("Dialogue / Speech", (0.65f + (maxPitchCorr * 0.22f) + (rms * 0.20f)).coerceIn(0.65f, 0.95f)),
                ClassifierScore("Broadcast Content", 0.55f),
                ClassifierScore("Background Ambience", 0.20f)
            )
        } else {
            listOf(
                ClassifierScore("Music / Sound Bed", 0.72f),
                ClassifierScore("Broadcast Content", 0.58f),
                ClassifierScore("Speech", 0.35f)
            )
        }
        return padToThree(rawList)
    }

    /**
     * Computes maximum normalized autocorrelation across human vocal pitch lags (80–500 Hz at 16 kHz)
     * after first-order pre-emphasis (0.92f) and DC mean removal to suppress 1/f room noise.
     */
    private fun computePreEmphasizedPitchCorrelation(samples: FloatArray): Float {
        val n = samples.size
        if (n < 64) return 0f

        var sum = 0.0
        for (i in 0 until n) {
            sum += samples[i]
        }
        val mean = (sum / n.toDouble()).toFloat()

        val p = FloatArray(n)
        for (i in 1 until n) {
            p[i] = (samples[i] - mean) - 0.92f * (samples[i - 1] - mean)
        }

        val maxLag = minOf(200, n / 2)
        var maxCorr = 0f
        var lag = 32
        while (lag <= maxLag) {
            var dot = 0.0
            var eA = 0.0
            var eB = 0.0
            val count = n - lag
            for (i in 0 until count) {
                val a = p[i].toDouble()
                val b = p[i + lag].toDouble()
                dot += a * b
                eA += a * a
                eB += b * b
            }
            val denom = sqrt(eA * eB)
            if (denom > 1e-8) {
                val corr = (dot / denom).toFloat()
                if (corr > maxCorr) maxCorr = corr
            }
            lag += 2
        }
        return maxCorr.coerceIn(0f, 1f)
    }

    private fun padToThree(items: List<ClassifierScore>): List<ClassifierScore> {
        val result = items.take(3).toMutableList()
        val defaultLabels = listOf("Broadcast Content", "Background Noise", "Audio Stream")
        var labelIdx = 0
        while (result.size < 3) {
            val label = defaultLabels.getOrElse(labelIdx++) { "Stream Item" }
            result.add(ClassifierScore(label, 0.0f))
        }
        return result
    }

    fun getActiveModelName(): String = activeModelName

    fun close() {
        synchronized(classifierLock) {
            closeInternalLocked()
        }
    }

    private fun closeInternalLocked() {
        try {
            mediaPipeClassifier?.close()
            mediaPipeClassifier = null
            cachedAudioData = null
            cachedAudioDataLength = -1
        } catch (e: Exception) {
            logE("Error closing classifier", e)
        }
    }
}
