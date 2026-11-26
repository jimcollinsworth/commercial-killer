// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import com.example.commercialkiller.data.audio.AudioClassifierEngine
import com.example.commercialkiller.data.audio.MelSpectrogramCalculator
import com.example.commercialkiller.data.audio.SpectrogramComparator
import com.example.commercialkiller.data.video.VideoFrameAnalyzer
import com.example.commercialkiller.data.video.VisualFrameDescriptor
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale

class BroadcastClipEvaluationTest {

    private data class DecodedClip(
        val file: File,
        val sha256: String,
        val audioSamples: FloatArray,
        val videoArgbFrames: List<IntArray>,
        val descriptors: List<VisualFrameDescriptor>
    )

    private data class StateAccumulator(
        var ticks: Int = 0,
        var logoDropSum: Double = 0.0,
        var logoDropMax: Float = 0f,
        var blackSilSum: Double = 0.0,
        var blackSilMax: Float = 0f,
        var aspectShiftSum: Double = 0.0,
        var aspectShiftMax: Float = 0f,
        var repeatDistSum: Double = 0.0,
        var repeatDistMin: Float = 1f,
        var repeatMatchMax: Float = 0f
    ) {
        fun record(cues: BroadcastCueSignals) {
            ticks++
            logoDropSum += cues.logoDropoutScore
            if (cues.logoDropoutScore > logoDropMax) logoDropMax = cues.logoDropoutScore
            blackSilSum += cues.blackSilencePulse
            if (cues.blackSilencePulse > blackSilMax) blackSilMax = cues.blackSilencePulse
            aspectShiftSum += cues.aspectRatioShift
            if (cues.aspectRatioShift > aspectShiftMax) aspectShiftMax = cues.aspectRatioShift
            repeatDistSum += cues.repeatAdDistance
            if (cues.repeatAdDistance < repeatDistMin) repeatDistMin = cues.repeatAdDistance
            if (cues.repeatAdMatchScore > repeatMatchMax) repeatMatchMax = cues.repeatAdMatchScore
        }

        fun formatRow(label: String): String {
            if (ticks == 0) {
                return "| $label | 0 | - | - | - | - | - | - | - | - |"
            }
            val n = ticks.toDouble()
            return String.format(
                Locale.US,
                "| %s | %d | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f |",
                label,
                ticks,
                logoDropSum / n,
                logoDropMax,
                blackSilSum / n,
                blackSilMax,
                aspectShiftSum / n,
                aspectShiftMax,
                repeatDistMin,
                repeatDistSum / n
            )
        }
    }

    private data class PassResult(
        val clipName: String,
        val passName: String,
        val sha256: String,
        val durationMs: Long,
        val modalityLabel: String,
        val muteTimestampsMs: List<Long>,
        val unmuteTimestampsMs: List<Long>,
        val programStats: StateAccumulator,
        val commercialStats: StateAccumulator,
        val noMediaStats: StateAccumulator,
        val segments: List<SessionSegment>,
        val stage2Prompts: List<String>,
        val decisions: List<Pair<Long, FrameDecision>>
    )

