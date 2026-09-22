// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class AudioFileDecoderTest {

    private val decoder = AudioFileDecoder()

    @Test
    fun resample_correctlyAdjustsSampleLength() {
        val originalRate = 44100
        val targetRate = 16000
        val originalLength = 44100 // 1 second of audio
        val input = FloatArray(originalLength) { i ->
            sin(2.0 * Math.PI * 440.0 * i / originalRate).toFloat()
        }

        val resampled = decoder.resample(input, originalRate, targetRate)
        assertEquals(16000, resampled.size)
    }

    @Test
    fun resample_preservesFrequencyCharacteristics() {
        val originalRate = 48000
        val targetRate = 16000
        val input = FloatArray(originalRate) { i ->
            sin(2.0 * Math.PI * 500.0 * i / originalRate).toFloat()
        }

        val resampled = decoder.resample(input, originalRate, targetRate)
        assertEquals(16000, resampled.size)

        // Peak amplitude should be maintained
        val maxAmp = resampled.maxOrNull() ?: 0f
        assertTrue("Max amplitude should be close to 1.0, was $maxAmp", maxAmp > 0.9f)
    }

    @Test
    fun resample_returnsIdenticalArrayWhenRatesMatch() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val resampled = decoder.resample(input, 16000, 16000)
        assertEquals(input.size, resampled.size)
        for (i in input.indices) {
            assertEquals(input[i], resampled[i], 0.0001f)
        }
    }
}
