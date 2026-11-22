// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.audio.ClassifierScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDecisionEngineTest {

    private fun commercialTags(): List<ClassifierScore> = listOf(
        ClassifierScore("Commercial / Jingle", 0.85f),
        ClassifierScore("Music Bed", 0.65f),
        ClassifierScore("Voiceover / Speech", 0.40f)
    )

    private fun speechTags(): List<ClassifierScore> = listOf(
        ClassifierScore("Dialogue / Speech", 0.88f),
        ClassifierScore("Broadcast Content", 0.55f),
        ClassifierScore("Background Ambience", 0.20f)
    )

    private fun silenceTags(): List<ClassifierScore> = listOf(
        ClassifierScore("Silence / Transition Gap", 0.95f),
        ClassifierScore("Background Noise", 0.05f),
        ClassifierScore("Broadcast Content", 0.00f)
    )

    private fun uniformVector(bands: Int, value: Float): FloatArray = FloatArray(bands) { value }

    private fun speechFormantVector(bands: Int = 80): FloatArray {
        return FloatArray(bands) { idx ->
            if (idx in 8..14 || idx in 24..28) 0.55f else 0.05f
        }
    }

    private fun orthogonalCommercialVector(bands: Int = 80): FloatArray {
        return FloatArray(bands) { idx ->
            if (idx >= bands / 2) 0.80f else 0.65f
        }
    }

    @Test
    fun sceneSimilarityThreshold_defaultsAndClampsWithinValidRange() {
        val engine = FrameDecisionEngine()
        assertEquals(0.85f, engine.getSceneSimilarityThreshold(), 0.0001f)

        engine.setSceneSimilarityThreshold(0.92f)
        assertEquals(0.92f, engine.getSceneSimilarityThreshold(), 0.0001f)

        engine.setSceneSimilarityThreshold(0.20f)
        assertEquals(0.50f, engine.getSceneSimilarityThreshold(), 0.0001f)

        engine.setSceneSimilarityThreshold(1.40f)
        assertEquals(0.99f, engine.getSceneSimilarityThreshold(), 0.0001f)

        engine.setSceneSimilarityThreshold(Float.NaN)
        assertEquals(0.85f, engine.getSceneSimilarityThreshold(), 0.0001f)
    }

    @Test
    fun evaluateSceneContinuity_tracksSameSceneShotDurationAndSceneCuts() {
        val engine = FrameDecisionEngine()
        val vecA = FloatArray(80) { idx -> if (idx < 40) 0.8f else 0.1f }
        val vecB = FloatArray(80) { idx -> if (idx >= 40) 0.8f else 0.1f }

        // Frame 1: initialization
        val r1 = engine.evaluateSceneContinuity(vecA, timestampMs = 1_000L)
        assertTrue(r1.isSameScene)
        assertEquals(1.0f, r1.sceneSimilarity, 0.001f)
        assertEquals(0L, r1.shotDurationMs)
        assertEquals(0f, r1.cutCadencePerMin, 0.001f)

        // Frame 2: identical vector -> same scene, shot duration advances
        val r2 = engine.evaluateSceneContinuity(vecA, timestampMs = 1_500L)
        assertTrue(r2.isSameScene)
        assertEquals(1.0f, r2.sceneSimilarity, 0.001f)
        assertEquals(500L, r2.shotDurationMs)
        assertEquals(0f, r2.cutCadencePerMin, 0.001f)

        // Frame 3: orthogonal spectral profile -> scene cut
        val r3 = engine.evaluateSceneContinuity(vecB, timestampMs = 2_000L)
        assertFalse(r3.isSameScene)
        assertTrue("Expected low cosine similarity on cut, got ${r3.sceneSimilarity}", r3.sceneSimilarity < 0.85f)
        assertEquals(0L, r3.shotDurationMs)
        assertEquals(1f, r3.cutCadencePerMin, 0.001f)

        // Frame 4: stays on new scene
        val r4 = engine.evaluateSceneContinuity(vecB, timestampMs = 2_400L)
        assertTrue(r4.isSameScene)
        assertEquals(400L, r4.shotDurationMs)
        assertEquals(1f, r4.cutCadencePerMin, 0.001f)
    }

    @Test
    fun evaluateSceneContinuity_handlesZeroNormAndBandCountChangeSafely() {
        val engine = FrameDecisionEngine()
        val normalVec = uniformVector(80, 0.6f)
        val zeroVec = FloatArray(80) { 0f }

        engine.evaluateSceneContinuity(normalVec, timestampMs = 1_000L)
        val zeroResult = engine.evaluateSceneContinuity(zeroVec, timestampMs = 1_100L)
        assertTrue(zeroResult.isSameScene)
        assertEquals(1.0f, zeroResult.sceneSimilarity, 0.001f)
        assertFalse(zeroResult.sceneSimilarity.isNaN())

        // Band resolution change from 80 -> 40 resets shot start cleanly without crashing
        val smallerVec = uniformVector(40, 0.7f)
        val resizedResult = engine.evaluateSceneContinuity(smallerVec, timestampMs = 1_200L)
        assertTrue(resizedResult.isSameScene)
        assertEquals(1.0f, resizedResult.sceneSimilarity, 0.001f)
        assertEquals(0L, resizedResult.shotDurationMs)
    }

    @Test
    fun evaluateSceneContinuity_prunesRolling60SecondCutWindow() {
        val engine = FrameDecisionEngine()
        val vecA = FloatArray(80) { idx -> if (idx < 40) 1.0f else 0.0f }
        val vecB = FloatArray(80) { idx -> if (idx >= 40) 1.0f else 0.0f }

        engine.evaluateSceneContinuity(vecA, timestampMs = 0L)

        // Generate 4 alternating cuts between t=1_000 and t=4_000
        engine.evaluateSceneContinuity(vecB, timestampMs = 1_000L)
        engine.evaluateSceneContinuity(vecA, timestampMs = 2_000L)
        engine.evaluateSceneContinuity(vecB, timestampMs = 3_000L)
        val at4s = engine.evaluateSceneContinuity(vecA, timestampMs = 4_000L)
        assertEquals(4f, at4s.cutCadencePerMin, 0.001f)

        // At t=62_500 (window start = 2_500), cuts at 1_000 and 2_000 are pruned; 3_000 and 4_000 remain
        val at62s = engine.evaluateSceneContinuity(vecA, timestampMs = 62_500L)
        assertTrue(at62s.isSameScene)
        assertEquals(2f, at62s.cutCadencePerMin, 0.001f)

        // At t=65_000 (window start = 5_000), all prior cuts are pruned
        val at65s = engine.evaluateSceneContinuity(vecA, timestampMs = 65_000L)
        assertEquals(0f, at65s.cutCadencePerMin, 0.001f)
    }

    @Test
    fun evaluateCommercial_distinguishesCommercialProgramAndSilence() {
        val engine = FrameDecisionEngine()

        val commercialRes = engine.evaluateCommercial(
            features = uniformVector(80, 0.72f),
            classifierTags = commercialTags(),
            shiftDistance = 0.15f,
            cutCadencePerMin = 4f
        )
        assertTrue("Expected commercial detection, score=${commercialRes.commercialScore}", commercialRes.isCommercial)
        assertTrue(commercialRes.commercialScore >= 0.75f)
        assertTrue(commercialRes.commercialScore <= 1.0f)

        val programRes = engine.evaluateCommercial(
            features = speechFormantVector(80),
            classifierTags = speechTags(),
            shiftDistance = 0.02f,
            cutCadencePerMin = 2f
        )
        assertFalse("Expected program speech not flagged as commercial, score=${programRes.commercialScore}", programRes.isCommercial)
        assertTrue(programRes.commercialScore < 0.40f)

        val silenceRes = engine.evaluateCommercial(
            features = FloatArray(80) { 0f },
            classifierTags = silenceTags(),
            shiftDistance = 0.30f,
            cutCadencePerMin = 0f
        )
        assertFalse(silenceRes.isCommercial)
        assertEquals(0.0f, silenceRes.commercialScore, 0.001f)
    }

    @Test
    fun evaluateCommercial_highCutCadenceBoostsBorderlineMusicScoreOverThreshold() {
        val engine = FrameDecisionEngine()
        val musicTags = listOf(
            ClassifierScore("Music / Sound Bed", 0.72f),
            ClassifierScore("Broadcast Content", 0.58f),
            ClassifierScore("Speech", 0.35f)
        )
        val compressedFeatures = uniformVector(80, 0.70f)

        val lowCadence = engine.evaluateCommercial(
            features = compressedFeatures,
            classifierTags = musicTags,
            shiftDistance = 0.01f,
            cutCadencePerMin = 4f
        )
        val highCadence = engine.evaluateCommercial(
            features = compressedFeatures,
            classifierTags = musicTags,
            shiftDistance = 0.01f,
            cutCadencePerMin = 18f
        )

        assertFalse("Calm music with low cut cadence should not trigger commercial", lowCadence.isCommercial)
        assertTrue("High cut cadence (>= 15/min) should boost commercial score >= 0.75", highCadence.isCommercial)
        assertTrue(highCadence.commercialScore > lowCadence.commercialScore)
    }

    @Test
    fun evaluateFrame_gatesMuteAfter3ConsecutiveCommercialFramesOrImmediateCut() {
        val engine = FrameDecisionEngine()
        val commercialVec = uniformVector(80, 0.75f)

        // Frame 1: first frame has isSameScene=true, consecutiveCommercialFrames=1 -> HOLD
        val f1 = engine.evaluateFrame(commercialVec, commercialTags(), shiftDistance = 0.02f, timestampMs = 100L)
        assertTrue(f1.isCommercial)
        assertTrue(f1.isSameScene)
        assertEquals(TvActionDecision.HOLD, f1.recommendedAction)

        // Frame 2: same scene, consecutiveCommercialFrames=2 -> HOLD
        val f2 = engine.evaluateFrame(commercialVec, commercialTags(), shiftDistance = 0.0f, timestampMs = 200L)
        assertTrue(f2.isCommercial)
        assertTrue(f2.isSameScene)
        assertEquals(TvActionDecision.HOLD, f2.recommendedAction)

        // Frame 3: same scene, consecutiveCommercialFrames=3 -> MUTE
        val f3 = engine.evaluateFrame(commercialVec, commercialTags(), shiftDistance = 0.0f, timestampMs = 300L)
        assertTrue(f3.isCommercial)
        assertEquals(TvActionDecision.MUTE, f3.recommendedAction)

        // Fresh engine: commercial coinciding with scene cut (!isSameScene) triggers MUTE immediately
        val cutEngine = FrameDecisionEngine()
        val speechVec = FloatArray(80) { idx -> if (idx < 20) 0.7f else 0.02f }
        val altCommercialVec = orthogonalCommercialVector(80)

        cutEngine.evaluateFrame(speechVec, speechTags(), shiftDistance = 0.0f, timestampMs = 1_000L)
        val cutFrame = cutEngine.evaluateFrame(altCommercialVec, commercialTags(), shiftDistance = 0.45f, timestampMs = 1_100L)
        assertTrue(cutFrame.isCommercial)
        assertFalse(cutFrame.isSameScene)
        assertEquals(TvActionDecision.MUTE, cutFrame.recommendedAction)
    }

    @Test
    fun evaluateFrame_gatesUnmuteAfter5ConsecutiveProgramFrames() {
        val engine = FrameDecisionEngine()
        val commercialVec = FloatArray(80) { idx -> if (idx >= 40) 0.80f else 0.65f }
        val programVec = speechFormantVector(80)

        // Enter commercial state first
        engine.evaluateFrame(commercialVec, commercialTags(), shiftDistance = 0.1f, timestampMs = 100L)

        // 4 consecutive program frames -> HOLD
        for (step in 1..4) {
            val res = engine.evaluateFrame(
                melEnergies = programVec,
                classifierTags = speechTags(),
                shiftDistance = 0.01f,
                timestampMs = 100L + step * 100L
            )
            assertFalse(res.isCommercial)
            assertEquals("Expected HOLD on program frame $step", TvActionDecision.HOLD, res.recommendedAction)
        }

        // 5th consecutive program frame -> UNMUTE
        val frame5 = engine.evaluateFrame(
            melEnergies = programVec,
            classifierTags = speechTags(),
            shiftDistance = 0.01f,
            timestampMs = 600L
        )
        assertFalse(frame5.isCommercial)
        assertEquals(TvActionDecision.UNMUTE, frame5.recommendedAction)
    }

    @Test
    fun evaluateFrame_interruptedCommercialStreakResetsCounter() {
        val engine = FrameDecisionEngine()
        val commercialVec = uniformVector(80, 0.75f)

        // Two same-scene commercial frames -> HOLD
        assertEquals(
            TvActionDecision.HOLD,
            engine.evaluateFrame(commercialVec, commercialTags(), 0f, 100L).recommendedAction
        )
        assertEquals(
            TvActionDecision.HOLD,
            engine.evaluateFrame(commercialVec, commercialTags(), 0f, 200L).recommendedAction
        )

        // Interruption by non-commercial frame using identical spectrum -> resets streak
        val nonCommercial = engine.evaluateFrame(
            melEnergies = commercialVec,
            classifierTags = silenceTags(),
            shiftDistance = 0f,
            timestampMs = 300L
        )
        // With silenceTags (0.95 -> -0.38 penalty) vs uniformVec: wait, use quiet vector to guarantee non-commercial
        val resetCheck = engine.evaluateFrame(
            melEnergies = FloatArray(80) { 0f },
            classifierTags = silenceTags(),
            shiftDistance = 0f,
            timestampMs = 350L
        )
        assertFalse(resetCheck.isCommercial)
        assertEquals(TvActionDecision.HOLD, resetCheck.recommendedAction)

        // Re-entering commercial on same scene (after zero norm) requires 3 frames again
        val afterInterrupt = engine.evaluateFrame(commercialVec, commercialTags(), 0f, 400L)
        assertTrue(afterInterrupt.isCommercial)
        assertEquals(TvActionDecision.HOLD, afterInterrupt.recommendedAction)
        // Confirm silenceTags score was lower even before zero-norm
        assertTrue(nonCommercial.commercialScore < 0.75f)
    }

    @Test
    fun evaluateCommercial_clampsScoreWithinZeroToOne() {
        val engine = FrameDecisionEngine()
        val extremeCommercial = engine.evaluateCommercial(
            features = uniformVector(80, 10.0f),
            classifierTags = listOf(ClassifierScore("Commercial Advertisement Jingle", 5.0f)),
            shiftDistance = 50.0f,
            cutCadencePerMin = 120f
        )
        assertEquals(1.0f, extremeCommercial.commercialScore, 0.0001f)
        assertTrue(extremeCommercial.isCommercial)

        val extremeSilence = engine.evaluateCommercial(
            features = FloatArray(80) { -1.0f },
            classifierTags = listOf(ClassifierScore("Silence", 1.0f), ClassifierScore("Speech", 1.0f)),
            shiftDistance = Float.NaN,
            cutCadencePerMin = 0f
        )
        assertEquals(0.0f, extremeSilence.commercialScore, 0.0001f)
        assertFalse(extremeSilence.isCommercial)
    }

    @Test
    fun reset_clearsShotHistoryAndCountersWhileKeepingThresholds() {
        val engine = FrameDecisionEngine()
        engine.setSceneSimilarityThreshold(0.90f)
        val vecA = FloatArray(80) { idx -> if (idx < 40) 0.9f else 0.05f }
        val vecB = FloatArray(80) { idx -> if (idx >= 40) 0.9f else 0.05f }

        engine.evaluateFrame(vecA, commercialTags(), shiftDistance = 0.1f, timestampMs = 1_000L)
        engine.evaluateFrame(vecB, commercialTags(), shiftDistance = 0.4f, timestampMs = 1_500L)

        engine.reset()

        assertEquals(0.90f, engine.getSceneSimilarityThreshold(), 0.0001f)
        val postReset = engine.evaluateFrame(vecA, speechTags(), shiftDistance = 0.0f, timestampMs = 2_000L)
        assertTrue(postReset.isSameScene)
        assertEquals(1.0f, postReset.sceneSimilarity, 0.001f)
        assertEquals(0L, postReset.shotDurationMs)
        assertEquals(0f, postReset.cutCadencePerMin, 0.001f)
        assertEquals(TvActionDecision.HOLD, postReset.recommendedAction)
    }
}
