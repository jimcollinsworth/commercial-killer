// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.audio.ClassifierScore
import kotlin.math.sqrt

enum class MediaEnvironment {
    BROADCAST_MEDIA,
    SILENCE,
    AMBIENT_NOISE
}

enum class TvActionDecision {
    MUTE,
    UNMUTE,
    HOLD
}

data class MediaEvaluationResult(
    val isMedia: Boolean,
    val mediaScore: Float,
    val mediaEnvironment: MediaEnvironment
)

data class FrameDecision(
    val isMedia: Boolean = false,
    val mediaScore: Float = 0f,
    val mediaEnvironment: MediaEnvironment = MediaEnvironment.SILENCE,
    val isCommercial: Boolean = false,
    val commercialScore: Float = 0f,
    val isSameScene: Boolean = true,
    val sceneSimilarity: Float = 1.0f,
    val shotDurationMs: Long = 0L,
    val cutCadencePerMin: Float = 0f,
    val recommendedAction: TvActionDecision = TvActionDecision.HOLD
)

data class SceneContinuityResult(
    val isSameScene: Boolean,
    val sceneSimilarity: Float,
    val shotDurationMs: Long,
    val cutCadencePerMin: Float
)

data class CommercialEvaluationResult(
    val isCommercial: Boolean,
    val commercialScore: Float
)

/**
 * Per-frame non-autoregressive decision model evaluating:
 * 0. Media presence (`is_media`: TV/radio broadcast vs. silence or car/ambient non-media noise),
 * 1. Scene continuity (`is_same_scene` via cosine similarity and rolling 60 s cut cadence), and
 * 2. Commercial probability (`is_commercial` gated by `is_media`) for TV mute/unmute actions.
 */
