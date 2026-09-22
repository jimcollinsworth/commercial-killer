package com.example.commercialkiller.ui.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    numMelBands: Int = 40,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF0A0E17)
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E3A5F), RoundedCornerShape(8.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (history.isEmpty()) return@Canvas

            val totalFrames = history.size
            val cellWidth = size.width / totalFrames.coerceAtLeast(1)
            val cellHeight = size.height / numMelBands.coerceAtLeast(1)

            for (t in 0 until totalFrames) {
                val frame = history[t]
                val x = t * cellWidth

                for (m in 0 until minOf(frame.size, numMelBands)) {
                    val energy = frame[m].coerceIn(0f, 1f)
                    // Invert y so low frequencies are at bottom, high frequencies at top
                    val y = size.height - (m + 1) * cellHeight

                    val cellColor = getThermalColor(energy)
                    drawRect(
                        color = cellColor,
                        topLeft = Offset(x, y),
                        size = Size(cellWidth + 0.5f, cellHeight + 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Maps normalized energy [0.0, 1.0] to a thermal heatmap color ramp (Dark Navy -> Cyan -> Green -> Yellow -> Red).
 */
private fun getThermalColor(v: Float): Color {
    return when {
        v < 0.2f -> {
            val t = v / 0.2f
            Color(0, (t * 128).toInt(), (t * 255).toInt())
        }
        v < 0.5f -> {
            val t = (v - 0.2f) / 0.3f
            Color(0, 128 + (t * 127).toInt(), (255 * (1f - t)).toInt())
        }
        v < 0.8f -> {
            val t = (v - 0.5f) / 0.3f
            Color((t * 255).toInt(), 255, 0)
        }
        else -> {
            val t = (v - 0.8f) / 0.2f
            Color(255, (255 * (1f - t)).toInt(), 0)
        }
    }
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
                text = "Shift Distance (Δ): %.4f".format(distance),
                style = MaterialTheme.typography.bodySmall,
                color = if (isTriggered) Color(0xFFFF5252) else Color(0xFF00E5FF)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Threshold (τ): %.2f".format(threshold),
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        // Progress bar with threshold indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(7.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(7.dp))
        ) {
            val fillRatio = (distance / 0.8f).coerceIn(0f, 1f)
            val thresholdRatio = (threshold / 0.8f).coerceIn(0f, 1f)

            // Current Distance Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillRatio)
                    .height(14.dp)
                    .background(
                        if (isTriggered) Color(0xFFFF5252) else Color(0xFF00E5FF),
                        RoundedCornerShape(7.dp)
                    )
            )

            // Threshold Marker Line
            Box(
                modifier = Modifier
                    .fillMaxWidth(thresholdRatio)
                    .height(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .height(14.dp)
                        .background(Color.Yellow)
                )
            }
        }
    }
}
