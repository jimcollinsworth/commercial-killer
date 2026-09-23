// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.action

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ControlMethod {
    WEBHOOK,
    IR,
    BOTH
}

enum class TargetDevice {
    TV_ONLY,
    SOUNDBAR_ONLY,
    BOTH
}

data class TvActionResult(
    val success: Boolean,
    val method: ControlMethod,
    val action: String,
    val message: String
)

/**
 * Manages persistent TV and Soundbar control settings (Webhook URL, IR Code Sets, Auto-Mute toggles)
 * and unifies dispatching mute/unmute actions across Webhook, Internal IR, and Tview USB-C IR blaster channels.
 */
class TvControlManager(
    private val context: Context?
) {
    private val irController = IrEmitterController(context)
    private val prefs: SharedPreferences? = context?.getSharedPreferences("tv_control_prefs", Context.MODE_PRIVATE)

    var targetDevice: TargetDevice
        get() {
            val name = prefs?.getString(KEY_TARGET_DEVICE, TargetDevice.BOTH.name) ?: TargetDevice.BOTH.name
            return try {
                TargetDevice.valueOf(name)
            } catch (e: Exception) {
                TargetDevice.BOTH
            }
        }
        set(value) {
            prefs?.edit()?.putString(KEY_TARGET_DEVICE, value.name)?.apply()
        }

    var webhookUrl: String
        get() = prefs?.getString(KEY_WEBHOOK_URL, "http://192.168.1.100:8080/api/v1/remote/mute") ?: "http://192.168.1.100:8080/api/v1/remote/mute"
        set(value) {
            prefs?.edit()?.putString(KEY_WEBHOOK_URL, value.trim())?.apply()
        }

    var controlMethod: ControlMethod
        get() {
            val name = prefs?.getString(KEY_CONTROL_METHOD, ControlMethod.WEBHOOK.name) ?: ControlMethod.WEBHOOK.name
            return try {
                ControlMethod.valueOf(name)
            } catch (e: Exception) {
                ControlMethod.WEBHOOK
            }
        }
        set(value) {
            prefs?.edit()?.putString(KEY_CONTROL_METHOD, value.name)?.apply()
        }

    var selectedTvCodeSetIndex: Int
        get() = prefs?.getInt(KEY_CODE_SET_INDEX, 0) ?: 0
        set(value) {
            prefs?.edit()?.putInt(KEY_CODE_SET_INDEX, value)?.apply()
        }

    // Retain legacy alias
    var selectedCodeSetIndex: Int
        get() = selectedTvCodeSetIndex
        set(value) {
            selectedTvCodeSetIndex = value
        }

    var selectedSoundbarCodeSetIndex: Int
        get() = prefs?.getInt(KEY_SOUNDBAR_CODE_SET_INDEX, 0) ?: 0
        set(value) {
            prefs?.edit()?.putInt(KEY_SOUNDBAR_CODE_SET_INDEX, value)?.apply()
        }

    var customProntoHex: String
        get() = prefs?.getString(KEY_PRONTO_HEX, "") ?: ""
        set(value) {
            prefs?.edit()?.putString(KEY_PRONTO_HEX, value.trim())?.apply()
        }

    var customSoundbarProntoHex: String
        get() = prefs?.getString(KEY_SOUNDBAR_PRONTO_HEX, "") ?: ""
        set(value) {
            prefs?.edit()?.putString(KEY_SOUNDBAR_PRONTO_HEX, value.trim())?.apply()
        }

    var isAutoMuteEnabled: Boolean
        get() = prefs?.getBoolean(KEY_AUTO_MUTE_ENABLED, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_AUTO_MUTE_ENABLED, value)?.apply()
        }

    fun hasIrHardware(): Boolean = irController.hasIrEmitter()

    fun hasInternalIr(): Boolean = irController.hasInternalIr()

    fun hasUsbDongle(): Boolean = irController.hasUsbDongle()

    fun getUsbDongleStatus(): UsbDongleStatus = irController.getUsbDongleStatus()

    fun requestUsbPermission(onResult: ((Boolean) -> Unit)? = null) {
        irController.requestUsbPermission(onResult)
    }

    fun getSelectedTvCodeSet(): TvCodeSet {
        val tvSets = IrCodeDatabase.tvCodeSets
        val index = selectedTvCodeSetIndex.coerceIn(0, tvSets.size - 1)
        val set = tvSets[index]
        return if (set.protocol == "PRONTO" && customProntoHex.isNotBlank()) {
            set.copy(prontoHex = customProntoHex)
        } else {
            set
        }
    }

    fun getSelectedCodeSet(): TvCodeSet = getSelectedTvCodeSet()

    fun getSelectedSoundbarCodeSet(): TvCodeSet {
        val sbSets = IrCodeDatabase.soundbarCodeSets
        val index = selectedSoundbarCodeSetIndex.coerceIn(0, sbSets.size - 1)
        val set = sbSets[index]
        return if (set.protocol == "PRONTO" && customSoundbarProntoHex.isNotBlank()) {
            set.copy(prontoHex = customSoundbarProntoHex)
        } else {
            set
        }
    }

    /**
     * Dispatches MUTE action to the active TV and/or Soundbar channel(s).
     */
    suspend fun sendMute(): TvActionResult = executeAction("MUTE")

    /**
     * Dispatches UNMUTE action to the active TV and/or Soundbar channel(s).
     */
    suspend fun sendUnmute(): TvActionResult = executeAction("UNMUTE")

    /**
     * Executes the requested action (MUTE or UNMUTE) through target devices.
     */
    suspend fun executeAction(action: String): TvActionResult = withContext(Dispatchers.IO) {
        val target = targetDevice
        val results = mutableListOf<String>()
        var isOverallSuccess = false

        // 1. TV Dispatch (if target includes TV)
        if (target == TargetDevice.TV_ONLY || target == TargetDevice.BOTH) {
            val tvResult = executeTvAction(action)
            results.add("TV [${tvResult.method.name}]: ${tvResult.message}")
            if (tvResult.success) isOverallSuccess = true
        }

        // 2. Soundbar Dispatch (if target includes Soundbar)
        if (target == TargetDevice.SOUNDBAR_ONLY || target == TargetDevice.BOTH) {
            val sbResult = executeSoundbarAction(action)
            results.add("Soundbar [IR]: ${sbResult.message}")
            if (sbResult.success) isOverallSuccess = true
        }

        TvActionResult(
            success = isOverallSuccess,
            method = controlMethod,
            action = action,
            message = results.joinToString(" | ")
        )
    }

    /**
     * Sends action specifically to TV via configured TV control method (Webhook, IR, or Both).
     */
    suspend fun executeTvAction(action: String): TvActionResult = withContext(Dispatchers.IO) {
        val method = controlMethod
        val subResults = mutableListOf<String>()
        var tvSuccess = false

        // Webhook Execution
        if (method == ControlMethod.WEBHOOK || method == ControlMethod.BOTH) {
            val url = webhookUrl
            if (url.isNotBlank()) {
                val webhookRes = irController.triggerTvWebhook(url, action)
                if (webhookRes.isSuccess) {
                    val code = webhookRes.getOrNull()
                    subResults.add("Webhook HTTP $code")
                    tvSuccess = true
                } else {
                    subResults.add("Webhook Err: ${webhookRes.exceptionOrNull()?.message}")
                }
            } else {
                subResults.add("Webhook URL empty")
            }
        }

        // IR Execution
        if (method == ControlMethod.IR || method == ControlMethod.BOTH) {
            val codeSet = getSelectedTvCodeSet()
            val isMuteCmd = action.equals("MUTE", ignoreCase = true)
            val irSent = irController.transmitCodeSet(codeSet, isMute = isMuteCmd)
            if (irSent) {
                subResults.add("IR sent (${codeSet.name})")
                tvSuccess = true
            } else {
                val status = if (hasIrHardware()) "IR fail" else "No IR blaster"
                subResults.add(status)
            }
        }

        TvActionResult(
            success = tvSuccess,
            method = method,
            action = action,
            message = subResults.joinToString("; ")
        )
    }

    /**
     * Sends action specifically to Soundbar via IR (Internal IR or USB Tview IR dongle).
     */
    suspend fun executeSoundbarAction(action: String): TvActionResult = withContext(Dispatchers.IO) {
        val codeSet = getSelectedSoundbarCodeSet()
        val isMuteCmd = action.equals("MUTE", ignoreCase = true)
        val irSent = irController.transmitCodeSet(codeSet, isMute = isMuteCmd)

        val message = if (irSent) {
            val transport = if (hasUsbDongle()) "Tview USB IR" else "Internal IR"
            "IR sent via $transport (${codeSet.name})"
        } else {
            if (hasIrHardware()) "IR transmit failed" else "No IR hardware (attach Tview USB)"
        }

        TvActionResult(
            success = irSent,
            method = ControlMethod.IR,
            action = action,
            message = message
        )
    }

    companion object {
        private const val KEY_TARGET_DEVICE = "tv_target_device"
        private const val KEY_WEBHOOK_URL = "tv_webhook_url"
        private const val KEY_CONTROL_METHOD = "tv_control_method"
        private const val KEY_CODE_SET_INDEX = "tv_code_set_index"
        private const val KEY_SOUNDBAR_CODE_SET_INDEX = "soundbar_code_set_index"
        private const val KEY_PRONTO_HEX = "tv_custom_pronto_hex"
        private const val KEY_SOUNDBAR_PRONTO_HEX = "soundbar_custom_pronto_hex"
        private const val KEY_AUTO_MUTE_ENABLED = "tv_auto_mute_enabled"
    }
}
