package com.droidunplugged.nanobananaandorid.feature.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameSampled: (frameCount: Long, detectedObjects: List<String>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var frameCount by remember { mutableLongStateOf(0L) }
    var lastSampleTimeMs by remember { mutableLongStateOf(0L) }
    var currentLabels by remember { mutableStateOf<List<String>>(emptyList()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val cameraExecutor = Executors.newSingleThreadExecutor()
                    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        // ImageAnalysis configured for 1 FPS keyframe sampling + On-Device Vision ML
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val currentTime = System.currentTimeMillis()
                                    val mediaImage = imageProxy.image

                                    if (mediaImage != null && currentTime - lastSampleTimeMs >= 1000) {
                                        lastSampleTimeMs = currentTime
                                        frameCount++

                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )

                                        labeler.process(inputImage)
                                            .addOnSuccessListener { labels ->
                                                val detected = labels.take(5).map { 
                                                    "${it.text} (${(it.confidence * 100).toInt()}%)" 
                                                }
                                                currentLabels = detected
                                                onFrameSampled(frameCount, detected)
                                                Log.d("CameraPreview", "Frame #$frameCount detected: $detected")
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("CameraPreview", "ML Kit labeling error", e)
                                                onFrameSampled(frameCount, emptyList())
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
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
                        } catch (e: Exception) {
                            Log.e("CameraPreview", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // TV Screen Framing Reticle Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AIM AT TV SCREEN",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Top Status Badge (Live + FPS + Frame Count)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Red, RoundedCornerShape(50))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LIVE VISION (1 FPS)",
                    color = Color.White,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Frame: #$frameCount",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }

            // Bottom Overlay: Live Detected Objects Tag
            if (currentLabels.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = "👀 DETECTED IN SCENE:",
                            color = Color.Yellow,
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = currentLabels.joinToString(" • "),
                            color = Color.White,
                            fontSize = 11.sp,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            // Permission Request State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = "No Camera Permission",
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Camera permission required to watch video",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Icon(Icons.Default.Videocam, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Camera Access")
                }
            }
        }
    }
}
