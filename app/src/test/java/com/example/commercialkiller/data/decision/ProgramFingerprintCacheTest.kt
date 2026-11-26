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

    private fun frameWithTopLeftLogoAndAspect(
        interiorArgb: Int,
        withTopLeftLogo: Boolean,
        letterboxRows: Int = 0,
        pillarboxCols: Int = 0,
        timestampMs: Long
    ): VisualFrameDescriptor {
        val width = 64
        val height = 36
        val pixels = IntArray(width * height) { interiorArgb }
        if (withTopLeftLogo) {
            for (y in 1 until 8) {
                for (x in 1 until 14) {
                    pixels[y * width + x] = if (y in 2..6 && x in 2..12) 0xFFFFFFFF.toInt() else 0xFF10141C.toInt()
                }
            }
        }
        for (y in 0 until letterboxRows) {
            for (x in 0 until width) {
                pixels[y * width + x] = 0xFF000000.toInt()
                pixels[(height - 1 - y) * width + x] = 0xFF000000.toInt()
            }
        }
        for (y in 0 until height) {
            for (x in 0 until pillarboxCols) {
                pixels[y * width + x] = 0xFF000000.toInt()
                pixels[y * width + (width - 1 - x)] = 0xFF000000.toInt()
            }
        }
        return VideoFrameAnalyzer.computeDescriptorFromArgbPixels(pixels, width, height, timestampMs)
    }

    @Test
    fun `four-corner edge mask tracks top-left logo across varying scenes and drops on logoless ad`() {
        val cache = ProgramFingerprintCache()
        val backgrounds = listOf(0xFF2B4C7E.toInt(), 0xFF3E6B48.toInt(), 0xFF7A5130.toInt(), 0xFF4A3B6E.toInt())
        for (i in 0 until 12) {
            val bg = backgrounds[i % backgrounds.size]
            cache.observeProgramFrame(frameWithTopLeftLogoAndAspect(bg, withTopLeftLogo = true, timestampMs = i * 250L))
        }

        val liveProgramMatch = cache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF556677.toInt(), withTopLeftLogo = true, timestampMs = 3_500L)
        )
        assertTrue("Expected logoMaskStrength >= 0.35, got ${liveProgramMatch.logoMaskStrength}", liveProgramMatch.logoMaskStrength >= 0.35f)
        assertTrue(liveProgramMatch.hasStableBug)
        assertTrue("Expected low logoDropoutScore when logo present, got ${liveProgramMatch.logoDropoutScore}", liveProgramMatch.logoDropoutScore < 0.25f)

        val logolessAdMatch = cache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF998877.toInt(), withTopLeftLogo = false, timestampMs = 3_750L)
        )
        assertTrue("Expected high logoDropoutScore on logo-free commercial, got ${logolessAdMatch.logoDropoutScore}", logolessAdMatch.logoDropoutScore >= 0.50f)

        // Logoless varying channel produces zero mask strength and zero dropout
        val logolessCache = ProgramFingerprintCache()
        for (i in 0 until 12) {
            val bg = backgrounds[i % backgrounds.size]
            logolessCache.observeProgramFrame(frameWithTopLeftLogoAndAspect(bg, withTopLeftLogo = false, timestampMs = i * 250L))
        }
        val logolessCheck = logolessCache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF884422.toInt(), withTopLeftLogo = false, timestampMs = 4_000L)
        )
        assertEquals(0f, logolessCheck.logoMaskStrength, 0.001f)
        assertEquals(0f, logolessCheck.logoDropoutScore, 0.001f)
    }

    @Test
    fun `aspectRatioShift detects pillarbox or letterbox change and unfreeze or clear manage lifecycle`() {
        val cache = ProgramFingerprintCache()
        // 12 frames of clean widescreen program with top-left logo (t = 0..2_750)
        for (i in 0 until 12) {
            cache.observeProgramFrame(
                frameWithTopLeftLogoAndAspect(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = i * 250L)
            )
        }
        // Final 1.75 s before mute: break-entry frames with pillarbox and no logo (t = 3_000..4_500)
        for (i in 0 until 7) {
            cache.observeProgramFrame(
                frameWithTopLeftLogoAndAspect(0xFFCC4422.toInt(), withTopLeftLogo = false, pillarboxCols = 8, timestampMs = 3_000L + i * 250L)
            )
        }

        // Mute at t = 4_750 rolls back 2_000 ms -> cutoff 2_750 excludes break-entry contamination
        cache.freezeForBreak(muteTimestampMs = 4_750L)
        val sameAspect = cache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = 10_000L)
        )
        assertEquals(0f, sameAspect.aspectRatioShift, 0.001f)

        val pillarAd = cache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF999999.toInt(), withTopLeftLogo = false, pillarboxCols = 8, timestampMs = 10_250L)
        )
        assertTrue("Expected aspectRatioShift >= 0.80 on 0.25 pillarbox shift, got ${pillarAd.aspectRatioShift}", pillarAd.aspectRatioShift >= 0.80f)

        val letterboxAd = cache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF999999.toInt(), withTopLeftLogo = false, letterboxRows = 6, timestampMs = 10_500L)
        )
        assertTrue("Expected aspectRatioShift >= 0.80 on 0.33 letterbox shift, got ${letterboxAd.aspectRatioShift}", letterboxAd.aspectRatioShift >= 0.80f)

        val blackFrame = solidWithCorners(0xFF000000.toInt(), timestampMs = 10_750L)
        assertEquals(0f, cache.scoreAgainstProgram(blackFrame).aspectRatioShift, 0.001f)

        // unfreeze() clears frozen state while keeping live baselines ready
        cache.unfreeze()
        assertFalse(cache.isFrozen())
        val afterUnfreeze = cache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = 11_000L)
        )
        assertTrue(afterUnfreeze.isBaselineReady)

        // clear() resets all live and frozen state
        cache.clear()
        val afterClear = cache.scoreAgainstProgram(
            frameWithTopLeftLogoAndAspect(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = 11_250L)
        )
        assertFalse(afterClear.isBaselineReady)
        assertEquals(0f, afterClear.logoMaskStrength, 0.001f)
        assertEquals(0f, afterClear.aspectRatioShift, 0.001f)
    }
}
