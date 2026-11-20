package com.ankiduel.android.data

import com.ankiduel.shared.engine.DuelDeltaCalculator
import com.ankiduel.shared.model.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBotEngineTest {

    @Test
    fun `demo bot duel starts locked and unlocks after simulated anki study`() {
        val engine = DemoBotEngine(userId = "me", username = "Moi")
        val initialSnapshot = UsageSnapshot(
            ankiForegroundSeconds = 0L,
            ankiEffectiveSeconds = 0L,
            cardsReviewed = 0,
            socialForegroundSeconds = 0L,
            trackedSocialPackages = setOf("com.instagram.android"),
            capturedAtEpochMs = 10_000L
        )

        val duelsBefore = engine.syncSnapshot(initialSnapshot)
        val lockBefore = DuelDeltaCalculator.computeGlobalLockStatus("me", duelsBefore, nowEpochMs = 10_000L)
        // Camille_Bot has +20m net, tolerance 5m -> 15m debt
        assertTrue(lockBefore.isLocked)
        assertEquals(15, lockBefore.maxDebtMinutes)

        // Study 20m on AnkiDroid -> net +20m matches Camille_Bot -> unlocked
        val afterStudy = engine.syncSnapshot(
            initialSnapshot.copy(
                ankiForegroundSeconds = 20 * 60L,
                ankiEffectiveSeconds = 20 * 60L,
                cardsReviewed = 40
            )
        )
        val lockAfter = DuelDeltaCalculator.computeGlobalLockStatus("me", afterStudy, nowEpochMs = 10_000L)
        assertFalse(lockAfter.isLocked)
        assertEquals(0, lockAfter.maxDebtMinutes)
    }

    @Test
    fun `reset clears simulated bonuses and joker activation is idempotent while active`() {
        val engine = DemoBotEngine(userId = "me", username = "Moi")
        engine.addSimulatedAnkiMinutes(15)
        engine.addSimulatedSocialMinutes(10)
        assertEquals(900L, engine.simulatedAnkiBonusSeconds)
        assertEquals(600L, engine.simulatedSocialBonusSeconds)

        engine.resetDefaultDemoDuels()
        assertEquals(0L, engine.simulatedAnkiBonusSeconds)
        assertEquals(0L, engine.simulatedSocialBonusSeconds)

        // First joker consumes 1 joker; immediate second call while still active is idempotent
        val duel = engine.createDuel(
            com.ankiduel.shared.model.DuelConfig(
                title = "Test Joker",
                jokersPerWeek = 2
            )
        )
        assertTrue(engine.activateJoker(duel.duelId))
        assertTrue(engine.activateJoker(duel.duelId))
        val updated = engine.listDuels().first { it.duelId == duel.duelId }
        val me = updated.participants.first { it.userId == "me" }
        assertEquals(1, me.jokersRemainingThisWeek)
    }
}
