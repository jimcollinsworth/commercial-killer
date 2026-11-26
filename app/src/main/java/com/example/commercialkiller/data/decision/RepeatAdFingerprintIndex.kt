// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import kotlin.math.sqrt

/**
 * Output of Cue 4 short-window repeat-ad audio+video fingerprint matching.
 */
data class RepeatAdMatchResult(
    val repeatAdDistance: Float = 1f,
    val repeatAdMatchScore: Float = 0f
)

/**
 * Rolling bounded index of 1.0 s non-program audio+video window fingerprints used to detect
 * repeated commercial spots across pods (within or across breaks) while excluding show anchors.
 */
class RepeatAdFingerprintIndex {

    companion object {
        const val WINDOW_DURATION_MS = 1_000L
        const val MIN_REPEAT_GAP_MS = 8_000L
        const val MAX_WINDOWS = 600
        const val REPEAT_MATCH_DISTANCE_THRESHOLD = 0.25f
        const val PROGRAM_EXCLUSION_SIMILARITY = 0.78f
        private const val MEL_BANDS = 16
        private const val LUMA_CELLS = 16
        private const val HIST_BINS = 24
        private const val INCOMPATIBLE_SENTINEL = 0.999f
    }

    private data class WindowFingerprint(
        val windowIndex: Long,
        val timestampMs: Long,
        val mel16: FloatArray?,
        val descriptor: VisualFrameDescriptor?,
        val isProgramAnchor: Boolean
    )

    private val history = ArrayDeque<WindowFingerprint>(MAX_WINDOWS)

    private var currentWindowStartMs = Long.MIN_VALUE
    private var currentWindowIndex = 0L
    private val accMel = FloatArray(MEL_BANDS)
    private var accMelCount = 0
    private val accLuma = FloatArray(LUMA_CELLS)
    private val accHist = FloatArray(HIST_BINS)
    private var accVisualCount = 0
    private var accFrameCount = 0
    private var accProgramAnchorCount = 0

    fun windowCount(): Int = history.size

    fun peekMatch(
        timestampMs: Long,
        melEnergies: FloatArray,
        visualDescriptor: VisualFrameDescriptor?,
        isTvMuted: Boolean,
        isKnownProgramVisual: Boolean
    ): RepeatAdMatchResult {
        if (isKnownProgramVisual) {
            return RepeatAdMatchResult(1f, 0f)
        }

        val hasIncomingAudio = !isTvMuted && melEnergies.isNotEmpty()
        val hasIncomingVisual = visualDescriptor != null && !visualDescriptor.isBlackFrame

        val rolled = currentWindowStartMs != Long.MIN_VALUE &&
            (timestampMs - currentWindowStartMs) >= WINDOW_DURATION_MS

        val curr: WindowFingerprint
        val prev: WindowFingerprint?
        val searchPool: List<WindowFingerprint>

        if (rolled) {
            val steps = ((timestampMs - currentWindowStartMs) / WINDOW_DURATION_MS).coerceAtLeast(1L)
            val finalizedAcc = buildFromAccumulatorOnly(currentWindowIndex, currentWindowStartMs)
            val incomingOnly = buildWindow(
                windowIndex = currentWindowIndex + steps,
                timestampMs = timestampMs,
                includeAcc = false,
                melEnergies = if (hasIncomingAudio) melEnergies else null,
                visualDescriptor = if (hasIncomingVisual) visualDescriptor else null,
                isProgramAnchor = false
            )
            curr = incomingOnly ?: return RepeatAdMatchResult(1f, 0f)
            if (steps == 1L && finalizedAcc != null) {
                prev = finalizedAcc
                searchPool = if (history.isEmpty()) {
                    listOf(finalizedAcc)
                } else {
                    ArrayList<WindowFingerprint>(history.size + 1).apply {
                        addAll(history)
                        add(finalizedAcc)
                    }
                }
            } else {
                return RepeatAdMatchResult(1f, 0f)
            }
        } else {
            val activeIdx = if (currentWindowStartMs == Long.MIN_VALUE) 0L else currentWindowIndex
            val activeTs = if (currentWindowStartMs == Long.MIN_VALUE) timestampMs else currentWindowStartMs
            val activeIsProgramAnchor = accFrameCount > 0 && accProgramAnchorCount * 2 > accFrameCount
            curr = buildWindow(
                windowIndex = activeIdx,
                timestampMs = activeTs,
                includeAcc = currentWindowStartMs != Long.MIN_VALUE,
                melEnergies = if (hasIncomingAudio) melEnergies else null,
                visualDescriptor = if (hasIncomingVisual) visualDescriptor else null,
                isProgramAnchor = activeIsProgramAnchor
            ) ?: return RepeatAdMatchResult(1f, 0f)
            prev = history.lastOrNull()
            searchPool = history.toList()
        }

        if (prev == null || prev.isProgramAnchor || searchPool.size < 2) {
            return RepeatAdMatchResult(1f, 0f)
        }
        if (curr.windowIndex - prev.windowIndex != 1L) {
            return RepeatAdMatchResult(1f, 0f)
        }

        var bestDist = Float.MAX_VALUE
        for (k in 1 until searchPool.size) {
            val hk = searchPool[k]
            val hkPrev = searchPool[k - 1]
            if (timestampMs - hk.timestampMs < MIN_REPEAT_GAP_MS) continue
            if (hk.isProgramAnchor || hkPrev.isProgramAnchor) continue
            if (hk.windowIndex - hkPrev.windowIndex != 1L) continue

            val d0 = pairDistance(curr, hk)
            if (d0 >= INCOMPATIBLE_SENTINEL) continue
            val d1 = pairDistance(prev, hkPrev)
            if (d1 >= INCOMPATIBLE_SENTINEL) continue

            val meanDist = 0.5f * (d0 + d1)
            if (meanDist < bestDist) {
                bestDist = meanDist
            }
        }

        if (bestDist == Float.MAX_VALUE) {
            return RepeatAdMatchResult(1f, 0f)
        }

        val clampedDist = bestDist.coerceIn(0f, 1f)
        val score = (1f - (clampedDist / REPEAT_MATCH_DISTANCE_THRESHOLD)).coerceIn(0f, 1f)
        return RepeatAdMatchResult(
            repeatAdDistance = clampedDist,
            repeatAdMatchScore = score
        )
    }

