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
}
