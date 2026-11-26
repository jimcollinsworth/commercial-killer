// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameAnalyzerTest {

    private fun filledFrame(argb: Int, width: Int = 64, height: Int = 36): IntArray {
        return IntArray(width * height) { argb }
    }

    @Test
    fun `identical frames yield zero visualChangeScore`() {
        val frame = filledFrame(0xFF336699.toInt())
        val first = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(frame, 64, 36, 100L, null)
        val second = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(frame, 64, 36, 350L, first)

        assertEquals(0f, second.visualChangeScore, 0.001f)
    }

    @Test
    fun `black and white frames yield extreme luma and high visualChangeScore`() {
        val blackPixels = filledFrame(0xFF000000.toInt())
        val whitePixels = filledFrame(0xFFFFFFFF.toInt())

        val blackDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(blackPixels, 64, 36, 100L, null)
        val whiteDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(whitePixels, 64, 36, 350L, blackDesc)

        assertEquals(0f, blackDesc.meanLuma, 0.001f)
        assertEquals(1f, whiteDesc.meanLuma, 0.001f)
        assertTrue(
            "Expected high visualChangeScore for black-to-white transition but got ${whiteDesc.visualChangeScore}",
            whiteDesc.visualChangeScore >= 0.90f
        )
    }

    @Test
    fun `solid blue to solid red yields color histogram visual shift`() {
        val bluePixels = filledFrame(0xFF0000FF.toInt())
        val redPixels = filledFrame(0xFFFF0000.toInt())

        val blueDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(bluePixels, 64, 36, 100L, null)
        val redDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(redPixels, 64, 36, 350L, blueDesc)

        assertTrue(
            "Expected visualChangeScore >= 0.45 for blue-to-red shift but got ${redDesc.visualChangeScore}",
            redDesc.visualChangeScore >= 0.45f
        )
    }

    @Test
    fun `lower-right corner bright region updates cornerRoiLuma without altering top-left lumaGrid`() {
        val width = 64
        val height = 36
        val baseDark = filledFrame(0xFF000000.toInt(), width, height)
        val cornerBright = baseDark.copyOf()

        for (y in (height * 3) / 4 until height) {
            for (x in (width * 3) / 4 until width) {
                cornerBright[y * width + x] = 0xFFFFFFFF.toInt()
            }
        }

        val darkDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(baseDark, width, height, 100L, null)
        val bugDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(cornerBright, width, height, 350L, darkDesc)

        // Top-left grid cell (0) remains completely black while corner ROI becomes bright (~1.0)
        assertEquals(0f, bugDesc.lumaGrid[0], 0.001f)
        assertTrue("Expected bright cornerRoiLuma but got ${bugDesc.cornerRoiLuma[0]}", bugDesc.cornerRoiLuma[0] >= 0.99f)
        assertTrue(
            "Expected bottom-right lumaGrid cell 15 to rise (~1.0) but got ${bugDesc.lumaGrid[15]}",
            bugDesc.lumaGrid[15] >= 0.99f
        )
    }

    @Test
    fun `bottomLeftRoiLuma isBlackFrame and similarity helpers behave deterministically`() {
        val width = 64
        val height = 36
        val black = filledFrame(0xFF000000.toInt(), width, height)
        val blackDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(black, width, height, 100L, null)
        assertTrue(blackDesc.isBlackFrame)

        val leftCornerBright = black.copyOf()
        for (y in (height * 3) / 4 until height) {
            for (x in 0 until width / 4) {
                leftCornerBright[y * width + x] = 0xFFFFFFFF.toInt()
            }
        }
        val leftDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(leftCornerBright, width, height, 350L, blackDesc)
        assertTrue(leftDesc.bottomLeftRoiLuma[0] >= 0.99f)
        assertEquals(0f, leftDesc.cornerRoiLuma[0], 0.001f)
        assertEquals(1.0f, VideoFrameAnalyzer.descriptorSimilarity(leftDesc, leftDesc), 0.001f)
        assertEquals(1.0f, VideoFrameAnalyzer.cornerSimilarity(leftDesc.bottomLeftRoiLuma, leftDesc.bottomLeftRoiLuma), 0.001f)
    }

    @Test
    fun `dark studio frame and bright beige commercial frame yield low similarity`() {
        val studioPixels = filledFrame(0xFF1A2233.toInt())
        val commercialPixels = filledFrame(0xFFD6C2A3.toInt())

        val studioDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(studioPixels, 64, 36, 100L, null)
        val commercialDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(commercialPixels, 64, 36, 350L, studioDesc)

        val sim = VideoFrameAnalyzer.descriptorSimilarity(studioDesc, commercialDesc)
        assertTrue(
            "Expected dark studio vs bright beige commercial similarity < 0.55f, got $sim",
            sim < 0.55f
        )
    }

    @Test
    fun `cornerEdgeMasks populates top-left cells only and solid frame stays zero`() {
        val width = 64
        val height = 36
        val solid = filledFrame(0xFF223344.toInt(), width, height)
        val solidDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(solid, width, height, 100L, null)
        for (i in 0 until 64) {
            assertEquals("Expected zero edge at index $i on solid frame", 0f, solidDesc.cornerEdgeMasks[i], 0.0001f)
        }

        val withTopLeftBox = solid.copyOf()
        for (y in 2 until 7) {
            for (x in 2 until 12) {
                withTopLeftBox[y * width + x] = 0xFFFFFFFF.toInt()
            }
        }
        val boxDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(withTopLeftBox, width, height, 250L, solidDesc)
        val tlSum = (0 until 16).sumOf { boxDesc.cornerEdgeMasks[it].toDouble() }.toFloat()
        assertTrue("Expected positive TL edge mask sum, got $tlSum", tlSum > 0.5f)
        for (i in 16 until 64) {
            assertEquals("Expected zero edge in TR/BL/BR at index $i", 0f, boxDesc.cornerEdgeMasks[i], 0.0001f)
        }

        val shiftedBox = boxDesc.copy(letterboxRatio = 0.25f)
        val shiftedPillar = boxDesc.copy(pillarboxRatio = 0.25f)
        val modifiedMask = boxDesc.cornerEdgeMasks.copyOf().also { it[0] = (it[0] + 0.1f).coerceAtMost(1f) }
        val shiftedEdge = boxDesc.copy(cornerEdgeMasks = modifiedMask)
        assertNotEquals(boxDesc, shiftedBox)
        assertNotEquals(boxDesc, shiftedPillar)
        assertNotEquals(boxDesc, shiftedEdge)
        assertNotEquals(boxDesc.hashCode(), shiftedEdge.hashCode())
    }

    @Test
    fun `letterboxRatio and pillarboxRatio detect symmetric bars and reject black or single-side frames`() {
        val width = 64
        val height = 36

        // Symmetric 6-row top+bottom black bars -> 12 / 36 = 0.3333f
        val letterbox = filledFrame(0xFF708090.toInt(), width, height)
        for (y in 0 until 6) {
            for (x in 0 until width) {
                letterbox[y * width + x] = 0xFF000000.toInt()
                letterbox[(height - 1 - y) * width + x] = 0xFF000000.toInt()
            }
        }
        val lbDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(letterbox, width, height, 100L, null)
        assertEquals(12f / 36f, lbDesc.letterboxRatio, 0.001f)
        assertEquals(0f, lbDesc.pillarboxRatio, 0.001f)

        // Symmetric 8-col left+right black bars -> 16 / 64 = 0.25f
        val pillarbox = filledFrame(0xFF708090.toInt(), width, height)
        for (y in 0 until height) {
            for (x in 0 until 8) {
                pillarbox[y * width + x] = 0xFF000000.toInt()
                pillarbox[y * width + (width - 1 - x)] = 0xFF000000.toInt()
            }
        }
        val pbDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(pillarbox, width, height, 200L, null)
        assertEquals(0f, pbDesc.letterboxRatio, 0.001f)
        assertEquals(16f / 64f, pbDesc.pillarboxRatio, 0.001f)

        // Asymmetric single-side bar -> 0f
        val topOnly = filledFrame(0xFF708090.toInt(), width, height)
        for (y in 0 until 6) {
            for (x in 0 until width) {
                topOnly[y * width + x] = 0xFF000000.toInt()
            }
        }
        val topOnlyDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(topOnly, width, height, 300L, null)
        assertEquals(0f, topOnlyDesc.letterboxRatio, 0.001f)
        assertEquals(0f, topOnlyDesc.pillarboxRatio, 0.001f)

        // Full black frame -> 0f for both
        val allBlack = filledFrame(0xFF000000.toInt(), width, height)
        val blackDesc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(allBlack, width, height, 400L, null)
        assertTrue(blackDesc.isBlackFrame)
        assertEquals(0f, blackDesc.letterboxRatio, 0.001f)
        assertEquals(0f, blackDesc.pillarboxRatio, 0.001f)
    }
}
