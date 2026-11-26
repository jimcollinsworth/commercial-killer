// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.audio.ClassifierScore
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import kotlin.math.abs
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

enum class DecisionModality {
    AUDIO_ONLY,
    AUDIO_VIDEO,
    VIDEO_ONLY_MUTED
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
    val recommendedAction: TvActionDecision = TvActionDecision.HOLD,
    val decisionModality: DecisionModality = DecisionModality.AUDIO_ONLY,
    val visualChangeScore: Float = 0f,
    val isKnownSceneAngle: Boolean = false,
    val programReturnScore: Float = 0f,
    val visualCutCadencePerMin: Float = 0f,
    val isProgramAnchorFrozen: Boolean = false,
    val bestAngleSimilarity: Float = 1.0f,
    val broadcastCues: BroadcastCueSignals = BroadcastCueSignals()
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
        const val VISUAL_STALE_TIMEOUT_MS = 1_500L
        const val VISUAL_NOVEL_CUT_THRESHOLD = 0.35f
        const val MIN_MUTE_HOLD_MS = 4_000L
        const val MAX_MUTE_WATCHDOG_MS = 240_000L
        const val UNMUTE_CONSECUTIVE_VIDEO_FRAMES = 6
        const val PROGRAM_RETURN_UNMUTE_THRESHOLD = 0.80f
        const val BLACK_SILENCE_PULSE_HOLD_MS = 2_000L
        const val BLACK_SILENCE_CO_OCCURRENCE_WINDOW_MS = 1_000L
        private const val BLACK_SILENCE_RECENT_MEDIA_MS = 1_500L
        private const val DARKNESS_LUMA_THRESHOLD = 0.16f
        private const val AUDIO_SILENCE_ENERGY_FLOOR = 0.16f
        private const val MIN_CUE_TRIGGER = 0.35f
        private const val PROLONGED_SILENCE_RESET_FRAMES = 20
        private const val RECENT_CUT_WINDOW_MS = 15_000L
        private const val INITIAL_BUFFER_CAPACITY = 128
    }

    val programCache = ProgramFingerprintCache()
    val repeatAdIndex = RepeatAdFingerprintIndex()

    private var sceneSimilarityThreshold: Float = clampSimilarityThreshold(initialSceneSimilarityThreshold)
    private var mediaThreshold: Float = clampMediaThreshold(initialMediaThreshold)

    // Pre-allocated frame buffer reused across 100 ms ticks to prevent per-frame heap allocations.
    private var previousFrameBuffer = FloatArray(INITIAL_BUFFER_CAPACITY)
    private var previousFrameLength = 0

    private var currentShotStartMs = 0L
    private var hasInitializedStart = false
    private val cutTimestampsMs = ArrayDeque<Long>(64)
    private val novelVisualCutTimestampsMs = ArrayDeque<Long>(64)

    private var consecutiveCommercialFrames = 0
    private var consecutiveProgramFrames = 0
    private var consecutiveNonMediaFrames = 0

    private var lastVisualTimestampMs = Long.MIN_VALUE
    private var lastBlackFrameMs = Long.MIN_VALUE
    private var lastMediaTimestampMs = Long.MIN_VALUE
    private var lastDarknessScore = 0f
    private var lastDarkFrameMs = Long.MIN_VALUE
    private var lastAudioSilenceScore = 0f
    private var lastAudioSilenceMs = Long.MIN_VALUE
    private var coOccurrencePeak = 0f
    private var coOccurrencePeakMs = Long.MIN_VALUE
    private var lastLatchedDarkMs = Long.MIN_VALUE
    private var lastLatchedAudioMs = Long.MIN_VALUE
    private var lastAngleObservation = ActiveSceneObservation(
        isKnownAngle = false,
        bestAngleSimilarity = 1.0f,
        activeAngleCount = 0
    )
    private var muteStartMs: Long? = null
    private var consecutiveReturnVideoFrames = 0

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

    private fun sanitizeUnit(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    private fun sanitizeNonNeg(value: Float): Float =
        if (value.isFinite()) value.coerceAtLeast(0f) else 0f

    private fun clampSimilarityThreshold(value: Float): Float {
        return if (!value.isFinite()) {
            DEFAULT_SCENE_SIMILARITY_THRESHOLD
        } else {
            value.coerceIn(MIN_SCENE_SIMILARITY_THRESHOLD, MAX_SCENE_SIMILARITY_THRESHOLD)
        }
    }

    private fun clampMediaThreshold(value: Float): Float {
        return if (!value.isFinite()) {
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
     * and finally gated commercial classification (`is_commercial`) and TV action decisions across
     * `AUDIO_ONLY`, `AUDIO_VIDEO`, or muted `VIDEO_ONLY_MUTED` modes.
     */
    fun evaluateFrame(
        melEnergies: FloatArray,
        classifierTags: List<ClassifierScore>,
        shiftDistance: Float,
        timestampMs: Long,
        visualDescriptor: VisualFrameDescriptor? = null,
        isTvMuted: Boolean = false
    ): FrameDecision {
        val activeVisual = if (visualDescriptor != null &&
            abs(timestampMs - visualDescriptor.timestampMs) <= VISUAL_STALE_TIMEOUT_MS
        ) {
            visualDescriptor
        } else {
            null
        }
        val visualFresh = activeVisual != null

        if (isTvMuted && activeVisual != null) {
            return evaluateMutedVideoOnlyFrame(activeVisual, timestampMs)
        }

        // Returning to unmuted monitoring clears mute-hold tracking and unfrozen break cache.
        muteStartMs = null
        consecutiveReturnVideoFrames = 0
        if (programCache.isFrozen()) {
            programCache.unfreeze()
            clearBreakTrackingOnUnmute()
        }

        updateSceneContinuityInternal(melEnergies, timestampMs)
        evaluateMediaPresenceInternal(melEnergies, classifierTags)
        if (lastIsMedia) {
            lastMediaTimestampMs = timestampMs
        }

        val audioSim = lastSceneSimilarity
        val prelimNovelCuts = novelVisualCutTimestampsMs.size.toFloat()

        if (activeVisual == null) {
            val repeatResult = if (lastIsMedia) {
                repeatAdIndex.peekMatch(
                    timestampMs = timestampMs,
                    melEnergies = melEnergies,
                    visualDescriptor = null,
                    isTvMuted = false,
                    isKnownProgramVisual = false
                )
            } else {
                RepeatAdMatchResult()
            }

            if (lastIsMedia) {
                scoreCommercialInternal(
                    features = melEnergies,
                    classifierTags = classifierTags,
                    shiftDistance = shiftDistance,
                    cutCadencePerMin = lastCutCadencePerMin,
                    repeatAdMatchScore = repeatResult.repeatAdMatchScore
                )
                repeatAdIndex.recordFrame(
                    timestampMs = timestampMs,
                    melEnergies = melEnergies,
                    visualDescriptor = null,
                    isTvMuted = false,
                    isMedia = true,
                    isProgramAnchor = !lastIsCommercial && lastCommercialScore < 0.45f
                )
            } else {
                lastIsCommercial = false
                lastCommercialScore = 0f
            }

            val recommendedAction: TvActionDecision
            if (!lastIsMedia) {
                consecutiveNonMediaFrames++
                if (consecutiveNonMediaFrames > NON_MEDIA_STREAK_RESET_FRAMES) {
                    consecutiveCommercialFrames = 0
                    consecutiveProgramFrames = 0
                }
                recommendedAction = TvActionDecision.HOLD
            } else {
                consecutiveNonMediaFrames = 0
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
                recommendedAction = recommendedAction,
                decisionModality = DecisionModality.AUDIO_ONLY,
                visualChangeScore = sanitizeUnit(visualDescriptor?.visualChangeScore ?: 0f),
                isKnownSceneAngle = false,
                programReturnScore = 0f,
                visualCutCadencePerMin = 0f,
                isProgramAnchorFrozen = programCache.isFrozen(),
                bestAngleSimilarity = 1.0f,
                broadcastCues = BroadcastCueSignals(
                    repeatAdDistance = repeatResult.repeatAdDistance,
                    repeatAdMatchScore = repeatResult.repeatAdMatchScore
                )
            )
        }

        val preMatch = programCache.scoreAgainstProgram(activeVisual)
        val isKnownProgramVisual = preMatch.isBaselineReady &&
            preMatch.prototypeSimilarity >= ProgramFingerprintCache.KNOWN_ANGLE_SIMILARITY_THRESHOLD
        val blackSilencePulse = evaluateBlackSilencePulse(
            melEnergies = melEnergies,
            classifierTags = classifierTags,
            activeVisual = activeVisual,
            timestampMs = timestampMs
        )
        val repeatResult = if (lastIsMedia) {
            repeatAdIndex.peekMatch(
                timestampMs = timestampMs,
                melEnergies = melEnergies,
                visualDescriptor = activeVisual,
                isTvMuted = false,
                isKnownProgramVisual = isKnownProgramVisual
            )
        } else {
            RepeatAdMatchResult()
        }

        if (lastIsMedia) {
            val effectiveCadence = maxOf(lastCutCadencePerMin, prelimNovelCuts)
            scoreCommercialInternal(
                features = melEnergies,
                classifierTags = classifierTags,
                shiftDistance = shiftDistance,
                cutCadencePerMin = effectiveCadence,
                repeatAdMatchScore = repeatResult.repeatAdMatchScore
            )
        }

        val bugIntact = !preMatch.hasStableBug || preMatch.bugSimilarity >= 0.75f
        val canLearnAngle = lastIsMedia && lastCommercialScore < 0.55f && bugIntact

        val vChange = sanitizeUnit(activeVisual.visualChangeScore)
        val isNewVisualSample = activeVisual.timestampMs != lastVisualTimestampMs
        if (isNewVisualSample) {
            lastVisualTimestampMs = activeVisual.timestampMs
            if (activeVisual.isBlackFrame) {
                lastBlackFrameMs = activeVisual.timestampMs
            }
            val obs = programCache.observeActiveSceneFrame(
                descriptor = activeVisual,
                allowLearningNewAngle = canLearnAngle
            )
            lastAngleObservation = obs

            if (!obs.isKnownAngle && !activeVisual.isBlackFrame && vChange >= VISUAL_NOVEL_CUT_THRESHOLD) {
                novelVisualCutTimestampsMs.addLast(activeVisual.timestampMs)
            }
        }
        pruneNovelVisualCuts(timestampMs)

        val visualSim = if (lastAngleObservation.isKnownAngle) {
            maxOf(sanitizeUnit(lastAngleObservation.bestAngleSimilarity), 1f - vChange)
        } else {
            sanitizeUnit(1f - vChange)
        }
        lastSceneSimilarity = sanitizeUnit(0.55f * audioSim + 0.45f * visualSim)
        lastIsSameScene = if (lastAngleObservation.isKnownAngle &&
            lastAngleObservation.bestAngleSimilarity >= ProgramFingerprintCache.KNOWN_ANGLE_SIMILARITY_THRESHOLD
        ) {
            audioSim >= (sceneSimilarityThreshold - 0.12f)
        } else {
            lastSceneSimilarity >= sceneSimilarityThreshold
        }

        val visualCutsPerMin = novelVisualCutTimestampsMs.size.toFloat()
        val cues = BroadcastCueSignals(
            logoMaskStrength = preMatch.logoMaskStrength,
            logoDropoutScore = preMatch.logoDropoutScore,
            blackSilencePulse = blackSilencePulse,
            aspectRatioShift = preMatch.aspectRatioShift,
            repeatAdDistance = repeatResult.repeatAdDistance,
            repeatAdMatchScore = repeatResult.repeatAdMatchScore
        )
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

            val fused = computeAudioVisualCommercialScore(
                audioScore = lastCommercialScore,
                match = preMatch,
                cues = cues,
                visualCutsPerMin = visualCutsPerMin
            )
            lastCommercialScore = fused
            lastIsCommercial = fused >= commercialThreshold

            if (isNewVisualSample && !lastIsCommercial) {
                programCache.observeProgramFrame(activeVisual)
            }

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

            if (recommendedAction == TvActionDecision.MUTE) {
                programCache.freezeForBreak(timestampMs)
            }
        }

        val isProgramFrameForRepeatIndex = !lastIsMedia || (
            !lastIsCommercial && (
                isKnownProgramVisual ||
                    (!preMatch.isBaselineReady && lastCommercialScore < 0.45f) ||
                    (preMatch.isBaselineReady && preMatch.prototypeSimilarity >= 0.68f &&
                        cues.logoDropoutScore < 0.35f && lastCommercialScore < 0.45f)
                )
            )
        repeatAdIndex.recordFrame(
            timestampMs = timestampMs,
            melEnergies = melEnergies,
            visualDescriptor = activeVisual,
            isTvMuted = false,
            isMedia = lastIsMedia,
            isProgramAnchor = isProgramFrameForRepeatIndex
        )

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
            recommendedAction = recommendedAction,
            decisionModality = DecisionModality.AUDIO_VIDEO,
            visualChangeScore = vChange,
            isKnownSceneAngle = lastAngleObservation.isKnownAngle,
            programReturnScore = 0f,
            visualCutCadencePerMin = visualCutsPerMin,
            isProgramAnchorFrozen = programCache.isFrozen(),
            bestAngleSimilarity = sanitizeUnit(lastAngleObservation.bestAngleSimilarity),
            broadcastCues = cues
        )
    }

    private fun evaluateMutedVideoOnlyFrame(
        visualDescriptor: VisualFrameDescriptor,
        timestampMs: Long
    ): FrameDecision {
        if (!programCache.isFrozen()) {
            programCache.freezeForBreak(timestampMs)
        }
        val activeMuteStart = muteStartMs ?: timestampMs.also { muteStartMs = it }
        val muteElapsedMs = (timestampMs - activeMuteStart).coerceAtLeast(0L)

        val isNewVisual = visualDescriptor.timestampMs != lastVisualTimestampMs
        if (isNewVisual) {
            lastVisualTimestampMs = visualDescriptor.timestampMs
            if (visualDescriptor.isBlackFrame) {
                lastBlackFrameMs = visualDescriptor.timestampMs
            }
        }
        pruneNovelVisualCuts(timestampMs)

        val match = programCache.scoreAgainstProgram(visualDescriptor)
        val isKnownProgramVisual = match.isBaselineReady &&
            match.prototypeSimilarity >= ProgramFingerprintCache.KNOWN_ANGLE_SIMILARITY_THRESHOLD
        val repeatResult = repeatAdIndex.peekMatch(
            timestampMs = timestampMs,
            melEnergies = FloatArray(0),
            visualDescriptor = visualDescriptor,
            isTvMuted = true,
            isKnownProgramVisual = isKnownProgramVisual
        )
        if (isNewVisual) {
            repeatAdIndex.recordFrame(
                timestampMs = timestampMs,
                melEnergies = FloatArray(0),
                visualDescriptor = visualDescriptor,
                isTvMuted = true,
                isMedia = true,
                isProgramAnchor = isKnownProgramVisual
            )
        }

        val vChange = sanitizeUnit(visualDescriptor.visualChangeScore)
        val vSim = sanitizeUnit(1f - vChange)
        lastSceneSimilarity = vSim
        lastIsSameScene = vSim >= sceneSimilarityThreshold

        if (isNewVisual && !visualDescriptor.isBlackFrame &&
            vChange >= VISUAL_NOVEL_CUT_THRESHOLD &&
            match.prototypeSimilarity < ProgramFingerprintCache.KNOWN_ANGLE_SIMILARITY_THRESHOLD
        ) {
            novelVisualCutTimestampsMs.addLast(visualDescriptor.timestampMs)
            pruneNovelVisualCuts(timestampMs)
        }

        val visualCutsPerMin = novelVisualCutTimestampsMs.size.toFloat()
        val recentCuts15s = countRecentVisualCuts(timestampMs)

        val returnScore = if (!match.isBaselineReady || visualDescriptor.isBlackFrame) {
            0f
        } else {
            val sProto = sanitizeUnit(match.prototypeSimilarity)
            val sBug = sanitizeUnit(match.bugSimilarity)
            val sCadence = if (recentCuts15s <= 1 || sProto >= 0.80f) {
                1.0f
            } else {
                sanitizeUnit(1.0f - ((recentCuts15s - 1) / 4f))
            }
            val gatedCadence = if (sProto >= 0.70f) sCadence else 0f
            val baseReturn = if (match.hasStableBug) {
                sanitizeUnit(0.45f * sProto + 0.35f * sBug + 0.20f * gatedCadence)
            } else {
                sanitizeUnit(0.80f * sProto + 0.20f * gatedCadence)
            }
            sanitizeUnit(baseReturn - 0.25f * sanitizeUnit(match.aspectRatioShift) - 0.25f * sanitizeUnit(repeatResult.repeatAdMatchScore))
        }

        if (isNewVisual) {
            if (muteElapsedMs >= MIN_MUTE_HOLD_MS && returnScore >= PROGRAM_RETURN_UNMUTE_THRESHOLD) {
                consecutiveReturnVideoFrames++
            } else {
                consecutiveReturnVideoFrames = 0
            }
        }

        val watchdogReached = muteElapsedMs >= MAX_MUTE_WATCHDOG_MS
        val sustainedReturn = muteElapsedMs >= MIN_MUTE_HOLD_MS &&
            consecutiveReturnVideoFrames >= UNMUTE_CONSECUTIVE_VIDEO_FRAMES

        val action = if (watchdogReached || sustainedReturn) {
            programCache.unfreeze()
            clearBreakTrackingOnUnmute()
            muteStartMs = null
            consecutiveReturnVideoFrames = 0
            TvActionDecision.UNMUTE
        } else {
            TvActionDecision.HOLD
        }

        lastIsMedia = true
        lastMediaScore = 1.0f
        lastMediaEnvironment = MediaEnvironment.BROADCAST_MEDIA
        lastIsCommercial = action != TvActionDecision.UNMUTE
        lastCommercialScore = (1f - returnScore).coerceIn(0f, 1f)

        return FrameDecision(
            isMedia = true,
            mediaScore = 1.0f,
            mediaEnvironment = MediaEnvironment.BROADCAST_MEDIA,
            isCommercial = lastIsCommercial,
            commercialScore = lastCommercialScore,
            isSameScene = lastIsSameScene,
            sceneSimilarity = vSim,
            shotDurationMs = muteElapsedMs,
            cutCadencePerMin = visualCutsPerMin,
            recommendedAction = action,
            decisionModality = DecisionModality.VIDEO_ONLY_MUTED,
            visualChangeScore = vChange,
            isKnownSceneAngle = match.prototypeSimilarity >= ProgramFingerprintCache.KNOWN_ANGLE_SIMILARITY_THRESHOLD,
            programReturnScore = returnScore,
            visualCutCadencePerMin = visualCutsPerMin,
            isProgramAnchorFrozen = programCache.isFrozen(),
            bestAngleSimilarity = sanitizeUnit(match.prototypeSimilarity),
            broadcastCues = BroadcastCueSignals(
                logoMaskStrength = match.logoMaskStrength,
                logoDropoutScore = match.logoDropoutScore,
                blackSilencePulse = 0f,
                aspectRatioShift = match.aspectRatioShift,
                repeatAdDistance = repeatResult.repeatAdDistance,
                repeatAdMatchScore = repeatResult.repeatAdMatchScore
            )
        )
    }

    private fun evaluateBlackSilencePulse(
        melEnergies: FloatArray,
        classifierTags: List<ClassifierScore>,
        activeVisual: VisualFrameDescriptor,
        timestampMs: Long
    ): Float {
        val safeLuma = sanitizeUnit(activeVisual.meanLuma)
        val darkness = sanitizeUnit((DARKNESS_LUMA_THRESHOLD - safeLuma) / DARKNESS_LUMA_THRESHOLD)
        if (darkness >= MIN_CUE_TRIGGER) {
            lastDarknessScore = darkness
            lastDarkFrameMs = activeVisual.timestampMs
        }

        var meanMelEnergy = 0f
        if (melEnergies.isNotEmpty()) {
            var sum = 0.0
            for (i in melEnergies.indices) {
                sum += sanitizeNonNeg(melEnergies[i])
            }
            val avg = (sum / melEnergies.size.toDouble()).toFloat()
            meanMelEnergy = if (avg.isFinite()) avg else Float.MAX_VALUE
        }
        var maxSilenceTag = 0f
        for (i in classifierTags.indices) {
            val tag = classifierTags[i]
            if (tag.label.contains("silence", ignoreCase = true) ||
                tag.label.contains("quiet", ignoreCase = true)
            ) {
                val s = sanitizeUnit(tag.score)
                if (s > maxSilenceTag) maxSilenceTag = s
            }
        }
        val audioDip = maxOf(
            maxSilenceTag,
            sanitizeUnit((AUDIO_SILENCE_ENERGY_FLOOR - meanMelEnergy) / AUDIO_SILENCE_ENERGY_FLOOR)
        )
        if (audioDip >= MIN_CUE_TRIGGER) {
            lastAudioSilenceScore = audioDip
            lastAudioSilenceMs = timestampMs
        }

        val effectiveNonMediaStreak = if (!lastIsMedia) consecutiveNonMediaFrames + 1 else 0
        if (!lastIsMedia && effectiveNonMediaStreak >= PROLONGED_SILENCE_RESET_FRAMES) {
            coOccurrencePeak = 0f
            coOccurrencePeakMs = Long.MIN_VALUE
            return 0f
        }

        val currentDecayed = if (coOccurrencePeakMs != Long.MIN_VALUE) {
            val elapsed = (timestampMs - coOccurrencePeakMs).coerceAtLeast(0L)
            sanitizeUnit(coOccurrencePeak * (1f - elapsed.toFloat() / BLACK_SILENCE_PULSE_HOLD_MS.toFloat()))
        } else {
            0f
        }

        val recentMedia = lastMediaTimestampMs != Long.MIN_VALUE &&
            (timestampMs - lastMediaTimestampMs) in 0L..BLACK_SILENCE_RECENT_MEDIA_MS
        val darkRecent = lastDarkFrameMs != Long.MIN_VALUE &&
            (timestampMs - lastDarkFrameMs) in 0L..BLACK_SILENCE_CO_OCCURRENCE_WINDOW_MS
        val audioRecent = lastAudioSilenceMs != Long.MIN_VALUE &&
            (timestampMs - lastAudioSilenceMs) in 0L..BLACK_SILENCE_CO_OCCURRENCE_WINDOW_MS

        if (recentMedia && darkRecent && audioRecent &&
            (lastDarkFrameMs > lastLatchedDarkMs || lastAudioSilenceMs > lastLatchedAudioMs)
        ) {
            val gap = abs(lastDarkFrameMs - lastAudioSilenceMs)
            if (gap <= BLACK_SILENCE_CO_OCCURRENCE_WINDOW_MS) {
                val rawPulse = sanitizeUnit(lastDarknessScore * lastAudioSilenceScore * (1f - gap.toFloat() / 1_200f))
                if (rawPulse > currentDecayed) {
                    coOccurrencePeak = rawPulse
                    coOccurrencePeakMs = timestampMs
                    lastLatchedDarkMs = lastDarkFrameMs
                    lastLatchedAudioMs = lastAudioSilenceMs
                    return rawPulse
                }
            }
        }

        return currentDecayed
    }

    private fun computeAudioVisualCommercialScore(
        audioScore: Float,
        match: ProgramMatchResult,
        cues: BroadcastCueSignals,
        visualCutsPerMin: Float
    ): Float {
        val safeAudioScore = sanitizeUnit(audioScore)
        val novelCadenceBoost = if (visualCutsPerMin.isFinite() && visualCutsPerMin >= 8f) 0.15f else 0f
        val cueComposite = sanitizeUnit(
            0.35f * sanitizeUnit(cues.logoDropoutScore) +
                0.25f * sanitizeUnit(cues.blackSilencePulse) +
                0.20f * sanitizeUnit(cues.aspectRatioShift) +
                0.20f * sanitizeUnit(cues.repeatAdMatchScore)
        )

        if (!match.isBaselineReady) {
            return sanitizeUnit(
                safeAudioScore + 0.22f * sanitizeUnit(cues.blackSilencePulse) +
                    0.20f * sanitizeUnit(cues.repeatAdMatchScore) + novelCadenceBoost
            )
        }

        val bugIntact = !match.hasStableBug || match.bugSimilarity >= 0.78f
        val cuesCalm = cues.logoDropoutScore < 0.35f &&
            cues.aspectRatioShift < 0.30f &&
            cues.repeatAdMatchScore < 0.50f &&
            cues.blackSilencePulse < 0.45f
        if ((lastAngleObservation.isKnownAngle || match.prototypeSimilarity >= 0.82f) &&
            match.prototypeSimilarity >= 0.78f &&
            bugIntact &&
            cuesCalm
        ) {
            return sanitizeUnit(safeAudioScore - 0.18f)
        }

        val protoDivergence = sanitizeUnit(1f - sanitizeUnit(match.prototypeSimilarity))
        val visualBreakScore = sanitizeUnit(protoDivergence * 0.45f + cueComposite * 0.40f + novelCadenceBoost)
        return sanitizeUnit(
            maxOf(
                safeAudioScore * 0.85f,
                safeAudioScore * 0.70f + visualBreakScore * 0.55f
            )
        )
    }

    private fun pruneNovelVisualCuts(timestampMs: Long) {
        val windowStart = timestampMs - CUT_WINDOW_MS
        while (novelVisualCutTimestampsMs.isNotEmpty() && novelVisualCutTimestampsMs.first() < windowStart) {
            novelVisualCutTimestampsMs.removeFirst()
        }
    }

    private fun countRecentVisualCuts(timestampMs: Long): Int {
        val start = timestampMs - RECENT_CUT_WINDOW_MS
        var count = 0
        for (ts in novelVisualCutTimestampsMs) {
            if (ts >= start) count++
        }
        return count
    }

    fun onManualUnmute() {
        programCache.unfreeze()
        clearBreakTrackingOnUnmute()
        muteStartMs = null
        consecutiveReturnVideoFrames = 0
    }

    private fun clearBreakTrackingOnUnmute() {
        cutTimestampsMs.clear()
        novelVisualCutTimestampsMs.clear()
        consecutiveCommercialFrames = 0
        consecutiveProgramFrames = 0
        lastBlackFrameMs = Long.MIN_VALUE
        lastMediaTimestampMs = Long.MIN_VALUE
        lastDarknessScore = 0f
        lastDarkFrameMs = Long.MIN_VALUE
        lastAudioSilenceScore = 0f
        lastAudioSilenceMs = Long.MIN_VALUE
        coOccurrencePeak = 0f
        coOccurrencePeakMs = Long.MIN_VALUE
        lastLatchedDarkMs = Long.MIN_VALUE
        lastLatchedAudioMs = Long.MIN_VALUE
        previousFrameLength = 0
        hasInitializedStart = false
    }

    fun resetActiveSceneAngles() {
        programCache.resetActiveSceneAngles()
    }

    fun reset() {
        previousFrameLength = 0
        previousFrameBuffer.fill(0f)
        currentShotStartMs = 0L
        hasInitializedStart = false
        cutTimestampsMs.clear()
        novelVisualCutTimestampsMs.clear()
        consecutiveCommercialFrames = 0
        consecutiveProgramFrames = 0
        consecutiveNonMediaFrames = 0
        lastVisualTimestampMs = Long.MIN_VALUE
        lastBlackFrameMs = Long.MIN_VALUE
        lastMediaTimestampMs = Long.MIN_VALUE
        lastDarknessScore = 0f
        lastDarkFrameMs = Long.MIN_VALUE
        lastAudioSilenceScore = 0f
        lastAudioSilenceMs = Long.MIN_VALUE
        coOccurrencePeak = 0f
        coOccurrencePeakMs = Long.MIN_VALUE
        lastLatchedDarkMs = Long.MIN_VALUE
        lastLatchedAudioMs = Long.MIN_VALUE
        lastAngleObservation = ActiveSceneObservation(
            isKnownAngle = false,
            bestAngleSimilarity = 1.0f,
            activeAngleCount = 0
        )
        muteStartMs = null
        consecutiveReturnVideoFrames = 0
        programCache.clear()
        repeatAdIndex.reset()
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
            val score = sanitizeUnit(tag.score)
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
                label.contains("monologue", ignoreCase = true) ||
                label.contains("conversation", ignoreCase = true) ||
                label.contains("advertisement", ignoreCase = true) ||
                label.contains("promo", ignoreCase = true) ||
                label.contains("laughter", ignoreCase = true) ||
                label.contains("applause", ignoreCase = true) ||
                label.contains("cheering", ignoreCase = true) ||
                label.contains("crowd", ignoreCase = true) ||
                label.contains("announcer", ignoreCase = true) ||
                label.contains("instrument", ignoreCase = true)
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
            val e = sanitizeNonNeg(features[i])
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
        if (!meanEnergy.isFinite() || meanEnergy < 0.08f || maxSilenceTag >= 0.70f) {
            lastMediaScore = if (maxSilenceTag >= 0.70f) {
                (1f - maxSilenceTag).coerceIn(0f, 0.20f)
            } else if (!meanEnergy.isFinite()) {
                0f
            } else {
                sanitizeUnit(meanEnergy / 0.08f * 0.25f).coerceAtMost(0.25f)
            }
            lastIsMedia = false
            lastMediaEnvironment = MediaEnvironment.SILENCE
            return
        }

        val lowMean = (lowSum / lowEnd.coerceAtLeast(1).toDouble()).toFloat()
        val midLen = (midEnd - midStart).coerceAtLeast(1)
        val midMean = (midSum / midLen.toDouble()).toFloat()
        val safeMidMin = if (midMin == Float.MAX_VALUE || !midMin.isFinite()) 0f else midMin
        val safeMidMax = if (midMax.isFinite()) midMax else 0f
        val midContrast = if (safeMidMax > 1e-4f) {
            sanitizeUnit((safeMidMax - safeMidMin) / (safeMidMax + 1e-4f))
        } else {
            0f
        }

        val safeLowMean = if (lowMean.isFinite()) lowMean else 0f
        val safeMidMean = if (midMean.isFinite()) midMean else 0f
        val lowDominanceRatio = safeLowMean / (safeMidMean + 1e-4f)
        val rumblePenalty = if (lowDominanceRatio.isFinite() && lowDominanceRatio > 2.2f) {
            sanitizeUnit((lowDominanceRatio - 2.2f) / 2.0f) * 0.45f
        } else {
            0f
        }
        val flatNoisePenalty = if (maxMediaTag < 0.40f && midContrast < 0.25f) 0.30f else 0f

        val rawMediaScore = (maxMediaTag * 0.55f) +
            (sanitizeUnit(safeMidMean / 0.35f) * 0.30f) +
            (sanitizeUnit(midContrast / 0.60f) * 0.25f) -
            (maxNonMediaTag * 0.55f) -
            rumblePenalty -
            flatNoisePenalty

        lastMediaScore = sanitizeUnit(rawMediaScore)
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
                val curr = sanitizeNonNeg(currentFeatures[i]).toDouble()
                val prev = sanitizeNonNeg(previousFrameBuffer[i]).toDouble()
                dot += curr * prev
                normCurrSq += curr * curr
                normPrevSq += prev * prev
            }

            if (normCurrSq == 0.0 || normPrevSq == 0.0 || !normCurrSq.isFinite() || !normPrevSq.isFinite()) {
                similarity = 1.0f
                isSameScene = true
            } else {
                val denom = sqrt(normCurrSq) * sqrt(normPrevSq)
                if (denom == 0.0 || !denom.isFinite()) {
                    similarity = 1.0f
                    isSameScene = true
                } else {
                    val rawSim = (dot / denom).toFloat()
                    similarity = if (rawSim.isFinite()) rawSim.coerceIn(0.0f, 1.0f) else 1.0f
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
            for (i in 0 until bandCount) {
                previousFrameBuffer[i] = sanitizeNonNeg(currentFeatures[i])
            }
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
        cutCadencePerMin: Float,
        repeatAdMatchScore: Float = 0f
    ) {
        // (a) Classifier tag probabilities (indexed loop avoids iterator heap allocations).
        var maxCommercialTagScore = 0f
        var maxMusicTagScore = 0f
        var maxSingingTagScore = 0f
        var maxProgramSpeechScore = 0f
        var maxSilenceScore = 0f

        for (i in classifierTags.indices) {
            val tag = classifierTags[i]
            val score = sanitizeUnit(tag.score)
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
                val energy = sanitizeNonNeg(features[i])
                sum += energy
                if (energy >= 0.25f) {
                    filledBands++
                }
            }
            val n = features.size.toDouble()
            val mean = (sum / n).toFloat()

            if (mean.isFinite() && mean > 0.05f) {
                var sqDiffSum = 0.0
                for (i in features.indices) {
                    val energy = sanitizeNonNeg(features[i])
                    val diff = energy - mean
                    sqDiffSum += diff * diff
                }
                val stdDev = sqrt(sqDiffSum / n).toFloat()
                val coeffOfVariation = if (stdDev.isFinite()) stdDev / mean else 1.2f

                val loudnessFactor = sanitizeUnit(mean / 0.65f)
                val fillRatio = filledBands.toFloat() / features.size.toFloat()
                val uniformityFactor = if (mean > 0.15f && coeffOfVariation.isFinite()) {
                    sanitizeUnit(1.0f - (coeffOfVariation / 1.2f))
                } else {
                    0f
                }
                val energyGate = sanitizeUnit(mean / 0.30f)
                compressionDensity = sanitizeUnit(
                    (0.45f * loudnessFactor + 0.30f * fillRatio + 0.25f * uniformityFactor) * energyGate
                )
            }
        }

        val safeShiftDistance = sanitizeNonNeg(shiftDistance)
        val shiftBoost = sanitizeUnit(safeShiftDistance / 0.50f) * 0.15f

        // (c) Rolling cut cadence boost at >= 15 cuts/min.
        val safeCadence = sanitizeNonNeg(cutCadencePerMin)
        val cadenceBoost = if (safeCadence >= HIGH_CADENCE_CUTS_PER_MIN) {
            0.15f + sanitizeUnit((safeCadence - HIGH_CADENCE_CUTS_PER_MIN) / 30f) * 0.10f
        } else {
            0f
        }

        val repeatBoost = 0.14f * sanitizeUnit(repeatAdMatchScore)

        val rawScore = (positiveTagScore * 0.58f) +
            (compressionDensity * 0.40f) +
            shiftBoost +
            cadenceBoost +
            repeatBoost -
            speechPenalty -
            silencePenalty

        val clampedScore = sanitizeUnit(rawScore)
        val safeThreshold = if (commercialThreshold.isFinite()) commercialThreshold else DEFAULT_COMMERCIAL_THRESHOLD
        lastCommercialScore = clampedScore
        lastIsCommercial = clampedScore >= safeThreshold
    }
}
