// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.6.
// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Argon.
package com.example.commercialkiller.ui.help

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.commercialkiller.ui.components.BackIcon

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        BackIcon(tint = Color.White)
                    }

                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            text = "SYSTEM HELP & DETAILS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "ARCHITECTURE, SIGNAL PIPELINES & HARDWARE ACTIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                // Overview Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "PROJECT OVERVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Commercial Killer is an on-device audio and video broadcast processing workbench. All signal transforms, audio classification, and frame decisions run locally on Android hardware with zero cloud API dependencies and zero generative LLM calls.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                // Engine & Signal Processing Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "SIGNAL PROCESSING & FEATURE DETECTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "1. Mel-Spectrogram Calculation (40–128 Bands)",
                            description = "Ingests 16 kHz PCM audio chunks and calculates 40 to 128 Mel-frequency bin energies (default 80 bands) at a configurable interval (default 100 ms) using Cooley-Tukey FFT."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "2. Spectrogram Distance Comparison (Δ)",
                            description = "Computes matrix distance (Euclidean, Cosine, MSE) between consecutive spectrogram frames. When shift distance Δ exceeds threshold τ, an acoustic boundary event is logged."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "3. Parallel Audio Classifier (LiteRT / TFLite)",
                            description = "Runs on-device audio classification in parallel to score speech, music, singing, silence dips, and commercial tags."
                        )
                    }
                }

                // On-Device Fast Decision Model Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ON-DEVICE FAST DECISION MODEL (FrameDecisionEngine)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "1. Per-Frame Commercial Query (is_commercial)",
                            description = "Evaluates each 100 ms audio frame via a non-autoregressive discriminator combining classifier tag scores, Mel-band loudness compression density and variance, shift distance Δ, and cut cadence into a clamped 0.00–1.00 commercial score."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "2. Per-Frame Scene Continuity Query (is_same_scene)",
                            description = "Computes cosine similarity between the current Mel-spectrogram vector F_t and previous frame vector F_{t-1}: dot(F_t, F_{t-1}) / (||F_t|| * ||F_{t-1}||). Frames below the Scene Cut Similarity Threshold (0.50–0.98, default 0.85) register a scene cut."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "3. Shot Duration & Rolling Cut Cadence",
                            description = "Tracks elapsed milliseconds since the last scene cut (shot duration) and counts scene cuts over a rolling 60,000 ms window (cuts/min) using pre-allocated float frame buffers."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "4. MUTE / UNMUTE / HOLD Action Gating",
                            description = "Applies consecutive-frame hysteresis before dispatching TV control signals: emits MUTE on commercial scoring with a scene cut or 3 consecutive commercial frames, UNMUTE after 5 consecutive program frames, and HOLD otherwise."
                        )
                    }
                }

                // Hardware IR & Webhooks Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "HARDWARE ACTIONS: IR & WEBHOOKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "1. Consumer IR Blaster (Mute / Unmute)",
                            description = "Sends 38 kHz NEC carrier frequency pulse bursts via Android's ConsumerIrManager service to directly mute and unmute television sets."
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "2. Hisense Android TV Webhooks",
                            description = "Dispatches HTTP POST REST payloads over local Wi-Fi to smart TV IP control endpoints (e.g. http://<tv-ip>:8080/api/v1/remote/mute) when IR blasters are unavailable."
                        )
                    }
                }

                // Data Sources Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "WORKBENCH DATA INPUT MODES",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        HelpSubItem(
                            title = "• SYNTH (Simulated Broadcast)",
                            description = "Synthesizes acoustic TV broadcast test waveforms: multi-formant speech (F1/F2/F3), 200 ms silence gaps, and compressed commercial jingle pods."
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        HelpSubItem(
                            title = "• MIC (Live Microphone)",
                            description = "Captures live ambient audio from device microphone for room testing against TV speakers."
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        HelpSubItem(
                            title = "• FILE (Local Multi-Format Audio & Video)",
                            description = "Decodes and plays local audio and video files (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS, MP4) for benchmark testing."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpSubItem(title: String, description: String) {
    Column {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = description,
            fontSize = 11.sp,
            color = Color.LightGray,
            lineHeight = 16.sp
        )
    }
}
