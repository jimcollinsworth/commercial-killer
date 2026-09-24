// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.window.core.layout.WindowSizeClass
import com.example.commercialkiller.data.audio.AudioSourceMode
import com.example.commercialkiller.data.audio.AudioWorkbenchEngine
import com.example.commercialkiller.data.audio.ClassifierScore
import com.example.commercialkiller.data.audio.WorkbenchEvent
import com.example.commercialkiller.ui.components.AudioFileIcon
import com.example.commercialkiller.ui.components.DistanceMeter
import com.example.commercialkiller.ui.components.HelpIcon
import com.example.commercialkiller.ui.components.MicIcon
import com.example.commercialkiller.ui.components.PlayIcon
import com.example.commercialkiller.ui.components.SettingsIcon
import com.example.commercialkiller.ui.components.SlidersIcon
import com.example.commercialkiller.ui.components.SpectrogramWaterfall
import com.example.commercialkiller.ui.components.StopIcon
import com.example.commercialkiller.ui.components.SynthIcon
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
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Media picker launcher supporting video and broad audio MIME types
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

    // Settings panel sheet for spectrogram threshold and intervals
    if (showSettingsSheet) {
        SpectrogramSettingsSheet(
            intervalMs = state.intervalMs,
            threshold = state.threshold,
            numMelBands = state.numMelBands,
            onIntervalChanged = { engine.setInterval(it) },
            onThresholdChanged = { engine.setThreshold(it) },
            onMelBandsChanged = { engine.setMelBands(it) },
            onDismiss = { showSettingsSheet = false }
        )
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
                    // Left Pane: Source Mode, Video/File Display, Spectrogram
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
                            onOpenSpectrogramSettings = { showSettingsSheet = true },
                            onToggleRunning = {
                                if (state.isRunning) engine.stop() else engine.start(state.sourceMode)
                            },
                            onSelectSource = { mode ->
                                handleSelectSource(mode, context, state, engine, micPermissionLauncher, filePickerLauncher)
                            }
                        )

                        // 1. In case of video, video display on top
                        if (state.sourceMode == AudioSourceMode.FILE && state.isVideo && state.mediaUri != null) {
                            VideoPlayerCard(
                                uri = state.mediaUri!!,
                                fileName = state.loadedFileName ?: "",
                                isPlaying = state.isRunning,
                                positionMs = state.filePositionMs,
                                durationMs = state.fileDurationMs,
                                progress = state.fileProgress,
                                onSeek = { engine.seekFile(it) },
                                onChangeFile = { launchMediaPicker(filePickerLauncher) }
                            )
                        } else if (state.sourceMode == AudioSourceMode.FILE && state.loadedFileName != null) {
                            FilePlaybackCard(
                                fileName = state.loadedFileName ?: "",
                                positionMs = state.filePositionMs,
                                durationMs = state.fileDurationMs,
                                progress = state.fileProgress,
                                onSeek = { engine.seekFile(it) },
                                onChangeFile = { launchMediaPicker(filePickerLauncher) }
                            )
                        }

                        if (state.isEventTriggered) {
                            AlertBanner(currentDistance = state.currentDistance, threshold = state.threshold)
                        }

                        // 2. Audio Mel Spectrogram underneath (3x as tall, ~50% of view)
                        SpectrogramWaterfallCard(
                            history = state.spectrogramHistory,
                            numMelBands = state.numMelBands,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(360.dp)
                        )
                    }

                    // Right Pane: Features and Labeling Underneath
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ClassifierLabelsContent(scores = state.classifierScores)

                        DistanceMeterCard(
                            distance = state.currentDistance,
                            threshold = state.threshold,
                            isTriggered = state.isEventTriggered
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
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .fillMaxHeight()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                }

                Column(
                    modifier = phoneColumnModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WorkbenchHeader(
                        isRunning = state.isRunning,
                        sourceMode = state.sourceMode,
                        isTvMuted = state.isTvMuted,
                        tvControlMethod = state.tvControlMethod,
                        onItemClick = onItemClick,
                        onOpenSpectrogramSettings = { showSettingsSheet = true },
                        onToggleRunning = {
                            if (state.isRunning) engine.stop() else engine.start(state.sourceMode)
                        },
                        onSelectSource = { mode ->
                            handleSelectSource(mode, context, state, engine, micPermissionLauncher, filePickerLauncher)
                        }
                    )

                    // 1. In case of video, video file display on top
                    if (state.sourceMode == AudioSourceMode.FILE && state.isVideo && state.mediaUri != null) {
                        VideoPlayerCard(
                            uri = state.mediaUri!!,
                            fileName = state.loadedFileName ?: "",
                            isPlaying = state.isRunning,
                            positionMs = state.filePositionMs,
                            durationMs = state.fileDurationMs,
                            progress = state.fileProgress,
                            onSeek = { engine.seekFile(it) },
                            onChangeFile = { launchMediaPicker(filePickerLauncher) }
                        )
                    } else if (state.sourceMode == AudioSourceMode.FILE && state.loadedFileName != null) {
                        FilePlaybackCard(
                            fileName = state.loadedFileName ?: "",
                            positionMs = state.filePositionMs,
                            durationMs = state.fileDurationMs,
                            progress = state.fileProgress,
                            onSeek = { engine.seekFile(it) },
                            onChangeFile = { launchMediaPicker(filePickerLauncher) }
                        )
                    }

                    if (state.isEventTriggered) {
                        AlertBanner(currentDistance = state.currentDistance, threshold = state.threshold)
                    }

                    // 2. Audio Mel Spectrogram underneath (3x as tall, ~50% total screen)
                    val spectrogramHeight = if (state.sourceMode == AudioSourceMode.FILE && state.isVideo) {
                        240.dp
                    } else if (isShortWindow) {
                        220.dp
                    } else {
                        380.dp
                    }

                    SpectrogramWaterfallCard(
                        history = state.spectrogramHistory,
                        numMelBands = state.numMelBands,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spectrogramHeight)
                    )

                    // 3. Features and labeling underneath that
                    ClassifierLabelsContent(scores = state.classifierScores)

                    DistanceMeterCard(
                        distance = state.currentDistance,
                        threshold = state.threshold,
                        isTriggered = state.isEventTriggered
                    )

                    EventConsoleCard(
                        events = state.eventLogs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                }
            }
        }
    }
}

