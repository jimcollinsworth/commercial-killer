// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.coroutineContext

enum class ModelKind {
    AUDIO_TFLITE,
    STAGE2_LITERTLM
}

enum class Stage2Mode {
    OFF,
    AUDIT,
    VETO
}

data class LocalModelEntry(
    val id: String,
    val displayName: String,
    val fileName: String,
    val absolutePath: String,
    val kind: ModelKind,
    val sizeBytes: Long,
    val isManaged: Boolean
)

data class CatalogModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val kind: ModelKind,
    val downloadUrl: String,
    val expectedSizeBytes: Long,
    val description: String
)

data class ModelDownloadState(
    val modelId: String,
    val fileName: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val isDownloading: Boolean = false,
    val errorMessage: String? = null
)

data class PipelinePreferences(
    val useNeuralAudio: Boolean = true,
    val selectedAudioModelPath: String? = null,
    val videoFusionEnabled: Boolean = true,
    val stage2Mode: Stage2Mode = Stage2Mode.AUDIT,
    val selectedStage2ModelPath: String? = null
)

/**
 * Discovers, imports, downloads, deletes, and persists configuration for local `.tflite`
 * audio classifiers and `.litertlm` Stage 2 verification models outside the APK and Git tree.
 */
object ModelRepository {
    private const val PREFS_NAME = "commercial_killer_pipeline_prefs"
    private const val KEY_USE_NEURAL_AUDIO = "use_neural_audio"
    private const val KEY_AUDIO_MODEL_PATH = "selected_audio_model_path"
    private const val KEY_VIDEO_FUSION = "video_fusion_enabled"
    private const val KEY_STAGE2_MODE = "stage2_mode"
    private const val KEY_STAGE2_MODEL_PATH = "selected_stage2_model_path"
    private const val MAX_REDIRECTS = 5
    private const val PROGRESS_EMIT_INTERVAL_BYTES = 262_144L

    val CATALOG: List<CatalogModelSpec> = listOf(
        CatalogModelSpec(
            id = "yamnet_float32",
            displayName = "YAMNet AudioSet (521 classes)",
            fileName = "yamnet.tflite",
            kind = ModelKind.AUDIO_TFLITE,
            downloadUrl = "https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite",
            expectedSizeBytes = 4_126_810L,
            description = "3.9 MB - MediaPipe 521-class audio classifier"
        ),
        CatalogModelSpec(
            id = "qwen3_0_6b_nothink_q4",
            displayName = "Qwen3 0.6B (NoThink Q4)",
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            kind = ModelKind.STAGE2_LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/main/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            expectedSizeBytes = 347_251_840L,
            description = "331 MB - Non-thinking 1-token CPU/GPU verifier"
        ),
        CatalogModelSpec(
            id = "qwen3_0_6b_int8",
            displayName = "Qwen3 0.6B (INT8)",
            fileName = "Qwen3-0.6B.litertlm",
            kind = ModelKind.STAGE2_LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            expectedSizeBytes = 613_722_264L,
            description = "585 MB - INT8 CPU/GPU verifier"
        ),
        CatalogModelSpec(
            id = "gemma_4_e2b_it",
            displayName = "Gemma 4 E2B IT",
            fileName = "gemma-4-E2B-it.litertlm",
            kind = ModelKind.STAGE2_LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            expectedSizeBytes = 2_588_147_712L,
            description = "2.41 GB - 2B edge model (CPU/GPU)"
        )
    )

    fun modelsRootDir(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }

    fun audioModelsDir(context: Context): File = File(modelsRootDir(context), "audio").apply { mkdirs() }

    fun lmModelsDir(context: Context): File = File(modelsRootDir(context), "lm").apply { mkdirs() }

    fun scanModels(context: Context): Pair<List<LocalModelEntry>, List<LocalModelEntry>> {
        val managedRoot = File(context.filesDir, "models")
        val legacyModelsDir = File(context.filesDir, "models")
        val tmpDir = File("/data/local/tmp")
        val audioDirs = listOf(
            File(managedRoot, "audio"),
            legacyModelsDir,
            context.filesDir,
            tmpDir
        )
        val lmDirs = listOf(
            File(managedRoot, "lm"),
            legacyModelsDir,
            context.filesDir,
            tmpDir
        )
        return scanDirectories(audioDirs, lmDirs, managedRoot)
    }

