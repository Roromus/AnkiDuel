package com.ankiduel.shared.engine

import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantState
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.ReferenceMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuelDeltaCalculatorTest {

    @Test
    fun `net score subtracts social penalty from anki time`() {
        val net = DuelDeltaCalculator.computeNetScoreSeconds(
            ankiEffectiveSeconds = 1800L, // 30 min
            socialSeconds = 600L,         // 10 min
            socialPenaltyRatio = 1.0
        )
        assertEquals(1200L, net)
    }

    @Test
    fun `1v1 leader mode triggers debt when delta exceeds tolerance`() {
        val duel = DuelState(
            duelId = "d1",
            inviteCode = "ANKI-1234",
            creatorId = "u1",
            config = DuelConfig(
                title = "Duel Médecine",
                maxPlayers = 2,
                referenceMode = ReferenceMode.LEADER,
                cycle = DuelCycle.DAILY_MIDNIGHT,
                toleranceMinutes = 10,
                dailyDebtCapMinutes = 60
            ),
            status = DuelStatus.ACTIVE,
            cycleKey = "2026-11-19",
            referenceScoreSeconds = 0L,
            participants = listOf(
                ParticipantState(
                    userId = "u1",
                    username = "Alice",
                    ankiEffectiveSeconds = 2400L, // 40 min
                    socialSeconds = 300L          // 5 min -> net 35 min
                ),
                ParticipantState(
                    userId = "u2",
                    username = "Bob",
                    ankiEffectiveSeconds = 600L,  // 10 min
                    socialSeconds = 600L          // 10 min -> net 0 min
                )
            ),
            createdAtEpochMs = 1L
        )

        val result = DuelDeltaCalculator.recalculateDuel(duel)
        val alice = result.participants.first { it.userId == "u1" }
        val bob = result.participants.first { it.userId == "u2" }

        assertEquals(2100L, alice.netScoreSeconds)
        assertEquals(0, alice.debtMinutes)

        // Delta = 35 min, tolerance = 10 min => debt = 25 min
        assertEquals(0L, bob.netScoreSeconds)
        assertEquals(25, bob.debtMinutes)
        assertEquals(1500L, bob.debtSeconds)
    }

    @Test
    fun `group average mode compares against average of other active players and respects cap`() {
        val duel = DuelState(
            duelId = "d2",
            inviteCode = "ANKI-9999",
            creatorId = "u1",
            config = DuelConfig(
                title = "Groupe PACES",
                maxPlayers = 5,
                referenceMode = ReferenceMode.AVERAGE,
                toleranceMinutes = 5,
                dailyDebtCapMinutes = 30
            ),
            status = DuelStatus.ACTIVE,
            cycleKey = "2026-11-19",
            referenceScoreSeconds = 0L,
            participants = listOf(
                ParticipantState(userId = "u1", username = "A", ankiEffectiveSeconds = 3600L), // 60 min
                ParticipantState(userId = "u2", username = "B", ankiEffectiveSeconds = 2400L), // 40 min
                ParticipantState(userId = "u3", username = "C", ankiEffectiveSeconds = 0L),    // 0 min -> avg others = 50m, delta = 50m, excess = 45m, capped = 30m
                ParticipantState(userId = "u4", username = "D", ankiEffectiveSeconds = 0L, status = ParticipantStatus.FORFEITED)
            ),
            createdAtEpochMs = 1L
        )

        val result = DuelDeltaCalculator.recalculateDuel(duel)
        val c = result.participants.first { it.userId == "u3" }
        val d = result.participants.first { it.userId == "u4" }

        assertEquals(30, c.debtMinutes)
        assertEquals(0, d.debtMinutes)
    }

    @Test
    fun `global lock picks worst debt across simultaneous duels and unlocks during joker`() {
        val d1 = DuelDeltaCalculator.recalculateDuel(
            DuelState(
                duelId = "d1",
                inviteCode = "ANKI-1",
                creatorId = "u2",
                config = DuelConfig(title = "Duel 1", toleranceMinutes = 0),
                status = DuelStatus.ACTIVE,
                cycleKey = "2026-11-19",
                referenceScoreSeconds = 0L,
                participants = listOf(
                    ParticipantState(userId = "u1", username = "Moi", ankiEffectiveSeconds = 0L),
                    ParticipantState(userId = "u2", username = "Adv1", ankiEffectiveSeconds = 900L) // 15m
                ),
                createdAtEpochMs = 1L
            )
        )
        val d2 = DuelDeltaCalculator.recalculateDuel(
            DuelState(
                duelId = "d2",
                inviteCode = "ANKI-2",
                creatorId = "u3",
                config = DuelConfig(title = "Duel 2", toleranceMinutes = 0),
                status = DuelStatus.ACTIVE,
                cycleKey = "2026-11-19",
                referenceScoreSeconds = 0L,
                participants = listOf(
                    ParticipantState(userId = "u1", username = "Moi", ankiEffectiveSeconds = 0L, jokerActiveUntilEpochMs = 2000L),
                    ParticipantState(userId = "u3", username = "Adv2", ankiEffectiveSeconds = 1800L) // 30m
                ),
                createdAtEpochMs = 1L
            )
        )

        val locked = DuelDeltaCalculator.computeGlobalLockStatus("u1", listOf(d1, d2), nowEpochMs = 3000L)
        assertTrue(locked.isLocked)
        assertEquals(30, locked.maxDebtMinutes)
        assertEquals("d2", locked.blockingDuelId)

        val duringJoker = DuelDeltaCalculator.computeGlobalLockStatus("u1", listOf(d1, d2), nowEpochMs = 1500L)
        assertFalse(duringJoker.isLocked)
        assertEquals(30, duringJoker.maxDebtMinutes)
    }
}
