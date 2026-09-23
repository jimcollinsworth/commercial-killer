// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

enum class UsbDongleStatus {
    NOT_CONNECTED,
    PERMISSION_REQUIRED,
    READY
}

/**
 * Controller managing external USB-C Infrared Transceiver dongles (Tview / Tiqiaa / ElkSmart)
 * using the Android USB Host API (android.hardware.usb.UsbManager).
 *
 * Supported Hardware:
 * - Vendor ID: 0x10C4 (Silicon Labs) / 0x045E (OEM)
 * - Product ID: 0x8468 (Tview / Tiqiaa IR Transceiver)
 */
class UsbIrDongleController(
    private val context: Context?
) {
    private val usbManager: UsbManager? = context?.let {
        try {
            it.getSystemService(Context.USB_SERVICE) as? UsbManager
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Finds any connected USB IR blaster device matching the supported Vendor and Product IDs.
     */
    fun findDongleDevice(): UsbDevice? {
        val manager = usbManager ?: return null
        return try {
            manager.deviceList.values.firstOrNull { device ->
                isSupportedDevice(device)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating USB devices: ${e.message}")
            null
        }
    }

    /**
     * Returns true if a supported USB IR dongle is physically attached.
     */
    fun isDongleAttached(): Boolean = findDongleDevice() != null

    /**
     * Checks current connection and permission status of the USB IR dongle.
     */
    fun getStatus(): UsbDongleStatus {
        val device = findDongleDevice() ?: return UsbDongleStatus.NOT_CONNECTED
        val manager = usbManager ?: return UsbDongleStatus.NOT_CONNECTED
        return if (manager.hasPermission(device)) {
            UsbDongleStatus.READY
        } else {
            UsbDongleStatus.PERMISSION_REQUIRED
        }
    }

    /**
     * Requests user permission to access the connected USB IR dongle.
     */
    fun requestPermission(onPermissionResult: ((Boolean) -> Unit)? = null) {
        val ctx = context ?: return
        val manager = usbManager ?: return
        val device = findDongleDevice() ?: return

        if (manager.hasPermission(device)) {
            onPermissionResult?.invoke(true)
            return
        }

        val action = ACTION_USB_PERMISSION + ".${System.currentTimeMillis()}"
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(action),
            flags
        )

        // Register one-time receiver
        val filter = IntentFilter(action)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == action) {
                    try {
                        ctx.unregisterReceiver(this)
                    } catch (e: Exception) {
                        // ignore
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "USB Permission result: granted=$granted")
                    onPermissionResult?.invoke(granted)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(receiver, filter)
        }

        manager.requestPermission(device, permissionIntent)
    }

    /**
     * Transmits a TV/Soundbar code set via the Tview USB IR dongle.
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
     * Decodes a Pronto Hex string and transmits it via the USB IR dongle.
     */
    fun transmitProntoHex(prontoHex: String): Boolean {
        val decodedResult = ProntoHexConverter.decode(prontoHex)
        val decoded = decodedResult.getOrNull() ?: return false
        return transmitRawPattern(decoded.carrierFrequency, decoded.pattern)
    }

    /**
     * Transmits a raw microsecond timing pattern through the Tview USB IR transceiver.
     * Uses Tview framing (0xFF 0xFF 0xFF 0xFF header, carrier frequency, 56-byte chunking).
     */
    fun transmitRawPattern(carrierFrequency: Int, pattern: IntArray): Boolean {
        val manager = usbManager ?: return false
        val device = findDongleDevice() ?: return false

        if (!manager.hasPermission(device)) {
            Log.w(TAG, "Cannot transmit: USB permission not granted for ${device.deviceName}")
            return false
        }

        var connection: UsbDeviceConnection? = null
        var claimedInterface: UsbInterface? = null

        return try {
            connection = manager.openDevice(device) ?: run {
                Log.e(TAG, "Failed to open USB device connection")
                return false
            }

            // Find interface and bulk endpoints
            val (usbInterface, outEndpoint, inEndpoint) = findEndpoints(device) ?: run {
                Log.e(TAG, "Failed to locate bulk endpoints on USB device")
                return false
            }

            if (!connection.claimInterface(usbInterface, true)) {
                Log.e(TAG, "Failed to claim USB interface")
                return false
            }
            claimedInterface = usbInterface

            // 1. Send Handshake (0xFC 0xFC 0xFC 0xFC)
            val handshake = byteArrayOf(0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte(), 0xFC.toByte())
            connection.bulkTransfer(outEndpoint, handshake, handshake.size, TIMEOUT_MS)

            // Optional drain response from IN endpoint
            inEndpoint?.let { inEp ->
                val responseBuf = ByteArray(64)
                connection.bulkTransfer(inEp, responseBuf, responseBuf.size, 50)
            }

            // 2. Build Tview IR Packet Payload
            val packet = buildTviewPayload(carrierFrequency, pattern)

            // 3. Fragment packet into 56-byte chunks over bulk OUT endpoint
            var offset = 0
            var allSucceeded = true
            while (offset < packet.size) {
                val chunkSize = minOf(CHUNK_SIZE, packet.size - offset)
                val chunk = packet.copyOfRange(offset, offset + chunkSize)
                val written = connection.bulkTransfer(outEndpoint, chunk, chunk.size, TIMEOUT_MS)
                if (written < 0) {
                    Log.e(TAG, "Failed writing chunk at offset $offset (code: $written)")
                    allSucceeded = false
                    break
                }
                offset += chunkSize
            }

            allSucceeded
        } catch (e: Exception) {
            Log.e(TAG, "Exception during USB IR transmission: ${e.message}", e)
            false
        } finally {
            try {
                claimedInterface?.let { connection?.releaseInterface(it) }
                connection?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Builds the Tview proprietary IR packet structure from microsecond pulse timings.
     * Format:
     * - Header: 0xFF, 0xFF, 0xFF, 0xFF (4 bytes)
     * - Carrier Frequency: 3 bytes big-endian
     * - Payload word count: 2 bytes big-endian
     * - Pulses: 16-bit big-endian integers (microseconds)
     */
    fun buildTviewPayload(carrierFrequency: Int, rawPattern: IntArray): ByteArray {
        // Ensure even-length pattern for tail safety (pulse-space pairs)
        val adjustedPattern = if (rawPattern.size % 2 != 0) {
            val copy = IntArray(rawPattern.size + 1)
            System.arraycopy(rawPattern, 0, copy, 0, rawPattern.size)
            copy[rawPattern.size] = 10000 // 10ms tail space
            copy
        } else {
            rawPattern
        }

        val wordCount = adjustedPattern.size
        val payloadByteSize = 4 + 3 + 2 + (wordCount * 2)
        val buffer = ByteArray(payloadByteSize)

        // Header: 0xFF 0xFF 0xFF 0xFF
        buffer[0] = 0xFF.toByte()
        buffer[1] = 0xFF.toByte()
        buffer[2] = 0xFF.toByte()
        buffer[3] = 0xFF.toByte()

        // Carrier Frequency (3 bytes big-endian)
        val clampedFreq = carrierFrequency.coerceIn(10000, 100000)
        buffer[4] = ((clampedFreq shr 16) and 0xFF).toByte()
        buffer[5] = ((clampedFreq shr 8) and 0xFF).toByte()
        buffer[6] = (clampedFreq and 0xFF).toByte()

        // Word count (2 bytes big-endian)
        buffer[7] = ((wordCount shr 8) and 0xFF).toByte()
        buffer[8] = (wordCount and 0xFF).toByte()

        // Timings (16-bit big-endian microseconds)
        var idx = 9
        for (duration in adjustedPattern) {
            val clampedDuration = duration.coerceIn(1, 65535)
            buffer[idx++] = ((clampedDuration shr 8) and 0xFF).toByte()
            buffer[idx++] = (clampedDuration and 0xFF).toByte()
        }

        return buffer
    }

    private fun findEndpoints(device: UsbDevice): Triple<UsbInterface, UsbEndpoint, UsbEndpoint?>? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            var outEp: UsbEndpoint? = null
            var inEp: UsbEndpoint? = null

            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_OUT && outEp == null) {
                        outEp = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_IN && inEp == null) {
                        inEp = ep
                    }
                }
            }

            if (outEp != null) {
                return Triple(iface, outEp, inEp)
            }
        }
        return null
    }

    private fun isSupportedDevice(device: UsbDevice): Boolean {
        val vid = device.vendorId
        val pid = device.productId
        return (vid == VID_SILICON_LABS || vid == VID_MICROSOFT_OEM) && pid == PID_TVIEW_IR
    }

    private fun generateNecPattern(address: Int, command: Int): IntArray {
        val patternList = mutableListOf<Int>()
        patternList.add(9000)
        patternList.add(4500)

        val fullAddress = (address and 0xFF) or (((address.inv()) and 0xFF) shl 8)
        for (i in 0 until 16) {
            val bit = (fullAddress shr i) and 1
            patternList.add(562)
            patternList.add(if (bit == 1) 1687 else 562)
        }

        val fullCommand = (command and 0xFF) or (((command.inv()) and 0xFF) shl 8)
        for (i in 0 until 16) {
            val bit = (fullCommand shr i) and 1
            patternList.add(562)
            patternList.add(if (bit == 1) 1687 else 562)
        }

        patternList.add(562)
        return patternList.toIntArray()
    }

    companion object {
        private const val TAG = "UsbIrDongleController"
        private const val ACTION_USB_PERMISSION = "com.example.commercialkiller.USB_PERMISSION"

        const val VID_SILICON_LABS = 0x10C4 // 4292
        const val VID_MICROSOFT_OEM = 0x045E // 1118
        const val PID_TVIEW_IR = 0x8468 // 33896

        private const val CHUNK_SIZE = 56
        private const val TIMEOUT_MS = 1000
    }
}
