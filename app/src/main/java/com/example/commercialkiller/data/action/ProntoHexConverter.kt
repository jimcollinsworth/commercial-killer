// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

import kotlin.math.roundToInt

data class DecodedPronto(
    val carrierFrequency: Int,
    val pattern: IntArray
)

/**
 * Universal Pronto Hex to ConsumerIrManager raw pulse pattern converter.
 *
 * Pronto Hex format:
 * - Word 0: 0000 (Learned code indicator)
 * - Word 1: Frequency code (e.g. 0x006D = 38 kHz)
 * - Word 2: Length of sequence 1 (once)
 * - Word 3: Length of sequence 2 (repeat)
 * - Remaining words: alternating pulse mark & space cycle counts
 */
object ProntoHexConverter {

    fun decode(prontoHex: String): Result<DecodedPronto> {
        return try {
            val tokens = prontoHex.trim().split("\\s+".toRegex())
            if (tokens.size < 4) {
                return Result.failure(IllegalArgumentException("Pronto Hex must have at least 4 header words"))
            }

            val hexValues = tokens.map { it.toInt(16) }
            val frequencyCode = hexValues[1]
            if (frequencyCode <= 0) {
                return Result.failure(IllegalArgumentException("Invalid frequency code in Pronto Hex"))
            }

            // Carrier frequency in Hz = 1,000,000 / (frequencyCode * 0.241246)
            val carrierFrequency = (1000000.0 / (frequencyCode * 0.241246)).roundToInt()
            val timeBaseMicroseconds = frequencyCode * 0.241246

            val seq1Len = hexValues[2] * 2
            val seq2Len = hexValues[3] * 2
            val totalPairs = seq1Len + seq2Len

            val rawData = hexValues.drop(4)
            val patternSize = totalPairs.coerceAtMost(rawData.size)

            val pattern = IntArray(patternSize) { i ->
                (rawData[i] * timeBaseMicroseconds).roundToInt()
            }

            if (pattern.isEmpty()) {
                return Result.failure(IllegalArgumentException("No pulse data found in Pronto Hex"))
            }

            Result.success(DecodedPronto(carrierFrequency, pattern))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