    /**
     * Scans candidate directories for `.tflite` and `.litertlm` files, deduplicating by lowercase
     * filename while preserving managed copies under [managedRoot] before external paths.
     */
    fun scanDirectories(
        audioDirs: List<File>,
        lmDirs: List<File>,
        managedRoot: File
    ): Pair<List<LocalModelEntry>, List<LocalModelEntry>> {
        val audioEntries = scanKind(audioDirs, ModelKind.AUDIO_TFLITE, ".tflite", managedRoot)
        val lmEntries = scanKind(lmDirs, ModelKind.STAGE2_LITERTLM, ".litertlm", managedRoot)
        return audioEntries to lmEntries
    }

    private fun scanKind(
        dirs: List<File>,
        kind: ModelKind,
        extension: String,
        managedRoot: File
    ): List<LocalModelEntry> {
        val rootPath = try {
            managedRoot.canonicalPath
        } catch (ignored: Exception) {
            managedRoot.absolutePath
        }
        val byName = LinkedHashMap<String, LocalModelEntry>()
        for (dir in dirs) {
            val files = try {
                if (!dir.exists() || !dir.isDirectory) continue
                dir.listFiles() ?: continue
            } catch (ignored: Exception) {
                continue
            }
            for (file in files) {
                try {
                    if (!file.isFile || file.length() <= 0L) continue
                    val name = file.name
                    if (!name.lowercase(Locale.US).endsWith(extension)) continue
                    val key = name.lowercase(Locale.US)
                    val canonical = try {
                        file.canonicalPath
                    } catch (ignored: Exception) {
                        file.absolutePath
                    }
                    val isManaged = canonical.startsWith(rootPath)
                    val existing = byName[key]
                    if (existing == null || (!existing.isManaged && isManaged)) {
                        byName[key] = LocalModelEntry(
                            id = "${kind.name}:$canonical",
                            displayName = name,
                            fileName = name,
                            absolutePath = file.absolutePath,
                            kind = kind,
                            sizeBytes = file.length(),
                            isManaged = isManaged
                        )
                    }
                } catch (ignored: Exception) {
                    // Ignore unreadable entries in restricted folders.
                }
            }
        }
        return byName.values.sortedBy { it.fileName.lowercase(Locale.US) }
    }

