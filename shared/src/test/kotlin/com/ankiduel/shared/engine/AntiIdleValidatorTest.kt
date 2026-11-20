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

    @Test
    fun `keeps idle flag active on zero-delta polls and does not erase idle cap on transient API drop`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        val r1 = validator.evaluate(AntiIdleValidator.State(), rawAnkiSeconds = 600L, cardsReviewed = 0, ankiApiConnected = true)
        assertEquals(180L, r1.effectiveSeconds)
        assertTrue(r1.isIdleFlagged)

        // Next 15s poll with no new Anki foreground time must stay flagged until cards are reviewed
        val r2 = validator.evaluate(r1.nextState, rawAnkiSeconds = 600L, cardsReviewed = 0, ankiApiConnected = true)
        assertEquals(180L, r2.effectiveSeconds)
        assertTrue(r2.isIdleFlagged)

        // Transient API disconnect must NOT retroactively credit the 420s of previously capped idle time
        val r3 = validator.evaluate(r2.nextState, rawAnkiSeconds = 630L, cardsReviewed = 0, ankiApiConnected = false)
        assertEquals(210L, r3.effectiveSeconds)
        assertEquals(420L, r3.idleCappedSeconds)
    }

    @Test
    fun `credits full 16 min session when extraReviewActivity signals learning or cloze reviews while rated_1 stays flat`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        // First 3 min: 15 notes rated for the first time today
        var state = validator.evaluate(
            AntiIdleValidator.State(),
            rawAnkiSeconds = 180L,
            cardsReviewed = 15,
            ankiApiConnected = true
        ).nextState

        // Next 13 min (13 polls of 60s): re-reviewing learning cards / cloze siblings -> rated:1 stays 15,
        // but deck_count / rating buckets change -> extraReviewActivity = true
        var lastResult = validator.evaluate(state, rawAnkiSeconds = 180L, cardsReviewed = 15, ankiApiConnected = true)
        for (min in 4..16) {
            lastResult = validator.evaluate(
                previous = state,
                rawAnkiSeconds = min * 60L,
                cardsReviewed = 15,
                ankiApiConnected = true,
                extraReviewActivity = true
            )
            state = lastResult.nextState
        }

        assertEquals(960L, lastResult.effectiveSeconds)
        assertEquals(0L, lastResult.idleCappedSeconds)
        assertFalse(lastResult.isIdleFlagged)
    }

    @Test
    fun `credits delayed UsageStats flush using banked cards reviewed while rawDelta was zero`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        // Initial 6 min session already flushed
        val r1 = validator.evaluate(AntiIdleValidator.State(), rawAnkiSeconds = 360L, cardsReviewed = 20, ankiApiConnected = true)
        assertEquals(360L, r1.effectiveSeconds)

        // User studies 10 more minutes and reviews 15 cards while UsageStats has not flushed yet (raw still 360s)
        val r2 = validator.evaluate(r1.nextState, rawAnkiSeconds = 360L, cardsReviewed = 35, ankiApiConnected = true)
        assertEquals(360L, r2.effectiveSeconds)
        assertFalse(r2.isIdleFlagged)

        // User exits AnkiDroid -> UsageStats flushes +600s (total 960s = 16 min) with cardsDelta = 0
        val r3 = validator.evaluate(r2.nextState, rawAnkiSeconds = 960L, cardsReviewed = 35, ankiApiConnected = true)
        assertEquals(960L, r3.effectiveSeconds)
        assertEquals(0L, r3.idleCappedSeconds)
        assertFalse(r3.isIdleFlagged)
    }

    @Test
    fun `preserves validated time when rawAnkiSeconds transiently drops within the same day`() {
        val validator = AntiIdleValidator(graceSecondsPerWindow = 180L)
        val r1 = validator.evaluate(AntiIdleValidator.State(), rawAnkiSeconds = 960L, cardsReviewed = 40, ankiApiConnected = true)
        assertEquals(960L, r1.effectiveSeconds)

        // UsageEvents buffer evicts earlier morning session -> raw drops to 360s
        val r2 = validator.evaluate(r1.nextState, rawAnkiSeconds = 360L, cardsReviewed = 40, ankiApiConnected = true)
        assertEquals(960L, r2.effectiveSeconds)
        assertEquals(960L, r2.nextState.lastRawAnkiSeconds)
        assertFalse(r2.isIdleFlagged)
    }
}
