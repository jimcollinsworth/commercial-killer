// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.6.
package com.example.commercialkiller.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
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

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("BACK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        text = "Commercial Killer is an on-device experimentation workbench designed for real-time audio and video broadcast processing. It operates 100% locally on phone hardware with zero cloud API dependencies, ensuring low latency, high privacy, and full offline functionality.",
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
                        title = "1. 40-Band Mel-Spectrogram Calculation",
                        description = "Ingests 16 kHz PCM audio chunks and calculates 40 Mel-frequency bin energies at a configurable interval (default 100 ms) using Cooley-Tukey FFT."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    HelpSubItem(
                        title = "2. Spectrogram Distance Comparison (Δ)",
                        description = "Computes matrix distance (Euclidean, Cosine, MSE) between consecutive spectrogram frames. When shift distance Δ exceeds threshold τ, an acoustic boundary or commercial transition event is triggered."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    HelpSubItem(
                        title = "3. Parallel Audio Classifier (Hugging Face / TFLite)",
                        description = "Runs open-weights audio classification models (YAMNet, AST, Wav2Vec2) in parallel to classify speech, music, silence dips, and commercial pods."
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
                        description = "Sends 38 kHz NEC carrier frequency pulse bursts via Android's native ConsumerIrManager service to directly mute and unmute television sets."
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
                        description = "Generates realistic acoustic TV broadcast simulations: multi-formant speech (F1/F2/F3), 200 ms silence gaps, and compressed commercial jingle pods."
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    HelpSubItem(
                        title = "• MIC (Live Microphone)",
                        description = "Captures live ambient audio from device microphone for real-time room testing against TV speakers."
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    HelpSubItem(
                        title = "• FILE (Local Multi-Format Audio)",
                        description = "Decodes and plays back local audio files (WAV, MP3, AAC, M4A, FLAC, OGG, OPUS) for precise benchmark testing."
                    )
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
