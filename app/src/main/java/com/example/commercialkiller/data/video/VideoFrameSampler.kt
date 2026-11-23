// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.video

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Compact 56-float visual embedding computed on a 64x36 frame:
 * - 16 floats: 4x4 spatial luminance grid
 * - 24 floats: 8R + 8G + 8B normalized color histograms
 * - 16 floats: 4x4 bottom-right corner ROI luminance grid (station watermark region)
 */
data class VisualFrameDescriptor(
    val timestampMs: Long = 0L,
    val meanLuma: Float = 0f,
    val lumaGrid: FloatArray = FloatArray(16),
    val colorHistogram: FloatArray = FloatArray(24),
    val cornerRoiLuma: FloatArray = FloatArray(16),
    val visualChangeScore: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VisualFrameDescriptor) return false
        return timestampMs == other.timestampMs &&
            meanLuma == other.meanLuma &&
            lumaGrid.contentEquals(other.lumaGrid) &&
            colorHistogram.contentEquals(other.colorHistogram) &&
            cornerRoiLuma.contentEquals(other.cornerRoiLuma) &&
            visualChangeScore == other.visualChangeScore
    }

    override fun hashCode(): Int {
        var result = timestampMs.hashCode()
        result = 31 * result + meanLuma.hashCode()
        result = 31 * result + lumaGrid.contentHashCode()
        result = 31 * result + colorHistogram.contentHashCode()
        result = 31 * result + cornerRoiLuma.contentHashCode()
        result = 31 * result + visualChangeScore.hashCode()
        return result
    }
}

/**
 * Pair of visual feature descriptor and low-resolution RGB_565 thumbnail (4,608 bytes at 64x36).
 */
data class VideoFrameSnapshot(
    val descriptor: VisualFrameDescriptor,
    val thumbnail: Bitmap? = null
)

/**
 * Extracts compact 64x36 visual descriptors and RGB_565 thumbnails from CameraX YUV_420_888 frames
 * or TextureView Bitmaps without cloud dependencies or autoregressive inference.
 */
object VideoFrameAnalyzer {
    const val THUMB_WIDTH = 64
    const val THUMB_HEIGHT = 36

    fun computeDescriptorFromArgbPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        timestampMs: Long,
        previous: VisualFrameDescriptor? = null
    ): VisualFrameDescriptor {
        if (width <= 0 || height <= 0 || pixels.size < width * height) {
            return VisualFrameDescriptor(timestampMs = timestampMs)
        }

        val lumaGrid = FloatArray(16)
        val lumaCounts = IntArray(16)
        val colorHistogram = FloatArray(24)
        val cornerRoiLuma = FloatArray(16)
        val roiCounts = IntArray(16)

        val roiStartX = (width * 3) / 4
        val roiStartY = (height * 3) / 4
        val roiWidth = (width - roiStartX).coerceAtLeast(1)
        val roiHeight = (height - roiStartY).coerceAtLeast(1)

        var totalLuma = 0.0

        for (y in 0 until height) {
            val gridY = ((y * 4) / height).coerceIn(0, 3)
            val rowOffset = y * width
            val inRoiY = y >= roiStartY
            val roiY = if (inRoiY) (((y - roiStartY) * 4) / roiHeight).coerceIn(0, 3) else 0

            for (x in 0 until width) {
                val argb = pixels[rowOffset + x]
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF

                val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                totalLuma += luma

                val gridX = ((x * 4) / width).coerceIn(0, 3)
                val gridIdx = gridY * 4 + gridX
                lumaGrid[gridIdx] += luma
                lumaCounts[gridIdx]++

                colorHistogram[(r ushr 5).coerceIn(0, 7)] += 1f
                colorHistogram[8 + (g ushr 5).coerceIn(0, 7)] += 1f
                colorHistogram[16 + (b ushr 5).coerceIn(0, 7)] += 1f

                if (inRoiY && x >= roiStartX) {
                    val roiX = (((x - roiStartX) * 4) / roiWidth).coerceIn(0, 3)
                    val roiIdx = roiY * 4 + roiX
                    cornerRoiLuma[roiIdx] += luma
                    roiCounts[roiIdx]++
                }
            }
        }

        val totalPixels = (width * height).toFloat()
        val meanLuma = (totalLuma / totalPixels).toFloat().coerceIn(0f, 1f)

        for (i in 0 until 16) {
            if (lumaCounts[i] > 0) lumaGrid[i] /= lumaCounts[i].toFloat()
            if (roiCounts[i] > 0) cornerRoiLuma[i] /= roiCounts[i].toFloat()
        }
        for (i in 0 until 24) {
            colorHistogram[i] /= totalPixels
        }

        val changeScore = if (previous != null) {
            val lumaDist = cosineDistance(lumaGrid, previous.lumaGrid)
            val histDist = histogramL1Distance(colorHistogram, previous.colorHistogram)
            val blended = 0.5f * lumaDist + 0.5f * histDist
            maxOf(blended, histDist * 0.75f).coerceIn(0f, 1f)
        } else {
            0f
        }

        return VisualFrameDescriptor(
            timestampMs = timestampMs,
            meanLuma = meanLuma,
            lumaGrid = lumaGrid,
            colorHistogram = colorHistogram,
            cornerRoiLuma = cornerRoiLuma,
            visualChangeScore = changeScore
        )
    }

    fun sampleYuvImageProxy(
        image: ImageProxy,
        timestampMs: Long,
        previous: VisualFrameDescriptor? = null
    ): VideoFrameSnapshot {
        val srcW = image.width
        val srcH = image.height
        val planes = image.planes
        if (srcW <= 0 || srcH <= 0 || planes.size < 3) {
            return VideoFrameSnapshot(VisualFrameDescriptor(timestampMs = timestampMs))
        }

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yLimit = (yBuf.limit() - 1).coerceAtLeast(0)
        val uLimit = (uBuf.limit() - 1).coerceAtLeast(0)
        val vLimit = (vBuf.limit() - 1).coerceAtLeast(0)

        val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360
        val pixels = IntArray(THUMB_WIDTH * THUMB_HEIGHT)

        for (dy in 0 until THUMB_HEIGHT) {
            val vNorm = (dy + 0.5f) / THUMB_HEIGHT
            for (dx in 0 until THUMB_WIDTH) {
                val uNorm = (dx + 0.5f) / THUMB_WIDTH
                val su: Float
                val sv: Float
                when (rotation) {
                    90 -> {
                        su = vNorm
                        sv = 1f - uNorm
                    }
                    180 -> {
                        su = 1f - uNorm
                        sv = 1f - vNorm
                    }
                    270 -> {
                        su = 1f - vNorm
                        sv = uNorm
                    }
                    else -> {
                        su = uNorm
                        sv = vNorm
                    }
                }

                val sx = (su * srcW).toInt().coerceIn(0, srcW - 1)
                val sy = (sv * srcH).toInt().coerceIn(0, srcH - 1)
                val chromaX = sx ushr 1
                val chromaY = sy ushr 1

                val yIdx = (sy * yPlane.rowStride + sx * yPlane.pixelStride).coerceIn(0, yLimit)
                val uIdx = (chromaY * uPlane.rowStride + chromaX * uPlane.pixelStride).coerceIn(0, uLimit)
                val vIdx = (chromaY * vPlane.rowStride + chromaX * vPlane.pixelStride).coerceIn(0, vLimit)

                val yVal = yBuf.get(yIdx).toInt() and 0xFF
                val uVal = (uBuf.get(uIdx).toInt() and 0xFF) - 128
                val vVal = (vBuf.get(vIdx).toInt() and 0xFF) - 128

                val r = (yVal + (1.370705f * vVal).toInt()).coerceIn(0, 255)
                val g = (yVal - (0.337633f * uVal).toInt() - (0.698001f * vVal).toInt()).coerceIn(0, 255)
                val b = (yVal + (1.732446f * uVal).toInt()).coerceIn(0, 255)

                pixels[dy * THUMB_WIDTH + dx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val descriptor = computeDescriptorFromArgbPixels(
            pixels = pixels,
            width = THUMB_WIDTH,
            height = THUMB_HEIGHT,
            timestampMs = timestampMs,
            previous = previous
        )
        val thumb = Bitmap.createBitmap(THUMB_WIDTH, THUMB_HEIGHT, Bitmap.Config.RGB_565)
        thumb.setPixels(pixels, 0, THUMB_WIDTH, 0, 0, THUMB_WIDTH, THUMB_HEIGHT)
        return VideoFrameSnapshot(descriptor = descriptor, thumbnail = thumb)
    }

    fun sampleBitmap(
        source: Bitmap,
        timestampMs: Long,
        previous: VisualFrameDescriptor? = null
    ): VideoFrameSnapshot {
        val scaled = if (source.width != THUMB_WIDTH || source.height != THUMB_HEIGHT) {
            Bitmap.createScaledBitmap(source, THUMB_WIDTH, THUMB_HEIGHT, false)
        } else {
            source
        }

        val pixels = IntArray(THUMB_WIDTH * THUMB_HEIGHT)
        return try {
            scaled.getPixels(pixels, 0, THUMB_WIDTH, 0, 0, THUMB_WIDTH, THUMB_HEIGHT)
            val descriptor = computeDescriptorFromArgbPixels(
                pixels = pixels,
                width = THUMB_WIDTH,
                height = THUMB_HEIGHT,
                timestampMs = timestampMs,
                previous = previous
            )
            val thumb = Bitmap.createBitmap(THUMB_WIDTH, THUMB_HEIGHT, Bitmap.Config.RGB_565)
            thumb.setPixels(pixels, 0, THUMB_WIDTH, 0, 0, THUMB_WIDTH, THUMB_HEIGHT)
            VideoFrameSnapshot(descriptor = descriptor, thumbnail = thumb)
        } finally {
            if (scaled !== source && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val va = a[i].toDouble()
            val vb = b[i].toDouble()
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        if (normA <= 1e-10 && normB <= 1e-10) return 0f
        if (normA <= 1e-10 || normB <= 1e-10) return 1f
        val sim = (dot / (sqrt(normA) * sqrt(normB))).toFloat().coerceIn(0f, 1f)
        return (1f - sim).coerceIn(0f, 1f)
    }

    private fun histogramL1Distance(a: FloatArray, b: FloatArray): Float {
        if (a.size < 24 || b.size < 24) return 0f
        var channelSum = 0f
        for (ch in 0 until 3) {
            val base = ch * 8
            var l1 = 0f
            for (bin in 0 until 8) {
                l1 += abs(a[base + bin] - b[base + bin])
            }
            channelSum += (0.5f * l1).coerceIn(0f, 1f)
        }
        return (channelSum / 3f).coerceIn(0f, 1f)
    }
}
