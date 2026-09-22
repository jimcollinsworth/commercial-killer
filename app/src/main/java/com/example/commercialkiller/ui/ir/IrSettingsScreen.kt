// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.ui.ir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.commercialkiller.data.action.IrCodeDatabase
import com.example.commercialkiller.data.action.IrEmitterController
import com.example.commercialkiller.data.action.TvCodeSet
import com.example.commercialkiller.ui.components.BackIcon
import com.example.commercialkiller.ui.components.StepNextIcon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IrSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val controller = remember { IrEmitterController(context) }
    val coroutineScope = rememberCoroutineScope()

    val codeSets = IrCodeDatabase.codeSets
    var selectedIndex by remember { mutableIntStateOf(0) }
    val selectedCodeSet = codeSets.getOrElse(selectedIndex) { codeSets.first() }

    var customProntoHex by remember {
        mutableStateOf(selectedCodeSet.prontoHex ?: "")
    }
    var webhookUrl by remember { mutableStateOf("http://192.168.1.100:8080/api/v1/remote/mute") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val logs = remember { mutableStateListOf<String>() }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    fun addLog(msg: String) {
        logs.add(0, "[${dateFormat.format(Date())}] $msg")
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar with Back Icon
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

                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = "IR & TV CONTROL SETTINGS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "CODE SET SELECTOR & HARDWARE TESTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                }

                // Hardware Status Badge
                val hasIr = controller.hasIrEmitter()
                Box(
                    modifier = Modifier
                        .background(
                            if (hasIr) Color(0xFF059669) else Color(0xFFD97706),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasIr) "IR READY" else "NO IR BLASTER",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // TV Code Set Selector Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "TV BRAND & CODE SET VERSION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Dropdown Anchor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = selectedCodeSet.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Protocol: ${selectedCodeSet.protocol} • ${selectedCodeSet.carrierFrequency / 1000} kHz",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp
                                )
                            }
                            Text(text = "▼", color = Color.LightGray, fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            codeSets.forEachIndexed { index, codeSet ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = codeSet.name,
                                                color = if (index == selectedIndex) Color(0xFF38BDF8) else Color.White,
                                                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "${codeSet.protocol} • ${codeSet.carrierFrequency / 1000} kHz",
                                                color = Color.Gray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedIndex = index
                                        dropdownExpanded = false
                                        if (codeSet.prontoHex != null) {
                                            customProntoHex = codeSet.prontoHex
                                        }
                                        addLog("Selected code set: ${codeSet.name}")
                                    }
                                )
                            }
                        }
                    }

                    // Custom Pronto Hex Editor (when PRONTO selected)
                    if (selectedCodeSet.protocol == "PRONTO") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customProntoHex,
                            onValueChange = { customProntoHex = it },
                            label = { Text("Pronto Hex String (0000 006D ...)", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray
                            )
                        )
                    }
                }
            }

            // Tester & Step Wizard Card
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
                            text = "TESTER & PAIRING WIZARD",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Set ${selectedIndex + 1} of ${codeSets.size}",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Test Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val set = if (selectedCodeSet.protocol == "PRONTO") {
                                    selectedCodeSet.copy(prontoHex = customProntoHex)
                                } else selectedCodeSet
                                val sent = controller.transmitCodeSet(set, isMute = true)
                                addLog("Sent MUTE (${set.name}). Success: $sent")
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Text("TEST MUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val set = if (selectedCodeSet.protocol == "PRONTO") {
                                    selectedCodeSet.copy(prontoHex = customProntoHex)
                                } else selectedCodeSet
                                val sent = controller.transmitCodeSet(set, isMute = false)
                                addLog("Sent UNMUTE (${set.name}). Success: $sent")
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("TEST UNMUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedIndex = (selectedIndex + 1) % codeSets.size
                                val nextSet = codeSets[selectedIndex]
                                if (nextSet.prontoHex != null) customProntoHex = nextSet.prontoHex
                                addLog("Stepped to: ${nextSet.name}")
                            },
                            modifier = Modifier.height(44.dp)
                        ) {
                            StepNextIcon(tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Smart TV Webhook Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "HISENSE ANDROID TV WEBHOOK",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = webhookUrl,
                            onValueChange = { webhookUrl = it },
                            label = { Text("Webhook URL", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0284C7),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray
                            )
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    addLog("Dispatching HTTP POST to $webhookUrl...")
                                    val res = controller.triggerTvWebhook(webhookUrl, "MUTE")
                                    if (res.isSuccess) {
                                        addLog("Webhook Success! HTTP ${res.getOrNull()}")
                                    } else {
                                        addLog("Webhook Error: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            modifier = Modifier.height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Text("TEST", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Action Log Console
            Text(
                text = "ACTION LOG CONSOLE",
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
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "Awaiting IR transmission or webhook action...",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    items(logs) { logEntry ->
                        Text(
                            text = logEntry,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
