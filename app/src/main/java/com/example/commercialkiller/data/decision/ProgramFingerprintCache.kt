// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import kotlin.math.min

/**
 * Observation from matching an active visual frame against the current scene's learned angle bank.
 */
data class ActiveSceneObservation(
    val isKnownAngle: Boolean,
    val bestAngleSimilarity: Float,
    val activeAngleCount: Int
)

/**
 * Similarity scores comparing a candidate video frame against the pre-break program baseline.
 */
data class ProgramMatchResult(
    val prototypeSimilarity: Float,
    val bugSimilarity: Float,
    val hasStableBug: Boolean,
    val isBaselineReady: Boolean,
    val isFrozen: Boolean
)

private data class CornerBugStats(
    val template: FloatArray,
    val hasStableBug: Boolean
)

/**
 * Maintains two complementary visual prototype sets:
 * 1. An active scene angle bank (`<= 6` views) so shot/reverse-shot camera edits within the same
 *    program scene remain recognized without triggering false scene transitions.
 * 2. A delayed pre-break program ring (`60` frames = `15 s`) that freezes into up to `K = 5`
 *    diverse prototypes and corner watermark stability stats when TV audio is muted.
 */
class ProgramFingerprintCache {

    companion object {
        const val MAX_ACTIVE_SCENE_ANGLES = 6
        const val KNOWN_ANGLE_SIMILARITY_THRESHOLD = 0.78f
        const val MAX_PRE_BREAK_FRAMES = 60
        const val PRE_MUTE_ROLLBACK_MS = 2_000L
        const val MAX_FROZEN_PROTOTYPES = 5
        const val MIN_BASELINE_FRAMES = 8
        const val BUG_MAX_MEAN_VARIANCE = 0.015f
        const val BUG_MIN_CONTRAST = 0.08f
        private const val CORNER_CELLS = 16
        private const val MIN_PROTOTYPE_SEPARATION = 0.02f
    }

    private val activeSceneAngles = ArrayList<VisualFrameDescriptor>(MAX_ACTIVE_SCENE_ANGLES)
    private val preBreakRing = ArrayDeque<VisualFrameDescriptor>(MAX_PRE_BREAK_FRAMES)

    private val frozenPrototypes = ArrayList<VisualFrameDescriptor>(MAX_FROZEN_PROTOTYPES)
    private var frozenBottomRightTemplate = FloatArray(CORNER_CELLS)
    private var frozenBottomLeftTemplate = FloatArray(CORNER_CELLS)
    private var hasStableBottomRightBug = false
    private var hasStableBottomLeftBug = false
    private var isFrozenState = false

    private var liveBottomRightStats = CornerBugStats(FloatArray(CORNER_CELLS), false)
    private var liveBottomLeftStats = CornerBugStats(FloatArray(CORNER_CELLS), false)

    /**
     * Checks whether [descriptor] matches any learned angle in the current program scene.
     * Learns novel non-black angles during confirmed program playback up to [MAX_ACTIVE_SCENE_ANGLES].
     */
    fun observeActiveSceneFrame(
        descriptor: VisualFrameDescriptor,
        allowLearningNewAngle: Boolean
    ): ActiveSceneObservation {
        if (activeSceneAngles.isEmpty()) {
            if (!descriptor.isBlackFrame && allowLearningNewAngle) {
                activeSceneAngles.add(descriptor)
                return ActiveSceneObservation(
                    isKnownAngle = true,
                    bestAngleSimilarity = 1.0f,
                    activeAngleCount = 1
                )
            }
            return ActiveSceneObservation(
                isKnownAngle = !descriptor.isBlackFrame,
                bestAngleSimilarity = if (descriptor.isBlackFrame) 0f else 1.0f,
                activeAngleCount = 0
            )
        }

        var bestSim = 0f
        for (i in activeSceneAngles.indices) {
            val sim = VideoFrameAnalyzer.descriptorSimilarity(descriptor, activeSceneAngles[i])
            if (sim > bestSim) {
                bestSim = sim
            }
        }

        if (descriptor.isBlackFrame) {
            return ActiveSceneObservation(
                isKnownAngle = false,
                bestAngleSimilarity = bestSim,
                activeAngleCount = activeSceneAngles.size
            )
        }

        if (bestSim >= KNOWN_ANGLE_SIMILARITY_THRESHOLD) {
            return ActiveSceneObservation(
                isKnownAngle = true,
                bestAngleSimilarity = bestSim,
                activeAngleCount = activeSceneAngles.size
            )
        }

        if (allowLearningNewAngle && activeSceneAngles.size < MAX_ACTIVE_SCENE_ANGLES) {
            activeSceneAngles.add(descriptor)
        }

        return ActiveSceneObservation(
            isKnownAngle = false,
            bestAngleSimilarity = bestSim,
            activeAngleCount = activeSceneAngles.size
        )
    }

