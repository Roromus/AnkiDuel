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

    @Test
    fun `credits full gap when multiple cards were reviewed while background sync was paused`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        // Phone killed background service for 20 min (1200s) while user reviewed 25 cards
        val r1 = validator.evaluate(AntiIdleValidator.State(), rawAnkiSeconds = 1200L, cardsReviewed = 25, ankiApiConnected = true)
        assertEquals(1200L, r1.effectiveSeconds)
        assertFalse(r1.isIdleFlagged)

        // Next short poll shouldn't immediately flag idle
        val r2 = validator.evaluate(r1.nextState, rawAnkiSeconds = 1230L, cardsReviewed = 25, ankiApiConnected = true)
        assertEquals(1230L, r2.effectiveSeconds)
        assertFalse(r2.isIdleFlagged)
    }

    @Test
    fun `preserves prior effective seconds when Anki day rollover resets card count mid-day`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        val r1 = validator.evaluate(AntiIdleValidator.State(), rawAnkiSeconds = 600L, cardsReviewed = 20, ankiApiConnected = true)
        assertEquals(600L, r1.effectiveSeconds)

        // At 4am Anki day rollover, cardsReviewed resets to 0 while rawAnkiSeconds stays >= 600
        val r2 = validator.evaluate(r1.nextState, rawAnkiSeconds = 660L, cardsReviewed = 0, ankiApiConnected = true)
        assertEquals(660L, r2.effectiveSeconds)

        // New cards reviewed after rollover continue crediting seamlessly
        val r3 = validator.evaluate(r2.nextState, rawAnkiSeconds = 840L, cardsReviewed = 5, ankiApiConnected = true)
        assertEquals(840L, r3.effectiveSeconds)
        assertFalse(r3.isIdleFlagged)
    }
}
