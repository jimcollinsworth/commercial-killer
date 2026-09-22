package com.example.commercialkiller.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class MelSpectrogramTest {

    private val calculator = MelSpectrogramCalculator(
        sampleRate = 16000,
        fftSize = 512,
        numMelBands = 40
    )

    @Test
    fun computeMelEnergies_returnsExpectedNumberOfBands() {
        val samples = FloatArray(512) { 0f }
        val energies = calculator.computeMelEnergies(samples)

        assertEquals(40, energies.size)
    }

    @Test
    fun computeMelEnergies_detectsPureToneInCorrectFrequencyBand() {
        val sampleRate = 16000
        val freq = 1000.0 // 1 kHz pure sine wave
        val samples = FloatArray(512) { i ->
            sin(2.0 * Math.PI * freq * i / sampleRate).toFloat()
        }

        val energies = calculator.computeMelEnergies(samples)

        // Find band with maximum energy
        val maxBandIndex = energies.indices.maxByOrNull { energies[it] } ?: -1

        // 1 kHz should fall in mid-low Mel bands (roughly band 10-20 out of 40)
        assertTrue("Max energy should be in mid-frequency bands, got band $maxBandIndex", maxBandIndex in 10..22)
        assertTrue("Max band energy should be non-zero", energies[maxBandIndex] > 0.3f)
    }

    @Test
    fun computeMelEnergies_handlesEmptyInputGracefully() {
        val energies = calculator.computeMelEnergies(FloatArray(0))
        assertEquals(40, energies.size)
        for (energy in energies) {
            assertEquals(0f, energy, 0.001f)
        }
    }
}
