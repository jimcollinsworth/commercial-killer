package com.droidunplugged.nanobananaandorid.airuntime

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.google.mlkit.genai.prompt.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

class RealGeminiNanoClient(private val context: Context) : GeminiNanoClient {

    override suspend fun generateContent(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Initialize with specific FAST preference which is optimized for S24 NPU
                val client = try {
                    val mConfig = ModelConfig.Builder().apply {
                        preference = 1   // 1 = FAST
                        releaseStage = 1 // 1 = PREVIEW
                    }.build()
                    
                    val gConfig = GenerationConfig.Builder().apply {
                        modelConfig = mConfig
                    }.build()
                    
                    Generation.getClient(gConfig)
                } catch (t: Throwable) {
                    Log.w("RealGeminiNanoClient", "Custom config failed, using default", t)
                    Generation.getClient()
                }

                // Check status: 1=AVAILABLE, 2=DOWNLOADABLE, 3=DOWNLOADING, 0=NOT_SUPPORTED
                val status = client.checkStatus()
                Log.d("RealGeminiNanoClient", "AICore Feature 636 status: $status")

                when (status) {
                    2 -> { 
                        client.download().collect { Log.d("RealGeminiNanoClient", "Download progress: $it") }
                        return@withContext "Gemini Nano model is downloading. Please try again in 2 minutes."
                    }
                    3 -> return@withContext "Model is still downloading..."
                    0 -> return@withContext "Gemini Nano status 0: Please check AICore Settings in Developer Options."
                }

                // Explicitly warmup to bind the service
                try { 
                    client.warmup() 
                } catch (e: Exception) { 
                    Log.e("RealGeminiNanoClient", "Warmup failed: ${e.message}") 
                }

                val response = client.generateContent(prompt)
                
                response.candidates.firstOrNull()?.text ?: "Error: Empty response from NPU."

            } catch (e: Exception) {
                Log.e("RealGeminiNanoClient", "Inference failed", e)
                val msg = e.message ?: ""
                if (msg.contains("606") || msg.contains("636")) {
                    "AICore status check: Feature 636 is initializing on your device.\n\n" +
                    "PIXEL 8 SETUP:\n" +
                    "1. Open Settings -> System -> Developer options.\n" +
                    "2. Enable 'AICore Settings' / 'Enable on-device GenAI features'.\n" +
                    "3. Open Google Play Store and ensure 'Android System AICore' is updated.\n" +
                    "4. If still downloading, give AICore 2 minutes to initialize on NPU."
                } else {
                    "Error: $msg"
                }
            }
        }
    }
}
