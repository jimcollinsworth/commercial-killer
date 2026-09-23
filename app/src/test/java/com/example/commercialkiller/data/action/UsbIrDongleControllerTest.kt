// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class UsbIrDongleControllerTest {

    private val controller = UsbIrDongleController(context = null)

    @Test
    fun testInitialStatus_withoutContext_returnsNotConnected() {
        assertFalse(controller.isDongleAttached())
        assertEquals(UsbDongleStatus.NOT_CONNECTED, controller.getStatus())
    }

    @Test
    fun testBuildTviewPayload_validHeaderAndFrequency() {
        val carrierFreq = 38000
        val rawPattern = intArrayOf(9000, 4500, 562, 1687) // 4 entries (even)
        val payload = controller.buildTviewPayload(carrierFreq, rawPattern)

        assertNotNull(payload)
        // Expected size: 4 (header) + 3 (freq) + 2 (word count) + (4 * 2) = 17 bytes
        assertEquals(17, payload.size)

        // Check header: 0xFF 0xFF 0xFF 0xFF
        assertEquals(0xFF.toByte(), payload[0])
        assertEquals(0xFF.toByte(), payload[1])
        assertEquals(0xFF.toByte(), payload[2])
        assertEquals(0xFF.toByte(), payload[3])

        // Check carrier frequency: 38000 = 0x009470
        assertEquals(0x00.toByte(), payload[4])
        assertEquals(0x94.toByte(), payload[5])
        assertEquals(0x70.toByte(), payload[6])

        // Check word count: 4 = 0x0004
        assertEquals(0x00.toByte(), payload[7])
        assertEquals(0x04.toByte(), payload[8])

        // Check first duration: 9000 = 0x2328
        assertEquals(0x23.toByte(), payload[9])
        assertEquals(0x28.toByte(), payload[10])
    }

    @Test
    fun testBuildTviewPayload_tailSafetyOddLengthPadding() {
        val carrierFreq = 38000
        val oddPattern = intArrayOf(9000, 4500, 562) // 3 entries (odd)
        val payload = controller.buildTviewPayload(carrierFreq, oddPattern)

        // Should be padded to 4 entries (word count = 4)
        // Size: 4 + 3 + 2 + (4 * 2) = 17 bytes
        assertEquals(17, payload.size)
        assertEquals(0x04.toByte(), payload[8])

        // Check padded tail duration: 10000us = 0x2710
        assertEquals(0x27.toByte(), payload[15])
        assertEquals(0x10.toByte(), payload[16])
    }
}
