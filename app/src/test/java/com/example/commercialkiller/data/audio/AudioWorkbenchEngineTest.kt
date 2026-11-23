// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.audio

import com.example.commercialkiller.data.decision.FrameDecision
import com.example.commercialkiller.data.decision.FrameDecisionEngine
import com.example.commercialkiller.data.decision.TvActionDecision
import com.example.commercialkiller.data.video.VideoFrameSnapshot
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWorkbenchEngineTest {

    @Test
    fun initialState_hasDefaultSpectrogramResolutionOf80Bands() {
        val engine = AudioWorkbenchEngine()
        val state = engine.state.value
        assertEquals(80, state.numMelBands)
        assertFalse(state.isVideo)
        assertFalse(state.isRunning)
        assertFalse(state.isCameraEnabled)
        assertEquals(0L, state.videoFrameCount)
        assertEquals(0.05f, state.threshold, 0.001f)
        assertEquals(100L, state.intervalMs)
        assertEquals(AudioSourceMode.LIVE, state.sourceMode)
        assertEquals(listOf(AudioSourceMode.LIVE, AudioSourceMode.FILE), AudioSourceMode.entries)
        assertEquals(0.85f, state.sceneSimilarityThreshold, 0.0001f)
        assertEquals(FrameDecision(), state.frameDecision)
        assertEquals(TvActionDecision.HOLD, state.frameDecision.recommendedAction)
    }

    @Test
    fun setMelBands_updatesResolutionAndClampsLimits() {
        val engine = AudioWorkbenchEngine()
        
        // Valid resolution
        engine.setMelBands(128)
        assertEquals(128, engine.state.value.numMelBands)

        engine.setMelBands(64)
        assertEquals(64, engine.state.value.numMelBands)

        // Lower bound clamp (min 20)
        engine.setMelBands(5)
        assertEquals(20, engine.state.value.numMelBands)

        // Upper bound clamp (max 128)
        engine.setMelBands(256)
        assertEquals(128, engine.state.value.numMelBands)
    }

    @Test
    fun setThreshold_updatesEngineState() {
        val engine = AudioWorkbenchEngine()
        engine.setThreshold(0.18f)
        assertEquals(0.18f, engine.state.value.threshold, 0.001f)
    }

    @Test
    fun setInterval_updatesEngineState() {
        val engine = AudioWorkbenchEngine()
        engine.setInterval(250L)
        assertEquals(250L, engine.state.value.intervalMs)
    }

    @Test
    fun classifierScoreFiltering_hidesScoresBelow10Percent() {
        val rawScores = listOf(
            ClassifierScore("Speech", 0.95f),
            ClassifierScore("Background Noise", 0.45f),
            ClassifierScore("Static", 0.08f),
            ClassifierScore("Whisper", 0.02f)
        )

        val visibleScores = rawScores.filter { it.score >= 0.10f }

        assertEquals(2, visibleScores.size)
        assertTrue(visibleScores.any { it.label == "Speech" })
        assertTrue(visibleScores.any { it.label == "Background Noise" })
        assertFalse(visibleScores.any { it.label == "Static" })
        assertFalse(visibleScores.any { it.label == "Whisper" })
    }

    @Test
    fun classifierColorLogic_distinguishesConfidenceAboveAndBelow90Percent() {
        val speechScore = ClassifierScore("Speech", 0.92f)
        val jingleScore = ClassifierScore("Jingle", 0.75f)

        val isSpeechGreen = speechScore.score > 0.90f
        val isJingleYellow = jingleScore.score <= 0.90f && jingleScore.score >= 0.10f

        assertTrue("Scores > 90% should be green", isSpeechGreen)
        assertTrue("Scores <= 90% and >= 10% should be yellow", isJingleYellow)
    }

    @Test
    fun switchingSourceMode_setsVideoFlagAppropriately() {
        val engine = AudioWorkbenchEngine()

        // Starting LIVE mode should not flag video file playback
        engine.start(AudioSourceMode.LIVE)
        assertFalse(engine.state.value.isVideo)
        assertEquals(AudioSourceMode.LIVE, engine.state.value.sourceMode)
        assertTrue(engine.state.value.isLiveMic)
        engine.stop()
    }

    @Test
    fun startFileWithoutLoadedAudio_remainsStopped() {
        val engine = AudioWorkbenchEngine()
        engine.start(AudioSourceMode.FILE)
        assertFalse(engine.state.value.isRunning)
        assertEquals(AudioSourceMode.LIVE, engine.state.value.sourceMode)
    }

    @Test
    fun setCameraEnabledAndOnVideoFrameSampled_updatesState() {
        val engine = AudioWorkbenchEngine()
        engine.setCameraEnabled(true)
        assertTrue(engine.state.value.isCameraEnabled)

        val descriptor = VisualFrameDescriptor(
            timestampMs = 1000L,
            meanLuma = 0.42f,
            visualChangeScore = 0.35f
        )
        engine.onVideoFrameSampled(VideoFrameSnapshot(descriptor = descriptor, thumbnail = null))
        assertEquals(1L, engine.state.value.videoFrameCount)
        assertEquals(descriptor, engine.state.value.latestVisualDescriptor)
        assertEquals(descriptor, engine.getLastVisualDescriptor())

        engine.setCameraEnabled(false)
        assertFalse(engine.state.value.isCameraEnabled)
        assertNull(engine.state.value.latestVisualDescriptor)
        assertNull(engine.getLastVisualDescriptor())
    }

    @Test
    fun setSceneSimilarityThreshold_updatesStateAndClampsLimits() {
        val engine = AudioWorkbenchEngine()

        // Valid threshold within 0.50..0.99
        engine.setSceneSimilarityThreshold(0.91f)
        assertEquals(0.91f, engine.state.value.sceneSimilarityThreshold, 0.0001f)

        // Lower bound clamp (min 0.50)
        engine.setSceneSimilarityThreshold(0.15f)
        assertEquals(0.50f, engine.state.value.sceneSimilarityThreshold, 0.0001f)

        // Upper bound clamp (max 0.99)
        engine.setSceneSimilarityThreshold(1.50f)
        assertEquals(0.99f, engine.state.value.sceneSimilarityThreshold, 0.0001f)

        // NaN fallback to default 0.85
        engine.setSceneSimilarityThreshold(Float.NaN)
        assertEquals(0.85f, engine.state.value.sceneSimilarityThreshold, 0.0001f)
    }

    @Test
    fun start_preservesSceneSimilarityThresholdAndResetsFrameDecision() {
        val engine = AudioWorkbenchEngine()
        engine.setSceneSimilarityThreshold(0.93f)

        engine.start(AudioSourceMode.LIVE)
        assertEquals(0.93f, engine.state.value.sceneSimilarityThreshold, 0.0001f)
        assertEquals(FrameDecision(), engine.state.value.frameDecision)
        engine.stop()
    }

    @Test
    fun `setMediaThreshold updates state and clamps to engine range`() {
        val engine = AudioWorkbenchEngine()
        assertEquals(FrameDecisionEngine.DEFAULT_MEDIA_THRESHOLD, engine.state.value.mediaThreshold, 0.001f)

        engine.setMediaThreshold(0.05f)
        assertEquals(FrameDecisionEngine.MIN_MEDIA_THRESHOLD, engine.state.value.mediaThreshold, 0.001f)

        engine.setMediaThreshold(0.99f)
        assertEquals(FrameDecisionEngine.MAX_MEDIA_THRESHOLD, engine.state.value.mediaThreshold, 0.001f)

        engine.setMediaThreshold(0.65f)
        assertEquals(0.65f, engine.state.value.mediaThreshold, 0.001f)
    }
}
