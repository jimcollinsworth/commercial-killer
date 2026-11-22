// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class AudioClassifierEngineTest {

    private val classifier = AudioClassifierEngine(context = null)

    @Test
    fun classifyByAcousticFeatures_detectsSilence() {
        val silentSamples = FloatArray(512) { 0f }
        val results = classifier.classifyByAcousticFeatures(silentSamples)

        assertTrue("Results should not be empty", results.isNotEmpty())
        assertEquals("Silence / Transition Gap", results[0].label)
        assertTrue("Confidence should be high for silence", results[0].score >= 0.9f)
    }

    @Test
    fun classifyByAcousticFeatures_detectsSpeechPattern() {
        // Dialogue / speech simulation with pitch and formants at moderate loudness
        val sampleRate = 16000
        val speechSamples = FloatArray(512) { i ->
            val t = i.toDouble() / sampleRate
            val pitch = sin(2.0 * Math.PI * 130.0 * t)
            val f1 = sin(2.0 * Math.PI * 500.0 * t) * 0.4
            ((pitch * 0.3 + f1) * 0.2).toFloat() // RMS ~ 0.05
        }

        val results = classifier.classifyByAcousticFeatures(speechSamples)
        assertTrue("Results should not be empty", results.isNotEmpty())
        val topLabel = results[0].label
        assertTrue("Top label should be Speech or Broadcast, got: $topLabel",
            topLabel.contains("Speech") || topLabel.contains("Broadcast") || topLabel.contains("Dialogue"))
    }

    @Test
    fun classifyByAcousticFeatures_detectsCommercialLoudnessJump() {
        // Commercial simulation: high loudness + dense high-frequency harmonics
        val sampleRate = 16000
        val commercialSamples = FloatArray(512) { i ->
            val t = i.toDouble() / sampleRate
            val chord = sin(2.0 * Math.PI * 440.0 * t) * 0.5 +
                    sin(2.0 * Math.PI * 554.37 * t) * 0.3 +
                    sin(2.0 * Math.PI * 659.25 * t) * 0.2
            val hf = sin(2.0 * Math.PI * 3500.0 * t) * 0.3
            ((chord + hf) * 0.8).toFloat().coerceIn(-1f, 1f) // High RMS > 0.2
        }

        val results = classifier.classifyByAcousticFeatures(commercialSamples)
        assertTrue("Results should not be empty", results.isNotEmpty())
        val topLabel = results[0].label
        assertTrue("Top label should indicate Commercial / Jingle or Music Bed, got: $topLabel",
            topLabel.contains("Commercial") || topLabel.contains("Music"))
    }

    @Test
    fun `vehicle low frequency steady rumble is not classified as speech or commercial`() {
        val sampleRate = 16000
        val samples = FloatArray(sampleRate / 10) { i ->
            val t = i.toDouble() / sampleRate
            (0.08 * sin(2.0 * Math.PI * 60.0 * t) + 0.05 * sin(2.0 * Math.PI * 95.0 * t)).toFloat()
        }

        val scores = classifier.classifyByAcousticFeatures(samples)
        assertTrue(scores.isNotEmpty())
        val topLabel = scores.first().label
        assertTrue(
            "Expected vehicle/ambient label for steady low-freq rumble, got $topLabel",
            topLabel.contains("Vehicle") || topLabel.contains("Rumble") || topLabel.contains("Ambient")
        )
    }

    @Test
    fun `stationary high frequency broadband ambient noise is not classified as speech or commercial`() {
        val sampleRate = 16000
        val samples = FloatArray(sampleRate / 10) { i ->
            val t = i.toDouble() / sampleRate
            (0.04 * sin(2.0 * Math.PI * 3100.0 * t) +
                0.04 * sin(2.0 * Math.PI * 4300.0 * t) +
                0.04 * sin(2.0 * Math.PI * 5700.0 * t)).toFloat()
        }

        val scores = classifier.classifyByAcousticFeatures(samples)
        assertTrue(scores.isNotEmpty())
        val topLabel = scores.first().label
        assertTrue(
            "Expected stationary ambient/noise label, got $topLabel",
            topLabel.contains("Ambient") || topLabel.contains("Noise") || topLabel.contains("Rain")
        )
    }
}
