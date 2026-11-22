// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.commercialkiller.data.decision.FrameDecision
import com.example.commercialkiller.data.decision.MediaEnvironment
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

    // Composition Lifecycle: Initialize synthesis on launch unless a media file intent already loaded FILE mode
    DisposableEffect(Unit) {
        if (engine.state.value.sourceMode == AudioSourceMode.SYNTH && !engine.state.value.isRunning) {
            engine.start(AudioSourceMode.SYNTH)
        }
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
            mediaThreshold = state.mediaThreshold,
            sceneSimilarityThreshold = state.sceneSimilarityThreshold,
            onIntervalChanged = { engine.setInterval(it) },
            onThresholdChanged = { engine.setThreshold(it) },
            onMelBandsChanged = { engine.setMelBands(it) },
            onMediaThresholdChanged = { engine.setMediaThreshold(it) },
            onSceneSimilarityThresholdChanged = { engine.setSceneSimilarityThreshold(it) },
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
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Left Pane: Source Mode, Video/File Display, Spectrogram
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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

                        ShiftStatusBanner(
                            isTriggered = state.isEventTriggered,
                            currentDistance = state.currentDistance,
                            threshold = state.threshold
                        )

                        // 2. Audio Mel Spectrogram underneath
                        SpectrogramWaterfallCard(
                            history = state.spectrogramHistory,
                            numMelBands = state.numMelBands,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        )

                        FrameDecisionBar(
                            decision = state.frameDecision,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Right Pane: Features and Labeling Underneath
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ClassifierLabelsContent(scores = state.classifierScores)

                        DistanceMeterCard(
                            distance = state.currentDistance,
                            threshold = state.threshold,
                            isTriggered = state.isEventTriggered,
                            modifier = Modifier.fillMaxWidth()
                        )

                        EventConsoleCard(
                            events = state.eventLogs,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Compact / Phone Single-Pane with Bounded Width (max 640dp) and fixed section heights
                val phoneColumnModifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()

                Column(
                    modifier = phoneColumnModifier,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
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

                    ShiftStatusBanner(
                        isTriggered = state.isEventTriggered,
                        currentDistance = state.currentDistance,
                        threshold = state.threshold
                    )

                    // 2. Audio Mel Spectrogram underneath
                    val spectrogramHeight = if (state.sourceMode == AudioSourceMode.FILE && state.isVideo) {
                        215.dp
                    } else if (isShortWindow) {
                        190.dp
                    } else {
                        310.dp
                    }

                    SpectrogramWaterfallCard(
                        history = state.spectrogramHistory,
                        numMelBands = state.numMelBands,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(spectrogramHeight)
                    )

                    FrameDecisionBar(
                        decision = state.frameDecision,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Features and labeling underneath that
                    ClassifierLabelsContent(scores = state.classifierScores)

                    DistanceMeterCard(
                        distance = state.currentDistance,
                        threshold = state.threshold,
                        isTriggered = state.isEventTriggered,
                        modifier = Modifier.fillMaxWidth()
                    )

                    EventConsoleCard(
                        events = state.eventLogs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
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
private fun ShiftStatusBanner(
    isTriggered: Boolean,
    currentDistance: Float,
    threshold: Float
) {
    val bgColor = if (isTriggered) Color(0xFFDC2626) else Color(0xFF1E293B)
    val borderMod = if (isTriggered) {
        Modifier
    } else {
        Modifier.border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
    }
    val statusText = if (isTriggered) {
        "SHIFT DETECTED  Δ=%.4f > %.2f".format(currentDistance, threshold)
    } else {
        "STEADY SIGNAL   Δ=%.4f / τ=%.2f".format(currentDistance, threshold)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(bgColor, RoundedCornerShape(6.dp))
            .then(borderMod)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = statusText,
            color = if (isTriggered) Color.White else Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
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
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            SpectrogramWaterfall(
                history = history,
                numMelBands = numMelBands,
                modifier = modifier
            )
        }
    }
}

private val DecisionBarShape = RoundedCornerShape(8.dp)
private val DecisionBadgeShape = RoundedCornerShape(4.dp)

@Composable
private fun FrameDecisionBar(
    decision: FrameDecision,
    modifier: Modifier = Modifier
) {
    val mediaBg = when {
        decision.isMedia -> Color(0xFF0D9488)
        decision.mediaEnvironment == MediaEnvironment.SILENCE -> Color(0xFF475569)
        else -> Color(0xFF64748B)
    }
    val mediaText = when {
        decision.isMedia -> "MEDIA %.0f%%".format(decision.mediaScore * 100f)
        decision.mediaEnvironment == MediaEnvironment.SILENCE -> "NO MEDIA: SILENCE"
        else -> "NO MEDIA: AMBIENT"
    }

    val commercialBg = when {
        !decision.isMedia -> Color(0xFF334155)
        decision.isCommercial -> Color(0xFFDC2626)
        else -> Color(0xFF16A34A)
    }
    val commercialText = when {
        !decision.isMedia -> "HOLD (NO MEDIA)"
        decision.isCommercial -> "COMMERCIAL %.0f%%".format(decision.commercialScore * 100f)
        else -> "PROGRAM %.0f%%".format((1f - decision.commercialScore) * 100f)
    }

    val sceneBg = if (decision.isSameScene) Color(0xFF0284C7) else Color(0xFFD97706)
    val sceneText = if (decision.isSameScene) {
        "SAME %.2f".format(decision.sceneSimilarity)
    } else {
        "CUT %.2f".format(decision.sceneSimilarity)
    }

    val leftTelemetry = "Media: %.0f%% | Shot: %.1fs".format(
        decision.mediaScore * 100f,
        decision.shotDurationMs / 1000f
    )
    val rightTelemetry = "%.0f cuts/min".format(decision.cutCadencePerMin)

    Surface(
        modifier = modifier.height(48.dp),
        color = Color(0xFF1E293B),
        shape = DecisionBarShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                        .background(mediaBg, DecisionBadgeShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mediaText,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight()
                        .background(commercialBg, DecisionBadgeShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = commercialText,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .background(sceneBg, DecisionBadgeShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sceneText,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = leftTelemetry,
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = rightTelemetry,
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun DistanceMeterCard(
    distance: Float,
    threshold: Float,
    isTriggered: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(34.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
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
 * Fixed-height 3-slot classifier strip:
 * Green for > 90%, yellow for <= 90%; scores below 10% are filtered out.
 */
@Composable
private fun ClassifierLabelsContent(scores: List<ClassifierScore>) {
    val activeScores = remember(scores) {
        scores.filter { it.score >= 0.10f }.take(3)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (activeScores.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active audio tags",
                    color = Color(0xFF64748B),
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        } else {
            activeScores.forEach { item ->
                val textColor = if (item.score > 0.90f) {
                    Color(0xFF22C55E) // Green for > 90%
                } else {
                    Color(0xFFFBBF24) // Yellow for <= 90%
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(textColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.label,
                        color = textColor,
                        fontSize = 9.5.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
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
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .background(Color(0xFF0A0F1D), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (events.isEmpty()) {
                item {
                    Text(
                        text = "Awaiting acoustic shift events...",
                        color = Color.Gray,
                        fontSize = 9.5.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            } else {
                items(events) { event ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[${event.timestamp}] ${event.description}",
                            color = Color(0xFF34D399),
                            fontSize = 9.5.sp,
                            lineHeight = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 6.dp)
                        )
                        Text(
                            text = "Δ=%.3f".format(event.distance),
                            color = Color(0xFFFBBF24),
                            fontSize = 9.5.sp,
                            lineHeight = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
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
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .background(
                            if (isTvMuted) Color(0xFFDC2626) else Color(0xFF1E293B),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isTvMuted) "TV MUTED [$tvControlMethod]" else "TV ACTIVE [$tvControlMethod]",
                        color = if (isTvMuted) Color.White else Color(0xFF38BDF8),
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF334155))
                        .clickable(onClick = onOpenSpectrogramSettings),
                    contentAlignment = Alignment.Center
                ) {
                    SlidersIcon(tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF0284C7))
                        .clickable { onItemClick(com.example.commercialkiller.IrSettings) },
                    contentAlignment = Alignment.Center
                ) {
                    SettingsIcon(tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF334155))
                        .clickable { onItemClick(com.example.commercialkiller.Help) },
                    contentAlignment = Alignment.Center
                ) {
                    HelpIcon(tint = Color.White)
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(7.dp))
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

        Spacer(modifier = Modifier.height(3.dp))

        // Compact Source Mode Selector (SYNTH, MIC, FILE) with icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                AudioSourceMode.entries.forEach { mode ->
                    val isSelected = sourceMode == mode
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { onSelectSource(mode) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        when (mode) {
                            AudioSourceMode.SYNTH -> SynthIcon(
                                modifier = Modifier.size(12.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                            AudioSourceMode.MIC -> MicIcon(
                                modifier = Modifier.size(12.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                            AudioSourceMode.FILE -> AudioFileIcon(
                                modifier = Modifier.size(12.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                        Text(
                            text = mode.name,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
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
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(RoundedCornerShape(6.dp))
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

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fileName,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                    fontSize = 10.sp
                )
                Text(
                    text = "%02d:%02d / %02d:%02d".format(
                        (positionMs / 1000) / 60, (positionMs / 1000) % 60,
                        (durationMs / 1000) / 60, (durationMs / 1000) % 60
                    ),
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onChangeFile,
                    modifier = Modifier.height(22.dp),
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
                    .height(18.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AUDIO: $fileName",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                    fontSize = 10.sp
                )
                Text(
                    text = "%02d:%02d / %02d:%02d".format(
                        (positionMs / 1000) / 60, (positionMs / 1000) % 60,
                        (durationMs / 1000) / 60, (durationMs / 1000) % 60
                    ),
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onChangeFile,
                    modifier = Modifier.height(22.dp),
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
                    .height(18.dp)
            )
        }
    }
}

/**
 * Spectrogram & Analysis Settings Panel:
 * Controls the significance threshold (τ), first-gate media threshold, cosine similarity scene-cut
 * threshold, analysis interval, and Mel filterbank bands without consuming permanent main-screen space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpectrogramSettingsSheet(
    intervalMs: Long,
    threshold: Float,
    numMelBands: Int,
    mediaThreshold: Float,
    sceneSimilarityThreshold: Float,
    onIntervalChanged: (Long) -> Unit,
    onThresholdChanged: (Float) -> Unit,
    onMelBandsChanged: (Int) -> Unit,
    onMediaThresholdChanged: (Float) -> Unit,
    onSceneSimilarityThresholdChanged: (Float) -> Unit,
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
                .verticalScroll(rememberScrollState())
                .imePadding(),
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

            // Media Detection Threshold Slider (Gate 1)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Media Detection Threshold",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Text(
                        text = "%.2f".format(mediaThreshold),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF14B8A6)
                    )
                }
                Text(
                    text = "Minimum confidence to classify frame as broadcast TV/radio media rather than silence or room/car noise",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Slider(
                    value = mediaThreshold.coerceIn(0.20f, 0.90f),
                    onValueChange = onMediaThresholdChanged,
                    valueRange = 0.20f..0.90f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF14B8A6),
                        activeTrackColor = Color(0xFF0D9488)
                    )
                )
            }

            // Scene Cut Similarity Threshold Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scene Cut Similarity Threshold",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Text(
                        text = "%.2f".format(sceneSimilarityThreshold),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }
                Text(
                    text = "Cosine similarity floor between consecutive spectrogram frames; drops below this value trigger a scene cut",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Slider(
                    value = sceneSimilarityThreshold.coerceIn(0.50f, 0.98f),
                    onValueChange = onSceneSimilarityThresholdChanged,
                    valueRange = 0.50f..0.98f,
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
