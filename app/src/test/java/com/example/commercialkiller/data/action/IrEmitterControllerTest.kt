// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.6.
package com.example.commercialkiller.data.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IrEmitterControllerTest {

    private val controller = IrEmitterController(context = null)

    @Test
    fun testHasIrEmitter_nullContext_returnsFalse() {
        assertFalse(controller.hasIrEmitter())
    }

    @Test
    fun testGenerateNecPattern_validStructure() {
        // Address 0x00, Command 0x0D (MUTE)
        val pattern = controller.generateNecPattern(address = 0x00, command = 0x0D)

        // Structure:
        // - 1 leader mark (9000)
        // - 1 leader space (4500)
        // - 16 address bits (32 entries)
        // - 16 command bits (32 entries)
        // - 1 stop mark (562)
        // Total entries = 2 + 32 + 32 + 1 = 67 entries
        assertEquals(67, pattern.size)

        // Check leader mark and space
        assertEquals(9000, pattern[0])
        assertEquals(4500, pattern[1])

        // Check final stop mark
        assertEquals(562, pattern[pattern.size - 1])
    }

    @Test
    fun testTransmitMute_withoutHardware_returnsFalse() {
        val result = controller.transmitMute()
        assertFalse(result)
    }

    @Test
    fun testTransmitUnmute_withoutHardware_returnsFalse() {
        val result = controller.transmitUnmute()
        assertFalse(result)
    }
}
