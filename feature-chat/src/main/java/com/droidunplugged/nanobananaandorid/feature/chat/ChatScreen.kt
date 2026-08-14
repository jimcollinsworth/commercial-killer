package com.droidunplugged.nanobananaandorid.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.droidunplugged.nanobananaandorid.feature.chat.components.CameraPreview
import com.droidunplugged.nanobananaandorid.feature.chat.components.SystemDiagnosticsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onShowAgentObservability: () -> Unit
) {
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isGenerating by viewModel.isGenerating.collectAsState()
    val useRealNano by viewModel.useRealNano.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var sampledFrameCount by remember { mutableLongStateOf(0L) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showSettingsDialog) {
        SystemDiagnosticsDialog(
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Commercial Killer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (useRealNano) "Real NPU" else "Simulated",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (useRealNano) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = useRealNano,
                            onCheckedChange = { viewModel.setUseRealNano(it) },
                            modifier = Modifier.scale(0.8f)
                        )
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings & Diagnostics",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onShowAgentObservability) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Observability",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ==========================================
            // TOP PANEL (50% Height): Live Camera Viewfinder
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFrameSampled = { count, labels ->
                        sampledFrameCount = count
                        viewModel.updateDetectedObjects(labels)
                    }
                )
            }

            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // ==========================================
            // BOTTOM PANEL (50% Height): Generated Stream & IR Action Feed
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                // Header Bar for Output Panel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Agent Output & IR Signals",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(
                        onClick = { viewModel.clearHistory() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }

                // Stream / Message Feed List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Aim camera at TV screen and tap an action below.\nAgent will watch video, reason, and emit IR commands.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(messages) { msg ->
                            MessageBubble(
                                text = msg.text,
                                isUser = msg.isFromUser
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    if (isGenerating) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Agent evaluating video frame #$sampledFrameCount...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Quick Action Chips Row (3 uncluttered buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            viewModel.askAgent("What objects do you see in the video frame?")
                        },
                        label = { Text("👀 What do you see?", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )

                    AssistChip(
                        onClick = {
                            viewModel.askAgent("Detect Commercial in frame #$sampledFrameCount")
                        },
                        label = { Text("⚡ Detect & Mute", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )

                    AssistChip(
                        onClick = {
                            viewModel.askAgent("Unmute TV (program resumed)")
                        },
                        label = { Text("⚡ Unmute", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Prompt Input Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Prompt agent...", fontSize = 13.sp) },
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val text = inputText
                                inputText = ""
                                viewModel.askAgent(text)
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                            .size(44.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(text: String, isUser: Boolean) {
    val isIrAction = text.contains("[IR-ACTION]")
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (isIrAction) {
            // Highlighted IR Action Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TRANSMITTED IR SIGNAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            // Standard Chat Bubble
            val bgColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
            val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = text,
                    color = textColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.padding(0.dp)
)
