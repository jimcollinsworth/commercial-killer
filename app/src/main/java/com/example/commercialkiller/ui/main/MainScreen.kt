// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import com.example.commercialkiller.data.audio.AudioSourceMode
import com.example.commercialkiller.data.audio.AudioWorkbenchEngine
import com.example.commercialkiller.data.audio.ClassifierScore
import com.example.commercialkiller.data.audio.WorkbenchEvent
import com.example.commercialkiller.ui.components.DistanceMeter
import com.example.commercialkiller.ui.components.HelpIcon
import com.example.commercialkiller.ui.components.PlayIcon
import com.example.commercialkiller.ui.components.SettingsIcon
import com.example.commercialkiller.ui.components.SpectrogramWaterfall
import com.example.commercialkiller.ui.components.StopIcon
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    engine: AudioWorkbenchEngine = remember { AudioWorkbenchEngine() }
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // File picker launcher supporting broad audio MIME types
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                engine.loadAudioFile(context, it)
            }
        }
    }

    // Microphone runtime permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            engine.start(AudioSourceMode.MIC)
        }
    }

    // Composition Lifecycle: Initialize synthesis on launch, stop when screen is disposed
    DisposableEffect(Unit) {
        engine.start(AudioSourceMode.SYNTH)
        onDispose {
            engine.stop()
        }
    }

    // Host Lifecycle: Automatically pause audio & analysis when app is backgrounded, resume on foreground
    LifecycleResumeEffect(engine) {
        engine.resume()
        onPauseOrDispose {
            engine.pause()
        }
    }

    // Adaptive Window Size Class policy
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val isExpanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val isShortWindow = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isExpanded) {
                // Adaptive Two-Pane Layout for Tablets & Foldables (sw600dp+)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Audio Sources, Playback, Waterfall & Controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WorkbenchHeader(
                            isRunning = state.isRunning,
                            sourceMode = state.sourceMode,
                            isTvMuted = state.isTvMuted,
                            tvControlMethod = state.tvControlMethod,
                            onItemClick = onItemClick,
                            onToggleRunning = {
                                if (state.isRunning) engine.stop() else engine.start(state.sourceMode)
                            },
                            onSelectSource = { mode ->
                                handleSelectSource(mode, context, state, engine, micPermissionLauncher, filePickerLauncher)
                            },
                            onOpenFilePicker = {
                                launchAudioPicker(filePickerLauncher)
                            }
                        )

                        if (state.sourceMode == AudioSourceMode.FILE && state.loadedFileName != null) {
                            FilePlaybackCard(
                                fileName = state.loadedFileName ?: "",
                                positionMs = state.filePositionMs,
                                durationMs = state.fileDurationMs,
                                progress = state.fileProgress,
                                onSeek = { engine.seekFile(it) },
                                onChangeFile = { launchAudioPicker(filePickerLauncher) }
                            )
                        }

                        if (state.isEventTriggered) {
                            AlertBanner(currentDistance = state.currentDistance, threshold = state.threshold)
                        }

                        SpectrogramWaterfallCard(
                            history = state.spectrogramHistory,
                            intervalMs = state.intervalMs
                        )

                        WorkbenchControls(
                            intervalMs = state.intervalMs,
                            threshold = state.threshold,
                            onIntervalChanged = { engine.setInterval(it) },
                            onThresholdChanged = { engine.setThreshold(it) }
                        )
                    }

                    // Right Pane: Distance Meter, Classifier Scores, Real-time Console
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DistanceMeterCard(
                            distance = state.currentDistance,
                            threshold = state.threshold,
                            isTriggered = state.isEventTriggered
                        )

                        ParallelClassifierCard(
                            activeModel = state.activeClassifierModel,
                            scores = state.classifierScores
                        )

                        EventConsoleCard(
                            events = state.eventLogs,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Compact / Phone Single-Pane with Bounded Width (max 640dp) and IME safety
                val phoneColumnModifier = if (isShortWindow) {
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .fillMaxHeight()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .fillMaxHeight()
                        .padding(16.dp)
                        .imePadding()
                }

                Column(
                    modifier = phoneColumnModifier,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WorkbenchHeader(
                        isRunning = state.isRunning,
                        sourceMode = state.sourceMode,
                        isTvMuted = state.isTvMuted,
                        tvControlMethod = state.tvControlMethod,
                        onItemClick = onItemClick,
                        onToggleRunning = {
                            if (state.isRunning) engine.stop() else engine.start(state.sourceMode)
                        },
                        onSelectSource = { mode ->
                            handleSelectSource(mode, context, state, engine, micPermissionLauncher, filePickerLauncher)
                        },
                        onOpenFilePicker = {
                            launchAudioPicker(filePickerLauncher)
                        }
                    )

                    if (state.sourceMode == AudioSourceMode.FILE && state.loadedFileName != null) {
                        FilePlaybackCard(
                            fileName = state.loadedFileName ?: "",
                            positionMs = state.filePositionMs,
                            durationMs = state.fileDurationMs,
                            progress = state.fileProgress,
                            onSeek = { engine.seekFile(it) },
                            onChangeFile = { launchAudioPicker(filePickerLauncher) }
                        )
                    }

                    if (state.isEventTriggered) {
                        AlertBanner(currentDistance = state.currentDistance, threshold = state.threshold)
                    }

                    SpectrogramWaterfallCard(
                        history = state.spectrogramHistory,
                        intervalMs = state.intervalMs
                    )

                    DistanceMeterCard(
                        distance = state.currentDistance,
                        threshold = state.threshold,
                        isTriggered = state.isEventTriggered
                    )

                    ParallelClassifierCard(
                        activeModel = state.activeClassifierModel,
                        scores = state.classifierScores
                    )

                    WorkbenchControls(
                        intervalMs = state.intervalMs,
                        threshold = state.threshold,
                        onIntervalChanged = { engine.setInterval(it) },
                        onThresholdChanged = { engine.setThreshold(it) }
                    )

                    val consoleModifier = if (isShortWindow) {
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    }

                    EventConsoleCard(
                        events = state.eventLogs,
                        modifier = consoleModifier
                    )
                }
            }
        }
    }
}

