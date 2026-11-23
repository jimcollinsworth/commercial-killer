// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
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
import com.example.commercialkiller.data.audio.WorkbenchState
import com.example.commercialkiller.data.decision.FrameDecision
import com.example.commercialkiller.data.decision.MediaEnvironment
import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VideoFrameSnapshot
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import com.example.commercialkiller.ui.camera.CameraScreen
import com.example.commercialkiller.ui.components.AudioFileIcon
import com.example.commercialkiller.ui.components.CameraIcon
import com.example.commercialkiller.ui.components.DistanceMeter
import com.example.commercialkiller.ui.components.HelpIcon
import com.example.commercialkiller.ui.components.MicIcon
import com.example.commercialkiller.ui.components.PlayIcon
import com.example.commercialkiller.ui.components.SettingsIcon
import com.example.commercialkiller.ui.components.SlidersIcon
import com.example.commercialkiller.ui.components.SpectrogramWaterfall
import com.example.commercialkiller.ui.components.StopIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // Live MIC / CAM runtime permissions launcher
    val livePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val camGranted = permissions[Manifest.permission.CAMERA] == true
        if (micGranted) {
            engine.setCameraEnabled(camGranted)
            engine.start(AudioSourceMode.LIVE)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        engine.setCameraEnabled(isGranted)
    }

    // Option A Cold-Launch: Auto-start MIC / CAM when RECORD_AUDIO is granted unless a file intent loaded FILE mode
    DisposableEffect(Unit) {
        if (engine.state.value.sourceMode == AudioSourceMode.LIVE && !engine.state.value.isRunning) {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                val hasCam = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                engine.setCameraEnabled(hasCam)
                engine.start(AudioSourceMode.LIVE)
            }
        }
        onDispose {
            engine.stop()
        }
    }

    val hasCameraPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    val isLiveCameraPreviewVisible =
        state.sourceMode == AudioSourceMode.LIVE && state.isCameraEnabled && hasCameraPermission

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
                                handleToggleRunning(
                                    context = context,
                                    state = state,
                                    engine = engine,
                                    livePermissionsLauncher = livePermissionsLauncher,
                                    filePickerLauncher = filePickerLauncher
                                )
                            },
                            onSelectSource = { mode ->
                                handleSelectSource(
                                    mode = mode,
                                    context = context,
                                    state = state,
                                    engine = engine,
                                    livePermissionsLauncher = livePermissionsLauncher,
                                    filePickerLauncher = filePickerLauncher
                                )
                            }
                        )

                        ActiveMediaInputSlot(
                            state = state,
                            hasCameraPermission = hasCameraPermission,
                            engine = engine,
                            onSeek = { engine.seekFile(it) },
                            onChangeFile = { launchMediaPicker(filePickerLauncher) },
                            onToggleCamera = {
                                handleToggleCamera(
                                    context = context,
                                    state = state,
                                    engine = engine,
                                    cameraPermissionLauncher = cameraPermissionLauncher
                                )
                            }
                        )

                        ShiftStatusBanner(
                            isRunning = state.isRunning,
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
                            handleToggleRunning(
                                context = context,
                                state = state,
                                engine = engine,
                                livePermissionsLauncher = livePermissionsLauncher,
                                filePickerLauncher = filePickerLauncher
                            )
                        },
                        onSelectSource = { mode ->
                            handleSelectSource(
                                mode = mode,
                                context = context,
                                state = state,
                                engine = engine,
                                livePermissionsLauncher = livePermissionsLauncher,
                                filePickerLauncher = filePickerLauncher
                            )
                        }
                    )

                    ActiveMediaInputSlot(
                        state = state,
                        hasCameraPermission = hasCameraPermission,
                        engine = engine,
                        onSeek = { engine.seekFile(it) },
                        onChangeFile = { launchMediaPicker(filePickerLauncher) },
                        onToggleCamera = {
                            handleToggleCamera(
                                context = context,
                                state = state,
                                engine = engine,
                                cameraPermissionLauncher = cameraPermissionLauncher
                            )
                        }
                    )

                    ShiftStatusBanner(
                        isRunning = state.isRunning,
                        isTriggered = state.isEventTriggered,
                        currentDistance = state.currentDistance,
                        threshold = state.threshold
                    )

                    // 2. Audio Mel Spectrogram underneath
                    val spectrogramHeight = if ((state.sourceMode == AudioSourceMode.FILE && state.isVideo) || isLiveCameraPreviewVisible) {
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

private fun handleToggleRunning(
    context: android.content.Context,
    state: WorkbenchState,
    engine: AudioWorkbenchEngine,
    livePermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    if (state.isRunning) {
        engine.stop()
        return
    }
    when (state.sourceMode) {
        AudioSourceMode.LIVE -> handleSelectSource(
            mode = AudioSourceMode.LIVE,
            context = context,
            state = state,
            engine = engine,
            livePermissionsLauncher = livePermissionsLauncher,
            filePickerLauncher = filePickerLauncher
        )
        AudioSourceMode.FILE -> {
            if (state.loadedFileName == null) {
                launchMediaPicker(filePickerLauncher)
            } else {
                engine.start(AudioSourceMode.FILE)
            }
        }
    }
}

private fun handleToggleCamera(
    context: android.content.Context,
    state: WorkbenchState,
    engine: AudioWorkbenchEngine,
    cameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val hasCam = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    if (state.isCameraEnabled && hasCam) {
        engine.setCameraEnabled(false)
    } else if (hasCam) {
        engine.setCameraEnabled(true)
    } else {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
}

private fun handleSelectSource(
    mode: AudioSourceMode,
    context: android.content.Context,
    state: WorkbenchState,
    engine: AudioWorkbenchEngine,
    livePermissionsLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    filePickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    when (mode) {
        AudioSourceMode.LIVE -> {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasMic) {
                engine.start(AudioSourceMode.LIVE)
            } else {
                livePermissionsLauncher.launch(
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                )
            }
        }
        AudioSourceMode.FILE -> {
            if (state.sourceMode == AudioSourceMode.FILE || state.loadedFileName == null) {
                launchMediaPicker(filePickerLauncher)
            } else {
                engine.start(AudioSourceMode.FILE)
            }
        }
    }
}

@Composable
private fun ActiveMediaInputSlot(
    state: WorkbenchState,
    hasCameraPermission: Boolean,
    engine: AudioWorkbenchEngine,
    onSeek: (Float) -> Unit,
    onChangeFile: () -> Unit,
    onToggleCamera: () -> Unit
) {
    if (state.sourceMode == AudioSourceMode.FILE && state.isVideo && state.mediaUri != null) {
        VideoPlayerCard(
            uri = state.mediaUri,
            fileName = state.loadedFileName ?: "",
            isPlaying = state.isRunning,
            positionMs = state.filePositionMs,
            durationMs = state.fileDurationMs,
            progress = state.fileProgress,
            onSeek = onSeek,
            onChangeFile = onChangeFile,
            previousDescriptorProvider = { engine.getLastVisualDescriptor() },
            onFrameSampled = { engine.onVideoFrameSampled(it) }
        )
    } else if (state.sourceMode == AudioSourceMode.FILE && state.loadedFileName != null) {
        FilePlaybackCard(
            fileName = state.loadedFileName,
            positionMs = state.filePositionMs,
            durationMs = state.fileDurationMs,
            progress = state.fileProgress,
            onSeek = onSeek,
            onChangeFile = onChangeFile
        )
    } else if (state.sourceMode == AudioSourceMode.LIVE) {
        LiveCameraCard(
            isCameraActive = state.isCameraEnabled && hasCameraPermission,
            isRunning = state.isRunning,
            visualChangeScore = state.latestVisualDescriptor?.visualChangeScore ?: 0f,
            videoFrameCount = state.videoFrameCount,
            previousDescriptorProvider = { engine.getLastVisualDescriptor() },
            onFrameSampled = { engine.onVideoFrameSampled(it) },
            onToggleCamera = onToggleCamera
        )
    }
}

@Composable
private fun ShiftStatusBanner(
    isRunning: Boolean,
    isTriggered: Boolean,
    currentDistance: Float,
    threshold: Float
) {
    val bgColor = if (isRunning && isTriggered) Color(0xFFDC2626) else Color(0xFF1E293B)
    val borderMod = if (isRunning && isTriggered) {
        Modifier
    } else {
        Modifier.border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
    }
    val statusText = when {
        !isRunning -> "IDLE   TAP MIC / CAM OR FILE / VIDEO"
        isTriggered -> "SHIFT DETECTED  Δ=%.4f > %.2f".format(currentDistance, threshold)
        else -> "STEADY SIGNAL   Δ=%.4f / τ=%.2f".format(currentDistance, threshold)
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
            color = if (isRunning && isTriggered) Color.White else Color(0xFF94A3B8),
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

        // Compact Two-Mode Source Selector (MIC / CAM, FILE / VIDEO) with icons
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
                    val label = when (mode) {
                        AudioSourceMode.LIVE -> "MIC / CAM"
                        AudioSourceMode.FILE -> "FILE / VIDEO"
                    }
                    Row(
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {}
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                            .clickable { onSelectSource(mode) }
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (mode) {
                            AudioSourceMode.LIVE -> MicIcon(
                                modifier = Modifier.size(12.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                            AudioSourceMode.FILE -> AudioFileIcon(
                                modifier = Modifier.size(12.dp),
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                        Text(
                            text = label,
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
private fun LiveCameraCard(
    isCameraActive: Boolean,
    isRunning: Boolean,
    visualChangeScore: Float,
    videoFrameCount: Long,
    previousDescriptorProvider: () -> VisualFrameDescriptor?,
    onFrameSampled: (VideoFrameSnapshot) -> Unit,
    onToggleCamera: () -> Unit
) {
    if (isCameraActive) {
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
                    if (isRunning) {
                        CameraScreen(
                            modifier = Modifier.fillMaxSize(),
                            sampleIntervalMs = 250L,
                            previousDescriptorProvider = previousDescriptorProvider,
                            onFrameSampled = onFrameSampled
                        )
                    } else {
                        Text(
                            text = "CAMERA IDLE",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
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
                        text = "LIVE: MIC + CAM",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        fontSize = 10.sp
                    )
                    Text(
                        text = "ΔV=%.2f  #%d".format(visualChangeScore, videoFrameCount),
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onToggleCamera,
                        modifier = Modifier
                            .height(22.dp)
                            .semantics(mergeDescendants = true) {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        CameraIcon(modifier = Modifier.size(11.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("CAM OFF", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE: MIC ONLY (AUDIO)",
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    maxLines = 1
                )
                Button(
                    onClick = onToggleCamera,
                    modifier = Modifier
                        .height(22.dp)
                        .semantics(mergeDescendants = true) {},
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    CameraIcon(modifier = Modifier.size(11.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("CAM ON", fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
    onChangeFile: () -> Unit,
    previousDescriptorProvider: () -> VisualFrameDescriptor?,
    onFrameSampled: (VideoFrameSnapshot) -> Unit
) {
    val currentPrevProvider by rememberUpdatedState(previousDescriptorProvider)
    val currentOnFrameSampled by rememberUpdatedState(onFrameSampled)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            key(uri) {
                val currentIsPlaying by rememberUpdatedState(isPlaying)
                val currentPositionMs by rememberUpdatedState(positionMs)

                var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
                var surfaceRef by remember { mutableStateOf<Surface?>(null) }
                var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
                var isPrepared by remember { mutableStateOf(false) }
                var videoAspectRatio by remember { mutableFloatStateOf(4f / 3f) }
                var isDisposed by remember { mutableStateOf(false) }

                fun releaseMediaResources() {
                    isDisposed = true
                    isPrepared = false
                    try {
                        mediaPlayer?.let { mp ->
                            if (mp.isPlaying) mp.stop()
                            mp.release()
                        }
                    } catch (ignored: Exception) {}
                    mediaPlayer = null
                    try {
                        surfaceRef?.release()
                    } catch (ignored: Exception) {}
                    surfaceRef = null
                    textureViewRef = null
                }

                DisposableEffect(uri) {
                    onDispose {
                        releaseMediaResources()
                    }
                }

                LaunchedEffect(uri, isPlaying, isPrepared) {
                    if (!isPlaying || !isPrepared) return@LaunchedEffect
                    while (true) {
                        val tv = textureViewRef
                        if (tv != null && tv.isAvailable) {
                            val bmp = tv.getBitmap(
                                VideoFrameAnalyzer.THUMB_WIDTH,
                                VideoFrameAnalyzer.THUMB_HEIGHT
                            )
                            if (bmp != null) {
                                try {
                                    val snapshot = withContext(Dispatchers.Default) {
                                        VideoFrameAnalyzer.sampleBitmap(
                                            source = bmp,
                                            timestampMs = System.currentTimeMillis(),
                                            previous = currentPrevProvider()
                                        )
                                    }
                                    currentOnFrameSampled(snapshot)
                                } finally {
                                    if (!bmp.isRecycled) bmp.recycle()
                                }
                            }
                        }
                        delay(250L)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                textureViewRef = this
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(
                                        st: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {
                                        val surface = Surface(st)
                                        surfaceRef = surface
                                        try {
                                            val mp = MediaPlayer().apply {
                                                setSurface(surface)
                                                setDataSource(ctx, uri)
                                                isLooping = true
                                                // Mute MediaPlayer audio so AudioWorkbenchEngine's AudioTrack remains
                                                // the single audio clock, preventing echo/double-audio
                                                setVolume(0f, 0f)
                                                setOnVideoSizeChangedListener { _, vw, vh ->
                                                    if (vw > 0 && vh > 0) {
                                                        videoAspectRatio = vw.toFloat() / vh.toFloat()
                                                    }
                                                }
                                                setOnPreparedListener { preparedMp ->
                                                    if (isDisposed) return@setOnPreparedListener
                                                    isPrepared = true
                                                    if (currentPositionMs > 0L) {
                                                        preparedMp.seekTo(currentPositionMs.toInt())
                                                    }
                                                    if (currentIsPlaying) {
                                                        preparedMp.start()
                                                    }
                                                }
                                                setOnErrorListener { _, _, _ ->
                                                    isPrepared = false
                                                    true
                                                }
                                                prepareAsync()
                                            }
                                            mediaPlayer = mp
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            surface.release()
                                            surfaceRef = null
                                        }
                                    }

                                    override fun onSurfaceTextureSizeChanged(
                                        st: SurfaceTexture,
                                        width: Int,
                                        height: Int
                                    ) {}

                                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                        releaseMediaResources()
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                                }
                            }
                        },
                        update = {
                            if (isPrepared) {
                                val mp = mediaPlayer ?: return@AndroidView
                                try {
                                    val diff = kotlin.math.abs(mp.currentPosition - positionMs)
                                    if (diff > 600) {
                                        mp.seekTo(positionMs.toInt())
                                    }
                                    if (isPlaying) {
                                        if (!mp.isPlaying) mp.start()
                                    } else {
                                        if (mp.isPlaying) mp.pause()
                                    }
                                } catch (ignored: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(videoAspectRatio.coerceIn(0.5f, 2.5f), matchHeightConstraintsFirst = true)
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
