// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProntoHexConverterTest {

    @Test
    fun testDecode_validProntoHex_success() {
        // Standard NEC 38 kHz Pronto Hex sample
        val prontoHex = "0000 006D 0002 0000 0157 00AC 0015 0040"
        val result = ProntoHexConverter.decode(prontoHex)

        assertTrue(result.isSuccess)
        val decoded = result.getOrNull()
        assertNotNull(decoded)

        // 0x006D = 109 -> Carrier freq approx 38000 Hz
        assertEquals(38000f, decoded!!.carrierFrequency.toFloat(), 100f)
        assertEquals(4, decoded.pattern.size)
    }

    @Test
    fun testDecode_shortString_failure() {
        val prontoHex = "0000 006D"
        val result = ProntoHexConverter.decode(prontoHex)
        assertTrue(result.isFailure)
    }

    @Test
    fun testIrCodeDatabase_presetsValid() {
        val sets = IrCodeDatabase.codeSets
        assertTrue(sets.size >= 8)

        val hisenseSet1 = IrCodeDatabase.getById("hisense_set_1")
        assertNotNull(hisenseSet1)
        assertEquals(38000, hisenseSet1!!.carrierFrequency)
        assertEquals(0x00, hisenseSet1.address)
        assertEquals(0x0D, hisenseSet1.muteCommand)

        val samsungSet1 = IrCodeDatabase.getById("samsung_set_1")
        assertNotNull(samsungSet1)
        assertEquals("Samsung", samsungSet1!!.brand)
    }
}
