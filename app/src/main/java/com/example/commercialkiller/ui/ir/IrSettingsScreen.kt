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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.commercialkiller.data.action.ControlMethod
import com.example.commercialkiller.data.action.IrCodeDatabase
import com.example.commercialkiller.data.action.TvControlManager
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
    val manager = remember { TvControlManager(context) }
    val coroutineScope = rememberCoroutineScope()

    val codeSets = IrCodeDatabase.codeSets
    var selectedIndex by remember { mutableIntStateOf(manager.selectedCodeSetIndex.coerceIn(0, codeSets.size - 1)) }
    val selectedCodeSet = codeSets.getOrElse(selectedIndex) { codeSets.first() }

    var controlMethod by remember { mutableStateOf(manager.controlMethod) }
    var autoMuteEnabled by remember { mutableStateOf(manager.isAutoMuteEnabled) }
    var webhookUrl by remember { mutableStateOf(manager.webhookUrl) }
    var customProntoHex by remember {
        mutableStateOf(manager.customProntoHex.ifEmpty { selectedCodeSet.prontoHex ?: "" })
    }
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
                        text = "TV CONTROL & AUTOMATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "WEBHOOK & IR CODE CONFIGURATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                }

                // Hardware Status Badge
                val hasIr = manager.hasIrHardware()
                Box(
                    modifier = Modifier
                        .background(
                            if (hasIr) Color(0xFF059669) else Color(0xFF475569),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hasIr) "IR READY" else "IP / WEBHOOK",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Mode Selector & Auto-Mute Switch Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Method Selector Tabs
                    Text(
                        text = "ACTIVE CONTROL METHOD",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ControlMethod.values().forEach { method ->
                            val isSelected = controlMethod == method
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                    .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable {
                                        controlMethod = method
                                        manager.controlMethod = method
                                        addLog("Active Control Method: ${method.name}")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = method.name,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Auto-Mute Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto-Mute on Detection",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mutes TV during commercial breaks, unmutes on program return",
                                color = Color.LightGray,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = autoMuteEnabled,
                            onCheckedChange = {
                                autoMuteEnabled = it
                                manager.isAutoMuteEnabled = it
                                addLog("Auto-Mute on Detection: ${if (it) "ENABLED" else "DISABLED"}")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF059669)
                            )
                        )
                    }
                }
            }

            // Webhook Configuration Card (Visible when WEBHOOK or BOTH)
            if (controlMethod == ControlMethod.WEBHOOK || controlMethod == ControlMethod.BOTH) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SMART TV WEBHOOK URL (HISENSE / ANDROID TV)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = webhookUrl,
                            onValueChange = {
                                webhookUrl = it
                                manager.webhookUrl = it
                            },
                            label = { Text("Webhook URL", fontSize = 10.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
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

            // TV Code Set Selector Card (Visible when IR or BOTH)
            if (controlMethod == ControlMethod.IR || controlMethod == ControlMethod.BOTH) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "IR BRAND & CODE SET VERSION",
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
                                            manager.selectedCodeSetIndex = index
                                            dropdownExpanded = false
                                            if (codeSet.prontoHex != null) {
                                                customProntoHex = codeSet.prontoHex
                                                manager.customProntoHex = codeSet.prontoHex
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
                                onValueChange = {
                                    customProntoHex = it
                                    manager.customProntoHex = it
                                },
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
            }

            // Universal Tester & Pairing Wizard Card
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
                            text = "Mode: ${controlMethod.name} • Set ${selectedIndex + 1}/${codeSets.size}",
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
                                coroutineScope.launch {
                                    addLog("Sending MUTE via ${controlMethod.name}...")
                                    val result = manager.sendMute()
                                    addLog("MUTE Result: ${result.message}")
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                        ) {
                            Text("TEST MUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    addLog("Sending UNMUTE via ${controlMethod.name}...")
                                    val result = manager.sendUnmute()
                                    addLog("UNMUTE Result: ${result.message}")
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("TEST UNMUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                selectedIndex = (selectedIndex + 1) % codeSets.size
                                manager.selectedCodeSetIndex = selectedIndex
                                val nextSet = codeSets[selectedIndex]
                                if (nextSet.prontoHex != null) {
                                    customProntoHex = nextSet.prontoHex
                                    manager.customProntoHex = nextSet.prontoHex
                                }
                                addLog("Stepped to IR Set ${selectedIndex + 1}: ${nextSet.name}")
                            },
                            modifier = Modifier.height(44.dp)
                        ) {
                            StepNextIcon(tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
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
                            text = "Ready to test. Press TEST MUTE or TEST UNMUTE above.",
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
