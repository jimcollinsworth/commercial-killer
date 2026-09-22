// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvControlManagerTest {

    private val manager = TvControlManager(context = null)

    @Test
    fun testDefaultSettings() {
        assertEquals("http://192.168.1.100:8080/api/v1/remote/mute", manager.webhookUrl)
        assertEquals(ControlMethod.WEBHOOK, manager.controlMethod)
        assertEquals(0, manager.selectedCodeSetIndex)
        assertTrue(manager.isAutoMuteEnabled)
        assertFalse(manager.hasIrHardware())
    }

    @Test
    fun testGetSelectedCodeSet_default() {
        val codeSet = manager.getSelectedCodeSet()
        assertNotNull(codeSet)
        assertEquals("Hisense (Set 1 - Standard NEC)", codeSet.name)
        assertEquals(38000, codeSet.carrierFrequency)
    }

    @Test
    fun testGetSelectedCodeSet_withCustomPronto() {
        manager.customProntoHex = "0000 006D 0022 0002 0157 00AC"
        val codeSet = manager.getSelectedCodeSet()
        assertNotNull(codeSet)
    }
}
