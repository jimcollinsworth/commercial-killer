// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

data class TvCodeSet(
    val id: String,
    val brand: String,
    val name: String,
    val protocol: String,
    val carrierFrequency: Int,
    val address: Int,
    val muteCommand: Int,
    val unmuteCommand: Int = muteCommand,
    val prontoHex: String? = null
)

object IrCodeDatabase {

    val codeSets = listOf(
        TvCodeSet(
            id = "hisense_set_1",
            brand = "Hisense",
            name = "Hisense (Set 1 - Standard NEC)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x00,
            muteCommand = 0x0D,
            unmuteCommand = 0x0E
        ),
        TvCodeSet(
            id = "hisense_set_2",
            brand = "Hisense",
            name = "Hisense (Set 2 - Smart Android TV)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x10,
            unmuteCommand = 0x10
        ),
        TvCodeSet(
            id = "hisense_set_3",
            brand = "Hisense",
            name = "Hisense (Set 3 - Alternative NEC)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0xBF,
            muteCommand = 0x0D,
            unmuteCommand = 0x0D
        ),
        TvCodeSet(
            id = "hisense_set_4",
            brand = "Hisense",
            name = "Hisense (Set 4 - Roku TV)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x57,
            muteCommand = 0x1A,
            unmuteCommand = 0x1A
        ),
        TvCodeSet(
            id = "samsung_set_1",
            brand = "Samsung",
            name = "Samsung (Set 1 - 32-bit)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x07,
            muteCommand = 0x0F,
            unmuteCommand = 0x0F
        ),
        TvCodeSet(
            id = "lg_set_1",
            brand = "LG",
            name = "LG (Set 1 - Standard NEC)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x09,
            unmuteCommand = 0x09
        ),
        TvCodeSet(
            id = "sony_set_1",
            brand = "Sony",
            name = "Sony (Set 1 - Bravia SIRC)",
            protocol = "NEC",
            carrierFrequency = 40000,
            address = 0x01,
            muteCommand = 0x14,
            unmuteCommand = 0x14
        ),
        TvCodeSet(
            id = "vizio_set_1",
            brand = "Vizio",
            name = "Vizio (Set 1 - Standard)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x04,
            muteCommand = 0x0C,
            unmuteCommand = 0x0C
        ),
        TvCodeSet(
            id = "tcl_set_1",
            brand = "TCL",
            name = "TCL (Set 1 - Roku/Google TV)",
            protocol = "NEC",
            carrierFrequency = 38000,
            address = 0x57,
            muteCommand = 0x1A,
            unmuteCommand = 0x1A
        ),
        TvCodeSet(
            id = "custom_pronto",
            brand = "Custom",
            name = "Custom Pronto Hex Code",
            protocol = "PRONTO",
            carrierFrequency = 38000,
            address = 0,
            muteCommand = 0,
            prontoHex = "0000 006D 0022 0002 0157 00AC 0015 0040 0015 0040 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0040 0015 0040 0015 0015 0015 0040 0015 0040 0015 0040 0015 0040 0015 0040 0015 0015 0015 0015 0015 0040 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0015 0040 0015 0040 0015 0015 0015 0040 0015 0040 0015 0040 0015 0040 0015 0040 0015 0689 0157 0056 0015 0E94"
        )
    )

    fun getById(id: String): TvCodeSet? = codeSets.firstOrNull { it.id == id }
}
