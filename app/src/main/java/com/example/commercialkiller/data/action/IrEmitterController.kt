// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.6.
package com.example.commercialkiller.data.action

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Controller managing hardware Infrared (IR) emitter transmission via ConsumerIrManager
 * and network Webhook dispatching to Hisense Android TV.
 */
class IrEmitterController(
    private val context: Context? = null
) {
    private val irManager: ConsumerIrManager? = context?.let {
        try {
            it.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the host Android device features a hardware IR blaster.
     */
    fun hasIrEmitter(): Boolean {
        return try {
            irManager?.hasIrEmitter() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Transmits a standard 38 kHz NEC-protocol IR pulse burst for TV MUTE key.
     * NEC Protocol timing:
     * - Leader: 9000 us mark, 4500 us space
     * - Bits: 562 us mark + (1687 us space for '1', 562 us space for '0')
     * - Stop: 562 us mark
     */
    fun transmitMute(): Boolean {
        // Standard NEC TV MUTE Key Code (Address 0x00, Command 0x0D)
        val mutePattern = generateNecPattern(address = 0x00, command = 0x0D)
        return transmitRawPattern(carrierFrequency = 38000, pattern = mutePattern)
    }

    /**
     * Transmits a standard 38 kHz NEC-protocol IR pulse burst for TV UNMUTE key.
     */
    fun transmitUnmute(): Boolean {
        // Standard NEC TV UNMUTE Key Code (Address 0x00, Command 0x0E)
        val unmutePattern = generateNecPattern(address = 0x00, command = 0x0E)
        return transmitRawPattern(carrierFrequency = 38000, pattern = unmutePattern)
    }

    /**
     * Transmits an arbitrary raw microsecond timing pattern via ConsumerIrManager.
     */
    fun transmitRawPattern(carrierFrequency: Int, pattern: IntArray): Boolean {
        val manager = irManager ?: return false
        return try {
            if (manager.hasIrEmitter()) {
                manager.transmit(carrierFrequency, pattern)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sends an HTTP POST Webhook payload over local Wi-Fi to Hisense Android TV.
     */
    suspend fun triggerTvWebhook(
        webhookUrl: String,
        action: String = "MUTE"
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val url = URL(webhookUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true

            val jsonInputString = "{\"action\": \"$action\", \"source\": \"commercial-killer\"}"
            conn.outputStream.use { os ->
                val input = jsonInputString.toByteArray(charset("utf-8"))
                os.write(input, 0, input.size)
            }

            val responseCode = conn.responseCode
            conn.disconnect()
            Result.success(responseCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Helper generating standard NEC protocol IR pulse timing array (microseconds).
     */
    fun generateNecPattern(address: Int, command: Int): IntArray {
        val patternList = mutableListOf<Int>()
        
        // 9ms leader mark, 4.5ms space
        patternList.add(9000)
        patternList.add(4500)

        // 16-bit address (address + inverted address)
        val fullAddress = (address and 0xFF) or (((address.inv()) and 0xFF) shl 8)
        appendBits(patternList, fullAddress, 16)

        // 16-bit command (command + inverted command)
        val fullCommand = (command and 0xFF) or (((command.inv()) and 0xFF) shl 8)
        appendBits(patternList, fullCommand, 16)

        // Final stop bit (562us mark)
        patternList.add(562)

        return patternList.toIntArray()
    }

    private fun appendBits(list: MutableList<Int>, value: Int, bitCount: Int) {
        for (i in 0 until bitCount) {
            val bit = (value shr i) and 1
            list.add(562) // Bit mark
            if (bit == 1) {
                list.add(1687) // Logical 1 space
            } else {
                list.add(562)  // Logical 0 space
            }
        }
    }
}
