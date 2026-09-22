package com.example.commercialkiller.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.example.commercialkiller.data.audio.AudioWorkbenchEngine
import com.example.commercialkiller.ui.components.DistanceMeter
import com.example.commercialkiller.ui.components.SpectrogramWaterfall
import com.example.commercialkiller.ui.components.WaveformOscilloscope

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    engine: AudioWorkbenchEngine = remember { AudioWorkbenchEngine() }
) {
    val state by engine.state.collectAsStateWithLifecycle()

    // Auto-start simulated workbench on launch for immediate real-time visualization
    DisposableEffect(Unit) {
        engine.start(useLiveMic = false)
        onDispose {
            engine.stop()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Bar
            WorkbenchHeader(
                isRunning = state.isRunning,
                isLiveMic = state.isLiveMic,
                onToggleRunning = {
                    if (state.isRunning) engine.stop() else engine.start(state.isLiveMic)
                },
                onToggleSource = { useMic ->
                    engine.stop()
                    engine.start(useMic)
                }
            )

            // Alert Banner (when shift triggered)
            if (state.isEventTriggered) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFDC2626), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚡ COMMERCIAL SHIFT DETECTED! (Δ = %.4f > %.2f)".format(
                            state.currentDistance, state.threshold
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Real-time Audio Waveform (Oscilloscope)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "AUDIO WAVEFORM (PCM 16-BIT / 16 KHZ)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    WaveformOscilloscope(
                        waveform = state.waveform,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    )
                }
            }

            // Real-time Mel-Spectrogram (40 Mel Bands Waterfall)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MEL-SPECTROGRAM WATERFALL (40 BANDS)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.intervalMs} ms interval",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    SpectrogramWaterfall(
                        history = state.spectrogramHistory,
                        numMelBands = 40,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                }
            }

            // Shift Distance Meter (Δ vs Threshold τ)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    DistanceMeter(
                        distance = state.currentDistance,
                        threshold = state.threshold,
                        isTriggered = state.isEventTriggered,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Workbench Tuning Controls
            WorkbenchControls(
                intervalMs = state.intervalMs,
                threshold = state.threshold,
                onIntervalChanged = { engine.setInterval(it) },
                onThresholdChanged = { engine.setThreshold(it) }
            )

            // Event Logs Console
            Text(
                text = "REAL-TIME EVENT CONSOLE",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0A0F1D), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.eventLogs.isEmpty()) {
                    item {
                        Text(
                            text = "Awaiting acoustic shift events...",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    items(state.eventLogs) { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "[${event.timestamp}] ${event.description}",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Δ=%.3f".format(event.distance),
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkbenchHeader(
    isRunning: Boolean,
    isLiveMic: Boolean,
    onToggleRunning: () -> Unit,
    onToggleSource: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "AUDIO / VIDEO WORKBENCH",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "100% LOCAL ON-DEVICE • ZERO CLOUD",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF10B981)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isLiveMic) "MIC" else "SYNTH",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray
            )
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = isLiveMic,
                onCheckedChange = onToggleSource,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF38BDF8),
                    checkedTrackColor = Color(0xFF0284C7)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onToggleRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFDC2626) else Color(0xFF059669)
                )
            ) {
                Text(if (isRunning) "STOP" else "START", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WorkbenchControls(
    intervalMs: Long,
    threshold: Float,
    onIntervalChanged: (Long) -> Unit,
    onThresholdChanged: (Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Threshold Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Significance Threshold (τ)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Text(
                    text = "%.2f".format(threshold),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF38BDF8)
                )
            }
            Slider(
                value = threshold,
                onValueChange = onThresholdChanged,
                valueRange = 0.01f..0.30f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7)
                )
            )

            // Interval Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Spectrogram Interval",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Text(
                    text = "$intervalMs ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF38BDF8)
                )
            }
            Slider(
                value = intervalMs.toFloat(),
                onValueChange = { onIntervalChanged(it.toLong()) },
                valueRange = 50f..500f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7)
                )
            )
        }
    }
}
