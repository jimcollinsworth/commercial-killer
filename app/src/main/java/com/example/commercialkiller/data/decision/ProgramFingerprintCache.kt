// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

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
    val isFrozen: Boolean,
    val logoMaskStrength: Float = 0f,
    val logoDropoutScore: Float = 0f,
    val aspectRatioShift: Float = 0f
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
        private const val EDGE_CORNERS = 4
        private const val EDGE_MASK_TOTAL = 64
        private const val EDGE_EMA_ALPHA = 0.08f
        private const val EDGE_MIN_PEAK = 0.12f
        private const val EDGE_MIN_MEAN = 0.04f
        private const val EDGE_STABLE_STRENGTH_THRESHOLD = 0.35f
        private const val ASPECT_EMA_ALPHA = 0.10f
        private const val ASPECT_SHIFT_NORM = 0.25f
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

    private val liveCornerEdgeEma = FloatArray(EDGE_MASK_TOTAL)
    private val liveCornerEdgeVar = FloatArray(EDGE_MASK_TOTAL)
    private var liveEdgeFrameCount = 0
    private var liveCornerStrength = FloatArray(EDGE_CORNERS)
    private var liveLetterboxEma = 0f
    private var livePillarboxEma = 0f
    private var liveAspectCount = 0

    private var frozenCornerEdgeMask = FloatArray(EDGE_MASK_TOTAL)
    private var frozenCornerStrength = FloatArray(EDGE_CORNERS)
    private var hasFrozenEdgeState = false
    private var frozenLetterbox = 0f
    private var frozenPillarbox = 0f
    private var hasFrozenAspect = false

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

        val edges = descriptor.cornerEdgeMasks
        if (liveEdgeFrameCount == 0) {
            val limit = min(EDGE_MASK_TOTAL, edges.size)
            for (i in 0 until limit) {
                liveCornerEdgeEma[i] = edges[i]
            }
            for (i in limit until EDGE_MASK_TOTAL) {
                liveCornerEdgeEma[i] = 0f
            }
            liveCornerEdgeVar.fill(0f)
        } else {
            for (i in 0 until EDGE_MASK_TOTAL) {
                val v = if (i < edges.size) edges[i] else 0f
                val diff = v - liveCornerEdgeEma[i]
                liveCornerEdgeEma[i] += EDGE_EMA_ALPHA * diff
                liveCornerEdgeVar[i] = (1f - EDGE_EMA_ALPHA) * liveCornerEdgeVar[i] + EDGE_EMA_ALPHA * diff * diff
            }
        }
        liveEdgeFrameCount++
        if (liveEdgeFrameCount >= MIN_BASELINE_FRAMES) {
            liveCornerStrength = computeCornerEdgeStrengths(liveCornerEdgeEma, liveCornerEdgeVar)
        }

        if (liveAspectCount == 0) {
            liveLetterboxEma = descriptor.letterboxRatio
            livePillarboxEma = descriptor.pillarboxRatio
        } else {
            liveLetterboxEma += ASPECT_EMA_ALPHA * (descriptor.letterboxRatio - liveLetterboxEma)
            livePillarboxEma += ASPECT_EMA_ALPHA * (descriptor.pillarboxRatio - livePillarboxEma)
        }
        liveAspectCount++

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

        val meanEdge = FloatArray(EDGE_MASK_TOTAL)
        val varEdge = FloatArray(EDGE_MASK_TOTAL)
        val baselineCount = baseline.size.toFloat()
        for (i in baseline.indices) {
            val edges = baseline[i].cornerEdgeMasks
            val limit = min(EDGE_MASK_TOTAL, edges.size)
            for (k in 0 until limit) {
                meanEdge[k] += edges[k]
            }
        }
        for (k in 0 until EDGE_MASK_TOTAL) {
            meanEdge[k] /= baselineCount
        }
        for (i in baseline.indices) {
            val edges = baseline[i].cornerEdgeMasks
            val limit = min(EDGE_MASK_TOTAL, edges.size)
            for (k in 0 until EDGE_MASK_TOTAL) {
                val d = edges[k] - meanEdge[k]
                varEdge[k] += d * d
            }
        }
        for (k in 0 until EDGE_MASK_TOTAL) {
            varEdge[k] /= baselineCount
        }
        frozenCornerEdgeMask = meanEdge
        frozenCornerStrength = computeCornerEdgeStrengths(meanEdge, varEdge)
        hasFrozenEdgeState = true

        var lbSum = 0f
        var pbSum = 0f
        for (i in baseline.indices) {
            lbSum += baseline[i].letterboxRatio
            pbSum += baseline[i].pillarboxRatio
        }
        frozenLetterbox = lbSum / baselineCount
        frozenPillarbox = pbSum / baselineCount
        hasFrozenAspect = true

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
            val hasLumaBug = hasStableBottomRightBug || hasStableBottomLeftBug
            val lumaBugSim = if (hasLumaBug) maxOf(rightSim, leftSim) else 0f

            val edgeResult = if (hasFrozenEdgeState) {
                scoreEdgeAndAspect(
                    descriptor = descriptor,
                    maskTemplate = frozenCornerEdgeMask,
                    cornerStrengths = frozenCornerStrength,
                    baseLb = if (hasFrozenAspect) frozenLetterbox else 0f,
                    basePb = if (hasFrozenAspect) frozenPillarbox else 0f,
                    aspectReady = hasFrozenAspect
                )
            } else {
                EdgeAspectScore(0f, 0f, 0f, 0f)
            }
            val hasEdgeBug = edgeResult.logoMaskStrength >= EDGE_STABLE_STRENGTH_THRESHOLD
            val anyStable = hasLumaBug || hasEdgeBug
            val bestBugSim = if (anyStable) {
                maxOf(
                    if (hasLumaBug) lumaBugSim else 0f,
                    if (hasEdgeBug) edgeResult.logoPresence else 0f
                )
            } else {
                0f
            }

            return ProgramMatchResult(
                prototypeSimilarity = maxProtoSim,
                bugSimilarity = bestBugSim,
                hasStableBug = anyStable,
                isBaselineReady = true,
                isFrozen = true,
                logoMaskStrength = edgeResult.logoMaskStrength,
                logoDropoutScore = edgeResult.logoDropoutScore,
                aspectRatioShift = edgeResult.aspectRatioShift
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
        val hasLumaBug = liveBottomRightStats.hasStableBug || liveBottomLeftStats.hasStableBug
        val lumaBugSim = if (hasLumaBug) maxOf(rightSim, leftSim) else 0f

        val edgeResult = scoreEdgeAndAspect(
            descriptor = descriptor,
            maskTemplate = liveCornerEdgeEma,
            cornerStrengths = liveCornerStrength,
            baseLb = liveLetterboxEma,
            basePb = livePillarboxEma,
            aspectReady = liveAspectCount >= MIN_BASELINE_FRAMES
        )
        val hasEdgeBug = edgeResult.logoMaskStrength >= EDGE_STABLE_STRENGTH_THRESHOLD
        val anyStable = hasLumaBug || hasEdgeBug
        val bestBugSim = if (anyStable) {
            maxOf(
                if (hasLumaBug) lumaBugSim else 0f,
                if (hasEdgeBug) edgeResult.logoPresence else 0f
            )
        } else {
            0f
        }

        return ProgramMatchResult(
            prototypeSimilarity = maxProtoSim,
            bugSimilarity = bestBugSim,
            hasStableBug = anyStable,
            isBaselineReady = true,
            isFrozen = false,
            logoMaskStrength = edgeResult.logoMaskStrength,
            logoDropoutScore = edgeResult.logoDropoutScore,
            aspectRatioShift = edgeResult.aspectRatioShift
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
        frozenCornerEdgeMask.fill(0f)
        frozenCornerStrength.fill(0f)
        hasFrozenEdgeState = false
        frozenLetterbox = 0f
        frozenPillarbox = 0f
        hasFrozenAspect = false
    }

    fun clear() {
        activeSceneAngles.clear()
        preBreakRing.clear()
        liveBottomRightStats = CornerBugStats(FloatArray(CORNER_CELLS), false)
        liveBottomLeftStats = CornerBugStats(FloatArray(CORNER_CELLS), false)
        liveCornerEdgeEma.fill(0f)
        liveCornerEdgeVar.fill(0f)
        liveEdgeFrameCount = 0
        liveCornerStrength.fill(0f)
        liveLetterboxEma = 0f
        livePillarboxEma = 0f
        liveAspectCount = 0
        unfreeze()
    }

    private data class EdgeAspectScore(
        val logoMaskStrength: Float,
        val logoPresence: Float,
        val logoDropoutScore: Float,
        val aspectRatioShift: Float
    )

    private fun computeCornerEdgeStrengths(mask: FloatArray, variance: FloatArray): FloatArray {
        val strengths = FloatArray(EDGE_CORNERS)
        for (c in 0 until EDGE_CORNERS) {
            val base = c * CORNER_CELLS
            var sum = 0f
            var peak = 0f
            var weightedVarSum = 0f
            for (k in 0 until CORNER_CELLS) {
                val m = mask[base + k]
                sum += m
                if (m > peak) peak = m
                weightedVarSum += m * variance[base + k]
            }
            val meanEdge = sum / CORNER_CELLS.toFloat()
            val weightedVar = if (sum > 1e-4f) weightedVarSum / sum else 1f
            if (peak < EDGE_MIN_PEAK || meanEdge < EDGE_MIN_MEAN || weightedVar >= BUG_MAX_MEAN_VARIANCE) {
                strengths[c] = 0f
            } else {
                val energy = ((meanEdge / 0.15f) * 0.5f + (peak / 0.40f) * 0.5f).coerceIn(0f, 1f)
                val stability = (1f - weightedVar / BUG_MAX_MEAN_VARIANCE).coerceIn(0f, 1f)
                strengths[c] = (energy * stability).coerceIn(0f, 1f)
            }
        }
        return strengths
    }

    private fun scoreEdgeAndAspect(
        descriptor: VisualFrameDescriptor,
        maskTemplate: FloatArray,
        cornerStrengths: FloatArray,
        baseLb: Float,
        basePb: Float,
        aspectReady: Boolean
    ): EdgeAspectScore {
        var bestCorner = 0
        var logoMaskStrength = 0f
        for (c in 0 until EDGE_CORNERS) {
            if (cornerStrengths[c] > logoMaskStrength) {
                logoMaskStrength = cornerStrengths[c]
                bestCorner = c
            }
        }
        logoMaskStrength = logoMaskStrength.coerceIn(0f, 1f)

        val logoPresence: Float
        val logoDropoutScore: Float
        if (logoMaskStrength > 0f && !descriptor.isBlackFrame) {
            val base = bestCorner * CORNER_CELLS
            val frameEdges = descriptor.cornerEdgeMasks
            var templateSum = 0f
            var minSum = 0f
            var dot = 0.0
            var normT = 0.0
            var normF = 0.0
            for (k in 0 until CORNER_CELLS) {
                val t = maskTemplate[base + k]
                val f = if (base + k < frameEdges.size) frameEdges[base + k] else 0f
                templateSum += t
                minSum += min(t, f)
                dot += t * f
                normT += t * t
                normF += f * f
            }
            val coverage = if (templateSum > 1e-4f) (minSum / templateSum).coerceIn(0f, 1f) else 0f
            val denom = sqrt(normT) * sqrt(normF)
            val cosine = if (denom > 1e-4) (dot / denom).toFloat().coerceIn(0f, 1f) else 0f
            logoPresence = (coverage * cosine).coerceIn(0f, 1f)
            logoDropoutScore = (logoMaskStrength * (1f - logoPresence)).coerceIn(0f, 1f)
        } else {
            logoPresence = 0f
            logoDropoutScore = 0f
        }

        val aspectRatioShift = if (descriptor.isBlackFrame || !aspectReady) {
            0f
        } else {
            ((abs(descriptor.letterboxRatio - baseLb) + abs(descriptor.pillarboxRatio - basePb)) / ASPECT_SHIFT_NORM)
                .coerceIn(0f, 1f)
        }

        return EdgeAspectScore(
            logoMaskStrength = logoMaskStrength,
            logoPresence = logoPresence,
            logoDropoutScore = logoDropoutScore,
            aspectRatioShift = aspectRatioShift
        )
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
