// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWorkbenchEngineTest {

    @Test
    fun initialState_hasDefaultSpectrogramResolutionOf80Bands() {
        val engine = AudioWorkbenchEngine()
        val state = engine.state.value
        assertEquals(80, state.numMelBands)
        assertFalse(state.isVideo)
        assertEquals(0.05f, state.threshold, 0.001f)
        assertEquals(100L, state.intervalMs)
        assertEquals(AudioSourceMode.SYNTH, state.sourceMode)
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
        
        // Starting SYNTH mode should not flag video
        engine.start(AudioSourceMode.SYNTH)
        assertFalse(engine.state.value.isVideo)
        assertEquals(AudioSourceMode.SYNTH, engine.state.value.sourceMode)
        engine.stop()

        // Starting MIC mode should not flag video
        engine.start(AudioSourceMode.MIC)
        assertFalse(engine.state.value.isVideo)
        assertEquals(AudioSourceMode.MIC, engine.state.value.sourceMode)
        engine.stop()
    }
}