private fun launchMediaPicker(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    launcher.launch(
        arrayOf(
            "audio/*",
            "video/*",
            "video/mp4",
            "video/x-matroska",
            "video/webm",
            "video/3gpp",
            "application/ogg"
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
                launchMediaPicker(filePickerLauncher)
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
    numMelBands: Int = 80,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.padding(6.dp)) {
            SpectrogramWaterfall(
                history = history,
                numMelBands = numMelBands,
                modifier = modifier
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
        Column(modifier = Modifier.padding(8.dp)) {
            DistanceMeter(
                distance = distance,
                threshold = threshold,
                isTriggered = isTriggered,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Output of the classifier:
 * Shows text of classifications without confidence values or bar charts.
 * Uses color green if confidence > 90%, yellow if confidence <= 90%.
 * Classifications below 10% are not shown at all.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassifierLabelsContent(scores: List<ClassifierScore>) {
    val activeScores = remember(scores) {
        scores.filter { it.score >= 0.10f }
    }

    if (activeScores.isNotEmpty()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            activeScores.forEach { item ->
                val textColor = if (item.score > 0.90f) {
                    Color(0xFF22C55E) // Green for > 90%
                } else {
                    Color(0xFFFBBF24) // Yellow for <= 90%
                }

                Box(
                    modifier = Modifier
                        .background(textColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.label,
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
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
    onOpenSpectrogramSettings: () -> Unit,
    onToggleRunning: () -> Unit,
    onSelectSource: (AudioSourceMode) -> Unit
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155))
                        .clickable(onClick = onOpenSpectrogramSettings),
                    contentAlignment = Alignment.Center
                ) {
                    SlidersIcon(tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0284C7))
                        .clickable { onItemClick(com.example.commercialkiller.IrSettings) },
                    contentAlignment = Alignment.Center
                ) {
                    SettingsIcon(tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF334155))
                        .clickable { onItemClick(com.example.commercialkiller.Help) },
                    contentAlignment = Alignment.Center
                ) {
                    HelpIcon(tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isRunning) Color(0xFFDC2626) else Color(0xFF059669)
                        )
                        .clickable(onClick = onToggleRunning),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRunning) {
                        StopIcon(tint = Color.White)
                    } else {
                        PlayIcon(tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Compact Source Mode Selector (SYNTH, MIC, FILE) with icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AudioSourceMode.entries.forEach { mode ->
                    val isSelected = sourceMode == mode
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { onSelectSource(mode) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (mode) {
                            AudioSourceMode.SYNTH -> SynthIcon(
                                modifier = Modifier.size(14.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                            AudioSourceMode.MIC -> MicIcon(
                                modifier = Modifier.size(14.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                            AudioSourceMode.FILE -> AudioFileIcon(
                                modifier = Modifier.size(14.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                        Text(
                            text = mode.name,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoPlayerCard(
    uri: Uri,
    fileName: String,
    isPlaying: Boolean,
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
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                key(uri) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setVideoURI(uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    // Mute VideoView audio so AudioWorkbenchEngine's AudioTrack remains
                                    // the single audio source, preventing echo/double-audio
                                    mp.setVolume(0f, 0f)
                                    if (isPlaying) {
                                        start()
                                    }
                                }
                            }
                        },
                        update = { view ->
                            val diff = kotlin.math.abs(view.currentPosition - positionMs)
                            if (diff > 600) {
                                view.seekTo(positionMs.toInt())
                            }
                            if (isPlaying) {
                                if (!view.isPlaying) {
                                    view.start()
                                }
                            } else {
                                if (view.isPlaying) {
                                    view.pause()
                                }
                            }
                        },
                        onRelease = { view ->
                            view.stopPlayback()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                    fontSize = 11.sp
                )
                Text(
                    text = "%02d:%02d / %02d:%02d".format(
                        (positionMs / 1000) / 60, (positionMs / 1000) % 60,
                        (durationMs / 1000) / 60, (durationMs / 1000) % 60
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onChangeFile,
                    modifier = Modifier.height(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("FILE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Slider(
                value = progress,
                onValueChange = onSeek,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
            )
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
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "AUDIO: $fileName",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "%02d:%02d / %02d:%02d".format(
                            (positionMs / 1000) / 60, (positionMs / 1000) % 60,
                            (durationMs / 1000) / 60, (durationMs / 1000) % 60
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                Button(
                    onClick = onChangeFile,
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("FILE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Slider(
                value = progress,
                onValueChange = onSeek,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF38BDF8),
                    activeTrackColor = Color(0xFF0284C7)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
            )
        }
    }
}

/**
 * Spectrogram & Analysis Settings Panel:
 * Controls the significance threshold (τ), analysis interval, Mel filterbank bands,
 * and classifier visibility parameters without consuming permanent main-screen space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpectrogramSettingsSheet(
    intervalMs: Long,
    threshold: Float,
    numMelBands: Int,
    onIntervalChanged: (Long) -> Unit,
    onThresholdChanged: (Float) -> Unit,
    onMelBandsChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SPECTROGRAM & ANALYSIS SETTINGS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text("DONE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Threshold Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Significance Threshold (τ)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Text(
                        text = "%.2f".format(threshold),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
                Text(
                    text = "Acoustic shift delta required to trigger commercial transition",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Slider(
                    value = threshold,
                    onValueChange = onThresholdChanged,
                    valueRange = 0.01f..0.30f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF0284C7)
                    )
                )
            }

            // Interval Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Analysis Interval",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Text(
                        text = "$intervalMs ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
                Text(
                    text = "FFT computation and audio classifier polling frequency",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
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

            // Mel Bands Resolution Selector
            Column {
                Text(
                    text = "Mel Filterbank Bands Resolution",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Text(
                    text = "Higher band count increases frequency detail on spectrogram",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(40, 64, 80, 128).forEach { bands ->
                        val isSelected = numMelBands == bands
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onMelBandsChanged(bands) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$bands Bands",
                                color = if (isSelected) Color.White else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Classification Filter Policy Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "CLASSIFIER DISPLAY POLICY",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Classifications below 10% confidence are hidden. Confidence > 90% highlighted green; below 90% yellow.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
