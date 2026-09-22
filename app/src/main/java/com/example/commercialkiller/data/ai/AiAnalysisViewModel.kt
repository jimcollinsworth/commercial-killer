package com.example.commercialkiller.data.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiAnalysisState(
    val isAnalyzing: Boolean = false,
    val promptResult: String = "",
    val detectedCommercial: Boolean = false,
    val confidence: Float = 0.0f
)

class AiAnalysisViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AiAnalysisState())
    val uiState: StateFlow<AiAnalysisState> = _uiState.asStateFlow()

    fun analyzeFrame(frameLabel: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzing = true)
            // On-device Gemini Nano / ML Kit Prompt API evaluation placeholder
            val isCommercial = frameLabel.contains("advertisement", ignoreCase = true) ||
                    frameLabel.contains("commercial", ignoreCase = true)
            _uiState.value = AiAnalysisState(
                isAnalyzing = false,
                promptResult = if (isCommercial) "Commercial Detected" else "Live Stream Active",
                detectedCommercial = isCommercial,
                confidence = if (isCommercial) 0.95f else 0.10f
            )
        }
    }
}