    /**
     * Clears the active shot/reverse-shot angle bank when a macro scene transition commits.
     */
    fun resetActiveSceneAngles() {
        activeSceneAngles.clear()
    }

    /**
     * Appends a confirmed non-commercial program frame to the delayed pre-break ring buffer.
     */
    fun observeProgramFrame(descriptor: VisualFrameDescriptor) {
        if (isFrozenState || descriptor.isBlackFrame) return
        if (preBreakRing.size >= MAX_PRE_BREAK_FRAMES) {
            preBreakRing.removeFirst()
        }
        preBreakRing.addLast(descriptor)

        if (preBreakRing.size >= MIN_BASELINE_FRAMES) {
            val snapshot = preBreakRing.toList()
            liveBottomRightStats = computeCornerStats(snapshot) { it.cornerRoiLuma }
            liveBottomLeftStats = computeCornerStats(snapshot) { it.bottomLeftRoiLuma }
        }
    }

    /**
     * Locks up to [MAX_FROZEN_PROTOTYPES] pre-break program frames excluding the final
     * [PRE_MUTE_ROLLBACK_MS] window prior to [muteTimestampMs] so break-entry frames do not
     * contaminate the frozen baseline.
     */
    fun freezeForBreak(muteTimestampMs: Long) {
        if (isFrozenState || preBreakRing.isEmpty()) return

        val cutoffMs = muteTimestampMs - PRE_MUTE_ROLLBACK_MS
        val rolledBack = preBreakRing.filter { it.timestampMs <= cutoffMs }
        val baseline = if (rolledBack.size >= MIN_BASELINE_FRAMES) {
            rolledBack
        } else {
            preBreakRing.toList()
        }

        if (baseline.size < MIN_BASELINE_FRAMES) return

        frozenPrototypes.clear()
        val chosen = ArrayList<VisualFrameDescriptor>(MAX_FROZEN_PROTOTYPES)
        // Seed with the latest pre-rollback program frame, then greedily maximize min cosine separation.
        chosen.add(baseline.last())

        val targetCount = min(MAX_FROZEN_PROTOTYPES, baseline.size)
        while (chosen.size < targetCount) {
            var bestCandidate: VisualFrameDescriptor? = null
            var bestMinDist = -1f
            for (i in baseline.indices) {
                val candidate = baseline[i]
                var minDist = Float.MAX_VALUE
                for (j in chosen.indices) {
                    val dist = 1f - VideoFrameAnalyzer.descriptorSimilarity(candidate, chosen[j])
                    if (dist < minDist) {
                        minDist = dist
                    }
                }
                if (minDist > bestMinDist) {
                    bestMinDist = minDist
                    bestCandidate = candidate
                }
            }
            if (bestCandidate == null || bestMinDist < MIN_PROTOTYPE_SEPARATION) {
                break
            }
            chosen.add(bestCandidate)
        }
        frozenPrototypes.addAll(chosen)

        val rightStats = computeCornerStats(baseline) { it.cornerRoiLuma }
        val leftStats = computeCornerStats(baseline) { it.bottomLeftRoiLuma }
        frozenBottomRightTemplate = rightStats.template
        hasStableBottomRightBug = rightStats.hasStableBug
        frozenBottomLeftTemplate = leftStats.template
        hasStableBottomLeftBug = leftStats.hasStableBug
        isFrozenState = true
    }

