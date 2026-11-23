// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Constrained Stage 2 broadcast classification verdict returned by an on-device `.litertlm` model.
 */
enum class Stage2Decision {
    PROGRAM,
    COMMERCIAL,
    NON_MEDIA
}

/**
 * Compact Stage 1 audio+video summary serialized into the Stage 2 Jev-style decision query.
 */
data class Stage2TransitionFeatures(
    val mediaScore: Float,
    val commercialScore: Float,
    val visualChangeScore: Float,
    val prototypeSimilarity: Float,
    val hasStableBug: Boolean,
    val bugSimilarity: Float,
    val cutsPerMin: Float,
    val topTags: List<String>,
    val isTvMuted: Boolean
)

/**
 * Option 2 Stage 2 Cascade Verifier powered by Google AI Edge `litertlm-android`.
 *
 * Keeps all `Engine` / `Conversation` calls confined to a dedicated single-thread executor,
 * leaves the real-time `100 ms` loop non-blocking, and operates in Stage-1-only mode whenever
 * no local `.litertlm` model file has been provisioned on device storage.
 */
class LiteRtLmDecisionVerifier(
    private val context: Context? = null
) : AutoCloseable {

    companion object {
        private const val TAG = "LiteRtLmVerifier"
        const val STATUS_STAGE_1_ONLY = "Stage 1 Active (No .litertlm model on device)"

        private const val SYSTEM_INSTRUCTION =
            "You are a broadcast TV commercial transition classifier. " +
                "Respond with exactly ONE token from {PROGRAM, COMMERCIAL, NON_MEDIA} and nothing else."

        /**
         * Resolves a local `.litertlm` model file outside the APK (`filesDir/models/`, `filesDir/`,
         * or `/data/local/tmp/`). Never bundles large models in `assets/`.
         */
        fun findModelFile(context: Context): File? {
            val candidates = listOf(
                File(context.filesDir, "models/gemma-4-E2B-it.litertlm"),
                File(context.filesDir, "model.litertlm"),
                File("/data/local/tmp/model.litertlm")
            )
            return candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }
        }

        /**
         * Parses a single-token Stage 2 verdict from model output text.
         */
        fun parseConstrainedVerdict(raw: String): Stage2Decision? {
            val normalized = raw.trim().uppercase(Locale.US)
            if (normalized.isEmpty()) return null
            val firstWord = normalized.split(Regex("\\s+|[.,:;!\\n]+")).firstOrNull { it.isNotEmpty() } ?: ""
            return when {
                firstWord == "NON_MEDIA" || firstWord == "NON-MEDIA" || normalized.startsWith("NON_MEDIA") ->
                    Stage2Decision.NON_MEDIA
                firstWord == "COMMERCIAL" || normalized.startsWith("COMMERCIAL") ->
                    Stage2Decision.COMMERCIAL
                firstWord == "PROGRAM" || normalized.startsWith("PROGRAM") ->
                    Stage2Decision.PROGRAM
                normalized.contains("COMMERCIAL") -> Stage2Decision.COMMERCIAL
                normalized.contains("NON_MEDIA") || normalized.contains("NON-MEDIA") -> Stage2Decision.NON_MEDIA
                normalized.contains("PROGRAM") -> Stage2Decision.PROGRAM
                else -> null
            }
        }

        fun buildPrompt(features: Stage2TransitionFeatures): String {
            val tagsText = if (features.topTags.isEmpty()) "none" else features.topTags.take(3).joinToString(",")
            return String.format(
                Locale.US,
                "media=%.2f comm=%.2f dv=%.2f proto=%.2f bugStable=%s bugSim=%.2f cuts=%.1f muted=%s tags=[%s]. Answer:",
                features.mediaScore,
                features.commercialScore,
                features.visualChangeScore,
                features.prototypeSimilarity,
                features.hasStableBug,
                features.bugSimilarity,
                features.cutsPerMin,
                features.isTvMuted,
                tagsText
            )
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private val mutex = Mutex()

    private var engine: Engine? = null

    @Volatile
    private var isReady = false

    @Volatile
    private var isBusy = false

    @Volatile
    private var isClosed = false

    @Volatile
    private var statusText: String = STATUS_STAGE_1_ONLY

    fun getStatusText(): String = statusText

    fun isModelReady(): Boolean = isReady && !isClosed

    fun initializeAsync(onStatusChanged: (String) -> Unit = {}) {
        val appContext = context?.applicationContext
        if (appContext == null || isClosed) {
            statusText = STATUS_STAGE_1_ONLY
            onStatusChanged(statusText)
            return
        }

        scope.launch {
            mutex.withLock {
                if (isClosed || isReady) return@withLock
                val modelFile = findModelFile(appContext)
                if (modelFile == null) {
                    statusText = STATUS_STAGE_1_ONLY
                    onStatusChanged(statusText)
                    return@withLock
                }

                val cachePath = appContext.cacheDir.path
                val loaded = tryInitialize(modelFile.absolutePath, Backend.GPU(), cachePath, "GPU")
                    ?: tryInitialize(modelFile.absolutePath, Backend.CPU(), cachePath, "CPU")

                if (loaded != null) {
                    engine = loaded.first
                    isReady = true
                    statusText = "Stage 2 Ready (${loaded.second}): ${modelFile.name}"
                } else {
                    statusText = "Stage 1 Active (${modelFile.name} load failed)"
                }
                onStatusChanged(statusText)
            }
        }
    }

    private fun tryInitialize(
        modelPath: String,
        backend: Backend,
        cachePath: String,
        backendLabel: String
    ): Pair<Engine, String>? {
        return try {
            val candidate = Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    cacheDir = cachePath
                )
            )
            candidate.initialize()
            candidate to backendLabel
        } catch (e: Exception) {
            try {
                Log.w(TAG, "LiteRT-LM $backendLabel init failed: ${e.message}")
            } catch (ignored: Exception) {}
            null
        }
    }

    /**
     * Dispatches a non-blocking single-turn verification request on a confirmed macro boundary.
     * Returns immediately if Stage 2 is unprovisioned or already busy so the 100 ms audio loop never stalls.
     */
    fun verifyTransitionAsync(
        features: Stage2TransitionFeatures,
        onVerdict: (Stage2Decision) -> Unit
    ) {
        if (!isReady || isBusy || isClosed) return

        scope.launch {
            mutex.withLock {
                val activeEngine = engine
                if (!isReady || isClosed || activeEngine == null) return@withLock
                isBusy = true
                var conversation: Conversation? = null
                try {
                    conversation = activeEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                            samplerConfig = SamplerConfig(topK = 4, topP = 0.9, temperature = 0.05)
                        )
                    )
                    val raw = conversation.sendMessage(buildPrompt(features)).toString()
                    parseConstrainedVerdict(raw)?.let(onVerdict)
                } catch (e: Exception) {
                    try {
                        Log.w(TAG, "Stage 2 transition check skipped: ${e.message}")
                    } catch (ignored: Exception) {}
                } finally {
                    try {
                        conversation?.close()
                    } catch (ignored: Exception) {}
                    isBusy = false
                }
            }
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        isReady = false
        scope.launch {
            mutex.withLock {
                try {
                    engine?.close()
                } catch (ignored: Exception) {}
                engine = null
            }
            executor.shutdown()
        }.invokeOnCompletion { scope.cancel() }
    }
}
