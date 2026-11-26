// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

/**
 * Normalized (`0f..1f`) Comskip/adhush broadcast transition cues computed per frame.
 */
data class BroadcastCueSignals(
    val logoMaskStrength: Float = 0f,
    val logoDropoutScore: Float = 0f,
    val blackSilencePulse: Float = 0f,
    val aspectRatioShift: Float = 0f,
    val repeatAdDistance: Float = 1f,
    val repeatAdMatchScore: Float = 0f
)
