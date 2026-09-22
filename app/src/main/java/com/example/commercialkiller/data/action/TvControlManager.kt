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

data class TvActionResult(
    val success: Boolean,
    val method: ControlMethod,
    val action: String,
    val message: String
)

/**
 * Manages persistent TV control settings (Webhook URL, IR Code Sets, Auto-Mute toggles)
 * and unifies dispatching mute/unmute actions across Webhook and IR blaster channels.
 */
class TvControlManager(
    private val context: Context?
) {
    private val irController = IrEmitterController(context)
    private val prefs: SharedPreferences? = context?.getSharedPreferences("tv_control_prefs", Context.MODE_PRIVATE)

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

    var selectedCodeSetIndex: Int
        get() = prefs?.getInt(KEY_CODE_SET_INDEX, 0) ?: 0
        set(value) {
            prefs?.edit()?.putInt(KEY_CODE_SET_INDEX, value)?.apply()
        }

    var customProntoHex: String
        get() = prefs?.getString(KEY_PRONTO_HEX, "") ?: ""
        set(value) {
            prefs?.edit()?.putString(KEY_PRONTO_HEX, value.trim())?.apply()
        }

    var isAutoMuteEnabled: Boolean
        get() = prefs?.getBoolean(KEY_AUTO_MUTE_ENABLED, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_AUTO_MUTE_ENABLED, value)?.apply()
        }

    fun hasIrHardware(): Boolean = irController.hasIrEmitter()

    fun getSelectedCodeSet(): TvCodeSet {
        val codeSets = IrCodeDatabase.codeSets
        val index = selectedCodeSetIndex.coerceIn(0, codeSets.size - 1)
        val set = codeSets[index]
        return if (set.protocol == "PRONTO" && customProntoHex.isNotBlank()) {
            set.copy(prontoHex = customProntoHex)
        } else {
            set
        }
    }

    /**
     * Dispatches MUTE action to the active TV channel(s).
     */
    suspend fun sendMute(): TvActionResult = executeAction("MUTE")

    /**
     * Dispatches UNMUTE action to the active TV channel(s).
     */
    suspend fun sendUnmute(): TvActionResult = executeAction("UNMUTE")

    /**
     * Executes the requested action (MUTE or UNMUTE) through Webhook, IR, or Both.
     */
    suspend fun executeAction(action: String): TvActionResult = withContext(Dispatchers.IO) {
        val method = controlMethod
        val results = mutableListOf<String>()
        var isOverallSuccess = false

        // 1. Webhook Execution
        if (method == ControlMethod.WEBHOOK || method == ControlMethod.BOTH) {
            val url = webhookUrl
            if (url.isNotBlank()) {
                val webhookRes = irController.triggerTvWebhook(url, action)
                if (webhookRes.isSuccess) {
                    val code = webhookRes.getOrNull()
                    results.add("Webhook HTTP $code")
                    isOverallSuccess = true
                } else {
                    results.add("Webhook Error: ${webhookRes.exceptionOrNull()?.message}")
                }
            } else {
                results.add("Webhook skipped (URL blank)")
            }
        }

        // 2. IR Execution
        if (method == ControlMethod.IR || method == ControlMethod.BOTH) {
            val codeSet = getSelectedCodeSet()
            val isMuteCmd = action.equals("MUTE", ignoreCase = true)
            val irSent = irController.transmitCodeSet(codeSet, isMute = isMuteCmd)
            if (irSent) {
                results.add("IR sent (${codeSet.name})")
                isOverallSuccess = true
            } else {
                val irStatus = if (hasIrHardware()) "IR transmit failed" else "No IR blaster hardware"
                results.add(irStatus)
            }
        }

        TvActionResult(
            success = isOverallSuccess,
            method = method,
            action = action,
            message = results.joinToString("; ")
        )
    }

    companion object {
        private const val KEY_WEBHOOK_URL = "tv_webhook_url"
        private const val KEY_CONTROL_METHOD = "tv_control_method"
        private const val KEY_CODE_SET_INDEX = "tv_code_set_index"
        private const val KEY_PRONTO_HEX = "tv_custom_pronto_hex"
        private const val KEY_AUTO_MUTE_ENABLED = "tv_auto_mute_enabled"
    }
}
