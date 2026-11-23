// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSegmentTrackerTest {

    private fun mel(level: Float): FloatArray = FloatArray(80) { level }

    private fun programDecision(
        isKnownAngle: Boolean = true,
        bestAngleSim: Float = 0.92f,
        commercialScore: Float = 0.20f,
        action: TvActionDecision = TvActionDecision.HOLD
    ) = FrameDecision(
        isMedia = true,
        mediaScore = 0.92f,
        mediaEnvironment = MediaEnvironment.BROADCAST_MEDIA,
        isCommercial = false,
        commercialScore = commercialScore,
        isSameScene = isKnownAngle,
        sceneSimilarity = bestAngleSim,
        recommendedAction = action,
        decisionModality = DecisionModality.AUDIO_VIDEO,
        isKnownSceneAngle = isKnownAngle,
        bestAngleSimilarity = bestAngleSim
    )

    private fun commercialDecision(
        commercialScore: Float = 0.88f,
        action: TvActionDecision = TvActionDecision.HOLD,
        modality: DecisionModality = DecisionModality.AUDIO_VIDEO
    ) = FrameDecision(
        isMedia = true,
        mediaScore = 0.95f,
        mediaEnvironment = MediaEnvironment.BROADCAST_MEDIA,
        isCommercial = true,
        commercialScore = commercialScore,
        isSameScene = false,
        sceneSimilarity = 0.45f,
        recommendedAction = action,
        decisionModality = modality
    )

    private fun silenceDecision() = FrameDecision(
        isMedia = false,
        mediaScore = 0.10f,
        mediaEnvironment = MediaEnvironment.SILENCE,
        isCommercial = false,
        commercialScore = 0f,
        isSameScene = true,
        sceneSimilarity = 1.0f,
        recommendedAction = TvActionDecision.HOLD
    )

    @Test
    fun `20 rapid A-B camera angle edits within a show remain 1 single PROGRAM segment`() {
        var macroResets = 0
        val tracker = SessionSegmentTracker { macroResets++ }

        for (i in 0 until 200) {
            val t = i * 100L
            // Simulate visual angle toggle every 1s (known angle stays true after learning, 1 transition tick false >= 0.55f)
            val isCutTick = (i % 10 == 0 && i > 0)
            val d = programDecision(
                isKnownAngle = !isCutTick,
                bestAngleSim = if (isCutTick) 0.58f else 0.94f
            )
            tracker.onFrame(t, d, mel(0.45f), null)
        }

        val segments = tracker.getSegments()
        assertEquals(1, segments.size)
        assertEquals(SegmentLabel.PROGRAM, segments[0].label)
        assertEquals(0, macroResets)
    }

    @Test
    fun `program to muted commercial break to returned program retains newest-at-top segments with 16-col strips`() {
        val tracker = SessionSegmentTracker()

        // 0..10s: PROGRAM
        for (i in 0 until 100) {
            tracker.onFrame(i * 100L, programDecision(), mel(0.40f), null)
        }
        // 10.0s: MUTE triggered -> immediate COMMERCIAL segment
        tracker.onFrame(10_000L, commercialDecision(action = TvActionDecision.MUTE), mel(0.85f), null)
        for (i in 101 until 220) {
            tracker.onFrame(i * 100L, commercialDecision(modality = DecisionModality.VIDEO_ONLY_MUTED), mel(0f), null)
        }
        // 22.0s: UNMUTE triggered -> immediate PROGRAM segment
        tracker.onFrame(22_000L, programDecision(action = TvActionDecision.UNMUTE), mel(0.42f), null)
        for (i in 221 until 300) {
            tracker.onFrame(i * 100L, programDecision(), mel(0.42f), null)
        }

        val finalized = tracker.finalizeActiveSegment(30_000L)
        assertEquals(3, finalized.size)
        // Newest at top (index 0)
        assertEquals(SegmentLabel.PROGRAM, finalized[0].label)
        assertEquals(SegmentLabel.COMMERCIAL, finalized[1].label)
        assertEquals(SegmentLabel.PROGRAM, finalized[2].label)

        // Active flags closed on stop and all strips populated
        assertFalse(finalized[0].isActive)
        assertTrue(finalized[0].melStrip.size <= SessionSegmentTracker.MEL_STRIP_COLUMNS)
        assertTrue(finalized[2].melStrip.isNotEmpty())
        assertEquals(SessionSegmentTracker.MEL_STRIP_BANDS, finalized[2].melStrip[0].size)

        // Stage 2 verdict attaches to the commercial break entry
        val annotated = tracker.attachStage2Verdict(finalized[1].id, "COMMERCIAL")
        assertEquals("COMMERCIAL", annotated[1].stage2Verdict)
    }

    @Test
    fun `brief dramatic silence gap does not fragment PROGRAM segment`() {
        val tracker = SessionSegmentTracker()
        for (i in 0 until 50) {
            tracker.onFrame(i * 100L, programDecision(), mel(0.5f), null)
        }
        // 1.2s dramatic pause (< 2.0s CONFIRM_NO_MEDIA_MS)
        for (i in 50 until 62) {
            tracker.onFrame(i * 100L, silenceDecision(), mel(0.01f), null)
        }
        for (i in 62 until 100) {
            tracker.onFrame(i * 100L, programDecision(), mel(0.5f), null)
        }
        assertEquals(1, tracker.getSegments().size)
    }

    @Test
    fun `30-minute simulated broadcast yields ~a dozen macro transitions instead of hundreds`() {
        val tracker = SessionSegmentTracker()
        val totalTicks = 18_000 // 30 minutes at 100 ms per tick
        for (i in 0 until totalTicks) {
            val t = i * 100L
            // Place 4 commercial pods across 30 min: [4-6m], [11-13m], [18-20m], [25-27m]
            val minute = t / 60_000L
            val inAd = minute in listOf(4L, 5L, 11L, 12L, 18L, 19L, 25L, 26L)
            val d = if (inAd) {
                commercialDecision()
            } else {
                // Every 2s simulate an intra-scene angle cut back/forth
                val cutTick = (i % 20 == 0)
                programDecision(isKnownAngle = !cutTick, bestAngleSim = if (cutTick) 0.56f else 0.93f)
            }
            tracker.onFrame(t, d, mel(if (inAd) 0.8f else 0.45f), null)
        }

        val segments = tracker.finalizeActiveSegment(totalTicks * 100L)
        // 4 ads + 5 show acts = 9 segments (far fewer than hundreds!)
        assertTrue("Expected <= 15 macro segments over 30m, got ${segments.size}", segments.size in 8..15)
    }

    @Test
    fun `2_5s silence pause with active video does not split PROGRAM while 5_5s gap transitions`() {
        val tracker = SessionSegmentTracker()
        for (i in 0 until 50) {
            tracker.onFrame(i * 100L, programDecision(), mel(0.5f), null)
        }
        val avSilence = silenceDecision().copy(decisionModality = DecisionModality.AUDIO_VIDEO)
        for (i in 50 until 75) {
            tracker.onFrame(i * 100L, avSilence, mel(0.01f), null)
        }
        for (i in 75 until 100) {
            tracker.onFrame(i * 100L, programDecision(), mel(0.5f), null)
        }
        assertEquals(1, tracker.getSegments().size)

        for (i in 100 until 156) {
            tracker.onFrame(i * 100L, avSilence, mel(0.01f), null)
        }
        val segments = tracker.getSegments()
        assertEquals(2, segments.size)
        assertEquals(SegmentLabel.NO_MEDIA, segments[0].label)
        assertEquals(SegmentLabel.PROGRAM, segments[1].label)
    }

    @Test
    fun `initial startup NO_MEDIA under 3s is replaced in-place while 3_5s NO_MEDIA is preserved`() {
        val shortLeadTracker = SessionSegmentTracker()
        for (i in 0 until 15) {
            shortLeadTracker.onFrame(i * 100L, silenceDecision(), mel(0.01f), null)
        }
        for (i in 15 until 35) {
            shortLeadTracker.onFrame(i * 100L, programDecision(), mel(0.5f), null)
        }
        val shortSegments = shortLeadTracker.getSegments()
        assertEquals(1, shortSegments.size)
        assertEquals(SegmentLabel.PROGRAM, shortSegments[0].label)

        val longLeadTracker = SessionSegmentTracker()
        for (i in 0 until 35) {
            longLeadTracker.onFrame(i * 100L, silenceDecision(), mel(0.01f), null)
        }
        for (i in 35 until 55) {
            longLeadTracker.onFrame(i * 100L, programDecision(), mel(0.5f), null)
        }
        val longSegments = longLeadTracker.getSegments()
        assertEquals(2, longSegments.size)
        assertEquals(SegmentLabel.PROGRAM, longSegments[0].label)
        assertEquals(SegmentLabel.NO_MEDIA, longSegments[1].label)
    }
}
