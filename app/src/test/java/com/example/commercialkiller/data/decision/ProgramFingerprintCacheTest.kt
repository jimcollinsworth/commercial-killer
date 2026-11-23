// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramFingerprintCacheTest {

    private fun solidWithCorners(
        baseArgb: Int,
        bottomRightArgb: Int = baseArgb,
        bottomLeftArgb: Int = baseArgb,
        timestampMs: Long
    ): VisualFrameDescriptor {
        val width = 64
        val height = 36
        val pixels = IntArray(width * height) { baseArgb }
        for (y in (height * 3) / 4 until height) {
            for (x in (width * 3) / 4 until width) {
                if ((x + y) % 2 == 0) {
                    pixels[y * width + x] = bottomRightArgb
                }
            }
            for (x in 0 until width / 4) {
                if ((x + y) % 2 == 0) {
                    pixels[y * width + x] = bottomLeftArgb
                }
            }
        }
        return VideoFrameAnalyzer.computeDescriptorFromArgbPixels(
            pixels = pixels,
            width = width,
            height = height,
            timestampMs = timestampMs
        )
    }

    private fun angleFrame(isAngleB: Boolean, timestampMs: Long, withRightBug: Boolean = false): VisualFrameDescriptor {
        val width = 64
        val height = 36
        val pixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (!isAngleB) {
                // Angle A: studio blue left side, warm studio right side
                if (x < width / 2) 0xFF1E3A8A.toInt() else 0xFFD97706.toInt()
            } else {
                // Angle B: reporter close-up green upper, neutral grey lower
                if (y < height / 2) 0xFF15803D.toInt() else 0xFF64748B.toInt()
            }
        }
        if (withRightBug) {
            // High-contrast alternating watermark cells inside bottom-right 4x4 ROI (quarter width/height)
            for (y in (height * 3) / 4 until height) {
                for (x in (width * 3) / 4 until width) {
                    if (y >= height - 4 && x >= width - 6) {
                        pixels[y * width + x] = 0xFFFFFFFF.toInt()
                    } else {
                        pixels[y * width + x] = 0xFF111827.toInt()
                    }
                }
            }
        }
        return VideoFrameAnalyzer.computeDescriptorFromArgbPixels(
            pixels = pixels,
            width = width,
            height = height,
            timestampMs = timestampMs
        )
    }

    @Test
    fun `active scene angle bank recognizes alternating A and B camera shots without novelty spikes`() {
        val cache = ProgramFingerprintCache()
        val angleA = angleFrame(isAngleB = false, timestampMs = 250L)
        val angleB = angleFrame(isAngleB = true, timestampMs = 500L)

        val obs1 = cache.observeActiveSceneFrame(angleA, allowLearningNewAngle = true)
        assertTrue(obs1.isKnownAngle)
        assertEquals(1, obs1.activeAngleCount)

        // First appearance of Angle B is learned into the active angle bank
        val obs2 = cache.observeActiveSceneFrame(angleB, allowLearningNewAngle = true)
        assertFalse(obs2.isKnownAngle)
        assertEquals(2, obs2.activeAngleCount)

        // Repeated cuts back and forth (A -> B -> A -> B) remain known angles
        for (tick in 1..20) {
            val frame = angleFrame(isAngleB = tick % 2 == 0, timestampMs = 750L + tick * 250L)
            val obs = cache.observeActiveSceneFrame(frame, allowLearningNewAngle = true)
            assertTrue("Expected cut #$tick back to learned angle to stay known", obs.isKnownAngle)
            assertTrue(obs.bestAngleSimilarity >= ProgramFingerprintCache.KNOWN_ANGLE_SIMILARITY_THRESHOLD)
        }
        assertEquals(2, cache.activeAngleCount())
    }

    @Test
    fun `freezeForBreak rolls back 2 seconds before mute and preserves multi-angle prototypes`() {
        val cache = ProgramFingerprintCache()

        // 12 seconds of program alternating Angle A and Angle B before break
        for (i in 0 until 48) {
            val t = i * 250L
            cache.observeProgramFrame(angleFrame(isAngleB = (i / 4) % 2 == 1, timestampMs = t))
        }

        // Last 1.75 seconds before mute (t = 12_000..13_500): inject bright red commercial contamination frames
        for (i in 48..54) {
            val t = i * 250L
            cache.observeProgramFrame(solidWithCorners(0xFFFF0000.toInt(), timestampMs = t))
        }

        // Mute triggers at t = 13_750 ms -> 2s rollback excludes >= 11_750 ms (removing all red break frames!)
        cache.freezeForBreak(muteTimestampMs = 13_750L)
        assertTrue(cache.isFrozen())
        assertTrue(cache.frozenPrototypeCount() >= 2)

        val returnAngleA = cache.scoreAgainstProgram(angleFrame(isAngleB = false, timestampMs = 30_000L))
        val returnAngleB = cache.scoreAgainstProgram(angleFrame(isAngleB = true, timestampMs = 31_000L))
        val breakAdFrame = cache.scoreAgainstProgram(solidWithCorners(0xFFFF0000.toInt(), timestampMs = 20_000L))

        assertTrue(returnAngleA.isBaselineReady)
        assertTrue("Expected Angle A prototype match >= 0.90, got ${returnAngleA.prototypeSimilarity}", returnAngleA.prototypeSimilarity >= 0.90f)
        assertTrue("Expected Angle B prototype match >= 0.90, got ${returnAngleB.prototypeSimilarity}", returnAngleB.prototypeSimilarity >= 0.90f)
        assertTrue("Expected rolled-back red ad frame match < 0.70, got ${breakAdFrame.prototypeSimilarity}", breakAdFrame.prototypeSimilarity < 0.70f)
    }

    @Test
    fun `corner watermark stability detects bottom-right bug and ignores uniform dark letterbox`() {
        val withBugCache = ProgramFingerprintCache()
        for (i in 0 until 12) {
            withBugCache.observeProgramFrame(
                angleFrame(isAngleB = i % 2 == 1, timestampMs = i * 250L, withRightBug = true)
            )
        }
        withBugCache.freezeForBreak(muteTimestampMs = 10_000L)

        val bugPresentMatch = withBugCache.scoreAgainstProgram(
            angleFrame(isAngleB = false, timestampMs = 20_000L, withRightBug = true)
        )
        assertTrue(bugPresentMatch.hasStableBug)
        assertTrue(bugPresentMatch.bugSimilarity >= 0.95f)

        val letterboxCache = ProgramFingerprintCache()
        for (i in 0 until 12) {
            letterboxCache.observeProgramFrame(
                solidWithCorners(baseArgb = 0xFF050505.toInt(), timestampMs = i * 250L)
            )
        }
        letterboxCache.freezeForBreak(muteTimestampMs = 10_000L)
        val noBugMatch = letterboxCache.scoreAgainstProgram(
            solidWithCorners(baseArgb = 0xFF050505.toInt(), timestampMs = 20_000L)
        )
        assertFalse("Uniform dark letterbox corner should not register as a station bug", noBugMatch.hasStableBug)
    }
}
