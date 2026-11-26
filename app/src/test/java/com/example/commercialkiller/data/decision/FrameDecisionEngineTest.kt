// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.audio.ClassifierScore
import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VisualFrameDescriptor
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

        // Interruption by non-commercial broadcast dialogue frame using identical spectrum -> resets streak
        val nonCommercial = engine.evaluateFrame(
            melEnergies = commercialVec,
            classifierTags = speechTags(),
            shiftDistance = 0f,
            timestampMs = 300L
        )
        assertFalse(nonCommercial.isCommercial)
        assertEquals(TvActionDecision.HOLD, nonCommercial.recommendedAction)
        assertTrue(nonCommercial.commercialScore < 0.75f)

        // Interruption by brief quiet gap preserves reset state -> requires 3 frames again
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

    @Test
    fun `media threshold clamps strictly within supported bounds`() {
        val engine = FrameDecisionEngine()
        assertEquals(FrameDecisionEngine.DEFAULT_MEDIA_THRESHOLD, engine.getMediaThreshold(), 0.001f)

        engine.setMediaThreshold(0.05f)
        assertEquals(FrameDecisionEngine.MIN_MEDIA_THRESHOLD, engine.getMediaThreshold(), 0.001f)

        engine.setMediaThreshold(0.99f)
        assertEquals(FrameDecisionEngine.MAX_MEDIA_THRESHOLD, engine.getMediaThreshold(), 0.001f)

        engine.setMediaThreshold(0.62f)
        assertEquals(0.62f, engine.getMediaThreshold(), 0.001f)
    }

    @Test
    fun `silence and ambient noise gate to non-media hold without commercial flag`() {
        val engine = FrameDecisionEngine()
        val silenceFeatures = FloatArray(80) { 0f }
        val silenceDecision = engine.evaluateFrame(
            melEnergies = silenceFeatures,
            classifierTags = listOf(ClassifierScore("Silence / Quiet Room", 0.90f)),
            shiftDistance = 0f,
            timestampMs = 100L
        )
        assertFalse(silenceDecision.isMedia)
        assertEquals(MediaEnvironment.SILENCE, silenceDecision.mediaEnvironment)
        assertFalse(silenceDecision.isCommercial)
        assertEquals(0f, silenceDecision.commercialScore, 0.001f)
        assertEquals(TvActionDecision.HOLD, silenceDecision.recommendedAction)

        // Low-frequency steady engine rumble profile with ambient tag
        val carFeatures = FloatArray(80) { i -> if (i < 12) 0.50f else 0.05f }
        val carDecision = engine.evaluateFrame(
            melEnergies = carFeatures,
            classifierTags = listOf(ClassifierScore("Vehicle / Engine Rumble", 0.78f)),
            shiftDistance = 0.01f,
            timestampMs = 200L
        )
        assertFalse(carDecision.isMedia)
        assertEquals(MediaEnvironment.AMBIENT_NOISE, carDecision.mediaEnvironment)
        assertFalse(carDecision.isCommercial)
        assertEquals(0f, carDecision.commercialScore, 0.001f)
        assertEquals(TvActionDecision.HOLD, carDecision.recommendedAction)
    }

    @Test
    fun `broadcast speech and commercial classify as media and gate after consecutive non-media reset`() {
        val engine = FrameDecisionEngine()
        val mediaFeatures = FloatArray(80) { i -> if (i in 8..38) 0.65f else 0.25f }
        val speechTags = listOf(ClassifierScore("Dialogue / Program Speech", 0.85f))

        val programDecision = engine.evaluateFrame(
            melEnergies = mediaFeatures,
            classifierTags = speechTags,
            shiftDistance = 0.04f,
            timestampMs = 100L
        )
        assertTrue(programDecision.isMedia)
        assertEquals(MediaEnvironment.BROADCAST_MEDIA, programDecision.mediaEnvironment)

        // Two commercial media frames accumulate toward the 3-frame MUTE threshold
        val commercialTags = listOf(
            ClassifierScore("Commercial / Ad Jingle", 0.90f),
            ClassifierScore("Music / Jingle", 0.55f)
        )
        val c1 = engine.evaluateFrame(mediaFeatures, commercialTags, 0.05f, 200L)
        val c2 = engine.evaluateFrame(mediaFeatures, commercialTags, 0.02f, 300L)
        assertTrue(c1.isMedia && c1.isCommercial)
        assertTrue(c2.isMedia && c2.isCommercial)
        assertEquals(TvActionDecision.HOLD, c2.recommendedAction)

        // 4 consecutive silence frames (> NON_MEDIA_STREAK_RESET_FRAMES=3) reset commercial streak
        val silenceFeatures = FloatArray(80) { 0f }
        val silenceTags = listOf(ClassifierScore("Silence / Quiet Room", 0.95f))
        for (tick in 1..4) {
            val nonMedia = engine.evaluateFrame(silenceFeatures, silenceTags, 0f, 300L + tick * 100L)
            assertFalse(nonMedia.isMedia)
            assertEquals(TvActionDecision.HOLD, nonMedia.recommendedAction)
        }

        // Next commercial media frame (same vector, high similarity) restarts streak at 1 -> HOLD
        val postReset = engine.evaluateFrame(mediaFeatures, commercialTags, 0.02f, 800L)
        assertTrue(postReset.isMedia)
        assertEquals(TvActionDecision.HOLD, postReset.recommendedAction)
    }

    @Test
    fun `low energy mic floor and room hum tags gate to non-media HOLD`() {
        val engine = FrameDecisionEngine()

        // Empty-room mic Mel energy below 0.08f floor gates to SILENCE + HOLD even without Silence tag
        val lowMicFloor = FloatArray(80) { 0.06f }
        val floorDecision = engine.evaluateFrame(
            melEnergies = lowMicFloor,
            classifierTags = listOf(ClassifierScore("Ambient / Room Noise", 0.60f)),
            shiftDistance = 0.01f,
            timestampMs = 100L
        )
        assertFalse(floorDecision.isMedia)
        assertEquals(MediaEnvironment.SILENCE, floorDecision.mediaEnvironment)
        assertFalse(floorDecision.isCommercial)
        assertEquals(TvActionDecision.HOLD, floorDecision.recommendedAction)

        // Expanded non-media ambient keywords ("Room", "Hum", "Air Conditioning") suppress media gate
        val hvacVector = FloatArray(80) { 0.14f }
        val hvacDecision = engine.evaluateFrame(
            melEnergies = hvacVector,
            classifierTags = listOf(
                ClassifierScore("Air Conditioning Hum", 0.85f),
                ClassifierScore("Room Noise", 0.72f)
            ),
            shiftDistance = 0.01f,
            timestampMs = 200L
        )
        assertFalse(hvacDecision.isMedia)
        assertEquals(MediaEnvironment.AMBIENT_NOISE, hvacDecision.mediaEnvironment)
        assertFalse(hvacDecision.isCommercial)
        assertEquals(TvActionDecision.HOLD, hvacDecision.recommendedAction)
    }

    private fun testVisualDescriptor(
        mode: String,
        timestampMs: Long,
        previous: VisualFrameDescriptor? = null
    ): VisualFrameDescriptor {
        val width = 64
        val height = 36
        val pixels = IntArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            when (mode) {
                "ANGLE_A" -> if (x < width / 2) 0xFF1D4ED8.toInt() else 0xFF334155.toInt()
                "ANGLE_B" -> if (y < height / 2) 0xFF15803D.toInt() else 0xFF475569.toInt()
                else -> if ((x + y) % 2 == 0) 0xFFEF4444.toInt() else 0xFFFACC15.toInt()
            }
        }
        if (mode != "AD") {
            for (y in (height * 3) / 4 until height) {
                for (x in (width * 3) / 4 until width) {
                    pixels[y * width + x] = if (y >= height - 4 && x >= width - 6) {
                        0xFFFFFFFF.toInt()
                    } else {
                        0xFF0F172A.toInt()
                    }
                }
            }
        }
        return VideoFrameAnalyzer.computeDescriptorFromArgbPixels(
            pixels = pixels,
            width = width,
            height = height,
            timestampMs = timestampMs,
            previous = previous
        )
    }

    @Test
    fun `AUDIO_VIDEO dampens borderline speech alerts on known A-B angles and fuses ad break MUTE`() {
        val engine = FrameDecisionEngine()
        val mediaVec = speechFormantVector(80)
        var prevDesc: VisualFrameDescriptor? = null

        // Learn baseline across 12 ticks (t = 0..2750 ms) alternating Angle A and Angle B
        for (i in 0 until 12) {
            val t = i * 250L
            val desc = testVisualDescriptor(if ((i / 3) % 2 == 0) "ANGLE_A" else "ANGLE_B", t, prevDesc)
            prevDesc = desc
            val d = engine.evaluateFrame(
                melEnergies = mediaVec,
                classifierTags = speechTags(),
                shiftDistance = 0.02f,
                timestampMs = t,
                visualDescriptor = desc,
                isTvMuted = false
            )
            assertEquals(DecisionModality.AUDIO_VIDEO, d.decisionModality)
            assertFalse(d.isCommercial)
        }

        // Borderline energetic dialogue back on known Angle A should stay PROGRAM thanks to dampening
        val energeticTags = listOf(
            ClassifierScore("Music / Sound Bed", 0.76f),
            ClassifierScore("Broadcast Content", 0.60f),
            ClassifierScore("Dialogue / Speech", 0.30f)
        )
        val backToAngleA = testVisualDescriptor("ANGLE_A", 3_000L, prevDesc)
        val damped = engine.evaluateFrame(
            melEnergies = uniformVector(80, 0.72f),
            classifierTags = energeticTags,
            shiftDistance = 0.04f,
            timestampMs = 3_000L,
            visualDescriptor = backToAngleA,
            isTvMuted = false
        )
        assertTrue(damped.isKnownSceneAngle)
        assertFalse("Known program angle + bug intact should dampen borderline commercial score (${damped.commercialScore})", damped.isCommercial)

        // Sudden commercial visual departure + lost watermark + moderate ad music crosses >= 0.75 -> MUTE
        val adVec = orthogonalCommercialVector(80)
        val adDesc = testVisualDescriptor("AD", 3_250L, backToAngleA)
        val mutedDecision = engine.evaluateFrame(
            melEnergies = adVec,
            classifierTags = energeticTags,
            shiftDistance = 0.45f,
            timestampMs = 3_250L,
            visualDescriptor = adDesc,
            isTvMuted = false
        )
        assertTrue("Expected visual divergence to boost commercial score >= 0.75, got ${mutedDecision.commercialScore}", mutedDecision.isCommercial)
        assertEquals(TvActionDecision.MUTE, mutedDecision.recommendedAction)
        assertTrue(mutedDecision.isProgramAnchorFrozen)
    }

    @Test
    fun `VIDEO_ONLY_MUTED ignores room silence and speech and unmutes on sustained program return`() {
        val engine = FrameDecisionEngine()
        val mediaVec = speechFormantVector(80)
        var prevDesc: VisualFrameDescriptor? = null

        // Build 3s pre-break program baseline (12 frames before rollback + 8 ticks)
        for (i in 0 until 20) {
            val t = i * 250L
            val desc = testVisualDescriptor(if ((i / 4) % 2 == 0) "ANGLE_A" else "ANGLE_B", t, prevDesc)
            prevDesc = desc
            engine.evaluateFrame(mediaVec, speechTags(), 0.02f, t, desc, isTvMuted = false)
        }

        // Trigger MUTE at t = 6_000 ms
        val adStartDesc = testVisualDescriptor("AD", 6_000L, prevDesc)
        val muteFrame = engine.evaluateFrame(
            melEnergies = uniformVector(80, 0.80f),
            classifierTags = commercialTags(),
            shiftDistance = 0.40f,
            timestampMs = 6_000L,
            visualDescriptor = adStartDesc,
            isTvMuted = false
        )
        assertEquals(TvActionDecision.MUTE, muteFrame.recommendedAction)
        assertTrue(muteFrame.isProgramAnchorFrozen)

        // While TV muted (6_250..9_500 < MIN_MUTE_HOLD_MS=4_000), pass room silence AND loud speech while visual shows ad
        var lastDesc = adStartDesc
        val silenceVec = FloatArray(80) { 0.01f }
        for (step in 1..14) {
            val t = 6_000L + step * 250L
            val curAd = testVisualDescriptor("AD", t, lastDesc)
            lastDesc = curAd
            val tags = if (step % 2 == 0) silenceTags() else speechTags()
            val vec = if (step % 2 == 0) silenceVec else mediaVec
            val d = engine.evaluateFrame(vec, tags, 0.25f, t, curAd, isTvMuted = true)
            assertEquals(DecisionModality.VIDEO_ONLY_MUTED, d.decisionModality)
            assertTrue("Must remain isMedia=true in muted video-only mode despite room silence", d.isMedia)
            assertTrue("Ad visual should keep return score below threshold, got ${d.programReturnScore}", d.programReturnScore < FrameDecisionEngine.PROGRAM_RETURN_UNMUTE_THRESHOLD)
            assertEquals(TvActionDecision.HOLD, d.recommendedAction)
        }

        // Program visually returns (Angle B, even while audio is still muted silence!) after MIN_MUTE_HOLD_MS elapsed
        var unmuted = false
        for (retStep in 1..7) {
            val t = 10_250L + retStep * 250L
            val retDesc = testVisualDescriptor("ANGLE_B", t, lastDesc)
            lastDesc = retDesc
            val d = engine.evaluateFrame(silenceVec, silenceTags(), 0f, t, retDesc, isTvMuted = true)
            assertEquals(DecisionModality.VIDEO_ONLY_MUTED, d.decisionModality)
            assertTrue("Expected high return score on Angle B return, got ${d.programReturnScore}", d.programReturnScore >= FrameDecisionEngine.PROGRAM_RETURN_UNMUTE_THRESHOLD)
            if (d.recommendedAction == TvActionDecision.UNMUTE) {
                unmuted = true
                assertFalse(d.isProgramAnchorFrozen)
                assertEquals(6, retStep)
                break
            }
        }
        assertTrue("Expected VIDEO_ONLY_MUTED to emit UNMUTE after 6 consecutive return video frames", unmuted)
    }

    @Test
    fun `AUDIO_ONLY fallback applies when visualDescriptor is null or stale beyond 1500 ms`() {
        val engine = FrameDecisionEngine()
        val staleDesc = testVisualDescriptor("AD", timestampMs = 100L)
        val decision = engine.evaluateFrame(
            melEnergies = speechFormantVector(80),
            classifierTags = speechTags(),
            shiftDistance = 0.02f,
            timestampMs = 5_000L, // 4,900 ms older than staleDesc
            visualDescriptor = staleDesc,
            isTvMuted = true // Even with mute flag true, stale visual falls back to audio-only
        )
        assertEquals(DecisionModality.AUDIO_ONLY, decision.decisionModality)
        assertTrue(decision.isMedia)
        assertFalse(decision.isCommercial)
    }

    @Test
    fun `UNMUTE clears break cadence history and restores pre-break scene angles`() {
        val engine = FrameDecisionEngine()
        val mediaVec = speechFormantVector(80)
        var prevDesc: VisualFrameDescriptor? = null

        // Build multi-angle pre-break baseline (Angle A + Angle B across 20 ticks)
        for (i in 0 until 20) {
            val t = i * 250L
            val desc = testVisualDescriptor(if ((i / 4) % 2 == 0) "ANGLE_A" else "ANGLE_B", t, prevDesc)
            prevDesc = desc
            engine.evaluateFrame(mediaVec, speechTags(), 0.02f, t, desc, isTvMuted = false)
        }

        // Trigger MUTE on commercial break and simulate activeSceneAngles reset as done on macro transition
        val adStartDesc = testVisualDescriptor("AD", 6_000L, prevDesc)
        val muteFrame = engine.evaluateFrame(
            melEnergies = uniformVector(80, 0.80f),
            classifierTags = commercialTags(),
            shiftDistance = 0.40f,
            timestampMs = 6_000L,
            visualDescriptor = adStartDesc,
            isTvMuted = false
        )
        assertEquals(TvActionDecision.MUTE, muteFrame.recommendedAction)
        engine.resetActiveSceneAngles()

        // Inject rapid novel visual cuts during muted ad break (10_000..11_500ms)
        val silenceVec = FloatArray(80) { 0.01f }
        var lastDesc = adStartDesc
        for (step in 1..6) {
            val t = 6_000L + step * 500L
            val curAd = testVisualDescriptor("AD", t, lastDesc).copy(visualChangeScore = 0.65f)
            lastDesc = curAd
            engine.evaluateFrame(silenceVec, silenceTags(), 0f, t, curAd, isTvMuted = true)
        }

        // Trigger sustained visual UNMUTE back on Angle A after MIN_MUTE_HOLD_MS
        var unmutedDecision: FrameDecision? = null
        for (retStep in 1..7) {
            val t = 10_500L + retStep * 250L
            val retDesc = testVisualDescriptor("ANGLE_A", t, lastDesc)
            lastDesc = retDesc
            val d = engine.evaluateFrame(silenceVec, silenceTags(), 0f, t, retDesc, isTvMuted = true)
            if (d.recommendedAction == TvActionDecision.UNMUTE) {
                unmutedDecision = d
                break
            }
        }
        assertTrue(unmutedDecision != null)
        assertTrue("Expected restored activeSceneAngles >= 2 after UNMUTE", engine.programCache.activeAngleCount() >= 2)

        // Post-unmute frame back on pre-break Angle B with borderline audio must have cleared cadence and not MUTE
        val postUnmuteAngleB = testVisualDescriptor("ANGLE_B", 13_000L, lastDesc)
        val borderlineTags = listOf(
            ClassifierScore("Music / Sound Bed", 0.72f),
            ClassifierScore("Broadcast Content", 0.65f),
            ClassifierScore("Dialogue / Speech", 0.35f)
        )
        val postDecision = engine.evaluateFrame(
            melEnergies = speechFormantVector(80),
            classifierTags = borderlineTags,
            shiftDistance = 0.04f,
            timestampMs = 13_000L,
            visualDescriptor = postUnmuteAngleB,
            isTvMuted = false
        )
        assertEquals(0f, postDecision.cutCadencePerMin, 0.001f)
        assertEquals(0f, postDecision.visualCutCadencePerMin, 0.001f)
        assertTrue("Expected restored angle or prototype similarity >= 0.78, got ${postDecision.bestAngleSimilarity}", postDecision.isKnownSceneAngle)
        assertTrue("Expected post-unmute returned show not to MUTE", postDecision.recommendedAction != TvActionDecision.MUTE)
    }

    private fun topLeftLogoDescriptor(
        interiorArgb: Int,
        withTopLeftLogo: Boolean,
        letterboxRows: Int = 0,
        pillarboxCols: Int = 0,
        timestampMs: Long,
        previous: VisualFrameDescriptor? = null
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
        return VideoFrameAnalyzer.computeDescriptorFromArgbPixels(pixels, width, height, timestampMs, previous)
    }

    @Test
    fun `blackSilencePulse triggers after broadcast media decays over 2s and stays zero when muted or prolonged`() {
        val engine = FrameDecisionEngine()
        val mediaVec = speechFormantVector(80)
        val silenceVec = FloatArray(80) { 0.01f }
        val brightFrame = topLeftLogoDescriptor(0xFF335577.toInt(), withTopLeftLogo = false, timestampMs = 0L)
        val darkFrame = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(IntArray(64 * 36) { 0xFF020202.toInt() }, 64, 36, 1_000L)

        // Startup silence without prior media keeps blackSilencePulse == 0
        val startupSilence = engine.evaluateFrame(silenceVec, silenceTags(), 0f, 100L, darkFrame.copy(timestampMs = 100L), isTvMuted = false)
        assertEquals(0f, startupSilence.broadcastCues.blackSilencePulse, 0.001f)

        // Broadcast media plays for 500 ms
        for (step in 1..5) {
            val t = 500L + step * 100L
            engine.evaluateFrame(mediaVec, speechTags(), 0.02f, t, brightFrame.copy(timestampMs = t), isTvMuted = false)
        }

        // Fade to black + silence at t = 1_100 ms -> pulse > 0.50
        val fadePulse = engine.evaluateFrame(silenceVec, silenceTags(), 0.05f, 1_100L, darkFrame.copy(timestampMs = 1_100L), isTvMuted = false)
        assertTrue("Expected blackSilencePulse > 0.50 on black+silence fade, got ${fadePulse.broadcastCues.blackSilencePulse}", fadePulse.broadcastCues.blackSilencePulse > 0.50f)

        // Subsequent bright ad frame decays linearly over 2_000 ms
        val at1s = engine.evaluateFrame(mediaVec, speechTags(), 0.02f, 2_100L, brightFrame.copy(timestampMs = 2_100L), isTvMuted = false)
        assertTrue("Expected decayed pulse around half peak at +1s, got ${at1s.broadcastCues.blackSilencePulse}", at1s.broadcastCues.blackSilencePulse in 0.20f..0.70f)
        val at2s = engine.evaluateFrame(mediaVec, speechTags(), 0.02f, 3_100L, brightFrame.copy(timestampMs = 3_100L), isTvMuted = false)
        assertEquals(0f, at2s.broadcastCues.blackSilencePulse, 0.001f)

        // Prolonged non-media silence >= 20 frames clears pulse
        engine.evaluateFrame(silenceVec, silenceTags(), 0.05f, 3_200L, darkFrame.copy(timestampMs = 3_200L), isTvMuted = false)
        var lastProlonged = at2s
        for (f in 1..20) {
            val t = 3_200L + f * 100L
            lastProlonged = engine.evaluateFrame(silenceVec, silenceTags(), 0f, t, darkFrame.copy(timestampMs = t), isTvMuted = false)
        }
        assertEquals(0f, lastProlonged.broadcastCues.blackSilencePulse, 0.001f)

        // AUDIO_ONLY and VIDEO_ONLY_MUTED keep blackSilencePulse == 0
        val audioOnly = engine.evaluateFrame(silenceVec, silenceTags(), 0f, 5_300L, visualDescriptor = null, isTvMuted = false)
        assertEquals(0f, audioOnly.broadcastCues.blackSilencePulse, 0.001f)
        val muted = engine.evaluateFrame(silenceVec, silenceTags(), 0f, 5_400L, darkFrame.copy(timestampMs = 5_400L), isTvMuted = true)
        assertEquals(0f, muted.broadcastCues.blackSilencePulse, 0.001f)

        // reset() / onManualUnmute() clear pulse
        engine.evaluateFrame(mediaVec, speechTags(), 0.02f, 6_000L, brightFrame.copy(timestampMs = 6_000L), isTvMuted = false)
        val reTrigger = engine.evaluateFrame(silenceVec, silenceTags(), 0.05f, 6_100L, darkFrame.copy(timestampMs = 6_100L), isTvMuted = false)
        assertTrue(reTrigger.broadcastCues.blackSilencePulse > 0.50f)
        engine.onManualUnmute()
        val postManual = engine.evaluateFrame(mediaVec, speechTags(), 0.02f, 6_200L, brightFrame.copy(timestampMs = 6_200L), isTvMuted = false)
        assertEquals(0f, postManual.broadcastCues.blackSilencePulse, 0.001f)
    }

    @Test
    fun `AUDIO_ONLY boosts commercialScore when repeat ad matches earlier pod`() {
        val engine = FrameDecisionEngine()
        val adVecA = FloatArray(80) { i -> if (i < 40) 0.55f else 0.15f }
        val adVecB = FloatArray(80) { i -> if (i >= 40) 0.55f else 0.15f }
        val borderlineTags = listOf(
            ClassifierScore("Music Bed", 0.72f),
            ClassifierScore("Broadcast Content", 0.55f),
            ClassifierScore("Dialogue / Speech", 0.20f)
        )

        // Establish baseline commercial score without repeat history
        val baseline = engine.evaluateCommercial(adVecB, borderlineTags, 0.10f, 0f).commercialScore

        // Feed Pod 1 (0..2s), filler (2..11s), and repeat window 1 (11..12s)
        for (step in 0 until 10) {
            engine.evaluateFrame(adVecA, borderlineTags, 0.05f, step * 100L, null, false)
        }
        for (step in 10 until 20) {
            engine.evaluateFrame(adVecB, borderlineTags, 0.05f, step * 100L, null, false)
        }
        val fillerVec = FloatArray(80) { 0.45f }
        for (step in 20 until 110) {
            engine.evaluateFrame(fillerVec, speechTags(), 0.02f, step * 100L, null, false)
        }
        for (step in 110 until 120) {
            engine.evaluateFrame(adVecA, borderlineTags, 0.05f, step * 100L, null, false)
        }

        val repeatDecision = engine.evaluateFrame(adVecB, borderlineTags, 0.10f, 12_200L, null, false)
        assertTrue("Expected repeatAdMatchScore > 0.5, got ${repeatDecision.broadcastCues.repeatAdMatchScore}", repeatDecision.broadcastCues.repeatAdMatchScore > 0.5f)
        assertTrue("Expected AUDIO_ONLY commercialScore boost (${repeatDecision.commercialScore} > $baseline)", repeatDecision.commercialScore > baseline + 0.08f)
    }

    @Test
    fun `AUDIO_VIDEO cues override known-angle dampening and VIDEO_ONLY_MUTED integrates cues 1 3 and 4`() {
        val engine = FrameDecisionEngine()
        val mediaVec = speechFormantVector(80)
        var prevDesc: VisualFrameDescriptor? = null

        // Build 12 frames (t = 0..2_750) of widescreen program with top-left edge logo
        for (i in 0 until 12) {
            val t = i * 250L
            val d = topLeftLogoDescriptor(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = t, previous = prevDesc)
            prevDesc = d
            engine.evaluateFrame(mediaVec, speechTags(), 0.02f, t, d, isTvMuted = false)
        }

        // Known background color WITH aspectRatioShift (pillarbox) + logo dropout overrides known-angle dampening
        val shiftedSameBg = topLeftLogoDescriptor(0xFF224466.toInt(), withTopLeftLogo = false, pillarboxCols = 8, timestampMs = 3_000L, previous = prevDesc)
        val energeticTags = listOf(
            ClassifierScore("Music / Sound Bed", 0.76f),
            ClassifierScore("Broadcast Content", 0.60f),
            ClassifierScore("Dialogue / Speech", 0.30f)
        )
        val baselineDamped = engine.evaluateFrame(
            uniformVector(80, 0.72f),
            energeticTags,
            0.04f,
            2_900L,
            topLeftLogoDescriptor(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = 2_900L, previous = prevDesc),
            isTvMuted = false
        )
        val overridden = engine.evaluateFrame(
            uniformVector(80, 0.72f),
            energeticTags,
            0.04f,
            3_000L,
            shiftedSameBg,
            isTvMuted = false
        )
        assertTrue("Expected cues to override dampening (${overridden.commercialScore} > ${baselineDamped.commercialScore})", overridden.commercialScore > baselineDamped.commercialScore + 0.10f)
        assertTrue(overridden.broadcastCues.aspectRatioShift >= 0.80f)
        assertTrue(overridden.broadcastCues.logoDropoutScore >= 0.50f)

        // Freeze for break and test VIDEO_ONLY_MUTED: clean top-left logo frame returns high score, aspect shift reduces returnScore below 0.80
        val cleanReturn = topLeftLogoDescriptor(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = 8_000L, previous = shiftedSameBg)
        val mutedClean = engine.evaluateFrame(FloatArray(80) { 0.01f }, silenceTags(), 0f, 8_000L, cleanReturn, isTvMuted = true)
        assertTrue("Expected high returnScore with top-left edge logo, got ${mutedClean.programReturnScore}", mutedClean.programReturnScore >= FrameDecisionEngine.PROGRAM_RETURN_UNMUTE_THRESHOLD)

        val mutedShifted = engine.evaluateFrame(FloatArray(80) { 0.01f }, silenceTags(), 0f, 8_250L, shiftedSameBg.copy(timestampMs = 8_250L), isTvMuted = true)
        assertTrue("Expected aspectRatioShift to block false unmute (${mutedShifted.programReturnScore} < 0.80)", mutedShifted.programReturnScore < FrameDecisionEngine.PROGRAM_RETURN_UNMUTE_THRESHOLD)
    }

    @Test
    fun `evaluateFrame sanitizes NaN and Infinity across melEnergies shiftDistance and visualDescriptor`() {
        val engine = FrameDecisionEngine()
        val baseVisual = topLeftLogoDescriptor(0xFF224466.toInt(), withTopLeftLogo = true, timestampMs = 0L)

        fun assertDecisionFiniteInUnitRange(d: FrameDecision) {
            assertTrue(d.mediaScore.isFinite() && d.mediaScore in 0f..1f)
            assertTrue(d.commercialScore.isFinite() && d.commercialScore in 0f..1f)
            assertTrue(d.sceneSimilarity.isFinite() && d.sceneSimilarity in 0f..1f)
            assertTrue(d.programReturnScore.isFinite() && d.programReturnScore in 0f..1f)
            assertTrue(d.visualChangeScore.isFinite() && d.visualChangeScore in 0f..1f)
            assertTrue(d.bestAngleSimilarity.isFinite() && d.bestAngleSimilarity in 0f..1f)
            assertTrue(d.broadcastCues.logoMaskStrength.isFinite() && d.broadcastCues.logoMaskStrength in 0f..1f)
            assertTrue(d.broadcastCues.logoDropoutScore.isFinite() && d.broadcastCues.logoDropoutScore in 0f..1f)
            assertTrue(d.broadcastCues.blackSilencePulse.isFinite() && d.broadcastCues.blackSilencePulse in 0f..1f)
            assertTrue(d.broadcastCues.aspectRatioShift.isFinite() && d.broadcastCues.aspectRatioShift in 0f..1f)
            assertTrue(d.broadcastCues.repeatAdDistance.isFinite() && d.broadcastCues.repeatAdDistance in 0f..1f)
            assertTrue(d.broadcastCues.repeatAdMatchScore.isFinite() && d.broadcastCues.repeatAdMatchScore in 0f..1f)
        }

        // Feed 12 frames with NaN/Infinity visual cue fields in AUDIO_VIDEO mode
        for (i in 0 until 12) {
            val t = i * 250L
            val badVisual = baseVisual.copy(
                timestampMs = t,
                meanLuma = if (i % 2 == 0) Float.NaN else Float.POSITIVE_INFINITY,
                visualChangeScore = if (i % 2 == 0) Float.POSITIVE_INFINITY else Float.NaN,
                cornerEdgeMasks = FloatArray(16) { Float.NaN },
                letterboxRatio = Float.NaN,
                pillarboxRatio = Float.POSITIVE_INFINITY
            )
            val d = engine.evaluateFrame(
                melEnergies = FloatArray(80) { if (it % 2 == 0) Float.NaN else Float.POSITIVE_INFINITY },
                classifierTags = speechTags(),
                shiftDistance = Float.POSITIVE_INFINITY,
                timestampMs = t,
                visualDescriptor = badVisual,
                isTvMuted = false
            )
            assertDecisionFiniteInUnitRange(d)
        }

        // Also test mixed finite + Infinity mid-band Mel energies + NaN shiftDistance in AUDIO_ONLY
        val mixedMel = FloatArray(80) { i -> if (i == 30) Float.POSITIVE_INFINITY else if (i == 31) Float.NaN else 0.5f }
        val audioOnlyDecision = engine.evaluateFrame(
            melEnergies = mixedMel,
            classifierTags = speechTags(),
            shiftDistance = Float.NaN,
            timestampMs = 3_500L,
            visualDescriptor = null,
            isTvMuted = false
        )
        assertDecisionFiniteInUnitRange(audioOnlyDecision)

        // Also test VIDEO_ONLY_MUTED with non-finite visualDescriptor fields
        val mutedDecision = engine.evaluateFrame(
            melEnergies = FloatArray(0),
            classifierTags = emptyList(),
            shiftDistance = Float.NaN,
            timestampMs = 4_000L,
            visualDescriptor = baseVisual.copy(
                timestampMs = 4_000L,
                visualChangeScore = Float.NaN,
                cornerEdgeMasks = FloatArray(0),
                letterboxRatio = Float.POSITIVE_INFINITY,
                pillarboxRatio = Float.NaN
            ),
            isTvMuted = true
        )
        assertDecisionFiniteInUnitRange(mutedDecision)
    }
}
