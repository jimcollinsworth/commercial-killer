package com.example.commercialkiller.data.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device real-time Mel-Spectrogram calculator.
 * Computes Fast Fourier Transform (FFT) and triangular Mel filterbank energies.
 */
class MelSpectrogramCalculator(
    val sampleRate: Int = 16000,
    val fftSize: Int = 512,
    val numMelBands: Int = 40,
    val minFreq: Float = 20f,
    val maxFreq: Float = 8000f
) {
    private val melFilterBank: Array<FloatArray> = createMelFilterBank()

    /**
     * Converts a 16-bit PCM short array (or float array) into a normalized Mel energy vector.
     */
    fun computeMelEnergies(audioSamples: FloatArray): FloatArray {
        if (audioSamples.isEmpty()) return FloatArray(numMelBands) { 0f }

        // 1. Apply Hann window
        val windowed = FloatArray(fftSize)
        val limit = minOf(audioSamples.size, fftSize)
        for (i in 0 until limit) {
            val multiplier = 0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))
            windowed[i] = (audioSamples[i] * multiplier).toFloat()
        }

        // 2. Compute Power Spectrum via FFT
        val powerSpectrum = computePowerSpectrum(windowed)

        // 3. Apply Mel Filterbank
        val melEnergies = FloatArray(numMelBands)
        for (m in 0 until numMelBands) {
            var sum = 0f
            val filter = melFilterBank[m]
            for (k in filter.indices) {
                sum += powerSpectrum[k] * filter[k]
            }
            // Log-energy (dB scale with floor)
            melEnergies[m] = max(0f, (10.0 * log10(max(sum.toDouble(), 1e-6)) + 60.0).toFloat() / 60.0f)
        }

        return melEnergies
    }

    private fun computePowerSpectrum(input: FloatArray): FloatArray {
        val n = fftSize
        val real = input.copyOf(n)
        val imag = FloatArray(n)

        // Radix-2 Cooley-Tukey FFT
        fft(real, imag)

        val numBins = n / 2 + 1
        val power = FloatArray(numBins)
        for (i in 0 until numBins) {
            power[i] = (real[i] * real[i] + imag[i] * imag[i]) / n
        }
        return power
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (m in 0 until halfLen) {
                    val uR = real[i + m]
                    val uI = imag[i + m]
                    val vR = real[i + m + halfLen] * wR - imag[i + m + halfLen] * wI
                    val vI = real[i + m + halfLen] * wI + imag[i + m + halfLen] * wR

                    real[i + m] = uR + vR
                    imag[i + m] = uI + vI
                    real[i + m + halfLen] = uR - vR
                    imag[i + m + halfLen] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun hzToMel(hz: Float): Float = (2595.0 * log10(1.0 + hz / 700.0)).toFloat()
    private fun melToHz(mel: Float): Float = (700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)).toFloat()

    private fun createMelFilterBank(): Array<FloatArray> {
        val numBins = fftSize / 2 + 1
        val minMel = hzToMel(minFreq)
        val maxMel = hzToMel(maxFreq)

        val melPoints = FloatArray(numMelBands + 2)
        val hzPoints = FloatArray(numMelBands + 2)
        val binPoints = IntArray(numMelBands + 2)

        for (i in 0 until numMelBands + 2) {
            melPoints[i] = minMel + i * (maxMel - minMel) / (numMelBands + 1)
            hzPoints[i] = melToHz(melPoints[i])
            binPoints[i] = ((fftSize + 1) * hzPoints[i] / sampleRate).toInt().coerceIn(0, numBins - 1)
        }

        val filterBank = Array(numMelBands) { FloatArray(numBins) }
        for (m in 1..numMelBands) {
            val left = binPoints[m - 1]
            val center = binPoints[m]
            val right = binPoints[m + 1]

            for (k in left until center) {
                if (center != left) {
                    filterBank[m - 1][k] = (k - left).toFloat() / (center - left)
                }
            }
            for (k in center until right) {
                if (right != center) {
                    filterBank[m - 1][k] = (right - k).toFloat() / (right - center)
                }
            }
        }
        return filterBank
    }
}