    /**
     * Scores [descriptor] against the frozen break anchor when muted, or the live pre-break ring
     * when enough unmuted program baseline frames exist.
     */
    fun scoreAgainstProgram(descriptor: VisualFrameDescriptor): ProgramMatchResult {
        if (isFrozenState) {
            if (frozenPrototypes.isEmpty()) {
                return ProgramMatchResult(
                    prototypeSimilarity = 0f,
                    bugSimilarity = 0f,
                    hasStableBug = false,
                    isBaselineReady = false,
                    isFrozen = true
                )
            }
            var maxProtoSim = 0f
            for (i in frozenPrototypes.indices) {
                val sim = VideoFrameAnalyzer.descriptorSimilarity(descriptor, frozenPrototypes[i])
                if (sim > maxProtoSim) {
                    maxProtoSim = sim
                }
            }
            val rightSim = if (hasStableBottomRightBug) {
                VideoFrameAnalyzer.cornerSimilarity(descriptor.cornerRoiLuma, frozenBottomRightTemplate)
            } else {
                0f
            }
            val leftSim = if (hasStableBottomLeftBug) {
                VideoFrameAnalyzer.cornerSimilarity(descriptor.bottomLeftRoiLuma, frozenBottomLeftTemplate)
            } else {
                0f
            }
            val anyStable = hasStableBottomRightBug || hasStableBottomLeftBug
            val bestBugSim = if (anyStable) maxOf(rightSim, leftSim) else 0f

            return ProgramMatchResult(
                prototypeSimilarity = maxProtoSim,
                bugSimilarity = bestBugSim,
                hasStableBug = anyStable,
                isBaselineReady = true,
                isFrozen = true
            )
        }

        if (preBreakRing.size < MIN_BASELINE_FRAMES) {
            return ProgramMatchResult(
                prototypeSimilarity = 0f,
                bugSimilarity = 0f,
                hasStableBug = false,
                isBaselineReady = false,
                isFrozen = false
            )
        }

        var maxProtoSim = 0f
        for (frame in preBreakRing) {
            val sim = VideoFrameAnalyzer.descriptorSimilarity(descriptor, frame)
            if (sim > maxProtoSim) {
                maxProtoSim = sim
            }
        }
        val rightSim = if (liveBottomRightStats.hasStableBug) {
            VideoFrameAnalyzer.cornerSimilarity(descriptor.cornerRoiLuma, liveBottomRightStats.template)
        } else {
            0f
        }
        val leftSim = if (liveBottomLeftStats.hasStableBug) {
            VideoFrameAnalyzer.cornerSimilarity(descriptor.bottomLeftRoiLuma, liveBottomLeftStats.template)
        } else {
            0f
        }
        val anyStable = liveBottomRightStats.hasStableBug || liveBottomLeftStats.hasStableBug
        val bestBugSim = if (anyStable) maxOf(rightSim, leftSim) else 0f

        return ProgramMatchResult(
            prototypeSimilarity = maxProtoSim,
            bugSimilarity = bestBugSim,
            hasStableBug = anyStable,
            isBaselineReady = true,
            isFrozen = false
        )
    }

    fun isFrozen(): Boolean = isFrozenState

    fun activeAngleCount(): Int = activeSceneAngles.size

    fun preBreakCount(): Int = preBreakRing.size

    fun frozenPrototypeCount(): Int = frozenPrototypes.size

    fun unfreeze() {
        if (activeSceneAngles.isEmpty() && frozenPrototypes.isNotEmpty()) {
            for (i in frozenPrototypes.indices) {
                if (activeSceneAngles.size >= MAX_ACTIVE_SCENE_ANGLES) break
                activeSceneAngles.add(frozenPrototypes[i])
            }
        }
        isFrozenState = false
        frozenPrototypes.clear()
        hasStableBottomRightBug = false
        hasStableBottomLeftBug = false
        frozenBottomRightTemplate.fill(0f)
        frozenBottomLeftTemplate.fill(0f)
    }

    fun clear() {
        activeSceneAngles.clear()
        preBreakRing.clear()
        liveBottomRightStats = CornerBugStats(FloatArray(CORNER_CELLS), false)
        liveBottomLeftStats = CornerBugStats(FloatArray(CORNER_CELLS), false)
        unfreeze()
    }

    private inline fun computeCornerStats(
        frames: List<VisualFrameDescriptor>,
        selector: (VisualFrameDescriptor) -> FloatArray
    ): CornerBugStats {
        val count = frames.size
        if (count == 0) {
            return CornerBugStats(FloatArray(CORNER_CELLS), false)
        }

        val mean = FloatArray(CORNER_CELLS)
        for (i in 0 until count) {
            val roi = selector(frames[i])
            val limit = min(CORNER_CELLS, roi.size)
            for (c in 0 until limit) {
                mean[c] += roi[c]
            }
        }
        for (c in 0 until CORNER_CELLS) {
            mean[c] /= count.toFloat()
        }

        var varSum = 0.0
        var minCell = Float.MAX_VALUE
        var maxCell = Float.MIN_VALUE
        for (c in 0 until CORNER_CELLS) {
            val cellMean = mean[c]
            if (cellMean < minCell) minCell = cellMean
            if (cellMean > maxCell) maxCell = cellMean

            var sqDiff = 0.0
            for (i in 0 until count) {
                val roi = selector(frames[i])
                val v = if (c < roi.size) roi[c] else 0f
                val d = v - cellMean
                sqDiff += d * d
            }
            varSum += sqDiff / count.toDouble()
        }

        val meanVariance = (varSum / CORNER_CELLS.toDouble()).toFloat()
        val contrast = (maxCell - minCell).coerceAtLeast(0f)
        val hasBug = meanVariance < BUG_MAX_MEAN_VARIANCE && contrast >= BUG_MIN_CONTRAST
        return CornerBugStats(template = mean, hasStableBug = hasBug)
    }
}