private fun launchAudioPicker(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    launcher.launch(
        arrayOf(
            "audio/*",
            "application/ogg",
            "video/mp4"
        )
    )
}

private fun handleSelectSource(
    mode: AudioSourceMode,
    context: android.content.Context,
    state: com.example.commercialkiller.data.audio.WorkbenchState,
    engine: AudioWorkbenchEngine,
    micPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    when (mode) {
        AudioSourceMode.MIC -> {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                engine.start(AudioSourceMode.MIC)
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        AudioSourceMode.FILE -> {
            if (state.sourceMode == AudioSourceMode.FILE || state.loadedFileName == null) {
                launchAudioPicker(filePickerLauncher)
            } else {
                engine.start(mode)
            }
        }
        AudioSourceMode.SYNTH -> {
            engine.start(mode)
        }
    }
}

@Composable
private fun AlertBanner(currentDistance: Float, threshold: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFDC2626), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "⚡ COMMERCIAL SHIFT DETECTED! (Δ = %.4f > %.2f)".format(
                currentDistance, threshold
            ),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SpectrogramWaterfallCard(
    history: List<FloatArray>,
    intervalMs: Long
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
                    text = "$intervalMs ms interval",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            SpectrogramWaterfall(
                history = history,
                numMelBands = 40,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
        }
    }
}

@Composable
private fun DistanceMeterCard(
    distance: Float,
    threshold: Float,
    isTriggered: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            DistanceMeter(
                distance = distance,
                threshold = threshold,
                isTriggered = isTriggered,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ParallelClassifierCard(
    activeModel: String,
    scores: List<ClassifierScore>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PARALLEL AUDIO CLASSIFIER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeModel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF34D399),
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            ParallelClassifierContent(scores = scores)
        }
    }
}

