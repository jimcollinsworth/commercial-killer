package com.droidunplugged.nanobananaandorid.airuntime

import com.droidunplugged.nanobananaandorid.tooling.AgentLogger
import com.droidunplugged.nanobananaandorid.tooling.ExecutionEvent
import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject

/**
 * Orchestrates the AI tasks, coordinating context gathering, tool execution, and local inference.
 */
class AdkOrchestrator @Inject constructor(
    @FakeNano private val fakeNanoClient: GeminiNanoClient,
    @RealNano private val realNanoClient: GeminiNanoClient,
    private val logger: AgentLogger
) {

    suspend fun processPrompt(
        userPrompt: String,
        detectedObjects: List<String> = emptyList(),
        useRealNano: Boolean = false
    ): String {
        val taskId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        logger.logEvent(ExecutionEvent.AgentStarted(taskId))

        // Step 1: Video Frame & Context Ingestion (Agent Tool)
        logger.logEvent(ExecutionEvent.ToolExecutionStarted("AnalyzeVideoFrame"))
        delay(300)
        logger.logEvent(ExecutionEvent.ToolExecutionCompleted("AnalyzeVideoFrame", 300, true))

        // Step 2: Formulate Vision Context
        val objectListStr = if (detectedObjects.isNotEmpty()) {
            detectedObjects.joinToString(", ")
        } else {
            "Television (94%), Display screen (89%), Living Room (78%)"
        }

        val enrichedPrompt = if (userPrompt.contains("object", ignoreCase = true) || userPrompt.contains("see", ignoreCase = true)) {
            "VISION_QUERY: Detected in current video frame: [$objectListStr]. User asks: $userPrompt"
        } else {
            "VISION_CONTEXT: Current scene objects: [$objectListStr]. User asks: $userPrompt"
        }

        // Step 3: Generate Evaluation & IR Action via Gemini Nano / Local Engine
        val clientName = if (useRealNano) "Real On-Device NPU (Gemini Nano)" else "On-Device Vision AI"
        logger.logEvent(ExecutionEvent.GeneratingDraft("Reasoning via $clientName"))
        
        val activeClient: GeminiNanoClient = if (useRealNano) realNanoClient else fakeNanoClient
        val response = activeClient.generateContent(enrichedPrompt)
        
        val totalDuration = System.currentTimeMillis() - startTime
        logger.logEvent(ExecutionEvent.AgentCompleted(taskId, totalDuration))

        return response
    }

    suspend fun summarizeAndDraft(chatHistory: List<String>, useRealNano: Boolean = false): String {
        val lastMessage = chatHistory.lastOrNull() ?: "Analyze current video frame"
        return processPrompt(lastMessage, emptyList(), useRealNano)
    }
}
