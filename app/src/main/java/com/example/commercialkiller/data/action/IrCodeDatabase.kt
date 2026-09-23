// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

enum class DeviceCategory {
    TV,
    SOUNDBAR
}

data class TvCodeSet(
    val id: String,
    val brand: String,
    val name: String,
    val protocol: String,
    val carrierFrequency: Int,
    val address: Int,
    val muteCommand: Int,
    val unmuteCommand: Int = muteCommand,
    val prontoHex: String? = null,
    val category: DeviceCategory = DeviceCategory.TV
)

object IrCodeDatabase {

    val tvCodeSets = listOf(
        TvCodeSet(
            id = "hisense_set_1",
            brand = "Hisense",
            name = "Hisense (Set 1 - Standard NEC)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x00,
            muteCommand = 0x0D,
            unmuteCommand = 0x0E,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "hisense_set_2",
            brand = "Hisense",
            name = "Hisense (Set 2 - Smart Android TV)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x10,
            unmuteCommand = 0x10,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "hisense_set_3",
            brand = "Hisense",
            name = "Hisense (Set 3 - Alternative NEC)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0xBF,
            muteCommand = 0x0D,
            unmuteCommand = 0x0D,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "hisense_set_4",
            brand = "Hisense",
            name = "Hisense (Set 4 - Roku TV)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x57,
            muteCommand = 0x1A,
            unmuteCommand = 0x1A,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "samsung_set_1",
            brand = "Samsung",
            name = "Samsung TV (Set 1 - 32-bit)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x07,
            muteCommand = 0x0F,
            unmuteCommand = 0x0F,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "lg_set_1",
            brand = "LG",
            name = "LG TV (Set 1 - Standard NEC)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x09,
            unmuteCommand = 0x09,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "sony_set_1",
            brand = "Sony",
            name = "Sony TV (Set 1 - Bravia SIRC)",
            protocol = "NEC",
            carrierFrequency = 40000,
            address = 0x01,
            muteCommand = 0x14,
            unmuteCommand = 0x14,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "vizio_set_1",
            brand = "Vizio",
            name = "Vizio TV (Set 1 - Standard)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x0C,
            unmuteCommand = 0x0C,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "tcl_set_1",
            brand = "TCL",
            name = "TCL TV (Set 1 - Roku/Google TV)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x57,
            muteCommand = 0x1A,
            unmuteCommand = 0x1A,
            category = DeviceCategory.TV
        ),
        TvCodeSet(
            id = "custom_pronto",
            brand = "Custom",
            name = "Custom TV Pronto Hex Code",
            protocol = "PRONTO",
            carrierFrequency = 38000,
            address = 0,
            muteCommand = 0,
            prontoHex = "0000 006D 0022 0002 0157 00AC 0015 0040 0015 0040 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0040 0015 0040 0015 0015 0015 0040 0015 0040 0015 0040 0015 0040 0015 0040 0015 0015 0015 0015 0015 0040 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0040 0015 0040 0015 0015 0015 0040 0015 0040 0015 0040 0015 0040 0015 0040 0015 0689 0157 0056 0015 0E94",
            category = DeviceCategory.TV
        )
    )

    val soundbarCodeSets = listOf(
        TvCodeSet(
            id = "sb_vizio_1",
            brand = "Vizio",
            name = "Vizio Soundbar (Standard)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x0C,
            unmuteCommand = 0x0C,
            category = DeviceCategory.SOUNDBAR
        ),
        TvCodeSet(
            id = "sb_samsung_1",
            brand = "Samsung",
            name = "Samsung Soundbar (HW Series)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x07,
            muteCommand = 0x0F,
            unmuteCommand = 0x0F,
            category = DeviceCategory.SOUNDBAR
        ),
        TvCodeSet(
            id = "sb_bose_1",
            brand = "Bose",
            name = "Bose Solo / CineMate Soundbar",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x55,
            muteCommand = 0x14,
            unmuteCommand = 0x14,
            category = DeviceCategory.SOUNDBAR
        ),
        TvCodeSet(
            id = "sb_lg_1",
            brand = "LG",
            name = "LG Soundbar (SK/SN/SP Series)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x09,
            unmuteCommand = 0x09,
            category = DeviceCategory.SOUNDBAR
        ),
        TvCodeSet(
            id = "sb_sony_1",
            brand = "Sony",
            name = "Sony Soundbar (HT Series)",
            protocol = "NEC",
            carrierFrequency = 40000,
            address = 0x01,
            muteCommand = 0x14,
            unmuteCommand = 0x14,
            category = DeviceCategory.SOUNDBAR
        ),
        TvCodeSet(
            id = "sb_yamaha_1",
            brand = "Yamaha",
            name = "Yamaha Soundbar (YAS/ATS Series)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x7E,
            muteCommand = 0x3C,
            unmuteCommand = 0x3C,
            category = DeviceCategory.SOUNDBAR
        ),
        TvCodeSet(
            id = "sb_polk_1",
            brand = "Polk",
            name = "Polk Audio Soundbar (Signa Series)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x02,
            muteCommand = 0x14,
            unmuteCommand = 0x14,
            category = DeviceCategory.SOUNDBAR
        ),
        TvCodeSet(
            id = "sb_custom_pronto",
            brand = "Custom",
            name = "Custom Soundbar Pronto Hex",
            protocol = "PRONTO",
            carrierFrequency = 38000,
            address = 0,
            muteCommand = 0,
            prontoHex = "0000 006D 0022 0002 0157 00AC 0015 0040 0015 0040 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0040 0015 0040 0015 0015 0015 0040 0015 0040 0015 0040 0015 0040 0015 0040 0015 0015 0015 0015 0015 0040 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0040 0015 0040 0015 0015 0015 0040 0015 0040 0015 0040 0015 0040 0015 0040 0015 0689 0157 0056 0015 0E94",
            category = DeviceCategory.SOUNDBAR
        )
    )

    // Combined list for backward compatibility
    val codeSets: List<TvCodeSet> = tvCodeSets + soundbarCodeSets

    fun getById(id: String): TvCodeSet? = codeSets.firstOrNull { it.id == id }
}
