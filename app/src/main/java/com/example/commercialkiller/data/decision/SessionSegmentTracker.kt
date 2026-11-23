// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import android.graphics.Bitmap
import kotlin.math.min

/**
 * High-level broadcast segment classification displayed as equal-length timeline entries.
 */
enum class SegmentLabel {
    PROGRAM,
    COMMERCIAL,
    NO_MEDIA
}

/**
 * Retained equal-width visual+audio timeline entry representing one broadcast segment or break.
 */
data class SessionSegment(
    val id: Long,
    val label: SegmentLabel,
    val startMs: Long,
    val endMs: Long,
    val isActive: Boolean,
    val confidence: Float,
    val transitionReason: String,
    val thumbnail: Bitmap? = null,
    val melStrip: List<FloatArray> = emptyList(),
    val stage2Verdict: String? = null
)

/**
 * Aggregates high-frequency 100 ms [FrameDecision] ticks into macro broadcast timeline segments
 * (typically a dozen or fewer transitions across a program, suppressing transient angle edits
 * and short dramatic pauses), with each entry carrying a fixed-length 16x16 Mel strip and a
 * representative 64x36 video thumbnail retained after session stop.
 */
class SessionSegmentTracker(
    private val onMacroSceneChanged: () -> Unit = {}
) {
    companion object {
        const val CONFIRM_COMMERCIAL_MS = 1_200L
        const val CONFIRM_PROGRAM_FROM_AD_MS = 1_500L
        const val CONFIRM_MEDIA_WAKE_MS = 800L
        const val CONFIRM_NO_MEDIA_MS = 2_000L
        const val CONFIRM_NO_MEDIA_VIDEO_MS = 5_000L
        const val INITIAL_STARTUP_NO_MEDIA_REPLACE_MS = 3_000L
        const val MIN_SEGMENT_DWELL_MS = 3_000L
        const val MACRO_SCENE_MIN_DWELL_MS = 45_000L
        const val MACRO_SCENE_CONFIRM_MS = 8_000L
        const val MACRO_SCENE_MAX_SIMILARITY = 0.45f
        const val THUMBNAIL_SETTLE_MS = 1_000L
        const val MAX_RETAINED_SEGMENTS = 60
        const val MEL_STRIP_COLUMNS = 16
        const val MEL_STRIP_BANDS = 16
        private const val RAW_MEL_BUFFER_LIMIT = 64
    }

    private data class MutableSegment(
        val id: Long,
        val label: SegmentLabel,
        val startMs: Long,
        var endMs: Long,
        var isActive: Boolean,
        var confidence: Float,
        val transitionReason: String,
        var thumbnail: Bitmap?,
        var thumbnailSettled: Boolean,
        val rawMelCols: ArrayList<FloatArray>,
        var melSampleCounter: Int,
        var melSampleStride: Int,
        var stage2Verdict: String? = null
    ) {
        fun appendMelSample(melEnergies: FloatArray) {
            if (melEnergies.isEmpty()) return
            val downsampled = downsampleMelFrame(melEnergies)
            melSampleCounter++
            if ((melSampleCounter - 1) % melSampleStride != 0) return

            rawMelCols.add(downsampled)
            if (rawMelCols.size > RAW_MEL_BUFFER_LIMIT) {
                val compacted = ArrayList<FloatArray>(RAW_MEL_BUFFER_LIMIT / 2 + 1)
                var i = 0
                while (i < rawMelCols.size) {
                    compacted.add(rawMelCols[i])
                    i += 2
                }
                rawMelCols.clear()
                rawMelCols.addAll(compacted)
                melSampleStride *= 2
            }
        }

        fun toImmutable(): SessionSegment {
            val strip: List<FloatArray> = if (rawMelCols.size <= MEL_STRIP_COLUMNS) {
                rawMelCols.toList()
            } else {
                List(MEL_STRIP_COLUMNS) { outIdx ->
                    val srcIdx = ((outIdx.toLong() * rawMelCols.size) / MEL_STRIP_COLUMNS).toInt()
                        .coerceIn(0, rawMelCols.lastIndex)
                    rawMelCols[srcIdx]
                }
            }
            return SessionSegment(
                id = id,
                label = label,
                startMs = startMs,
                endMs = endMs,
                isActive = isActive,
                confidence = confidence,
                transitionReason = transitionReason,
                thumbnail = thumbnail,
                melStrip = strip,
                stage2Verdict = stage2Verdict
            )
        }
    }

    private val segments = ArrayList<MutableSegment>(16)
    private var nextSegmentId = 1L

    private var pendingLabel: SegmentLabel? = null
    private var pendingLabelStartMs = 0L
    private var pendingReason: String = ""

    private var pendingMacroSceneStartMs: Long? = null

    /**
     * Evaluates the current 100 ms frame and returns a pair of (newest-first segments list, newly
     * opened macro segment if a transition occurred on this tick).
     */
    fun onFrame(
        timestampMs: Long,
        decision: FrameDecision,
        melEnergies: FloatArray,
        thumbnail: Bitmap?
    ): Pair<List<SessionSegment>, SessionSegment?> {
        val desiredLabel = classifyLabel(decision)
        val desiredConfidence = computeConfidence(desiredLabel, decision)

        if (segments.isEmpty()) {
            val initial = createSegment(
                label = desiredLabel,
                timestampMs = timestampMs,
                confidence = desiredConfidence,
                reason = initialReasonFor(desiredLabel),
                thumbnail = thumbnail
            )
            segments.add(initial)
            initial.appendMelSample(melEnergies)
            clearPending()
            return snapshot() to initial.toImmutable()
        }

        val current = segments.last()
        current.endMs = timestampMs.coerceAtLeast(current.startMs)
        current.appendMelSample(melEnergies)
        maybeSettleThumbnail(current, timestampMs, thumbnail)

        if (current.label == desiredLabel) {
            current.confidence = current.confidence * 0.85f + desiredConfidence * 0.15f
        }

        val dwellMs = (timestampMs - current.startMs).coerceAtLeast(0L)

        // 1. Hardware control actions (MUTE / UNMUTE) immediately commit macro break transitions.
        if (decision.recommendedAction == TvActionDecision.MUTE && current.label != SegmentLabel.COMMERCIAL) {
            if (segments.size == 1 &&
                current.label == SegmentLabel.NO_MEDIA &&
                dwellMs < INITIAL_STARTUP_NO_MEDIA_REPLACE_MS
            ) {
                val replaced = replaceInitialSegment(
                    current = current,
                    newLabel = SegmentLabel.COMMERCIAL,
                    timestampMs = timestampMs,
                    confidence = desiredConfidence,
                    reason = initialReasonFor(SegmentLabel.COMMERCIAL),
                    thumbnail = thumbnail,
                    melEnergies = melEnergies
                )
                onMacroSceneChanged()
                return snapshot() to replaced
            }
            val opened = commitTransition(
                current = current,
                newLabel = SegmentLabel.COMMERCIAL,
                timestampMs = timestampMs,
                confidence = desiredConfidence,
                reason = "Auto-Mute Triggered",
                thumbnail = thumbnail
            )
            return snapshot() to opened
        }
        if (decision.recommendedAction == TvActionDecision.UNMUTE && current.label == SegmentLabel.COMMERCIAL) {
            val opened = commitTransition(
                current = current,
                newLabel = SegmentLabel.PROGRAM,
                timestampMs = timestampMs,
                confidence = desiredConfidence,
                reason = "Program Return Unmute",
                thumbnail = thumbnail
            )
            return snapshot() to opened
        }

        // 2. Hysteresis-gated label transitions (suppression against micro fluctuations).
        if (desiredLabel != current.label) {
            pendingMacroSceneStartMs = null

            val dwellOk = if (current.label == SegmentLabel.NO_MEDIA) {
                true
            } else {
                dwellMs >= MIN_SEGMENT_DWELL_MS
            }
            if (!dwellOk) {
                clearPending()
                return snapshot() to null
            }

            if (pendingLabel != desiredLabel) {
                pendingLabel = desiredLabel
                pendingLabelStartMs = timestampMs
                pendingReason = reasonForTransition(current.label, desiredLabel, decision)
                return snapshot() to null
            }

            val requiredMs = when {
                current.label == SegmentLabel.NO_MEDIA -> CONFIRM_MEDIA_WAKE_MS
                desiredLabel == SegmentLabel.COMMERCIAL -> CONFIRM_COMMERCIAL_MS
                desiredLabel == SegmentLabel.PROGRAM -> CONFIRM_PROGRAM_FROM_AD_MS
                decision.decisionModality != DecisionModality.AUDIO_ONLY -> CONFIRM_NO_MEDIA_VIDEO_MS
                else -> CONFIRM_NO_MEDIA_MS
            }

            if (timestampMs - pendingLabelStartMs >= requiredMs) {
                if (segments.size == 1 &&
                    current.label == SegmentLabel.NO_MEDIA &&
                    desiredLabel != SegmentLabel.NO_MEDIA &&
                    dwellMs < INITIAL_STARTUP_NO_MEDIA_REPLACE_MS
                ) {
                    val replaced = replaceInitialSegment(
                        current = current,
                        newLabel = desiredLabel,
                        timestampMs = timestampMs,
                        confidence = desiredConfidence,
                        reason = initialReasonFor(desiredLabel),
                        thumbnail = thumbnail,
                        melEnergies = melEnergies
                    )
                    if (desiredLabel == SegmentLabel.COMMERCIAL) {
                        onMacroSceneChanged()
                    }
                    return snapshot() to replaced
                }
                val opened = commitTransition(
                    current = current,
                    newLabel = desiredLabel,
                    timestampMs = timestampMs,
                    confidence = desiredConfidence,
                    reason = pendingReason,
                    thumbnail = thumbnail
                )
                return snapshot() to opened
            }
            return snapshot() to null
        }

        clearPending()

        // 3. Sustained intra-PROGRAM macro scene boundary (ignores A<->B angle cuts completely).
        if (current.label == SegmentLabel.PROGRAM &&
            dwellMs >= MACRO_SCENE_MIN_DWELL_MS &&
            !decision.isKnownSceneAngle &&
            decision.bestAngleSimilarity < MACRO_SCENE_MAX_SIMILARITY
        ) {
            val start = pendingMacroSceneStartMs
            if (start == null) {
                pendingMacroSceneStartMs = timestampMs
            } else if (timestampMs - start >= MACRO_SCENE_CONFIRM_MS) {
                pendingMacroSceneStartMs = null
                onMacroSceneChanged()
                val opened = commitTransition(
                    current = current,
                    newLabel = SegmentLabel.PROGRAM,
                    timestampMs = timestampMs,
                    confidence = desiredConfidence,
                    reason = "Sustained Program Scene Change",
                    thumbnail = thumbnail
                )
                return snapshot() to opened
            }
        } else {
            pendingMacroSceneStartMs = null
        }

        return snapshot() to null
    }

    /**
     * Marks the active trailing segment complete when capture or playback stops, retaining all
     * recorded segments so the user can continue scrolling through the session history.
     */
    fun finalizeActiveSegment(timestampMs: Long): List<SessionSegment> {
        val current = segments.lastOrNull() ?: return emptyList()
        current.endMs = timestampMs.coerceAtLeast(current.startMs)
        current.isActive = false
        clearPending()
        pendingMacroSceneStartMs = null
        return snapshot()
    }

    /**
     * Attaches an asynchronous Stage 2 verification verdict to a previously recorded segment.
     */
    fun attachStage2Verdict(segmentId: Long, verdict: String): List<SessionSegment> {
        for (i in segments.indices) {
            if (segments[i].id == segmentId) {
                segments[i].stage2Verdict = verdict
                break
            }
        }
        return snapshot()
    }

    fun getSegments(): List<SessionSegment> = snapshot()

    fun reset() {
        segments.clear()
        nextSegmentId = 1L
        clearPending()
        pendingMacroSceneStartMs = null
    }

    private fun commitTransition(
        current: MutableSegment,
        newLabel: SegmentLabel,
        timestampMs: Long,
        confidence: Float,
        reason: String,
        thumbnail: Bitmap?
    ): SessionSegment {
        current.endMs = timestampMs.coerceAtLeast(current.startMs)
        current.isActive = false

        val created = createSegment(
            label = newLabel,
            timestampMs = timestampMs,
            confidence = confidence,
            reason = reason,
            thumbnail = thumbnail
        )
        segments.add(created)
        if (segments.size > MAX_RETAINED_SEGMENTS) {
            segments.removeAt(0)
        }
        clearPending()
        pendingMacroSceneStartMs = null
        if (newLabel == SegmentLabel.COMMERCIAL) {
            onMacroSceneChanged()
        }
        return created.toImmutable()
    }

    private fun replaceInitialSegment(
        current: MutableSegment,
        newLabel: SegmentLabel,
        timestampMs: Long,
        confidence: Float,
        reason: String,
        thumbnail: Bitmap?,
        melEnergies: FloatArray
    ): SessionSegment {
        val replaced = MutableSegment(
            id = current.id,
            label = newLabel,
            startMs = current.startMs,
            endMs = timestampMs.coerceAtLeast(current.startMs),
            isActive = true,
            confidence = confidence,
            transitionReason = reason,
            thumbnail = thumbnail ?: current.thumbnail,
            thumbnailSettled = false,
            rawMelCols = ArrayList(current.rawMelCols),
            melSampleCounter = current.melSampleCounter,
            melSampleStride = current.melSampleStride,
            stage2Verdict = null
        )
        replaced.appendMelSample(melEnergies)
        segments[0] = replaced
        clearPending()
        pendingMacroSceneStartMs = null
        return replaced.toImmutable()
    }

    private fun createSegment(
        label: SegmentLabel,
        timestampMs: Long,
        confidence: Float,
        reason: String,
        thumbnail: Bitmap?
    ): MutableSegment {
        return MutableSegment(
            id = nextSegmentId++,
            label = label,
            startMs = timestampMs,
            endMs = timestampMs,
            isActive = true,
            confidence = confidence,
            transitionReason = reason,
            thumbnail = thumbnail,
            thumbnailSettled = false,
            rawMelCols = ArrayList(16),
            melSampleCounter = 0,
            melSampleStride = 1
        )
    }

    private fun maybeSettleThumbnail(
        segment: MutableSegment,
        timestampMs: Long,
        candidate: Bitmap?
    ) {
        if (candidate == null || segment.thumbnailSettled) return
        if (segment.thumbnail == null) {
            segment.thumbnail = candidate
        } else if (timestampMs - segment.startMs >= THUMBNAIL_SETTLE_MS) {
            segment.thumbnail = candidate
            segment.thumbnailSettled = true
        }
    }

    private fun snapshot(): List<SessionSegment> {
        val result = ArrayList<SessionSegment>(segments.size)
        for (i in segments.indices.reversed()) {
            result.add(segments[i].toImmutable())
        }
        return result
    }

    private fun clearPending() {
        pendingLabel = null
        pendingLabelStartMs = 0L
        pendingReason = ""
    }

    private fun classifyLabel(decision: FrameDecision): SegmentLabel {
        return when {
            !decision.isMedia -> SegmentLabel.NO_MEDIA
            decision.isCommercial -> SegmentLabel.COMMERCIAL
            else -> SegmentLabel.PROGRAM
        }
    }

    private fun computeConfidence(label: SegmentLabel, decision: FrameDecision): Float {
        return when (label) {
            SegmentLabel.NO_MEDIA -> (1f - decision.mediaScore).coerceIn(0f, 1f)
            SegmentLabel.COMMERCIAL -> decision.commercialScore.coerceIn(0f, 1f)
            SegmentLabel.PROGRAM -> if (decision.decisionModality == DecisionModality.VIDEO_ONLY_MUTED &&
                decision.programReturnScore > 0f
            ) {
                decision.programReturnScore.coerceIn(0f, 1f)
            } else {
                (1f - decision.commercialScore).coerceIn(0f, 1f)
            }
        }
    }

    private fun initialReasonFor(label: SegmentLabel): String {
        return when (label) {
            SegmentLabel.PROGRAM -> "Program Playback Started"
            SegmentLabel.COMMERCIAL -> "Commercial Detected"
            SegmentLabel.NO_MEDIA -> "Awaiting Broadcast Media"
        }
    }

    private fun reasonForTransition(
        from: SegmentLabel,
        to: SegmentLabel,
        decision: FrameDecision
    ): String {
        return when (to) {
            SegmentLabel.COMMERCIAL -> if (decision.decisionModality == DecisionModality.AUDIO_VIDEO) {
                "Audio+Video Break Detected"
            } else {
                "Audio Commercial Shift"
            }
            SegmentLabel.PROGRAM -> if (from == SegmentLabel.COMMERCIAL) {
                if (decision.decisionModality == DecisionModality.VIDEO_ONLY_MUTED) {
                    "Visual Program Return"
                } else {
                    "Program Resumed"
                }
            } else {
                "Broadcast Media Active"
            }
            SegmentLabel.NO_MEDIA -> if (decision.mediaEnvironment == MediaEnvironment.SILENCE) {
                "Sustained Silence"
            } else {
                "Ambient / Non-Media Noise"
            }
        }
    }
}

private fun downsampleMelFrame(melEnergies: FloatArray): FloatArray {
    val bands = SessionSegmentTracker.MEL_STRIP_BANDS
    val out = FloatArray(bands)
    val srcLen = melEnergies.size
    if (srcLen == 0) return out
    for (b in 0 until bands) {
        val start = (b * srcLen) / bands
        val end = (((b + 1) * srcLen) / bands).coerceAtLeast(start + 1).coerceAtMost(srcLen)
        var sum = 0f
        for (i in start until end) {
            sum += melEnergies[i].coerceIn(0f, 1f)
        }
        out[b] = (sum / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    return out
}
