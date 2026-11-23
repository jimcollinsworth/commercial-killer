// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Real-time oscilloscope visualizer for PCM audio waveforms.
 */
@Composable
fun WaveformOscilloscope(
    waveform: FloatArray,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF00E5FF),
    backgroundColor: Color = Color(0xFF0D1B2A)
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (waveform.isEmpty()) return@Canvas

            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val stepX = width / (waveform.size - 1).coerceAtLeast(1)

            // Draw center baseline
            drawLine(
                color = Color(0x3300E5FF),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 1f
            )

            // Draw waveform path
            val path = Path()
            path.moveTo(0f, centerY + waveform[0] * centerY)
            for (i in 1 until waveform.size) {
                val x = i * stepX
                val y = centerY + waveform[i] * centerY * 0.9f
                path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2f)
            )
        }
    }
}

/**
 * Real-time Mel-spectrogram waterfall / heatmap visualizer.
 * Columns represent time frames, rows represent Mel frequency bands (low to high).
 */
@Composable
fun SpectrogramWaterfall(
    history: List<FloatArray>,
    numMelBands: Int = 80,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF0A0E17)
) {
    val effectiveBands = numMelBands.coerceAtLeast(1)
    val buffer = remember(effectiveBands) { WaterfallPingPongBuffer() }

    val activeImage = remember(history, effectiveBands) {
        if (history.isEmpty()) {
            null
        } else {
            val totalFrames = history.size.coerceAtLeast(1)
            val needed = totalFrames * effectiveBands
            val slot = buffer.acquire(totalFrames, effectiveBands, needed)
            val pixels = slot.pixels
            for (t in 0 until totalFrames) {
                val frame = history[t]
                val limit = minOf(frame.size, effectiveBands)
                for (m in 0 until effectiveBands) {
                    val energy = if (m < limit) frame[m].coerceIn(0f, 1f) else 0f
                    val row = effectiveBands - 1 - m
                    pixels[row * totalFrames + t] = getThermalArgb(energy)
                }
            }
            slot.bitmap.setPixels(pixels, 0, totalFrames, 0, 0, totalFrames, effectiveBands)
            slot.imageBitmap
        }
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val img = activeImage ?: return@Canvas
            val dstW = size.width.toInt().coerceAtLeast(1)
            val dstH = size.height.toInt().coerceAtLeast(1)
            drawImage(
                image = img,
                dstSize = IntSize(dstW, dstH),
                filterQuality = FilterQuality.None
            )
        }
    }
}

/**
 * Compact equal-width audio Mel strip rendered per timeline segment (16x16 bitmap blit).
 */
@Composable
fun MiniMelStrip(
    melStrip: List<FloatArray>,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF0A0E17)
) {
    val cols = melStrip.size
    val bands = melStrip.firstOrNull()?.size ?: 16
    val image = remember(melStrip, cols, bands) {
        if (cols <= 0 || bands <= 0) {
            null
        } else {
            val pixels = IntArray(cols * bands)
            for (t in 0 until cols) {
                val col = melStrip[t]
                for (m in 0 until bands) {
                    val energy = if (m < col.size) col[m].coerceIn(0f, 1f) else 0f
                    val row = bands - 1 - m
                    pixels[row * cols + t] = getThermalArgb(energy)
                }
            }
            val bmp = Bitmap.createBitmap(cols, bands, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, cols, 0, 0, cols, bands)
            bmp.asImageBitmap()
        }
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(4.dp))
            .padding(1.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val img = image ?: return@Canvas
            val dstW = size.width.toInt().coerceAtLeast(1)
            val dstH = size.height.toInt().coerceAtLeast(1)
            drawImage(
                image = img,
                dstSize = IntSize(dstW, dstH),
                filterQuality = FilterQuality.None
            )
        }
    }
}

private class WaterfallSlot(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val bitmap: Bitmap,
    val imageBitmap: androidx.compose.ui.graphics.ImageBitmap
)

private class WaterfallPingPongBuffer {
    private val slots = arrayOfNulls<WaterfallSlot>(2)
    private var cursor = 0

    fun acquire(width: Int, height: Int, neededPixels: Int): WaterfallSlot {
        val idx = cursor
        cursor = 1 - cursor
        val existing = slots[idx]
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val created = WaterfallSlot(
            width = width,
            height = height,
            pixels = IntArray(neededPixels),
            bitmap = bmp,
            imageBitmap = bmp.asImageBitmap()
        )
        slots[idx] = created
        return created
    }
}

/**
 * Maps normalized energy [0.0, 1.0] to an ARGB int along the thermal ramp.
 */
private fun getThermalArgb(v: Float): Int {
    val r: Int
    val g: Int
    val b: Int
    when {
        v < 0.2f -> {
            val t = v / 0.2f
            r = 0
            g = (t * 128f).toInt().coerceIn(0, 255)
            b = (t * 255f).toInt().coerceIn(0, 255)
        }
        v < 0.5f -> {
            val t = (v - 0.2f) / 0.3f
            r = 0
            g = (128 + (t * 127f).toInt()).coerceIn(0, 255)
            b = (255f * (1f - t)).toInt().coerceIn(0, 255)
        }
        v < 0.8f -> {
            val t = (v - 0.5f) / 0.3f
            r = (t * 255f).toInt().coerceIn(0, 255)
            g = 255
            b = 0
        }
        else -> {
            val t = (v - 0.8f) / 0.2f
            r = 255
            g = (255f * (1f - t)).toInt().coerceIn(0, 255)
            b = 0
        }
    }
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Real-time visual gauge displaying shift distance Δ relative to threshold τ.
 */
@Composable
fun DistanceMeter(
    distance: Float,
    threshold: Float,
    isTriggered: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Shift (Δ): %.4f".format(distance),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                color = if (isTriggered) Color(0xFFFF5252) else Color(0xFF00E5FF)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "τ: %.2f".format(threshold),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                softWrap = false,
                color = Color.LightGray
            )
        }
        Spacer(modifier = Modifier.height(2.dp))

        // Progress bar with threshold indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
        ) {
            val fillRatio = (distance / 0.8f).coerceIn(0f, 1f)
            val thresholdRatio = (threshold / 0.8f).coerceIn(0f, 1f)

            // Current Distance Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillRatio)
                    .height(8.dp)
                    .background(
                        if (isTriggered) Color(0xFFFF5252) else Color(0xFF00E5FF),
                        RoundedCornerShape(4.dp)
                    )
            )

            // Threshold Marker Line
            Box(
                modifier = Modifier
                    .fillMaxWidth(thresholdRatio)
                    .height(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .height(8.dp)
                        .background(Color.Yellow)
                )
            }
        }
    }
}
