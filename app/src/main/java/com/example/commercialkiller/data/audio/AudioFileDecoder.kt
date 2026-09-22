// Author Attribution: Co-authored by Project Owner & LLM-Gemini3.8.
package com.example.commercialkiller.data.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DecodedAudio(
    val samples: FloatArray,
    val sampleRate: Int = 16000,
    val durationMs: Long,
    val fileName: String
)

/**
 * Multi-format audio file decoder using Android MediaExtractor and MediaCodec.
 * Decodes MP3, WAV, AAC, M4A, FLAC, OGG, and OPUS audio tracks into normalized 16 kHz mono PCM float arrays.
 */
class AudioFileDecoder {

    suspend fun decode(context: Context, uri: Uri): DecodedAudio = withContext(Dispatchers.IO) {
        val fileName = getFileName(context, uri) ?: "audio_track"
        val extractor = MediaExtractor()
        val pfd = if (uri.scheme == "file" || uri.scheme == null) {
            val path = uri.path ?: uri.toString()
            val file = java.io.File(path)
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        } ?: throw IllegalArgumentException("Cannot open file descriptor for URI: $uri")

        try {
            extractor.setDataSource(pfd.fileDescriptor)
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                throw IllegalArgumentException("No audio track found in media file: $fileName")
            }

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Audio track has missing MIME type")

            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val rawSamples = decodeToPcm(extractor, decoder, format)
            decoder.stop()
            decoder.release()

            // Extract source properties from format
            val sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 16000

            val sourceChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            // 1. Convert to mono if multi-channel
            val monoSamples = if (sourceChannels > 1) {
                downmixToMono(rawSamples, sourceChannels)
            } else {
                rawSamples
            }

            // 2. Resample to 16,000 Hz if needed
            val targetSampleRate = 16000
            val normalizedSamples = if (sourceSampleRate != targetSampleRate && monoSamples.isNotEmpty()) {
                resample(monoSamples, sourceSampleRate, targetSampleRate)
            } else {
                monoSamples
            }

            val durationMs = if (durationUs > 0) durationUs / 1000L else (normalizedSamples.size * 1000L / targetSampleRate)

            DecodedAudio(
                samples = normalizedSamples,
                sampleRate = targetSampleRate,
                durationMs = durationMs,
                fileName = fileName
            )
        } finally {
            extractor.release()
            pfd.close()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) {
                return i
            }
        }
        return -1
    }

    private fun decodeToPcm(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        initialFormat: MediaFormat
    ): FloatArray {
        val bufferInfo = MediaCodec.BufferInfo()
        val pcmList = ArrayList<FloatArray>()
        var totalFloats = 0
        var isExtractorEOS = false
        var isDecoderEOS = false
        val timeoutUs = 10000L

        while (!isDecoderEOS) {
            if (!isExtractorEOS) {
                val inIndex = decoder.dequeueInputBuffer(timeoutUs)
                if (inIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                    val shortBuffer = outputBuffer.asShortBuffer()
                    val floatChunk = FloatArray(shortBuffer.remaining())
                    for (i in floatChunk.indices) {
                        floatChunk[i] = (shortBuffer.get() / 32768.0f).coerceIn(-1.0f, 1.0f)
                    }
                    pcmList.add(floatChunk)
                    totalFloats += floatChunk.size
                }
                decoder.releaseOutputBuffer(outIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isDecoderEOS = true
                }
            }
        }

        // Concatenate all chunks
        val fullArray = FloatArray(totalFloats)
        var offset = 0
        for (chunk in pcmList) {
            System.arraycopy(chunk, 0, fullArray, offset, chunk.size)
            offset += chunk.size
        }
        return fullArray
    }

    private fun downmixToMono(interleaved: FloatArray, channels: Int): FloatArray {
        val monoLength = interleaved.size / channels
        val mono = FloatArray(monoLength)
        val channelFactor = 1.0f / channels
        for (i in 0 until monoLength) {
            var sum = 0.0f
            for (ch in 0 until channels) {
                sum += interleaved[i * channels + ch]
            }
            mono[i] = (sum * channelFactor).coerceIn(-1.0f, 1.0f)
        }
        return mono
    }

    fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate || input.isEmpty()) return input
        val ratio = targetRate.toDouble() / sourceRate.toDouble()
        val targetLength = (input.size * ratio).toInt()
        val output = FloatArray(targetLength)

        for (i in 0 until targetLength) {
            val srcPos = i / ratio
            val index0 = srcPos.toInt()
            val index1 = (index0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcPos - index0).toFloat()
            output[i] = (input[index0] * (1.0f - frac) + input[index1] * frac).coerceIn(-1.0f, 1.0f)
        }
        return output
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
