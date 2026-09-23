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
        assertEquals(TargetDevice.BOTH, manager.targetDevice)
        assertEquals("http://192.168.1.100:8080/api/v1/remote/mute", manager.webhookUrl)
        assertEquals(ControlMethod.WEBHOOK, manager.controlMethod)
        assertEquals(0, manager.selectedTvCodeSetIndex)
        assertEquals(0, manager.selectedSoundbarCodeSetIndex)
        assertTrue(manager.isAutoMuteEnabled)
        assertFalse(manager.hasIrHardware())
    }

    @Test
    fun testGetSelectedTvCodeSet_default() {
        val codeSet = manager.getSelectedTvCodeSet()
        assertNotNull(codeSet)
        assertEquals("Hisense (Set 1 - Standard NEC)", codeSet.name)
        assertEquals(38000, codeSet.carrierFrequency)
        assertEquals(DeviceCategory.TV, codeSet.category)
    }

    @Test
    fun testGetSelectedSoundbarCodeSet_default() {
        val codeSet = manager.getSelectedSoundbarCodeSet()
        assertNotNull(codeSet)
        assertEquals("Vizio Soundbar (Standard)", codeSet.name)
        assertEquals(38000, codeSet.carrierFrequency)
        assertEquals(DeviceCategory.SOUNDBAR, codeSet.category)
    }

    @Test
    fun testDatabaseSeparation() {
        assertTrue(IrCodeDatabase.tvCodeSets.isNotEmpty())
        assertTrue(IrCodeDatabase.soundbarCodeSets.isNotEmpty())
        assertEquals(
            IrCodeDatabase.tvCodeSets.size + IrCodeDatabase.soundbarCodeSets.size,
            IrCodeDatabase.codeSets.size
        )
    }
}
