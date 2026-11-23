// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ModelRepositoryTest {

    @Test
    fun `scanDirectories finds tflite and litertlm files ignoring empty or unrelated files and prefers managed copies`() {
        val tempRoot = Files.createTempDirectory("ck_model_scan_test").toFile()
        try {
            val managedRoot = File(tempRoot, "managed")
            val managedAudio = File(managedRoot, "audio").apply { mkdirs() }
            val managedLm = File(managedRoot, "lm").apply { mkdirs() }
            val tmpDir = File(tempRoot, "tmp").apply { mkdirs() }

            // Managed and unmanaged duplicate yamnet.tflite -> managed entry must win
            File(managedAudio, "yamnet.tflite").writeBytes(ByteArray(128) { 1 })
            File(tmpDir, "yamnet.tflite").writeBytes(ByteArray(64) { 2 })
            // Empty or non-model files in tmp must be ignored
            File(tmpDir, "empty.tflite").writeBytes(ByteArray(0))
            File(tmpDir, "notes.txt").writeText("ignore")
            // Valid unmanaged .tflite and .litertlm
            File(tmpDir, "custom_sound.tflite").writeBytes(ByteArray(256) { 3 })
            File(managedLm, "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm").writeBytes(ByteArray(512) { 4 })
            File(tmpDir, "gemma-4-E2B-it.litertlm").writeBytes(ByteArray(1024) { 5 })

            val (audioModels, lmModels) = ModelRepository.scanDirectories(
                audioDirs = listOf(managedAudio, managedRoot, tmpDir),
                lmDirs = listOf(managedLm, managedRoot, tmpDir),
                managedRoot = managedRoot
            )

            assertEquals(2, audioModels.size)
            assertEquals(listOf("custom_sound.tflite", "yamnet.tflite"), audioModels.map { it.fileName })
            val yamnet = audioModels.first { it.fileName == "yamnet.tflite" }
            assertTrue(yamnet.isManaged)
            assertEquals(128L, yamnet.sizeBytes)
            assertFalse(audioModels.first { it.fileName == "custom_sound.tflite" }.isManaged)

            assertEquals(2, lmModels.size)
            assertEquals(
                listOf("gemma-4-E2B-it.litertlm", "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm"),
                lmModels.map { it.fileName }
            )
            assertFalse(lmModels[0].isManaged)
            assertTrue(lmModels[1].isManaged)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `curated catalog defines HTTPS URLs and positive byte sizes for all entries`() {
        assertEquals(4, ModelRepository.CATALOG.size)
        ModelRepository.CATALOG.forEach { spec ->
            assertTrue("Expected HTTPS URL for ${spec.id}", spec.downloadUrl.startsWith("https://"))
            assertTrue("Expected positive size for ${spec.id}", spec.expectedSizeBytes > 0L)
            assertTrue("Expected non-blank filename for ${spec.id}", spec.fileName.isNotBlank())
        }
    }
}
