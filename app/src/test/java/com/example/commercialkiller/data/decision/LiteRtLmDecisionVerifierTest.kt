// Author Attribution: Co-authored by Project Owner & LLM-Gemini4Neon.
package com.example.commercialkiller.data.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmDecisionVerifierTest {

    @Test
    fun `parseConstrainedVerdict extracts single token verdicts reliably`() {
        assertEquals(Stage2Decision.COMMERCIAL, LiteRtLmDecisionVerifier.parseConstrainedVerdict("COMMERCIAL"))
        assertEquals(Stage2Decision.PROGRAM, LiteRtLmDecisionVerifier.parseConstrainedVerdict("  program.\n"))
        assertEquals(Stage2Decision.NON_MEDIA, LiteRtLmDecisionVerifier.parseConstrainedVerdict("NON_MEDIA"))
        assertEquals(Stage2Decision.NON_MEDIA, LiteRtLmDecisionVerifier.parseConstrainedVerdict("non-media"))
        assertNull(LiteRtLmDecisionVerifier.parseConstrainedVerdict(""))
        assertNull(LiteRtLmDecisionVerifier.parseConstrainedVerdict("uncertain response"))
    }

    @Test
    fun `buildPrompt formats compact numeric transition features without nulls`() {
        val prompt = LiteRtLmDecisionVerifier.buildPrompt(
            Stage2TransitionFeatures(
                mediaScore = 0.92f,
                commercialScore = 0.81f,
                visualChangeScore = 0.44f,
                prototypeSimilarity = 0.35f,
                hasStableBug = true,
                bugSimilarity = 0.22f,
                cutsPerMin = 18.0f,
                topTags = listOf("Music", "Jingle"),
                isTvMuted = true
            )
        )
        assertTrue(prompt.contains("media=0.92"))
        assertTrue(prompt.contains("comm=0.81"))
        assertTrue(prompt.contains("bugStable=true"))
        assertTrue(prompt.contains("muted=true"))
        assertTrue(prompt.contains("tags=[Music,Jingle]"))
    }

    @Test
    fun `parseConstrainedVerdict strips think blocks from Qwen3 outputs`() {
        val wrappedCommercial = "<think>\nChecking cuts=22.0 and Jingle tags.\n</think>\nCOMMERCIAL"
        val wrappedProgram = "<think></think> PROGRAM."
        val wrappedNonMedia = "<think>Silence floor</think>\nNON_MEDIA"

        assertEquals(Stage2Decision.COMMERCIAL, LiteRtLmDecisionVerifier.parseConstrainedVerdict(wrappedCommercial))
        assertEquals(Stage2Decision.PROGRAM, LiteRtLmDecisionVerifier.parseConstrainedVerdict(wrappedProgram))
        assertEquals(Stage2Decision.NON_MEDIA, LiteRtLmDecisionVerifier.parseConstrainedVerdict(wrappedNonMedia))
    }

    @Test
    fun `parseConstrainedVerdict selects final token and buildPrompt adds discrete cues`() {
        assertEquals(
            Stage2Decision.PROGRAM,
            LiteRtLmDecisionVerifier.parseConstrainedVerdict("Not NON_MEDIA, final label is PROGRAM")
        )
        assertEquals(
            Stage2Decision.NON_MEDIA,
            LiteRtLmDecisionVerifier.parseConstrainedVerdict("OFF_AIR")
        )
        assertEquals(
            Stage2Decision.NON_MEDIA,
            LiteRtLmDecisionVerifier.parseConstrainedVerdict("SILENCE")
        )

        val commercialPrompt = LiteRtLmDecisionVerifier.buildPrompt(
            Stage2TransitionFeatures(
                mediaScore = 0.92f,
                commercialScore = 0.81f,
                visualChangeScore = 0.44f,
                prototypeSimilarity = 0.35f,
                hasStableBug = true,
                bugSimilarity = 0.22f,
                cutsPerMin = 18.0f,
                topTags = listOf("Music", "Jingle"),
                isTvMuted = false
            )
        )
        assertTrue(commercialPrompt.contains("commercial break"))

        val returnPrompt = LiteRtLmDecisionVerifier.buildPrompt(
            Stage2TransitionFeatures(
                mediaScore = 1.00f,
                commercialScore = 0.22f,
                visualChangeScore = 0.12f,
                prototypeSimilarity = 0.62f,
                hasStableBug = true,
                bugSimilarity = 0.86f,
                cutsPerMin = 2.0f,
                topTags = listOf("Silence"),
                isTvMuted = true
            )
        )
        assertTrue(returnPrompt.contains("Main TV show program is actively playing on screen"))
        assertTrue(returnPrompt.contains("tags=[video-only]"))
        assertTrue(!returnPrompt.contains("tags=[Silence]"))
    }

    @Test
    fun `buildPrompt formats Cue 1-4 telemetry and updates commercialBreak and programReturn conditions`() {
        val cuePrompt = LiteRtLmDecisionVerifier.buildPrompt(
            Stage2TransitionFeatures(
                mediaScore = 0.88f,
                commercialScore = 0.62f,
                visualChangeScore = 0.15f,
                prototypeSimilarity = 0.70f,
                hasStableBug = true,
                bugSimilarity = 0.35f,
                cutsPerMin = 6.0f,
                topTags = listOf("Speech"),
                isTvMuted = false,
                logoDropoutScore = 0.64f,
                blackSilencePulse = 0.52f,
                aspectRatioShift = 0.48f,
                repeatAdDistance = 0.18f
            ),
            noThink = true
        )
        assertTrue(cuePrompt.contains("logoDrop=0.64"))
        assertTrue(cuePrompt.contains("blkSil=0.52"))
        assertTrue(cuePrompt.contains("arShift=0.48"))
        assertTrue(cuePrompt.contains("repDist=0.18"))
        assertTrue(cuePrompt.contains("commercial break"))
        assertTrue(cuePrompt.endsWith("/no_think"))

        val blockedReturnAspect = LiteRtLmDecisionVerifier.buildPrompt(
            Stage2TransitionFeatures(
                mediaScore = 1.00f,
                commercialScore = 0.25f,
                visualChangeScore = 0.10f,
                prototypeSimilarity = 0.65f,
                hasStableBug = true,
                bugSimilarity = 0.82f,
                cutsPerMin = 2.0f,
                topTags = listOf("Silence"),
                isTvMuted = true,
                aspectRatioShift = 0.60f,
                repeatAdDistance = 0.90f
            )
        )
        assertTrue(!blockedReturnAspect.contains("matching video scene and channel logo"))

        val blockedReturnRepeat = LiteRtLmDecisionVerifier.buildPrompt(
            Stage2TransitionFeatures(
                mediaScore = 1.00f,
                commercialScore = 0.25f,
                visualChangeScore = 0.10f,
                prototypeSimilarity = 0.65f,
                hasStableBug = true,
                bugSimilarity = 0.82f,
                cutsPerMin = 2.0f,
                topTags = listOf("Silence"),
                isTvMuted = true,
                aspectRatioShift = 0.10f,
                repeatAdDistance = 0.15f
            )
        )
        assertTrue(!blockedReturnRepeat.contains("matching video scene and channel logo"))
    }
}
