// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.ui.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VideoFrameSnapshot
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    sampleIntervalMs: Long = 250L,
    previousDescriptorProvider: (() -> VisualFrameDescriptor?)? = null,
    onFrameSampled: ((VideoFrameSnapshot) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentIntervalMs by rememberUpdatedState(sampleIntervalMs)
    val currentPrevProvider by rememberUpdatedState(previousDescriptorProvider)
    val currentOnFrameSampled by rememberUpdatedState(onFrameSampled)

    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var analysisRef by remember { mutableStateOf<ImageAnalysis?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val cameraProvider = context.getCameraProvider()
        cameraProviderRef = cameraProvider
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(320, 240),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysisRef = imageAnalysis

        var lastSampledAt = 0L
        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastSampledAt >= currentIntervalMs) {
                    lastSampledAt = nowMs
                    val previous = currentPrevProvider?.invoke()
                    val snapshot = VideoFrameAnalyzer.sampleYuvImageProxy(
                        image = imageProxy,
                        timestampMs = nowMs,
                        previous = previous
                    )
                    currentOnFrameSampled?.invoke(snapshot)
                }
            } finally {
                imageProxy.close()
            }
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                analysisRef?.clearAnalyzer()
                cameraProviderRef?.unbindAll()
            } catch (ignored: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(this)
            )
        }
    }