    private fun resolveTestAssetsDir(): File {
        val candidates = listOf(
            File("test_assets"),
            File("../test_assets"),
            File(System.getProperty("user.dir") ?: ".", "test_assets"),
            File(System.getProperty("user.dir") ?: ".", "../test_assets")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate test_assets directory from ${System.getProperty("user.dir")}")
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun runFfmpegPipe(vararg args: String): ByteArray {
        val pb = ProcessBuilder(args.toList())
        val proc = pb.start()
        val errBytes = ByteArrayOutputStream()
        val errThread = Thread {
            proc.errorStream.use { it.copyTo(errBytes) }
        }.apply { isDaemon = true; start() }
        val out = ByteArrayOutputStream()
        proc.inputStream.use { input ->
            val buf = ByteArray(16384)
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                out.write(buf, 0, read)
            }
        }
        val code = proc.waitFor()
        errThread.join(2000)
        check(code == 0) {
            "ffmpeg exited with code $code for args=${args.joinToString(" ")}: ${errBytes.toString("UTF-8")}"
        }
        return out.toByteArray()
    }

    private fun decodeClip(file: File): DecodedClip {
        val sha = sha256Hex(file)
        val audioBytes = runFfmpegPipe(
            "ffmpeg", "-v", "error", "-i", file.absolutePath,
            "-vn", "-f", "s16le", "-ac", "1", "-ar", "16000", "pipe:1"
        )
        val sampleCount = audioBytes.size / 2
        val audioSamples = FloatArray(sampleCount)
        val bb = ByteBuffer.wrap(audioBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            audioSamples[i] = bb.short.toFloat() / 32768.0f
        }

        val argbFrames = ArrayList<IntArray>()
        val descriptors = ArrayList<VisualFrameDescriptor>()
        if (file.name.endsWith(".mp4", ignoreCase = true)) {
            val videoBytes = runFfmpegPipe(
                "ffmpeg", "-v", "error", "-i", file.absolutePath,
                "-an", "-vf", "fps=4,scale=64:36", "-f", "rawvideo", "-pix_fmt", "bgra", "pipe:1"
            )
            val framePixels = 64 * 36
            val frameByteSize = framePixels * 4
            val frameCount = videoBytes.size / frameByteSize
            var prevDesc: VisualFrameDescriptor? = null
            for (f in 0 until frameCount) {
                val base = f * frameByteSize
                val argb = IntArray(framePixels)
                for (p in 0 until framePixels) {
                    val b = videoBytes[base + p * 4].toInt() and 0xFF
                    val g = videoBytes[base + p * 4 + 1].toInt() and 0xFF
                    val r = videoBytes[base + p * 4 + 2].toInt() and 0xFF
                    val a = videoBytes[base + p * 4 + 3].toInt() and 0xFF
                    argb[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
                argbFrames.add(argb)
                val desc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(
                    pixels = argb,
                    width = 64,
                    height = 36,
                    timestampMs = f * 250L,
                    previous = prevDesc
                )
                descriptors.add(desc)
                prevDesc = desc
            }
        }

        return DecodedClip(
            file = file,
            sha256 = sha,
            audioSamples = audioSamples,
            videoArgbFrames = argbFrames,
            descriptors = descriptors
        )
    }

    private fun runPipelinePass(
        clipName: String,
        passName: String,
        sha256: String,
        audioSamples: FloatArray,
        descriptors: List<VisualFrameDescriptor>,
        simulateMutedBreak: Boolean
    ): PassResult {
        val calculator = MelSpectrogramCalculator(sampleRate = 16000, fftSize = 512, numMelBands = 80)
        val classifier = AudioClassifierEngine(context = null)
        val comparator = SpectrogramComparator()
        val engine = FrameDecisionEngine()
        val tracker = SessionSegmentTracker()

        val muteTimestamps = ArrayList<Long>()
        val unmuteTimestamps = ArrayList<Long>()
        val programStats = StateAccumulator()
        val commercialStats = StateAccumulator()
        val noMediaStats = StateAccumulator()
        val stage2Prompts = ArrayList<String>()
        val decisions = ArrayList<Pair<Long, FrameDecision>>()

        val totalAudioTicks = (audioSamples.size / 1600).coerceAtLeast(1)
        val totalVideoTicks = if (descriptors.isNotEmpty()) ((descriptors.size * 250L) / 100L).toInt() else 0
        val totalTicks = maxOf(totalAudioTicks, totalVideoTicks)

        val windowBuf = FloatArray(512)
        val zeroBuf = FloatArray(512)
        var isTvMuted = false
        var lastAction: TvActionDecision? = null
        var latestSegments: List<SessionSegment> = emptyList()

        for (tick in 0 until totalTicks) {
            val tickMs = tick * 100L
            val offset = tick * 1600
            val mutedBeforeEval = isTvMuted
            if (!mutedBeforeEval && offset + 512 <= audioSamples.size) {
                System.arraycopy(audioSamples, offset, windowBuf, 0, 512)
            } else {
                windowBuf.fill(0f)
            }
            val activeAudio = if (mutedBeforeEval) zeroBuf else windowBuf

            val mel = calculator.computeMelEnergies(activeAudio)
            val tags = classifier.classifyByAcousticFeatures(activeAudio)
            val shift = comparator.compare(mel).distance
            val visual = if (descriptors.isNotEmpty()) {
                val vIdx = (tickMs / 250L).toInt().coerceAtMost(descriptors.lastIndex)
                descriptors[vIdx]
            } else {
                null
            }

            val decision = engine.evaluateFrame(
                melEnergies = mel,
                classifierTags = tags,
                shiftDistance = shift,
                timestampMs = tickMs,
                visualDescriptor = visual,
                isTvMuted = mutedBeforeEval
            )
            decisions.add(tickMs to decision)

            when {
                !decision.isMedia -> noMediaStats.record(decision.broadcastCues)
                decision.isCommercial -> commercialStats.record(decision.broadcastCues)
                else -> programStats.record(decision.broadcastCues)
            }

            if (decision.recommendedAction == TvActionDecision.MUTE && lastAction != TvActionDecision.MUTE) {
                muteTimestamps.add(tickMs)
                if (simulateMutedBreak) {
                    isTvMuted = true
                }
            } else if (decision.recommendedAction == TvActionDecision.UNMUTE && lastAction != TvActionDecision.UNMUTE) {
                unmuteTimestamps.add(tickMs)
                if (simulateMutedBreak) {
                    isTvMuted = false
                }
            }
            if (decision.recommendedAction != TvActionDecision.HOLD) {
                lastAction = decision.recommendedAction
            }

            val (segs, opened) = tracker.onFrame(
                timestampMs = tickMs,
                decision = decision,
                melEnergies = mel,
                thumbnail = null
            )
            latestSegments = segs

            if (opened != null) {
                val match = visual?.let { engine.programCache.scoreAgainstProgram(it) }
                val topTags = tags.filter { it.score >= 0.10f }.map { it.label }
                val prompt = LiteRtLmDecisionVerifier.buildPrompt(
                    Stage2TransitionFeatures(
                        mediaScore = decision.mediaScore,
                        commercialScore = decision.commercialScore,
                        visualChangeScore = decision.visualChangeScore,
                        prototypeSimilarity = match?.prototypeSimilarity ?: decision.bestAngleSimilarity,
                        hasStableBug = match?.hasStableBug ?: false,
                        bugSimilarity = match?.bugSimilarity ?: 0f,
                        cutsPerMin = maxOf(decision.cutCadencePerMin, decision.visualCutCadencePerMin),
                        topTags = topTags,
                        isTvMuted = mutedBeforeEval,
                        logoDropoutScore = decision.broadcastCues.logoDropoutScore,
                        blackSilencePulse = decision.broadcastCues.blackSilencePulse,
                        aspectRatioShift = decision.broadcastCues.aspectRatioShift,
                        repeatAdDistance = decision.broadcastCues.repeatAdDistance
                    ),
                    noThink = true
                )
                stage2Prompts.add("[t=${tickMs}ms ${opened.label.name}] $prompt")
            }
        }

        val modality = if (descriptors.isEmpty()) {
            "AUDIO_ONLY"
        } else if (simulateMutedBreak) {
            "AUDIO_VIDEO -> VIDEO_ONLY_MUTED"
        } else {
            "AUDIO_VIDEO"
        }

        return PassResult(
            clipName = clipName,
            passName = passName,
            sha256 = sha256,
            durationMs = totalTicks * 100L,
            modalityLabel = modality,
            muteTimestampsMs = muteTimestamps,
            unmuteTimestampsMs = unmuteTimestamps,
            programStats = programStats,
            commercialStats = commercialStats,
            noMediaStats = noMediaStats,
            segments = latestSegments,
            stage2Prompts = stage2Prompts,
            decisions = decisions
        )
    }

    @Test
    fun `evaluate all test_assets broadcast clips and concatenated repeat-ad stream`() {
        if (System.getProperty("runBroadcastEval") != "true") {
            return
        }

        val assetsDir = resolveTestAssetsDir()
        val files = assetsDir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
        assertTrue("Expected 10 files in test_assets, found ${files.size}", files.size == 10)

        val decodedByName = LinkedHashMap<String, DecodedClip>(files.size)
        for (file in files) {
            decodedByName[file.name] = decodeClip(file)
        }

        val shaGroups = decodedByName.values.groupBy { it.sha256 }
        val md = StringBuilder()
        md.appendLine("# Commercial Killer v2.7 Broadcast Cue Evaluation (`test_assets/`)")
        md.appendLine()
        md.appendLine("- Pipeline: `MelSpectrogramCalculator(80)` + `AudioClassifierEngine(null)` + `SpectrogramComparator` + `VideoFrameAnalyzer(64x36@4Hz)` + `FrameDecisionEngine` (`v2.7` Cues 1–4) + `SessionSegmentTracker` + `LiteRtLmDecisionVerifier.buildPrompt`")
        md.appendLine("- Clip count: `${files.size}` files in `test_assets/`")

        val duplicatePairs = shaGroups.values.filter { it.size > 1 }
        for (group in duplicatePairs) {
            val names = group.joinToString(", ") { "`${it.file.name}`" }
            md.appendLine("- SHA-256 duplicate pair confirmed: $names (`${group.first().sha256}`)")
        }
        md.appendLine()

        val allPasses = ArrayList<PassResult>()
        for (decoded in decodedByName.values) {
            val unmuted = runPipelinePass(
                clipName = decoded.file.name,
                passName = "Unmuted",
                sha256 = decoded.sha256,
                audioSamples = decoded.audioSamples,
                descriptors = decoded.descriptors,
                simulateMutedBreak = false
            )
            allPasses.add(unmuted)

            if (decoded.descriptors.isNotEmpty()) {
                val simMute = runPipelinePass(
                    clipName = decoded.file.name,
                    passName = "SimMute",
                    sha256 = decoded.sha256,
                    audioSamples = decoded.audioSamples,
                    descriptors = decoded.descriptors,
                    simulateMutedBreak = true
                )
                allPasses.add(simMute)
            }
        }

        // Concatenated multi-pod repeat-ad stream:
        // 0..14.0s show_angles_ad_return (29..43s Program) + 14.0..15.2s show_angles_ad_return (15.3..16.5s break entry -> MUTE)
        // 15.2..30.2s vw_commercial_1997 (15..30s Ad Pod 1, muted)
        // 30.2..44.2s show_angles_ad_return (29..43s Program Return -> UNMUTE)
        // 44.2..59.2s frosted_flakes_1976_ad (15..30s Ad Pod 2, unmuted, +29s gap)
        val showClip = decodedByName.getValue("show_angles_ad_return.mp4")
        val pod1Clip = decodedByName.getValue("vw_commercial_1997.mp4")
        val pod2Clip = decodedByName.getValue("frosted_flakes_1976_ad.mp4")

        data class SliceSpec(val clip: DecodedClip, val startMs: Long, val durationMs: Long)
        val slices = listOf(
            SliceSpec(showClip, 29_000L, 14_000L),
            SliceSpec(showClip, 15_300L, 1_200L),
            SliceSpec(pod1Clip, 15_000L, 15_000L),
            SliceSpec(showClip, 29_000L, 14_000L),
            SliceSpec(pod2Clip, 15_000L, 15_000L)
        )

        val totalConcatSamples = slices.sumOf { ((it.durationMs * 16000L) / 1000L).toInt() }
        val concatAudio = FloatArray(totalConcatSamples)
        val concatDescriptors = ArrayList<VisualFrameDescriptor>()
        var prevConcatDesc: VisualFrameDescriptor? = null
        var audioCursor = 0
        var timeCursorMs = 0L

        for (slice in slices) {
            val startSample = ((slice.startMs * 16000L) / 1000L).toInt()
            val sampleLen = ((slice.durationMs * 16000L) / 1000L).toInt()
            val avail = (slice.clip.audioSamples.size - startSample).coerceAtLeast(0)
            val copyLen = minOf(sampleLen, avail)
            if (copyLen > 0) {
                System.arraycopy(slice.clip.audioSamples, startSample, concatAudio, audioCursor, copyLen)
            }
            audioCursor += sampleLen

            val frameCount = (slice.durationMs / 250L).toInt()
            val startFrame = (slice.startMs / 250L).toInt()
            for (f in 0 until frameCount) {
                val srcFrameIdx = (startFrame + f).coerceAtMost(slice.clip.videoArgbFrames.lastIndex)
                val tsMs = timeCursorMs + f * 250L
                val desc = VideoFrameAnalyzer.computeDescriptorFromArgbPixels(
                    pixels = slice.clip.videoArgbFrames[srcFrameIdx],
                    width = 64,
                    height = 36,
                    timestampMs = tsMs,
                    previous = prevConcatDesc
                )
                concatDescriptors.add(desc)
                prevConcatDesc = desc
            }
            timeCursorMs += slice.durationMs
        }

        val concatPass = runPipelinePass(
            clipName = "concat_repeat_ad_stream_59s",
            passName = "MultiPod-Cue4",
            sha256 = "in-memory-concat(show_program+break+vw_1997+show_return+frosted_flakes_1976)",
            audioSamples = concatAudio,
            descriptors = concatDescriptors,
            simulateMutedBreak = true
        )
        allPasses.add(concatPass)

        // Verify Cue 4 assertions on concatenated multi-pod repeat-ad stream
        val segC = concatPass.decisions.filter { (t, _) -> t in 30_200L until 44_200L }
        val segD = concatPass.decisions.filter { (t, _) -> t in 44_200L until 59_200L }
        val segCMinDist = segC.minOf { it.second.broadcastCues.repeatAdDistance }
        val segCMaxScore = segC.maxOf { it.second.broadcastCues.repeatAdMatchScore }
        val segDMinDist = segD.minOf { it.second.broadcastCues.repeatAdDistance }
        val segDMaxScore = segD.maxOf { it.second.broadcastCues.repeatAdMatchScore }

        md.appendLine("## Summary Table (All 10 `test_assets/` Clips + Multi-Pod Repeat-Ad Stream)")
        md.appendLine()
        md.appendLine("| Clip | Pass | Duration (ms) | Modality | MUTE ms | UNMUTE ms | Segments |")
        md.appendLine("|---|---|---|---|---|---|---|")
        for (p in allPasses) {
            val mutes = if (p.muteTimestampsMs.isEmpty()) "none" else p.muteTimestampsMs.joinToString(",")
            val unmutes = if (p.unmuteTimestampsMs.isEmpty()) "none" else p.unmuteTimestampsMs.joinToString(",")
            val segDesc = p.segments.joinToString(" -> ") { "${it.label.name}(${it.startMs}..${it.endMs})" }
            md.appendLine("| `${p.clipName}` | ${p.passName} | ${p.durationMs} | ${p.modalityLabel} | $mutes | $unmutes | $segDesc |")
        }
        md.appendLine()

        md.appendLine("## Multi-Pod Cue 4 Verification (`concat_repeat_ad_stream_59s`)")
        md.appendLine()
        md.appendLine("- Segment A (`0..15,200 ms`): `show_angles_ad_return.mp4` (`29..43 s` Program + `15.3..16.5 s` break entry)")
        md.appendLine("- Segment B (`15,200..30,200 ms`): `vw_commercial_1997.mp4` (`15..30 s`, Ad Pod 1, muted)")
        md.appendLine(String.format(Locale.US, "- Segment C (`30,200..44,200 ms`): `show_angles_ad_return.mp4` (`29..43 s`, Program Return) -> `min(repeatAdDistance)=%.4f`, `max(repeatAdMatchScore)=%.4f`", segCMinDist, segCMaxScore))
        md.appendLine(String.format(Locale.US, "- Segment D (`44,200..59,200 ms`): `frosted_flakes_1976_ad.mp4` (`15..30 s`, Ad Pod 2, `+29s` gap) -> `min(repeatAdDistance)=%.4f`, `max(repeatAdMatchScore)=%.4f`", segDMinDist, segDMaxScore))
        md.appendLine()

        md.appendLine("## Per-Clip Cue Distributions & Stage 2 Prompts")
        md.appendLine()
        for (p in allPasses) {
            md.appendLine("### `${p.clipName}` (${p.passName})")
            md.appendLine("- SHA-256: `${p.sha256}`")
            md.appendLine()
            md.appendLine("| State | Ticks | logoDrop mean | logoDrop max | blkSil mean | blkSil max | arShift mean | arShift max | repDist min | repDist mean |")
            md.appendLine("|---|---|---|---|---|---|---|---|---|---|")
            md.appendLine(p.programStats.formatRow("PROGRAM"))
            md.appendLine(p.commercialStats.formatRow("COMMERCIAL"))
            md.appendLine(p.noMediaStats.formatRow("NO_MEDIA"))
            md.appendLine()
            md.appendLine("Stage 2 prompts emitted on segment opens (${p.stage2Prompts.size}):")
            for (line in p.stage2Prompts) {
                md.appendLine("- `$line`")
            }
            md.appendLine()
        }

        val report = md.toString()
        println(report)

        val outPath = System.getProperty("broadcastEvalOut")?.trim().orEmpty()
        if (outPath.isNotEmpty()) {
            val outFile = File(outPath)
            outFile.parentFile?.mkdirs()
            outFile.writeText(report)
        }

        assertTrue(
            "Expected Pod 2 (45..60s) repeatAdDistance <= 0.25, got $segDMinDist",
            segDMinDist <= RepeatAdFingerprintIndex.REPEAT_MATCH_DISTANCE_THRESHOLD
        )
        assertTrue(
            "Expected Pod 2 (45..60s) repeatAdMatchScore > 0, got $segDMaxScore",
            segDMaxScore > 0f
        )
        assertTrue(
            "Expected Segment C program return (30..45s) to exclude repeat match (minDist=$segCMinDist, maxScore=$segCMaxScore)",
            segCMinDist > RepeatAdFingerprintIndex.REPEAT_MATCH_DISTANCE_THRESHOLD && segCMaxScore == 0f
        )
    }
}
