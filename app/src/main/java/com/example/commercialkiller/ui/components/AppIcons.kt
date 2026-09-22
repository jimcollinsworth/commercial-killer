// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Lightweight, zero-dependency vector icons optimized for 44x44dp touch targets.
 */
@Composable
fun BackIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.7f, h * 0.2f)
            lineTo(w * 0.3f, h * 0.5f)
            lineTo(w * 0.7f, h * 0.8f)
        }
        drawPath(path, color = tint, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = tint,
            start = Offset(w * 0.3f, h * 0.5f),
            end = Offset(w * 0.85f, h * 0.5f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun HelpIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.45f
        drawCircle(color = tint, radius = r, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = 2.dp.toPx()))
        // Top dot
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(w * 0.5f, h * 0.32f))
        // Bottom bar
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.45f),
            end = Offset(w * 0.5f, h * 0.72f),
            strokeWidth = 2.5f.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun SettingsIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // TV / Remote icon
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.25f)
            lineTo(w * 0.8f, h * 0.25f)
            lineTo(w * 0.8f, h * 0.75f)
            lineTo(w * 0.2f, h * 0.75f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 2.dp.toPx()))
        // Screen stand
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.75f),
            end = Offset(w * 0.5f, h * 0.9f),
            strokeWidth = 2.dp.toPx()
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.9f),
            end = Offset(w * 0.65f, h * 0.9f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PlayIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun StopIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.56f)
        )
    }
}

@Composable
fun StepNextIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.2f)
            lineTo(w * 0.65f, h * 0.5f)
            lineTo(w * 0.25f, h * 0.8f)
        }
        drawPath(path, color = tint, style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = tint,
            start = Offset(w * 0.75f, h * 0.2f),
            end = Offset(w * 0.75f, h * 0.8f),
            strokeWidth = 2.5f.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
