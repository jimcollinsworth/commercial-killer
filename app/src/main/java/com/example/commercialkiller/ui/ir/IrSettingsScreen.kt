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
import com.example.commercialkiller.data.action.TargetDevice
import com.example.commercialkiller.data.action.TvControlManager
import com.example.commercialkiller.data.action.UsbDongleStatus
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

    val tvCodeSets = IrCodeDatabase.tvCodeSets
    val soundbarCodeSets = IrCodeDatabase.soundbarCodeSets

    var targetDevice by remember { mutableStateOf(manager.targetDevice) }
    var controlMethod by remember { mutableStateOf(manager.controlMethod) }
    var autoMuteEnabled by remember { mutableStateOf(manager.isAutoMuteEnabled) }
    var webhookUrl by remember { mutableStateOf(manager.webhookUrl) }

    var selectedTvIndex by remember { mutableIntStateOf(manager.selectedTvCodeSetIndex.coerceIn(0, tvCodeSets.size - 1)) }
    val selectedTvSet = tvCodeSets.getOrElse(selectedTvIndex) { tvCodeSets.first() }

    var selectedSoundbarIndex by remember { mutableIntStateOf(manager.selectedSoundbarCodeSetIndex.coerceIn(0, soundbarCodeSets.size - 1)) }
    val selectedSoundbarSet = soundbarCodeSets.getOrElse(selectedSoundbarIndex) { soundbarCodeSets.first() }

    var customTvProntoHex by remember {
        mutableStateOf(manager.customProntoHex.ifEmpty { selectedTvSet.prontoHex ?: "" })
    }
    var customSoundbarProntoHex by remember {
        mutableStateOf(manager.customSoundbarProntoHex.ifEmpty { selectedSoundbarSet.prontoHex ?: "" })
    }

    var tvDropdownExpanded by remember { mutableStateOf(false) }
    var soundbarDropdownExpanded by remember { mutableStateOf(false) }

    var usbDongleStatus by remember { mutableStateOf(manager.getUsbDongleStatus()) }

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
            // Header Bar with Back Icon & Hardware Status Badges
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
                        text = "TV & SOUNDBAR AUTOMATION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "WEBHOOK + TVIEW USB IR DUAL CONTROL",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8)
                    )
                }

                // Interactive USB / IR Hardware Status Badge
                val (badgeColor, badgeText, isActionable) = when (usbDongleStatus) {
                    UsbDongleStatus.READY -> Triple(Color(0xFF059669), "TVIEW USB READY", false)
                    UsbDongleStatus.PERMISSION_REQUIRED -> Triple(Color(0xFFD97706), "TVIEW (TAP TO GRANT)", true)
                    UsbDongleStatus.NOT_CONNECTED -> {
                        if (manager.hasInternalIr()) {
                            Triple(Color(0xFF059669), "INTERNAL IR", false)
                        } else {
                            Triple(Color(0xFF475569), "NO USB DONGLE", true)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(6.dp))
                        .clickable(enabled = isActionable) {
                            addLog("Scanning & requesting USB permission for Tview...")
                            manager.requestUsbPermission { granted ->
                                usbDongleStatus = manager.getUsbDongleStatus()
                                addLog("USB Permission result: ${if (granted) "GRANTED (Ready)" else "DENIED"}")
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Target Device Selector Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TARGET OUTPUT DEVICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TargetDevice.values().forEach { target ->
                            val isSelected = targetDevice == target
                            val label = when (target) {
                                TargetDevice.TV_ONLY -> "TV ONLY"
                                TargetDevice.SOUNDBAR_ONLY -> "SOUNDBAR"
                                TargetDevice.BOTH -> "BOTH (TV + SOUNDBAR)"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A))
                                    .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable {
                                        targetDevice = target
                                        manager.targetDevice = target
                                        addLog("Target Device changed: ${target.name}")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontSize = 10.sp,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Mute on Detection",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mutes target during commercials, restores volume on return",
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

            // TV Configuration Section (Visible when TV_ONLY or BOTH)
            if (targetDevice == TargetDevice.TV_ONLY || targetDevice == TargetDevice.BOTH) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "TV CONTROL METHOD & PRESETS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )

                        // TV Method Tabs (Webhook, IR, Both)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ControlMethod.values().forEach { method ->
                                val isSelected = controlMethod == method
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF0369A1) else Color(0xFF0F172A))
                                    .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(6.dp))
                                    .clickable {
                                        controlMethod = method
                                        manager.controlMethod = method
                                        addLog("TV Control Method: ${method.name}")
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = method.name,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Webhook URL Field
                        if (controlMethod == ControlMethod.WEBHOOK || controlMethod == ControlMethod.BOTH) {
                            OutlinedTextField(
                                value = webhookUrl,
                                onValueChange = {
                                    webhookUrl = it
                                    manager.webhookUrl = it
                                },
                                label = { Text("Smart TV Webhook URL (Hisense / Android TV)", fontSize = 10.sp) },
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

                        // TV IR Preset Dropdown
                        if (controlMethod == ControlMethod.IR || controlMethod == ControlMethod.BOTH) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                    .clickable { tvDropdownExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = selectedTvSet.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "TV Protocol: ${selectedTvSet.protocol} • ${selectedTvSet.carrierFrequency / 1000} kHz",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 9.sp
                                        )
                                    }
                                    Text(text = "▼", color = Color.LightGray, fontSize = 11.sp)
                                }

                                DropdownMenu(
                                    expanded = tvDropdownExpanded,
                                    onDismissRequest = { tvDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    tvCodeSets.forEachIndexed { index, codeSet ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        text = codeSet.name,
                                                        color = if (index == selectedTvIndex) Color(0xFF38BDF8) else Color.White,
                                                        fontWeight = if (index == selectedTvIndex) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "${codeSet.protocol} • ${codeSet.carrierFrequency / 1000} kHz",
                                                        color = Color.Gray,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedTvIndex = index
                                                manager.selectedTvCodeSetIndex = index
                                                tvDropdownExpanded = false
                                                if (codeSet.prontoHex != null) {
                                                    customTvProntoHex = codeSet.prontoHex
                                                    manager.customProntoHex = codeSet.prontoHex
                                                }
                                                addLog("Selected TV Set: ${codeSet.name}")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Soundbar Configuration Section (Visible when SOUNDBAR_ONLY or BOTH)
            if (targetDevice == TargetDevice.SOUNDBAR_ONLY || targetDevice == TargetDevice.BOTH) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SOUNDBAR IR CONFIGURATION (TVIEW USB DONGLE)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )

                        // Soundbar Preset Dropdown
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                                .clickable { soundbarDropdownExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = selectedSoundbarSet.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Soundbar Protocol: ${selectedSoundbarSet.protocol} • ${selectedSoundbarSet.carrierFrequency / 1000} kHz",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp
                                    )
                                }
                                Text(text = "▼", color = Color.LightGray, fontSize = 11.sp)
                            }

                            DropdownMenu(
                                expanded = soundbarDropdownExpanded,
                                onDismissRequest = { soundbarDropdownExpanded = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                soundbarCodeSets.forEachIndexed { index, codeSet ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = codeSet.name,
                                                    color = if (index == selectedSoundbarIndex) Color(0xFF38BDF8) else Color.White,
                                                    fontWeight = if (index == selectedSoundbarIndex) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = "${codeSet.protocol} • ${codeSet.carrierFrequency / 1000} kHz",
                                                    color = Color.Gray,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedSoundbarIndex = index
                                            manager.selectedSoundbarCodeSetIndex = index
                                            soundbarDropdownExpanded = false
                                            if (codeSet.prontoHex != null) {
                                                customSoundbarProntoHex = codeSet.prontoHex
                                                manager.customSoundbarProntoHex = codeSet.prontoHex
                                            }
                                            addLog("Selected Soundbar Set: ${codeSet.name}")
                                        }
                                    )
                                }
                            }
                        }

                        // Custom Pronto Hex Editor for Soundbar
                        if (selectedSoundbarSet.protocol == "PRONTO") {
                            OutlinedTextField(
                                value = customSoundbarProntoHex,
                                onValueChange = {
                                    customSoundbarProntoHex = it
                                    manager.customSoundbarProntoHex = it
                                },
                                label = { Text("Pronto Hex String (0000 006D ...)", fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
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
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COMMAND DISPATCH TESTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Target: ${targetDevice.name}",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Primary Test Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    addLog("Sending MUTE (${targetDevice.name})...")
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
                                    addLog("Sending UNMUTE (${targetDevice.name})...")
                                    val result = manager.sendUnmute()
                                    addLog("UNMUTE Result: ${result.message}")
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("TEST UNMUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Step Next Presets Icon Button
                        OutlinedButton(
                            onClick = {
                                if (targetDevice == TargetDevice.SOUNDBAR_ONLY) {
                                    selectedSoundbarIndex = (selectedSoundbarIndex + 1) % soundbarCodeSets.size
                                    manager.selectedSoundbarCodeSetIndex = selectedSoundbarIndex
                                    val nextSet = soundbarCodeSets[selectedSoundbarIndex]
                                    addLog("Stepped to Soundbar IR: ${nextSet.name}")
                                } else {
                                    selectedTvIndex = (selectedTvIndex + 1) % tvCodeSets.size
                                    manager.selectedTvCodeSetIndex = selectedTvIndex
                                    val nextSet = tvCodeSets[selectedTvIndex]
                                    addLog("Stepped to TV IR: ${nextSet.name}")
                                }
                            },
                            modifier = Modifier.height(44.dp)
                        ) {
                            StepNextIcon(tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        }
                    }

                    // Individual Diagnostic Buttons (When Target is BOTH)
                    if (targetDevice == TargetDevice.BOTH) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        addLog("Testing TV Mute individually...")
                                        val res = manager.executeTvAction("MUTE")
                                        addLog("TV Test: ${res.message}")
                                    }
                                },
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("TEST TV ONLY", fontSize = 10.sp, color = Color(0xFF38BDF8))
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        addLog("Testing Soundbar Mute individually...")
                                        val res = manager.executeSoundbarAction("MUTE")
                                        addLog("Soundbar Test: ${res.message}")
                                    }
                                },
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("TEST SOUNDBAR ONLY", fontSize = 10.sp, color = Color(0xFF38BDF8))
                            }
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
                            text = "Ready. Plug in Tview USB-C dongle & test commands above.",
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