    suspend fun importModelFromUri(
        context: Context,
        uri: Uri,
        kind: ModelKind
    ): Result<LocalModelEntry> = withContext(Dispatchers.IO) {
        val ext = if (kind == ModelKind.AUDIO_TFLITE) ".tflite" else ".litertlm"
        val destDir = if (kind == ModelKind.AUDIO_TFLITE) audioModelsDir(context) else lmModelsDir(context)
        val rawName = resolveDisplayName(context, uri)
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "imported_${System.currentTimeMillis()}$ext"
        val fileName = if (rawName.lowercase(Locale.US).endsWith(ext)) rawName else "$rawName$ext"
        val finalFile = File(destDir, fileName)
        val partFile = File(destDir, "$fileName.part")
        if (partFile.exists()) {
            partFile.delete()
        }

        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("Unable to open selected model file"))
            stream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                        }
                    }
                    output.fd.sync()
                }
            }
            if (partFile.length() <= 0L) {
                partFile.delete()
                return@withContext Result.failure(IllegalStateException("Selected model file is empty"))
            }
            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!partFile.renameTo(finalFile)) {
                return@withContext Result.failure(IllegalStateException("Failed to save imported model"))
            }
            Result.success(
                LocalModelEntry(
                    id = "${kind.name}:${finalFile.absolutePath}",
                    displayName = finalFile.name,
                    fileName = finalFile.name,
                    absolutePath = finalFile.absolutePath,
                    kind = kind,
                    sizeBytes = finalFile.length(),
                    isManaged = true
                )
            )
        } catch (t: Throwable) {
            if (partFile.exists()) {
                partFile.delete()
            }
            Result.failure(t)
        }
    }

    suspend fun downloadCatalogModel(
        context: Context,
        spec: CatalogModelSpec,
        onProgress: (Long, Long) -> Unit
    ): Result<LocalModelEntry> = withContext(Dispatchers.IO) {
        val destDir = if (spec.kind == ModelKind.AUDIO_TFLITE) {
            audioModelsDir(context)
        } else {
            lmModelsDir(context)
        }
        val finalFile = File(destDir, spec.fileName)
        val partFile = File(destDir, "${spec.fileName}.part")
        if (partFile.exists()) {
            partFile.delete()
        }

        var conn: HttpURLConnection? = null
        try {
            conn = openWithRedirects(spec.downloadUrl)
            val contentLength = conn.contentLengthLong.takeIf { it > 0L } ?: spec.expectedSizeBytes
            onProgress(0L, contentLength)

            var downloaded = 0L
            var lastReported = 0L
            conn.inputStream.use { input ->
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastReported >= PROGRESS_EMIT_INTERVAL_BYTES || downloaded == contentLength) {
                                lastReported = downloaded
                                onProgress(downloaded, contentLength)
                            }
                        }
                    }
                    output.fd.sync()
                }
            }
            if (partFile.length() <= 0L) {
                partFile.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded file is empty"))
            }
            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!partFile.renameTo(finalFile)) {
                return@withContext Result.failure(IllegalStateException("Failed to finalize ${spec.fileName}"))
            }
            onProgress(finalFile.length(), finalFile.length())
            Result.success(
                LocalModelEntry(
                    id = "${spec.kind.name}:${finalFile.absolutePath}",
                    displayName = finalFile.name,
                    fileName = finalFile.name,
                    absolutePath = finalFile.absolutePath,
                    kind = spec.kind,
                    sizeBytes = finalFile.length(),
                    isManaged = true
                )
            )
        } catch (t: Throwable) {
            if (partFile.exists()) {
                partFile.delete()
            }
            Result.failure(t)
        } finally {
            conn?.disconnect()
        }
    }

    private fun openWithRedirects(urlString: String): HttpURLConnection {
        var currentUrl = urlString
        for (hop in 0..MAX_REDIRECTS) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("Redirect HTTP $code missing Location header")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Download failed with HTTP $code")
            }
            return conn
        }
        throw IllegalStateException("Exceeded maximum HTTP redirects ($MAX_REDIRECTS)")
    }

    fun deleteManagedModel(context: Context, entry: LocalModelEntry): Boolean {
        if (!entry.isManaged) return false
        return try {
            val managedRoot = modelsRootDir(context).canonicalPath
            val target = File(entry.absolutePath)
            if (target.canonicalPath.startsWith(managedRoot) && target.exists()) {
                target.delete()
            } else {
                false
            }
        } catch (ignored: Exception) {
            false
        }
    }

    fun loadPreferences(context: Context?): PipelinePreferences {
        if (context == null) return PipelinePreferences()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stage2Name = prefs.getString(KEY_STAGE2_MODE, Stage2Mode.AUDIT.name) ?: Stage2Mode.AUDIT.name
        val stage2Mode = try {
            Stage2Mode.valueOf(stage2Name)
        } catch (ignored: Exception) {
            Stage2Mode.AUDIT
        }
        return PipelinePreferences(
            useNeuralAudio = prefs.getBoolean(KEY_USE_NEURAL_AUDIO, true),
            selectedAudioModelPath = prefs.getString(KEY_AUDIO_MODEL_PATH, null),
            videoFusionEnabled = prefs.getBoolean(KEY_VIDEO_FUSION, true),
            stage2Mode = stage2Mode,
            selectedStage2ModelPath = prefs.getString(KEY_STAGE2_MODEL_PATH, null)
        )
    }

    fun savePreferences(context: Context?, preferences: PipelinePreferences) {
        if (context == null) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_USE_NEURAL_AUDIO, preferences.useNeuralAudio)
            .putString(KEY_AUDIO_MODEL_PATH, preferences.selectedAudioModelPath)
            .putBoolean(KEY_VIDEO_FUSION, preferences.videoFusionEnabled)
            .putString(KEY_STAGE2_MODE, preferences.stage2Mode.name)
            .putString(KEY_STAGE2_MODEL_PATH, preferences.selectedStage2ModelPath)
            .apply()
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else {
                    null
                }
            } ?: uri.lastPathSegment
        } catch (ignored: Exception) {
            uri.lastPathSegment
        }
    }
}
