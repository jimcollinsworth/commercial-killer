// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import android.content.Context
import android.util.Log
import com.example.commercialkiller.data.model.ModelRepository
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
    val isTvMuted: Boolean,
    val logoDropoutScore: Float = 0f,
    val blackSilencePulse: Float = 0f,
    val aspectRatioShift: Float = 0f,
    val repeatAdDistance: Float = 1f
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
        const val STATUS_DISABLED = "Stage 2 Disabled (Stage 1 Only)"

        private const val SYSTEM_INSTRUCTION =
            "You classify live TV stream status. Reply with exactly one word: COMMERCIAL, PROGRAM, or SILENCE."

        /**
         * Resolves a local `.litertlm` model file outside the APK (`filesDir/models/lm/`,
         * `filesDir/models/`, `filesDir/`, or `/data/local/tmp/`).
         */
        fun findModelFile(context: Context): File? {
            val entries = ModelRepository.scanModels(context).second
            val preferred = entries.firstOrNull {
                it.fileName.equals("Qwen3-0.6B.litertlm", ignoreCase = true)
            } ?: entries.firstOrNull {
                it.fileName.contains("gemma-4-E2B", ignoreCase = true)
            } ?: entries.firstOrNull {
                it.fileName.contains("qwen3_0.6b_nothink", ignoreCase = true)
            } ?: entries.firstOrNull {
                it.fileName.equals("model.litertlm", ignoreCase = true)
            } ?: entries.firstOrNull()
            return preferred?.let { File(it.absolutePath) }?.takeIf { it.exists() && it.isFile && it.length() > 0L }
        }

        /**
         * Parses a single-token Stage 2 verdict from model output text, stripping any optional
         * `<think>...</think>` blocks emitted by reasoning models.
         */
        fun parseConstrainedVerdict(raw: String): Stage2Decision? {
            val stripped = raw
                .replace(Regex("(?is)<think>.*?</think>"), " ")
                .replace(Regex("(?is)</?think>"), " ")
                .trim()
            val normalized = stripped.uppercase(Locale.US)
            if (normalized.isEmpty()) return null
            val firstWord = normalized.split(Regex("\\s+|[.,:;!\\n]+")).firstOrNull { it.isNotEmpty() } ?: ""
            return when {
                firstWord == "NON_MEDIA" || firstWord == "NON-MEDIA" ||
                    firstWord == "NO_MEDIA" || firstWord == "SILENCE" || firstWord == "OFF_AIR" ||
                    normalized.startsWith("NON_MEDIA") || normalized.startsWith("SILENCE") || normalized.startsWith("OFF_AIR") ->
                    Stage2Decision.NON_MEDIA
                firstWord == "COMMERCIAL" || normalized.startsWith("COMMERCIAL") ->
                    Stage2Decision.COMMERCIAL
                firstWord == "PROGRAM" || normalized.startsWith("PROGRAM") ->
                    Stage2Decision.PROGRAM
                else -> when (Regex("\\b(NON_MEDIA|NON-MEDIA|NO_MEDIA|SILENCE|OFF_AIR|COMMERCIAL|PROGRAM)\\b").findAll(normalized).lastOrNull()?.value) {
                    "COMMERCIAL" -> Stage2Decision.COMMERCIAL
                    "NON_MEDIA", "NON-MEDIA", "NO_MEDIA", "SILENCE", "OFF_AIR" -> Stage2Decision.NON_MEDIA
                    "PROGRAM" -> Stage2Decision.PROGRAM
                    else -> null
                }
            }
        }

        fun buildPrompt(features: Stage2TransitionFeatures, noThink: Boolean = false): String {
            val filteredTags = if (features.isTvMuted) {
                features.topTags.filterNot { it.contains("silence", ignoreCase = true) }
            } else {
                features.topTags
            }
            val tagsText = when {
                filteredTags.isNotEmpty() -> filteredTags.take(3).joinToString(",")
                features.isTvMuted -> "video-only"
                else -> "none"
            }
            val programReturn = features.isTvMuted &&
                (features.prototypeSimilarity >= 0.55f || (features.hasStableBug && features.bugSimilarity >= 0.75f)) &&
                features.aspectRatioShift < 0.50f &&
                features.repeatAdDistance > 0.25f
            val silentRoom = !features.isTvMuted && features.mediaScore < 0.40f
            val commercialBreak = !features.isTvMuted && (
                features.commercialScore >= 0.70f ||
                    (features.visualChangeScore >= 0.30f && features.prototypeSimilarity < 0.65f) ||
                    features.blackSilencePulse >= 0.45f ||
                    features.logoDropoutScore >= 0.50f ||
                    features.aspectRatioShift >= 0.45f ||
                    (features.repeatAdDistance <= 0.25f && features.commercialScore >= 0.55f)
                )
            val summary = when {
                programReturn ->
                    "Main TV show program is actively playing on screen with matching video scene and channel logo"
                silentRoom ->
                    "Microphone hears empty room silence with no TV broadcast playing"
                commercialBreak ->
                    "TV broadcast switched to a commercial break with fast cuts and scene or logo departure"
                else ->
                    "Main TV show program is actively playing on screen"
            }
            val noThinkSuffix = if (noThink) " /no_think" else ""
            return String.format(
                Locale.US,
                "Status: %s (media=%.2f comm=%.2f dv=%.2f proto=%.2f bugStable=%s bugSim=%.2f cuts=%.1f muted=%s logoDrop=%.2f blkSil=%.2f arShift=%.2f repDist=%.2f tags=[%s]). Classify as COMMERCIAL, PROGRAM, or SILENCE.%s",
                summary,
                features.mediaScore,
                features.commercialScore,
                features.visualChangeScore,
                features.prototypeSimilarity,
                features.hasStableBug,
                features.bugSimilarity,
                features.cutsPerMin,
                features.isTvMuted,
                features.logoDropoutScore,
                features.blackSilencePulse,
                features.aspectRatioShift,
                features.repeatAdDistance,
                tagsText,
                noThinkSuffix
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

    @Volatile
    private var activeModelPath: String? = null

    @Volatile
    private var lastLatencyMs: Long? = null

    fun getStatusText(): String = statusText

    fun getActiveModelPath(): String? = activeModelPath

    fun getLastLatencyMs(): Long? = lastLatencyMs

    fun isModelReady(): Boolean = isReady && !isClosed

    fun initializeAsync(onStatusChanged: (String) -> Unit = {}) {
        configureAsync(enabled = true, modelFile = null) { status, _ ->
            onStatusChanged(status)
        }
    }

    /**
     * Configures or switches the active `.litertlm` model dynamically off the main thread.
     * Passing `enabled = false` unloads the native `Engine` while keeping this instance reusable.
     */
    fun configureAsync(
        enabled: Boolean,
        modelFile: File?,
        onStatusChanged: (String, String?) -> Unit = { _, _ -> }
    ) {
        val appContext = context?.applicationContext
        if (isClosed) return
        if (!enabled) {
            scope.launch {
                mutex.withLock {
                    if (isClosed) return@withLock
                    closeEngineLocked()
                    statusText = STATUS_DISABLED
                    onStatusChanged(statusText, null)
                }
            }
            return
        }
        if (appContext == null) {
            statusText = STATUS_STAGE_1_ONLY
            onStatusChanged(statusText, null)
            return
        }

        scope.launch {
            mutex.withLock {
                if (isClosed) return@withLock
                val target = modelFile?.takeIf { it.exists() && it.isFile && it.length() > 0L }
                    ?: findModelFile(appContext)
                if (target == null) {
                    closeEngineLocked()
                    statusText = STATUS_STAGE_1_ONLY
                    onStatusChanged(statusText, null)
                    return@withLock
                }

                if (isReady && activeModelPath == target.absolutePath && engine != null) {
                    onStatusChanged(statusText, activeModelPath)
                    return@withLock
                }

                closeEngineLocked()
                statusText = "Loading ${target.name}..."
                onStatusChanged(statusText, null)

                val cachePath = appContext.cacheDir.path
                // XNNPACK CPU avoids Mali OpenCL weight-prep omissions while keeping async Stage 2 off the audio loop.
                val loaded = tryInitialize(target.absolutePath, Backend.CPU(), cachePath, "CPU")
                    ?: tryInitialize(target.absolutePath, Backend.GPU(), cachePath, "GPU")

                if (loaded != null) {
                    engine = loaded.first
                    activeModelPath = target.absolutePath
                    isReady = true
                    statusText = "Stage 2 Ready (${loaded.second}): ${target.name}"
                } else {
                    statusText = "Stage 1 Active (${target.name} load failed)"
                }
                onStatusChanged(statusText, activeModelPath)
            }
        }
    }

    private fun closeEngineLocked() {
        isReady = false
        activeModelPath = null
        try {
            engine?.close()
        } catch (ignored: Exception) {}
        engine = null
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
        onErrorOrEmpty: (Long) -> Unit = {},
        onVerdict: (Stage2Decision, Long) -> Unit
    ) {
        if (!isReady || isBusy || isClosed) return

        scope.launch {
            mutex.withLock {
                val activeEngine = engine
                if (!isReady || isClosed || activeEngine == null) return@withLock
                isBusy = true
                var conversation: Conversation? = null
                val startNs = System.nanoTime()
                try {
                    conversation = activeEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
                            samplerConfig = SamplerConfig(topK = 1, topP = 0.9, temperature = 0.01)
                        )
                    )
                    val useNoThink = activeModelPath?.contains("qwen3", ignoreCase = true) == true &&
                        activeModelPath?.contains("nothink", ignoreCase = true) != true
                    val prompt = buildPrompt(features, noThink = useNoThink)
                    val raw = conversation.sendMessage(prompt).toString()
                    val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    lastLatencyMs = latencyMs
                    val parsed = parseConstrainedVerdict(raw)
                    try {
                        Log.i(
                            TAG,
                            "Stage2 [${latencyMs}ms] parsed=$parsed | prompt=${prompt.replace("\n", " ")} | raw=${raw.replace("\n", "\\n")}"
                        )
                    } catch (ignored: Exception) {}
                    if (parsed != null) {
                        onVerdict(parsed, latencyMs)
                    } else {
                        onErrorOrEmpty(latencyMs)
                    }
                } catch (e: Exception) {
                    val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    try {
                        Log.w(TAG, "Stage 2 transition check skipped: ${e.message}")
                    } catch (ignored: Exception) {}
                    onErrorOrEmpty(latencyMs)
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
