package com.droidunplugged.nanobananaandorid.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidunplugged.nanobananaandorid.airuntime.AdkOrchestrator
import com.droidunplugged.nanobananaandorid.data.ChatRepository
import com.droidunplugged.nanobananaandorid.data.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val orchestrator: AdkOrchestrator
) : ViewModel() {

    val messages = repository.messages

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _useRealNano = MutableStateFlow(false)
    val useRealNano: StateFlow<Boolean> = _useRealNano.asStateFlow()

    private val _latestDetectedObjects = MutableStateFlow<List<String>>(emptyList())
    val latestDetectedObjects: StateFlow<List<String>> = _latestDetectedObjects.asStateFlow()

    fun setUseRealNano(value: Boolean) {
        _useRealNano.value = value
    }

    fun updateDetectedObjects(objects: List<String>) {
        _latestDetectedObjects.value = objects
    }

    fun askAgent(userPrompt: String) {
        if (userPrompt.isBlank() || _isGenerating.value) return
        
        viewModelScope.launch {
            // 1. Post user message to stream
            repository.addMessage(userPrompt, isFromUser = true)
            
            // 2. Start generation
            _isGenerating.value = true
            
            // 3. Process prompt via AI Orchestrator with current real-time vision context
            val response = orchestrator.processPrompt(
                userPrompt = userPrompt,
                detectedObjects = _latestDetectedObjects.value,
                useRealNano = _useRealNano.value
            )
            
            // 4. Post agent response & IR actions to stream
            repository.addMessage(response, isFromUser = false)
            _draft.value = response
            _isGenerating.value = false
        }
    }

    fun sendMessage(text: String) {
        askAgent(text)
    }

    fun generateReply(currentMessages: List<MessageEntity>) {
        val lastMessage = currentMessages.lastOrNull()?.text ?: "Analyze current video frame"
        askAgent(lastMessage)
    }

    fun updateDraft(text: String) {
        _draft.value = text
    }

    fun sendDraft() {
        if (_draft.value.isBlank()) return
        viewModelScope.launch {
            repository.addMessage(_draft.value, isFromUser = false)
            _draft.value = ""
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
