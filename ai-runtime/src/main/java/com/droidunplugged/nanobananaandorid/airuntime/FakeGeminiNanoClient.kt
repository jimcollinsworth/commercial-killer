package com.droidunplugged.nanobananaandorid.airuntime

import kotlinx.coroutines.delay
import javax.inject.Inject

class FakeGeminiNanoClient @Inject constructor() : GeminiNanoClient {
    override suspend fun generateContent(prompt: String): String {
        // Simulate on-device inference delay
        delay(800)

        // Extract detected objects if present in the vision prompt
        val objectsMatch = Regex("\\[(.*?)\\]").find(prompt)
        val objectsList = objectsMatch?.groupValues?.get(1) ?: "Television (95%), Screen (90%)"

        return when {
            prompt.contains("object", ignoreCase = true) || prompt.contains("see", ignoreCase = true) || prompt.contains("what", ignoreCase = true) && prompt.contains("in", ignoreCase = true) -> {
                val formattedItems = objectsList.split(", ").joinToString("\n") { "• $it" }
                "Based on real-time on-device camera analysis, here are the objects currently detected in your viewfinder:\n\n" +
                "$formattedItems\n\n" +
                "Confidence is high. TV screen area is being tracked continuously."
            }
            prompt.contains("mute", ignoreCase = true) || prompt.contains("commercial", ignoreCase = true) || prompt.contains("ad", ignoreCase = true) -> {
                "Commercial detected in video stream!\n" +
                "Frame analysis indicates commercial sponsor graphics ($objectsList).\n" +
                "Action: Automatically muting television audio.\n\n" +
                "[IR-ACTION] IR-Mute on 0x0ae (NEC Protocol, 38kHz)"
            }
            prompt.contains("power", ignoreCase = true) -> {
                "TV power command requested.\n" +
                "Action: Transmitting power toggle code.\n\n" +
                "[IR-ACTION] IR-Power on 0x20df10ef (NEC Protocol, 38kHz)"
            }
            prompt.contains("unmute", ignoreCase = true) -> {
                "Program content resumed. Unmuting television audio.\n\n" +
                "[IR-ACTION] IR-Unmute on 0x0ae (NEC Protocol, 38kHz)"
            }
            else -> {
                "Analyzing video stream: detected $objectsList.\n" +
                "Scene appears normal. No commercial detected. Monitoring continuous video feed."
            }
        }
    }
}
