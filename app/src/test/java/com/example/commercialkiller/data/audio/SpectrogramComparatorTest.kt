package com.example.commercialkiller.data.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrogramComparatorTest {

    @Test
    fun compare_firstCallReturnsZeroDistance() {
        val comparator = SpectrogramComparator(threshold = 0.2f)
        val vec1 = FloatArray(40) { 0.5f }

        val result = comparator.compare(vec1)

        assertEquals(0f, result.distance, 0.001f)
        assertFalse(result.isSignificant)
    }

    @Test
    fun compare_identicalVectorsYieldZeroDistance() {
        val comparator = SpectrogramComparator(threshold = 0.2f)
        val vec1 = FloatArray(40) { 0.5f }

        comparator.compare(vec1)
        val result = comparator.compare(vec1)

        assertEquals(0f, result.distance, 0.001f)
        assertFalse(result.isSignificant)
    }

    @Test
    fun compare_differingVectorsTriggerSignificantEvent() {
        val comparator = SpectrogramComparator(threshold = 0.15f)
        val vec1 = FloatArray(40) { 0.1f }
        val vec2 = FloatArray(40) { 0.9f } // Large shift

        comparator.compare(vec1)
        val result = comparator.compare(vec2)

        assertTrue("Distance should exceed threshold", result.distance > 0.15f)
        assertTrue("Event should be flagged as significant", result.isSignificant)
    }

    @Test
    fun compare_cosineDistanceMetricWorks() {
        val comparator = SpectrogramComparator(
            threshold = 0.1f,
            metric = DistanceMetric.COSINE_DISTANCE
        )
        val vec1 = floatArrayOf(1f, 0f, 0f, 0f)
        val vec2 = floatArrayOf(0f, 1f, 0f, 0f) // Orthogonal vectors -> cosine distance = 1.0

        comparator.compare(vec1)
        val result = comparator.compare(vec2)

        assertEquals(1.0f, result.distance, 0.01f)
        assertTrue(result.isSignificant)
    }
}
