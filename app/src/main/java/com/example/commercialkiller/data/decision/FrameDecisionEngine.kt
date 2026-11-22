// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.audio.ClassifierScore
import kotlin.math.sqrt

enum class TvActionDecision {
    MUTE,
    UNMUTE,
    HOLD
}

data class FrameDecision(
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
 * Per-frame non-autoregressive decision model evaluating scene continuity via cosine similarity
 * and commercial probability from classifier tags, spectral compression density, shift distance,
 * and rolling scene-cut cadence.
 */
class FrameDecisionEngine(
    initialSceneSimilarityThreshold: Float = DEFAULT_SCENE_SIMILARITY_THRESHOLD,
    var commercialThreshold: Float = DEFAULT_COMMERCIAL_THRESHOLD
) {
    companion object {
        const val MIN_SCENE_SIMILARITY_THRESHOLD = 0.50f
        const val MAX_SCENE_SIMILARITY_THRESHOLD = 0.99f
        const val DEFAULT_SCENE_SIMILARITY_THRESHOLD = 0.85f
        const val DEFAULT_COMMERCIAL_THRESHOLD = 0.75f
        const val CUT_WINDOW_MS = 60_000L
        const val HIGH_CADENCE_CUTS_PER_MIN = 15f
        const val MUTE_CONSECUTIVE_COMMERCIAL_FRAMES = 3
        const val UNMUTE_CONSECUTIVE_PROGRAM_FRAMES = 5
        private const val INITIAL_BUFFER_CAPACITY = 128
    }

    private var sceneSimilarityThreshold: Float = clampSimilarityThreshold(initialSceneSimilarityThreshold)

    // Pre-allocated frame buffer reused across 100 ms ticks to prevent per-frame heap allocations.
    private var previousFrameBuffer = FloatArray(INITIAL_BUFFER_CAPACITY)
    private var previousFrameLength = 0

    private var currentShotStartMs = 0L
    private var hasInitializedStart = false
    private val cutTimestampsMs = ArrayDeque<Long>(64)

    private var consecutiveCommercialFrames = 0
    private var consecutiveProgramFrames = 0

    // Internal scratch fields populated by in-place helpers to avoid intermediate object allocations.
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

    private fun clampSimilarityThreshold(value: Float): Float {
        return if (value.isNaN()) {
            DEFAULT_SCENE_SIMILARITY_THRESHOLD
        } else {
            value.coerceIn(MIN_SCENE_SIMILARITY_THRESHOLD, MAX_SCENE_SIMILARITY_THRESHOLD)
        }
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
     * Evaluates both scene continuity and commercial probability for a single 100 ms audio frame
     * and computes the gated TV mute/unmute/hold decision.
     */
    fun evaluateFrame(
        melEnergies: FloatArray,
        classifierTags: List<ClassifierScore>,
        shiftDistance: Float,
        timestampMs: Long
    ): FrameDecision {
        updateSceneContinuityInternal(melEnergies, timestampMs)
        scoreCommercialInternal(melEnergies, classifierTags, shiftDistance, lastCutCadencePerMin)

        if (lastIsCommercial) {
            consecutiveCommercialFrames++
            consecutiveProgramFrames = 0
        } else {
            consecutiveProgramFrames++
            consecutiveCommercialFrames = 0
        }

        val recommendedAction = when {
            lastIsCommercial && (!lastIsSameScene || consecutiveCommercialFrames >= MUTE_CONSECUTIVE_COMMERCIAL_FRAMES) ->
                TvActionDecision.MUTE
            !lastIsCommercial && consecutiveProgramFrames >= UNMUTE_CONSECUTIVE_PROGRAM_FRAMES ->
                TvActionDecision.UNMUTE
            else -> TvActionDecision.HOLD
        }

        return FrameDecision(
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
        lastIsSameScene = true
        lastSceneSimilarity = 1.0f
        lastShotDurationMs = 0L
        lastCutCadencePerMin = 0f
        lastIsCommercial = false
        lastCommercialScore = 0f
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