@Composable
private fun EventConsoleCard(
    events: List<WorkbenchEvent>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "REAL-TIME EVENT CONSOLE",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .background(Color(0xFF0A0F1D), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (events.isEmpty()) {
                item {
                    Text(
                        text = "Awaiting acoustic shift events...",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                items(events) { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[${event.timestamp}] ${event.description}",
                            color = Color(0xFF34D399),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Δ=%.3f".format(event.distance),
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkbenchHeader(
    isRunning: Boolean,
    sourceMode: AudioSourceMode,
    isTvMuted: Boolean,
    tvControlMethod: String,
    onItemClick: (NavKey) -> Unit = {},
    onToggleRunning: () -> Unit,
    onSelectSource: (AudioSourceMode) -> Unit,
    onOpenFilePicker: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "COMMERCIAL KILLER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (isTvMuted) Color(0xFFDC2626) else Color(0xFF1E293B),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isTvMuted) "TV MUTED [$tvControlMethod]" else "TV ACTIVE [$tvControlMethod]",
                            color = if (isTvMuted) Color.White else Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onItemClick(com.example.commercialkiller.Help) },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF334155), RoundedCornerShape(8.dp))
                ) {
                    HelpIcon(tint = Color.White)
                }

                IconButton(
                    onClick = { onItemClick(com.example.commercialkiller.IrSettings) },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF0284C7), RoundedCornerShape(8.dp))
                ) {
                    SettingsIcon(tint = Color.White)
                }

                IconButton(
                    onClick = onToggleRunning,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (isRunning) Color(0xFFDC2626) else Color(0xFF059669),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    if (isRunning) {
                        StopIcon(tint = Color.White)
                    } else {
                        PlayIcon(tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Source Mode Selector (SYNTH, MIC, FILE)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AudioSourceMode.values().forEach { mode ->
                val isSelected = sourceMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B))
                        .clickable { onSelectSource(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.name,
                        color = if (isSelected) Color.White else Color.LightGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FilePlaybackCard(
    fileName: String,
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    onSeek: (Float) -> Unit,
    onChangeFile: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "FILE: $fileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "%02d:%02d / %02d:%02d".format(
                            (positionMs / 1000) / 60, (positionMs / 1000) % 60,
                            (durationMs / 1000) / 60, (durationMs / 1000) % 60
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Button(
                    onClick = onChangeFile,
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("RESELECT FILE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Slider(
                value = progress,
                onValueChange = onSeek,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7)
                )
            )
        }
    }
}

@Composable
private fun ParallelClassifierContent(scores: List<ClassifierScore>) {
    val displayScores = remember(scores) {
        val list = scores.take(3).toMutableList()
        val defaultLabels = listOf("Dialogue / Speech", "Broadcast Content", "Background Noise")
        var idx = 0
        while (list.size < 3) {
            val label = defaultLabels.getOrElse(idx++) { "Audio Stream" }
            list.add(ClassifierScore(label, 0.0f))
        }
        list
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        displayScores.forEach { item ->
            val animatedProgress by animateFloatAsState(
                targetValue = item.score.coerceIn(0f, 1f),
                label = "classifierProgress"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.width(140.dp)
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = when {
                        item.label.contains("Commercial", ignoreCase = true) -> Color(0xFFDC2626)
                        item.label.contains("Silence", ignoreCase = true) -> Color(0xFFFBBF24)
                        else -> Color(0xFF38BDF8)
                    },
                    trackColor = Color(0xFF0F172A),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "%.0f%%".format(item.score * 100f),
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(36.dp)
                )
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
        Column(modifier = Modifier.padding(10.dp)) {
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
                    text = "Analysis Interval",
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
