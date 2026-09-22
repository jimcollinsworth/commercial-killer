package com.example.commercialkiller.data.audio

import kotlin.math.sqrt

enum class DistanceMetric {
    MEAN_SQUARED_ERROR,
    COSINE_DISTANCE,
    EUCLIDEAN_DISTANCE
}

data class ComparisonResult(
    val distance: Float,
    val isSignificant: Boolean,
    val threshold: Float
)

/**
 * Compares consecutive Mel-spectrogram feature vectors to identify significant transitions (commercial breaks).
 */
class SpectrogramComparator(
    var threshold: Float = 0.05f,
    var metric: DistanceMetric = DistanceMetric.EUCLIDEAN_DISTANCE
) {
    private var previousVector: FloatArray? = null

    /**
     * Compares the current Mel-vector with the previous vector.
     * Returns a ComparisonResult indicating distance and significance.
     */
    fun compare(currentVector: FloatArray): ComparisonResult {
        val prev = previousVector
        if (prev == null || prev.size != currentVector.size) {
            previousVector = currentVector.copyOf()
            return ComparisonResult(distance = 0f, isSignificant = false, threshold = threshold)
        }

        val dist = when (metric) {
            DistanceMetric.MEAN_SQUARED_ERROR -> computeMse(prev, currentVector)
            DistanceMetric.COSINE_DISTANCE -> computeCosineDistance(prev, currentVector)
            DistanceMetric.EUCLIDEAN_DISTANCE -> computeEuclidean(prev, currentVector)
        }

        previousVector = currentVector.copyOf()
        val significant = dist >= threshold

        return ComparisonResult(
            distance = dist,
            isSignificant = significant,
            threshold = threshold
        )
    }

    fun reset() {
        previousVector = null
    }

    private fun computeMse(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sum / a.size
    }

    private fun computeEuclidean(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return sqrt(sum)
    }

    private fun computeCosineDistance(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        val similarity = (dot / (sqrt(normA) * sqrt(normB))).coerceIn(-1.0f, 1.0f)
        return 1.0f - similarity
    }
}
