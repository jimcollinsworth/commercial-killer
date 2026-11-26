// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatAdFingerprintIndexTest {

    private fun adAudio(seed: Int): FloatArray = FloatArray(80) { band ->
        val group = band / 20
        if (group == (seed % 4)) 0.85f else 0.05f
    }

    private fun adVisual(seed: Int, timestampMs: Long): VisualFrameDescriptor {
        val width = 64
        val height = 36
        val pixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            when (seed % 4) {
                0 -> if (x < width / 2) 0xFFE11D48.toInt() else 0xFFF59E0B.toInt()
                1 -> if (y < height / 2) 0xFF0EA5E9.toInt() else 0xFF10B981.toInt()
                2 -> if ((x + y) % 2 == 0) 0xFF9333EA.toInt() else 0xFFEC4899.toInt()
                else -> 0xFF1E293B.toInt()
            }
        }
        return VideoFrameAnalyzer.computeDescriptorFromArgbPixels(pixels, width, height, timestampMs)
    }

    private fun feedWindow(
        index: RepeatAdFingerprintIndex,
        startMs: Long,
        mel: FloatArray,
        visual: VisualFrameDescriptor?,
        isTvMuted: Boolean = false,
        isMedia: Boolean = true,
        isProgramAnchor: Boolean = false
    ) {
        for (step in 0 until 10) {
            val t = startMs + step * 100L
            val frameVisual = visual?.copy(timestampMs = t)
            index.recordFrame(
                timestampMs = t,
                melEnergies = mel,
                visualDescriptor = frameVisual,
                isTvMuted = isTvMuted,
                isMedia = isMedia,
                isProgramAnchor = isProgramAnchor
            )
        }
    }

    @Test
    fun `suppresses matches within MIN_REPEAT_GAP_MS and requires two consecutive windows`() {
        val index = RepeatAdFingerprintIndex()

        // Pod 1 at t = 0..2_000 ms (2 windows: seed 0 then seed 1)
        feedWindow(index, 0L, adAudio(0), adVisual(0, 0L))
        feedWindow(index, 1_000L, adAudio(1), adVisual(1, 1_000L))
        // Different content at t = 2_000..4_000 ms
        feedWindow(index, 2_000L, adAudio(2), adVisual(2, 2_000L))
        feedWindow(index, 3_000L, adAudio(2), adVisual(2, 3_000L))

        // Repeat inside MIN_REPEAT_GAP_MS (t = 4_000..6_000 ms) must stay suppressed
        feedWindow(index, 4_000L, adAudio(0), adVisual(0, 4_000L))
        val insideGap = index.peekMatch(5_500L, adAudio(1), adVisual(1, 5_500L), isTvMuted = false, isKnownProgramVisual = false)
        assertEquals(1f, insideGap.repeatAdDistance, 0.001f)
        assertEquals(0f, insideGap.repeatAdMatchScore, 0.001f)

        // Advance beyond MIN_REPEAT_GAP_MS with distinct non-matching filler (seed 3)
        for (w in 5 until 12) {
            feedWindow(index, w * 1_000L, adAudio(3), adVisual(3, w * 1_000L))
        }

        // Single-window coincidence (only window 1 matches, prev was filler seed 3) must not trigger
        val singleWindow = index.peekMatch(12_200L, adAudio(0), adVisual(0, 12_200L), isTvMuted = false, isKnownProgramVisual = false)
        assertTrue(singleWindow.repeatAdDistance > RepeatAdFingerprintIndex.REPEAT_MATCH_DISTANCE_THRESHOLD)
        assertEquals(0f, singleWindow.repeatAdMatchScore, 0.001f)

        // Commit first window (seed 0), then second consecutive window (seed 1) matches Pod 1!
        feedWindow(index, 12_000L, adAudio(0), adVisual(0, 12_000L))
        val twoWindowMatch = index.peekMatch(13_200L, adAudio(1), adVisual(1, 13_200L), isTvMuted = false, isKnownProgramVisual = false)
        assertTrue("Expected repeatAdDistance < 0.15, got ${twoWindowMatch.repeatAdDistance}", twoWindowMatch.repeatAdDistance < 0.15f)
        assertTrue("Expected repeatAdMatchScore >= 0.40, got ${twoWindowMatch.repeatAdMatchScore}", twoWindowMatch.repeatAdMatchScore >= 0.40f)
    }

    @Test
    fun `detects repeat ads across AUDIO_ONLY and VIDEO_ONLY_MUTED against unmuted airing`() {
        // AUDIO_ONLY repeat
        val audioIndex = RepeatAdFingerprintIndex()
        feedWindow(audioIndex, 0L, adAudio(0), null)
        feedWindow(audioIndex, 1_000L, adAudio(1), null)
        for (w in 2 until 11) {
            feedWindow(audioIndex, w * 1_000L, adAudio(2), null)
        }
        feedWindow(audioIndex, 11_000L, adAudio(0), null)
        val audioRepeat = audioIndex.peekMatch(12_200L, adAudio(1), null, isTvMuted = false, isKnownProgramVisual = false)
        assertTrue(audioRepeat.repeatAdDistance < 0.15f)
        assertTrue(audioRepeat.repeatAdMatchScore >= 0.40f)

        // VIDEO_ONLY_MUTED matching an earlier unmuted AUDIO_VIDEO airing
        val crossModalIndex = RepeatAdFingerprintIndex()
        feedWindow(crossModalIndex, 0L, adAudio(0), adVisual(0, 0L), isTvMuted = false)
        feedWindow(crossModalIndex, 1_000L, adAudio(1), adVisual(1, 1_000L), isTvMuted = false)
        for (w in 2 until 11) {
            feedWindow(crossModalIndex, w * 1_000L, adAudio(2), adVisual(2, w * 1_000L), isTvMuted = false)
        }
        feedWindow(crossModalIndex, 11_000L, FloatArray(0), adVisual(0, 11_000L), isTvMuted = true)
        val mutedRepeat = crossModalIndex.peekMatch(12_200L, FloatArray(0), adVisual(1, 12_200L), isTvMuted = true, isKnownProgramVisual = false)
        assertTrue("Expected muted video repeat match < 0.15, got ${mutedRepeat.repeatAdDistance}", mutedRepeat.repeatAdDistance < 0.15f)
        assertTrue("Expected muted repeat score >= 0.40, got ${mutedRepeat.repeatAdMatchScore}", mutedRepeat.repeatAdMatchScore >= 0.40f)
    }

    @Test
    fun `excludes program anchors bounds history to MAX_WINDOWS and clears on reset`() {
        val index = RepeatAdFingerprintIndex()
        feedWindow(index, 0L, adAudio(0), adVisual(0, 0L), isProgramAnchor = true)
        feedWindow(index, 1_000L, adAudio(1), adVisual(1, 1_000L), isProgramAnchor = true)
        for (w in 2 until 11) {
            feedWindow(index, w * 1_000L, adAudio(2), adVisual(2, w * 1_000L), isProgramAnchor = false)
        }
        feedWindow(index, 11_000L, adAudio(0), adVisual(0, 11_000L), isProgramAnchor = false)

        val anchorExcluded = index.peekMatch(12_200L, adAudio(1), adVisual(1, 12_200L), isTvMuted = false, isKnownProgramVisual = false)
        assertTrue(anchorExcluded.repeatAdDistance > RepeatAdFingerprintIndex.REPEAT_MATCH_DISTANCE_THRESHOLD)
        assertEquals(0f, anchorExcluded.repeatAdMatchScore, 0.001f)

        val knownProgramExcluded = index.peekMatch(12_200L, adAudio(1), adVisual(1, 12_200L), isTvMuted = false, isKnownProgramVisual = true)
        assertEquals(1f, knownProgramExcluded.repeatAdDistance, 0.001f)
        assertEquals(0f, knownProgramExcluded.repeatAdMatchScore, 0.001f)

        // Bound check > MAX_WINDOWS = 600
        index.reset()
        assertEquals(0, index.windowCount())
        for (w in 0 until 625) {
            index.recordFrame(w * 1_000L, adAudio(w % 3), adVisual(w % 3, w * 1_000L), isTvMuted = false, isMedia = true, isProgramAnchor = false)
        }
        assertTrue("Expected windowCount <= 600, got ${index.windowCount()}", index.windowCount() <= RepeatAdFingerprintIndex.MAX_WINDOWS)

        index.reset()
        assertEquals(0, index.windowCount())
    }

    @Test
    fun `non-finite melEnergies sanitize to zero-norm without collapsing cosine distance`() {
        for (badVal in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val index = RepeatAdFingerprintIndex()
            val badMel = FloatArray(80) { badVal }
            feedWindow(index, 0L, badMel, null, isTvMuted = false, isMedia = true, isProgramAnchor = false)
            feedWindow(index, 1_000L, badMel, null, isTvMuted = false, isMedia = true, isProgramAnchor = false)
            for (w in 2 until 11) {
                feedWindow(index, w * 1_000L, adAudio(2), null, isTvMuted = false, isMedia = true, isProgramAnchor = false)
            }
            feedWindow(index, 11_000L, badMel, null, isTvMuted = false, isMedia = true, isProgramAnchor = false)
            val result = index.peekMatch(12_200L, badMel, null, isTvMuted = false, isKnownProgramVisual = false)
            assertEquals("Expected repeatAdDistance == 1f for $badVal", 1f, result.repeatAdDistance, 0.0001f)
            assertEquals("Expected repeatAdMatchScore == 0f for $badVal", 0f, result.repeatAdMatchScore, 0.0001f)
        }
    }
}