    fun recordFrame(
        timestampMs: Long,
        melEnergies: FloatArray,
        visualDescriptor: VisualFrameDescriptor?,
        isTvMuted: Boolean,
        isMedia: Boolean,
        isProgramAnchor: Boolean
    ) {
        if (currentWindowStartMs == Long.MIN_VALUE) {
            currentWindowStartMs = timestampMs
            currentWindowIndex = 0L
        } else if (timestampMs - currentWindowStartMs >= WINDOW_DURATION_MS) {
            commitActiveWindow()
            val steps = ((timestampMs - currentWindowStartMs) / WINDOW_DURATION_MS).coerceAtLeast(1L)
            currentWindowIndex += steps
            currentWindowStartMs += steps * WINDOW_DURATION_MS
        }

        if (!isTvMuted && !isMedia) {
            return
        }

        if (!isTvMuted && melEnergies.isNotEmpty()) {
            val mel16 = downsampleMel16(melEnergies)
            for (i in 0 until MEL_BANDS) {
                accMel[i] += mel16[i]
            }
            accMelCount++
        }
        if (visualDescriptor != null && !visualDescriptor.isBlackFrame) {
            val luma = visualDescriptor.lumaGrid
            val hist = visualDescriptor.colorHistogram
            val lumaLimit = minOf(LUMA_CELLS, luma.size)
            for (i in 0 until lumaLimit) {
                accLuma[i] += sanitizeUnit(luma[i])
            }
            val histLimit = minOf(HIST_BINS, hist.size)
            for (i in 0 until histLimit) {
                accHist[i] += sanitizeUnit(hist[i])
            }
            accVisualCount++
        }
        accFrameCount++
        if (isProgramAnchor) {
            accProgramAnchorCount++
        }
    }

    fun reset() {
        history.clear()
        currentWindowStartMs = Long.MIN_VALUE
        currentWindowIndex = 0L
        clearAccumulator()
    }

    private fun commitActiveWindow() {
        val window = buildFromAccumulatorOnly(currentWindowIndex, currentWindowStartMs)
        if (window != null) {
            if (history.size >= MAX_WINDOWS) {
                history.removeFirst()
            }
            history.addLast(window)
        }
        clearAccumulator()
    }

    private fun clearAccumulator() {
        accMel.fill(0f)
        accMelCount = 0
        accLuma.fill(0f)
        accHist.fill(0f)
        accVisualCount = 0
        accFrameCount = 0
        accProgramAnchorCount = 0
    }

    private fun buildFromAccumulatorOnly(windowIndex: Long, timestampMs: Long): WindowFingerprint? {
        val isAnchor = accFrameCount > 0 && accProgramAnchorCount * 2 > accFrameCount
        return buildWindow(
            windowIndex = windowIndex,
            timestampMs = timestampMs,
            includeAcc = true,
            melEnergies = null,
            visualDescriptor = null,
            isProgramAnchor = isAnchor
        )
    }