class FrameDecisionEngine(
    initialSceneSimilarityThreshold: Float = DEFAULT_SCENE_SIMILARITY_THRESHOLD,
    var commercialThreshold: Float = DEFAULT_COMMERCIAL_THRESHOLD,
    initialMediaThreshold: Float = DEFAULT_MEDIA_THRESHOLD
) {
    companion object {
        const val MIN_SCENE_SIMILARITY_THRESHOLD = 0.50f
        const val MAX_SCENE_SIMILARITY_THRESHOLD = 0.99f
        const val DEFAULT_SCENE_SIMILARITY_THRESHOLD = 0.85f
        const val MIN_MEDIA_THRESHOLD = 0.20f
        const val MAX_MEDIA_THRESHOLD = 0.90f
        const val DEFAULT_MEDIA_THRESHOLD = 0.50f
        const val DEFAULT_COMMERCIAL_THRESHOLD = 0.75f
        const val CUT_WINDOW_MS = 60_000L
        const val HIGH_CADENCE_CUTS_PER_MIN = 15f
        const val NON_MEDIA_STREAK_RESET_FRAMES = 3
        const val MUTE_CONSECUTIVE_COMMERCIAL_FRAMES = 3
        const val UNMUTE_CONSECUTIVE_PROGRAM_FRAMES = 5
        private const val INITIAL_BUFFER_CAPACITY = 128
    }

    private var sceneSimilarityThreshold: Float = clampSimilarityThreshold(initialSceneSimilarityThreshold)
    private var mediaThreshold: Float = clampMediaThreshold(initialMediaThreshold)

    // Pre-allocated frame buffer reused across 100 ms ticks to prevent per-frame heap allocations.
    private var previousFrameBuffer = FloatArray(INITIAL_BUFFER_CAPACITY)
    private var previousFrameLength = 0

    private var currentShotStartMs = 0L
    private var hasInitializedStart = false
    private val cutTimestampsMs = ArrayDeque<Long>(64)

    private var consecutiveCommercialFrames = 0
    private var consecutiveProgramFrames = 0
    private var consecutiveNonMediaFrames = 0

    // Internal scratch fields populated by in-place helpers to avoid intermediate object allocations.
    private var lastIsMedia = false
    private var lastMediaScore = 0f
    private var lastMediaEnvironment = MediaEnvironment.SILENCE
    private var lastIsSameScene = true
    private var lastSceneSimilarity = 1.0f
    private var lastShotDurationMs = 0L
    private var lastCutCadencePerMin = 0f
    private var lastIsCommercial = false
    private var lastCommercialScore = 0f

    fun setSceneSimilarityThreshold(value: Float) {
        sceneSimilarityThreshold = clampSimilarityThreshold(value)
    }

    fun getSceneSimilarityThreshold(): Float = sceneSimilarityThreshold

    fun setMediaThreshold(value: Float) {
        mediaThreshold = clampMediaThreshold(value)
    }

    fun getMediaThreshold(): Float = mediaThreshold

    private fun clampSimilarityThreshold(value: Float): Float {
        return if (value.isNaN()) {
            DEFAULT_SCENE_SIMILARITY_THRESHOLD
        } else {
            value.coerceIn(MIN_SCENE_SIMILARITY_THRESHOLD, MAX_SCENE_SIMILARITY_THRESHOLD)
        }
    }

    private fun clampMediaThreshold(value: Float): Float {
        return if (value.isNaN()) {
            DEFAULT_MEDIA_THRESHOLD
        } else {
            value.coerceIn(MIN_MEDIA_THRESHOLD, MAX_MEDIA_THRESHOLD)
        }
    }

    /**
     * Query 0: First-gate check distinguishing TV/radio broadcast media from silence
     * or non-media ambient environments (in-car engine rumble, rain, room fan noise).
     */
    fun evaluateMediaPresence(
        features: FloatArray,
        classifierTags: List<ClassifierScore>
    ): MediaEvaluationResult {
        evaluateMediaPresenceInternal(features, classifierTags)
        return MediaEvaluationResult(
            isMedia = lastIsMedia,
            mediaScore = lastMediaScore,
            mediaEnvironment = lastMediaEnvironment
        )
    }

    /**
     * Query 1: Evaluates cosine similarity between current Mel-spectrogram vector F_t
     * and previous frame vector F_{t-1}, updates shot duration, and tracks rolling 60 s cut cadence.
     */
    fun evaluateSceneContinuity(
        currentFeatures: FloatArray,
        timestampMs: Long
    ): SceneContinuityResult {
        updateSceneContinuityInternal(currentFeatures, timestampMs)
        return SceneContinuityResult(
            isSameScene = lastIsSameScene,
            sceneSimilarity = lastSceneSimilarity,
            shotDurationMs = lastShotDurationMs,
            cutCadencePerMin = lastCutCadencePerMin
        )
    }

    /**
     * Query 2: Evaluates commercial probability combining classifier tag weights,
     * Mel-band spectral compression density with transition distance boost, and rolling cut cadence.
     */
    fun evaluateCommercial(
        features: FloatArray,
        classifierTags: List<ClassifierScore>,
        shiftDistance: Float,
        cutCadencePerMin: Float
    ): CommercialEvaluationResult {
        scoreCommercialInternal(features, classifierTags, shiftDistance, cutCadencePerMin)
        return CommercialEvaluationResult(
            isCommercial = lastIsCommercial,
            commercialScore = lastCommercialScore
        )
    }

    /**
     * Evaluates media presence first (`is_media`), then scene continuity (`is_same_scene`),
     * and finally gated commercial classification (`is_commercial`) and TV action decisions.
     */
    fun evaluateFrame(
        melEnergies: FloatArray,
        classifierTags: List<ClassifierScore>,
        shiftDistance: Float,
        timestampMs: Long
    ): FrameDecision {
        updateSceneContinuityInternal(melEnergies, timestampMs)
        evaluateMediaPresenceInternal(melEnergies, classifierTags)

        val recommendedAction: TvActionDecision
        if (!lastIsMedia) {
            lastIsCommercial = false
            lastCommercialScore = 0f
            consecutiveNonMediaFrames++
            if (consecutiveNonMediaFrames > NON_MEDIA_STREAK_RESET_FRAMES) {
                consecutiveCommercialFrames = 0
                consecutiveProgramFrames = 0
            }
            recommendedAction = TvActionDecision.HOLD
        } else {
            consecutiveNonMediaFrames = 0
            scoreCommercialInternal(melEnergies, classifierTags, shiftDistance, lastCutCadencePerMin)

            if (lastIsCommercial) {
                consecutiveCommercialFrames++
                consecutiveProgramFrames = 0
            } else {
                consecutiveProgramFrames++
                consecutiveCommercialFrames = 0
            }

            recommendedAction = when {
                lastIsCommercial && (!lastIsSameScene || consecutiveCommercialFrames >= MUTE_CONSECUTIVE_COMMERCIAL_FRAMES) ->
                    TvActionDecision.MUTE
                !lastIsCommercial && consecutiveProgramFrames >= UNMUTE_CONSECUTIVE_PROGRAM_FRAMES ->
                    TvActionDecision.UNMUTE
                else -> TvActionDecision.HOLD
            }
        }

        return FrameDecision(
            isMedia = lastIsMedia,
            mediaScore = lastMediaScore,
            mediaEnvironment = lastMediaEnvironment,
            isCommercial = lastIsCommercial,
            commercialScore = lastCommercialScore,
            isSameScene = lastIsSameScene,
            sceneSimilarity = lastSceneSimilarity,
            shotDurationMs = lastShotDurationMs,
            cutCadencePerMin = lastCutCadencePerMin,
            recommendedAction = recommendedAction
        )
    }

    fun reset() {
        previousFrameLength = 0
        previousFrameBuffer.fill(0f)
        currentShotStartMs = 0L
        hasInitializedStart = false
        cutTimestampsMs.clear()
        consecutiveCommercialFrames = 0
        consecutiveProgramFrames = 0
        consecutiveNonMediaFrames = 0
        lastIsMedia = false
        lastMediaScore = 0f
        lastMediaEnvironment = MediaEnvironment.SILENCE
        lastIsSameScene = true
        lastSceneSimilarity = 1.0f
        lastShotDurationMs = 0L
        lastCutCadencePerMin = 0f
        lastIsCommercial = false
        lastCommercialScore = 0f
    }

    private fun evaluateMediaPresenceInternal(
        features: FloatArray,
        classifierTags: List<ClassifierScore>
    ) {
        var maxMediaTag = 0f
        var maxNonMediaTag = 0f
        var maxSilenceTag = 0f

        for (i in classifierTags.indices) {
            val tag = classifierTags[i]
            val score = tag.score.coerceIn(0f, 1f)
            val label = tag.label

            if (label.contains("speech", ignoreCase = true) ||
                label.contains("dialogue", ignoreCase = true) ||
                label.contains("broadcast", ignoreCase = true) ||
                label.contains("music", ignoreCase = true) ||
                label.contains("commercial", ignoreCase = true) ||
                label.contains("jingle", ignoreCase = true) ||
                label.contains("singing", ignoreCase = true) ||
                label.contains("voiceover", ignoreCase = true) ||
                label.contains("radio", ignoreCase = true) ||
                label.contains("television", ignoreCase = true) ||
                label.contains("narration", ignoreCase = true) ||
                label.contains("conversation", ignoreCase = true) ||
                label.contains("advertisement", ignoreCase = true) ||
                label.contains("promo", ignoreCase = true)
            ) {
                if (score > maxMediaTag) maxMediaTag = score
            }
            if (label.contains("vehicle", ignoreCase = true) ||
                label.contains("car", ignoreCase = true) ||
                label.contains("engine", ignoreCase = true) ||
                label.contains("traffic", ignoreCase = true) ||
                label.contains("rumble", ignoreCase = true) ||
                label.contains("ambient /", ignoreCase = true) ||
                label.contains("ambient noise", ignoreCase = true) ||
                label.contains("non-media", ignoreCase = true) ||
                label.contains("wind", ignoreCase = true) ||
                label.contains("rain", ignoreCase = true) ||
                label.contains("static", ignoreCase = true) ||
                label.contains("fan", ignoreCase = true) ||
                label.contains("hiss", ignoreCase = true) ||
                label.contains("background noise", ignoreCase = true) ||
                label.contains("room", ignoreCase = true) ||
                label.contains("air conditioning", ignoreCase = true) ||
                label.contains("hum", ignoreCase = true) ||
                label.contains("buzz", ignoreCase = true) ||
                label.contains("environmental", ignoreCase = true) ||
                label.contains("pink", ignoreCase = true) ||
                label.contains("white", ignoreCase = true) ||
                label.contains("motor", ignoreCase = true) ||
                label.contains("idle", ignoreCase = true)
            ) {
                if (score > maxNonMediaTag) maxNonMediaTag = score
            }
            if (label.contains("silence", ignoreCase = true) ||
                label.contains("quiet", ignoreCase = true)
            ) {
                if (score > maxSilenceTag) maxSilenceTag = score
            }
        }

        if (features.isEmpty()) {
            lastMediaScore = 0f
            lastIsMedia = false
            lastMediaEnvironment = MediaEnvironment.SILENCE
            return
        }

        val bandCount = features.size
        var totalEnergy = 0.0
        val lowEnd = (bandCount * 15 / 100).coerceAtLeast(1)
        val midStart = lowEnd.coerceAtMost(bandCount - 1)
        val midEnd = (bandCount * 78 / 100).coerceAtLeast(midStart + 1).coerceAtMost(bandCount)

        var lowSum = 0.0
        var midSum = 0.0
        var midMin = Float.MAX_VALUE
        var midMax = 0f

        for (i in 0 until bandCount) {
            val e = features[i].coerceAtLeast(0f)
            totalEnergy += e
            if (i < lowEnd) {
                lowSum += e
            }
            if (i in midStart until midEnd) {
                midSum += e
                if (e < midMin) midMin = e
                if (e > midMax) midMax = e
            }
        }

        val meanEnergy = (totalEnergy / bandCount.toDouble()).toFloat()
        if (meanEnergy < 0.08f || maxSilenceTag >= 0.70f) {
            lastMediaScore = if (maxSilenceTag >= 0.70f) {
                (1f - maxSilenceTag).coerceIn(0f, 0.20f)
            } else {
                (meanEnergy / 0.08f * 0.25f).coerceIn(0f, 0.25f)
            }
            lastIsMedia = false
            lastMediaEnvironment = MediaEnvironment.SILENCE
            return
        }

        val lowMean = (lowSum / lowEnd.coerceAtLeast(1).toDouble()).toFloat()
        val midLen = (midEnd - midStart).coerceAtLeast(1)
        val midMean = (midSum / midLen.toDouble()).toFloat()
        val safeMidMin = if (midMin == Float.MAX_VALUE) 0f else midMin
        val midContrast = if (midMax > 1e-4f) ((midMax - safeMidMin) / (midMax + 1e-4f)).coerceIn(0f, 1f) else 0f

        val lowDominanceRatio = lowMean / (midMean + 1e-4f)
        val rumblePenalty = if (lowDominanceRatio > 2.2f) {
            ((lowDominanceRatio - 2.2f) / 2.0f).coerceIn(0f, 1f) * 0.45f
        } else {
            0f
        }
        val flatNoisePenalty = if (maxMediaTag < 0.40f && midContrast < 0.25f) 0.30f else 0f

        val rawMediaScore = (maxMediaTag * 0.55f) +
            ((midMean / 0.35f).coerceIn(0f, 1f) * 0.30f) +
            ((midContrast / 0.60f).coerceIn(0f, 1f) * 0.25f) -
            (maxNonMediaTag * 0.55f) -
            rumblePenalty -
            flatNoisePenalty

        lastMediaScore = rawMediaScore.coerceIn(0f, 1f)
        lastIsMedia = lastMediaScore >= mediaThreshold
        lastMediaEnvironment = if (lastIsMedia) {
            MediaEnvironment.BROADCAST_MEDIA
        } else {
            MediaEnvironment.AMBIENT_NOISE
        }
    }

    private fun updateSceneContinuityInternal(
        currentFeatures: FloatArray,
        timestampMs: Long
    ) {
        val bandCount = currentFeatures.size
        val similarity: Float
        val isSameScene: Boolean

        if (bandCount == 0 || !hasInitializedStart || previousFrameLength != bandCount) {
            similarity = 1.0f
            isSameScene = true
            currentShotStartMs = timestampMs
            hasInitializedStart = true
        } else {
            var dot = 0.0
            var normCurrSq = 0.0
            var normPrevSq = 0.0
            for (i in 0 until bandCount) {
                val curr = currentFeatures[i].toDouble()
                val prev = previousFrameBuffer[i].toDouble()
                dot += curr * prev
                normCurrSq += curr * curr
                normPrevSq += prev * prev
            }

            if (normCurrSq == 0.0 || normPrevSq == 0.0) {
                similarity = 1.0f
                isSameScene = true
            } else {
                val denom = sqrt(normCurrSq) * sqrt(normPrevSq)
                if (denom == 0.0) {
                    similarity = 1.0f
                    isSameScene = true
                } else {
                    similarity = (dot / denom).toFloat().coerceIn(-1.0f, 1.0f)
                    isSameScene = similarity >= sceneSimilarityThreshold
                }
            }
        }

        if (!isSameScene) {
            cutTimestampsMs.addLast(timestampMs)
            currentShotStartMs = timestampMs
        }

        val windowStartMs = timestampMs - CUT_WINDOW_MS
        while (cutTimestampsMs.isNotEmpty() && cutTimestampsMs.first() < windowStartMs) {
            cutTimestampsMs.removeFirst()
        }

        if (bandCount > 0) {
            if (previousFrameBuffer.size < bandCount) {
                previousFrameBuffer = FloatArray(bandCount)
            }
            System.arraycopy(currentFeatures, 0, previousFrameBuffer, 0, bandCount)
            previousFrameLength = bandCount
        } else {
            previousFrameLength = 0
        }

        lastIsSameScene = isSameScene
        lastSceneSimilarity = similarity
        lastShotDurationMs = (timestampMs - currentShotStartMs).coerceAtLeast(0L)
        lastCutCadencePerMin = cutTimestampsMs.size.toFloat()
    }

    private fun scoreCommercialInternal(
        features: FloatArray,
        classifierTags: List<ClassifierScore>,
        shiftDistance: Float,
        cutCadencePerMin: Float
    ) {
        // (a) Classifier tag probabilities (indexed loop avoids iterator heap allocations).
        var maxCommercialTagScore = 0f
        var maxMusicTagScore = 0f
        var maxSingingTagScore = 0f
        var maxProgramSpeechScore = 0f
        var maxSilenceScore = 0f

        for (i in classifierTags.indices) {
            val tag = classifierTags[i]
            val score = tag.score.coerceIn(0f, 1f)
            val label = tag.label

            if (label.contains("commercial", ignoreCase = true) ||
                label.contains("advertisement", ignoreCase = true) ||
                label.contains("jingle", ignoreCase = true) ||
                label.contains("promo", ignoreCase = true)
            ) {
                if (score > maxCommercialTagScore) maxCommercialTagScore = score
            }
            if (label.contains("music", ignoreCase = true) ||
                label.contains("beat", ignoreCase = true) ||
                label.contains("instrument", ignoreCase = true)
            ) {
                if (score > maxMusicTagScore) maxMusicTagScore = score
            }
            if (label.contains("singing", ignoreCase = true)) {
                if (score > maxSingingTagScore) maxSingingTagScore = score
            }
            if ((label.contains("speech", ignoreCase = true) ||
                    label.contains("dialogue", ignoreCase = true) ||
                    label.contains("conversation", ignoreCase = true) ||
                    label.contains("narration", ignoreCase = true) ||
                    label.contains("monologue", ignoreCase = true)) &&
                !label.contains("voiceover", ignoreCase = true)
            ) {
                if (score > maxProgramSpeechScore) maxProgramSpeechScore = score
            }
            if (label.contains("silence", ignoreCase = true) ||
                label.contains("quiet", ignoreCase = true)
            ) {
                if (score > maxSilenceScore) maxSilenceScore = score
            }
        }

        val musicComposite = (maxOf(maxMusicTagScore, maxSingingTagScore) * 0.78f +
            if (maxMusicTagScore > 0.20f && maxSingingTagScore > 0.20f) 0.08f else 0f)
            .coerceAtMost(0.95f)
        val positiveTagScore = maxOf(maxCommercialTagScore, musicComposite)
        val speechPenalty = (maxProgramSpeechScore - positiveTagScore).coerceAtLeast(0f) * 0.30f
        val silencePenalty = maxSilenceScore * 0.40f

        // (b) Spectral energy compression and loudness density across Mel bands.
        var compressionDensity = 0f
        if (features.isNotEmpty()) {
            var sum = 0.0
            var filledBands = 0
            for (i in features.indices) {
                val energy = features[i].coerceAtLeast(0f)
                sum += energy
                if (energy >= 0.25f) {
                    filledBands++
                }
            }
            val n = features.size.toDouble()
            val mean = (sum / n).toFloat()

            if (mean > 0.05f) {
                var sqDiffSum = 0.0
                for (i in features.indices) {
                    val energy = features[i].coerceAtLeast(0f)
                    val diff = energy - mean
                    sqDiffSum += diff * diff
                }
                val stdDev = sqrt(sqDiffSum / n).toFloat()
                val coeffOfVariation = stdDev / mean

                val loudnessFactor = (mean / 0.65f).coerceIn(0f, 1f)
                val fillRatio = filledBands.toFloat() / features.size.toFloat()
                val uniformityFactor = if (mean > 0.15f) {
                    (1.0f - (coeffOfVariation / 1.2f)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val energyGate = (mean / 0.30f).coerceIn(0f, 1f)
                compressionDensity = (0.45f * loudnessFactor + 0.30f * fillRatio + 0.25f * uniformityFactor) * energyGate
            }
        }

        val safeShiftDistance = if (shiftDistance.isNaN()) 0f else shiftDistance.coerceAtLeast(0f)
        val shiftBoost = (safeShiftDistance / 0.50f).coerceIn(0f, 1f) * 0.15f

        // (c) Rolling cut cadence boost at >= 15 cuts/min.
        val cadenceBoost = if (cutCadencePerMin >= HIGH_CADENCE_CUTS_PER_MIN) {
            0.15f + ((cutCadencePerMin - HIGH_CADENCE_CUTS_PER_MIN) / 30f).coerceIn(0f, 1f) * 0.10f
        } else {
            0f
        }

        val rawScore = (positiveTagScore * 0.58f) +
            (compressionDensity * 0.40f) +
            shiftBoost +
            cadenceBoost -
            speechPenalty -
            silencePenalty

        val clampedScore = rawScore.coerceIn(0.0f, 1.0f)
        lastCommercialScore = clampedScore
        lastIsCommercial = clampedScore >= commercialThreshold
    }
}
