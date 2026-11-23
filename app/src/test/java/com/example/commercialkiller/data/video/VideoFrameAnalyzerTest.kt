// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.video

import org.junit.Assert.assertEquals
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
}