    private fun buildWindow(
        windowIndex: Long,
        timestampMs: Long,
        includeAcc: Boolean,
        melEnergies: FloatArray?,
        visualDescriptor: VisualFrameDescriptor?,
        isProgramAnchor: Boolean
    ): WindowFingerprint? {
        val totalMelCount = (if (includeAcc) accMelCount else 0) + (if (melEnergies != null) 1 else 0)
        val totalVisCount = (if (includeAcc) accVisualCount else 0) + (if (visualDescriptor != null) 1 else 0)
        if (totalMelCount == 0 && totalVisCount == 0) {
            return null
        }

        val mel16 = if (totalMelCount > 0) {
            FloatArray(MEL_BANDS).also { out ->
                if (includeAcc && accMelCount > 0) {
                    for (i in 0 until MEL_BANDS) out[i] += accMel[i]
                }
                if (melEnergies != null) {
                    val m = downsampleMel16(melEnergies)
                    for (i in 0 until MEL_BANDS) out[i] += m[i]
                }
                val denom = totalMelCount.toFloat()
                for (i in 0 until MEL_BANDS) out[i] /= denom
            }
        } else {
            null
        }

        val descriptor = if (totalVisCount > 0) {
            val avgLuma = FloatArray(LUMA_CELLS)
            val avgHist = FloatArray(HIST_BINS)
            if (includeAcc && accVisualCount > 0) {
                for (i in 0 until LUMA_CELLS) avgLuma[i] += accLuma[i]
                for (i in 0 until HIST_BINS) avgHist[i] += accHist[i]
            }
            if (visualDescriptor != null) {
                val lumaLimit = minOf(LUMA_CELLS, visualDescriptor.lumaGrid.size)
                for (i in 0 until lumaLimit) avgLuma[i] += sanitizeUnit(visualDescriptor.lumaGrid[i])
                val histLimit = minOf(HIST_BINS, visualDescriptor.colorHistogram.size)
                for (i in 0 until histLimit) avgHist[i] += sanitizeUnit(visualDescriptor.colorHistogram[i])
            }
            val denom = totalVisCount.toFloat()
            for (i in 0 until LUMA_CELLS) avgLuma[i] /= denom
            for (i in 0 until HIST_BINS) avgHist[i] /= denom
            VisualFrameDescriptor(
                timestampMs = timestampMs,
                lumaGrid = avgLuma,
                colorHistogram = avgHist
            )
        } else {
            null
        }

        return WindowFingerprint(
            windowIndex = windowIndex,
            timestampMs = timestampMs,
            mel16 = mel16,
            descriptor = descriptor,
            isProgramAnchor = isProgramAnchor
        )
    }

    private fun pairDistance(a: WindowFingerprint, b: WindowFingerprint): Float {
        val melA = a.mel16
        val melB = b.mel16
        val descA = a.descriptor
        val descB = b.descriptor
        return when {
            melA != null && melB != null && descA != null && descB != null -> {
                val aDist = melCosineDistance(melA, melB)
                val vDist = (1f - VideoFrameAnalyzer.descriptorSimilarity(descA, descB)).coerceIn(0f, 1f)
                (0.50f * aDist + 0.50f * vDist).coerceIn(0f, 1f)
            }
            melA != null && melB != null -> melCosineDistance(melA, melB)
            descA != null && descB != null -> (1f - VideoFrameAnalyzer.descriptorSimilarity(descA, descB)).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    private fun sanitizeUnit(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    private fun melCosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        val limit = minOf(a.size, b.size)
        for (i in 0 until limit) {
            val va = if (a[i].isFinite()) a[i].toDouble() else 0.0
            val vb = if (b[i].isFinite()) b[i].toDouble() else 0.0
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        if (normA <= 1e-6 || normB <= 1e-6 || !normA.isFinite() || !normB.isFinite()) {
            return 1f
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom <= 1e-6 || !denom.isFinite()) {
            return 1f
        }
        val cosSim = (dot / denom).coerceIn(-1.0, 1.0)
        val dist = (1.0 - cosSim).toFloat()
        return if (dist.isFinite()) dist.coerceIn(0f, 1f) else 1f
    }

    private fun downsampleMel16(melEnergies: FloatArray): FloatArray {
        val out = FloatArray(MEL_BANDS)
        val srcLen = melEnergies.size
        if (srcLen == 0) return out
        for (b in 0 until MEL_BANDS) {
            val start = (b * srcLen) / MEL_BANDS
            val end = (((b + 1) * srcLen) / MEL_BANDS).coerceAtLeast(start + 1).coerceAtMost(srcLen)
            var sum = 0f
            for (i in start until end) {
                sum += sanitizeUnit(melEnergies[i])
            }
            val avg = sum / (end - start).coerceAtLeast(1)
            out[b] = sanitizeUnit(avg)
        }
        return out
    }
}
