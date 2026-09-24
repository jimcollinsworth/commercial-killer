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

@Composable
fun SynthIcon(modifier: Modifier = Modifier.size(18.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.5f)
            cubicTo(w * 0.25f, h * 0.15f, w * 0.4f, h * 0.15f, w * 0.5f, h * 0.5f)
            cubicTo(w * 0.6f, h * 0.85f, w * 0.75f, h * 0.85f, w * 0.9f, h * 0.5f)
        }
        drawPath(path, color = tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun MicIcon(modifier: Modifier = Modifier.size(18.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val capsuleWidth = w * 0.36f
        val capsuleHeight = h * 0.48f
        val left = (w - capsuleWidth) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, h * 0.12f),
            size = androidx.compose.ui.geometry.Size(capsuleWidth, capsuleHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f)
        )
        val arcPath = Path().apply {
            moveTo(w * 0.22f, h * 0.42f)
            cubicTo(w * 0.22f, h * 0.74f, w * 0.78f, h * 0.74f, w * 0.78f, h * 0.42f)
        }
        drawPath(arcPath, color = tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.72f),
            end = Offset(w * 0.5f, h * 0.88f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.35f, h * 0.88f),
            end = Offset(w * 0.65f, h * 0.88f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun AudioFileIcon(modifier: Modifier = Modifier.size(18.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.2f, h * 0.15f)
            lineTo(w * 0.58f, h * 0.15f)
            lineTo(w * 0.8f, h * 0.37f)
            lineTo(w * 0.8f, h * 0.85f)
            lineTo(w * 0.2f, h * 0.85f)
            close()
        }
        drawPath(path, color = tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        val foldPath = Path().apply {
            moveTo(w * 0.58f, h * 0.15f)
            lineTo(w * 0.58f, h * 0.37f)
            lineTo(w * 0.8f, h * 0.37f)
        }
        drawPath(foldPath, color = tint, style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round))
        val playPath = Path().apply {
            moveTo(w * 0.42f, h * 0.5f)
            lineTo(w * 0.62f, h * 0.62f)
            lineTo(w * 0.42f, h * 0.74f)
            close()
        }
        drawPath(playPath, color = tint)
    }
}

@Composable
fun SlidersIcon(modifier: Modifier = Modifier.size(18.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val x1 = w * 0.25f
        val x2 = w * 0.5f
        val x3 = w * 0.75f
        drawLine(color = tint, start = Offset(x1, h * 0.15f), end = Offset(x1, h * 0.85f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(x2, h * 0.15f), end = Offset(x2, h * 0.85f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(x3, h * 0.15f), end = Offset(x3, h * 0.85f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(x1, h * 0.35f))
        drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(x2, h * 0.65f))
        drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(x3, h * 0.45f))
    }
}

