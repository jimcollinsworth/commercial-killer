// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Controller managing hardware Infrared (IR) emitter transmission via ConsumerIrManager
 * or external Tview USB-C IR dongle, and network Webhook dispatching to Hisense Android TV.
 */
class IrEmitterController(
    private val context: Context? = null
) {
    val usbDongleController = UsbIrDongleController(context)

    private val irManager: ConsumerIrManager? = context?.let {
        try {
            it.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if the host Android device features any hardware IR capability
     * (either internal ConsumerIrManager or external USB Tview dongle).
     */
    fun hasIrEmitter(): Boolean {
        return hasInternalIr() || hasUsbDongle()
    }

    /**
     * Checks if device has internal ConsumerIrManager blaster hardware.
     */
    fun hasInternalIr(): Boolean {
        return try {
            irManager?.hasIrEmitter() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if external USB-C Tview IR dongle is attached.
     */
    fun hasUsbDongle(): Boolean = usbDongleController.isDongleAttached()

    /**
     * Gets status of USB-C Tview IR dongle.
     */
    fun getUsbDongleStatus(): UsbDongleStatus = usbDongleController.getStatus()

    /**
     * Requests USB permission for the attached Tview dongle.
     */
    fun requestUsbPermission(onResult: ((Boolean) -> Unit)? = null) {
        usbDongleController.requestPermission(onResult)
    }

    /**
     * Transmits a TV or Soundbar code set for MUTE or UNMUTE.
     */
    fun transmitCodeSet(codeSet: TvCodeSet, isMute: Boolean = true): Boolean {
        return if (codeSet.protocol == "PRONTO" && !codeSet.prontoHex.isNullOrBlank()) {
            transmitProntoHex(codeSet.prontoHex)
        } else {
            val cmd = if (isMute) codeSet.muteCommand else codeSet.unmuteCommand
            val pattern = generateNecPattern(codeSet.address, cmd)
            transmitRawPattern(codeSet.carrierFrequency, pattern)
        }
    }

    /**
     * Decodes a Pronto Hex string and transmits it via internal IR or USB dongle.
     */
    fun transmitProntoHex(prontoHex: String): Boolean {
        val decodedResult = ProntoHexConverter.decode(prontoHex)
        val decoded = decodedResult.getOrNull() ?: return false
        return transmitRawPattern(decoded.carrierFrequency, decoded.pattern)
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
     * Transmits an arbitrary raw microsecond timing pattern via internal IR or USB dongle.
     */
    fun transmitRawPattern(carrierFrequency: Int, pattern: IntArray): Boolean {
        // 1. Try internal IR if available
        val manager = irManager
        if (manager != null) {
            try {
                if (manager.hasIrEmitter()) {
                    manager.transmit(carrierFrequency, pattern)
                    return true
                }
            } catch (e: Exception) {
                // continue to USB dongle
            }
        }

        // 2. Try external USB IR dongle if attached
        if (usbDongleController.isDongleAttached()) {
            return usbDongleController.transmitRawPattern(carrierFrequency, pattern)
        }

        return false
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
