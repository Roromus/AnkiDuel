package com.ankiduel.shared.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AntiIdleValidatorTest {

    @Test
    fun `credits full time while cards are being reviewed within grace window`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        val r1 = validator.evaluate(AntiIdleValidator.State(), rawAnkiSeconds = 120L, cardsReviewed = 5, ankiApiConnected = true)
        assertEquals(120L, r1.effectiveSeconds)
        assertFalse(r1.isIdleFlagged)

        val r2 = validator.evaluate(r1.nextState, rawAnkiSeconds = 240L, cardsReviewed = 12, ankiApiConnected = true)
        assertEquals(240L, r2.effectiveSeconds)
        assertFalse(r2.isIdleFlagged)
    }

    @Test
    fun `caps idle time when screen stays open without new card reviews`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        val r1 = validator.evaluate(AntiIdleValidator.State(), rawAnkiSeconds = 60L, cardsReviewed = 4, ankiApiConnected = true)
        assertEquals(60L, r1.effectiveSeconds)

        // 10 minutes pass with 0 new cards reviewed -> 180s total grace since last review (60s in r1 + 120s in r2)
        val r2 = validator.evaluate(r1.nextState, rawAnkiSeconds = 660L, cardsReviewed = 4, ankiApiConnected = true)
        assertEquals(180L, r2.effectiveSeconds)
        assertEquals(480L, r2.idleCappedSeconds)
        assertTrue(r2.isIdleFlagged)

        // Player resumes reviewing cards -> window resets and new activity counts again
        val r3 = validator.evaluate(r2.nextState, rawAnkiSeconds = 720L, cardsReviewed = 6, ankiApiConnected = true)
        assertEquals(240L, r3.effectiveSeconds)
        assertFalse(r3.isIdleFlagged)
    }
}
