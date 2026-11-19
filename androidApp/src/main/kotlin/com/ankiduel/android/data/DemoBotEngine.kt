package com.ankiduel.android.data

import com.ankiduel.shared.engine.DuelDeltaCalculator
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantState
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.ReferenceMode
import com.ankiduel.shared.model.UsageSnapshot
import java.time.LocalDate
import java.util.UUID

/**
 * Moteur de simulation local permettant de tester l'intégralité du système de duels,
 * de comparaison AnkiDroid/Réseaux Sociaux et de verrouillage sans avoir besoin d'un serveur distant.
 */
class DemoBotEngine(
    private val userId: String = "local-demo-user",
    private val username: String = "Moi"
) {
    private val duels = mutableListOf<DuelState>()

    // Temps additionnel simulé par l'utilisateur depuis l'écran de démo pour tester facilement
    var simulatedAnkiBonusSeconds: Long = 0L
        private set
    var simulatedSocialBonusSeconds: Long = 0L
        private set

    init {
        resetDefaultDemoDuels()
    }

    fun resetDefaultDemoDuels() {
        duels.clear()
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()

        val duel1 = DuelDeltaCalculator.recalculateDuel(
            DuelState(
                duelId = "demo-duel-1",
                inviteCode = "ANKI-DEMO",
                creatorId = "bot-camille",
                config = DuelConfig(
                    title = "Duel Médecine 1v1",
                    maxPlayers = 2,
                    referenceMode = ReferenceMode.LEADER,
                    cycle = DuelCycle.DAILY_MIDNIGHT,
                    toleranceMinutes = 5,
                    dailyDebtCapMinutes = 60,
                    jokerEnabled = true,
                    jokerDurationMinutes = 5,
                    jokersPerWeek = 1,
                    mandatorySocialPackages = setOf("com.instagram.android")
                ),
                status = DuelStatus.ACTIVE,
                cycleKey = today,
                referenceScoreSeconds = 0L,
                participants = listOf(
                    ParticipantState(
                        userId = "bot-camille",
                        username = "Camille (Bot)",
                        ankiEffectiveSeconds = 25 * 60L, // 25 min Anki
                        socialSeconds = 5 * 60L,          // 5 min social -> net +20 min
                        cardsReviewed = 64,
                        lastSyncEpochMs = now,
                        ankiApiConnected = true
                    ),
                    ParticipantState(
                        userId = userId,
                        username = username,
                        ankiEffectiveSeconds = 0L,
                        socialSeconds = 0L,
                        cardsReviewed = 0,
                        lastSyncEpochMs = now
                    )
                ),
                createdAtEpochMs = now - 3_600_000L
            )
        )

        val duel2 = DuelDeltaCalculator.recalculateDuel(
            DuelState(
                duelId = "demo-duel-2",
                inviteCode = "ANKI-GRPE",
                creatorId = userId,
                config = DuelConfig(
                    title = "Sprint Japonais (Groupe)",
                    maxPlayers = 5,
                    referenceMode = ReferenceMode.AVERAGE,
                    cycle = DuelCycle.DAILY_MIDNIGHT,
                    toleranceMinutes = 10,
                    dailyDebtCapMinutes = 45,
                    jokerEnabled = true
                ),
                status = DuelStatus.ACTIVE,
                cycleKey = today,
                referenceScoreSeconds = 0L,
                participants = listOf(
                    ParticipantState(
                        userId = userId,
                        username = username,
                        lastSyncEpochMs = now
                    ),
                    ParticipantState(
                        userId = "bot-lucas",
                        username = "Lucas (Bot)",
                        ankiEffectiveSeconds = 30 * 60L,
                        socialSeconds = 15 * 60L, // net 15m
                        cardsReviewed = 42,
                        lastSyncEpochMs = now,
                        ankiApiConnected = true
                    ),
                    ParticipantState(
                        userId = "bot-emma",
                        username = "Emma (Bot)",
                        ankiEffectiveSeconds = 20 * 60L,
                        socialSeconds = 10 * 60L, // net 10m
                        cardsReviewed = 31,
                        lastSyncEpochMs = now,
                        ankiApiConnected = true
                    )
                ),
                createdAtEpochMs = now - 7_200_000L
            )
        )

        duels.add(duel1)
        duels.add(duel2)
    }

    fun addSimulatedAnkiMinutes(minutes: Int) {
        simulatedAnkiBonusSeconds = (simulatedAnkiBonusSeconds + minutes * 60L).coerceAtLeast(0L)
    }

    fun addSimulatedSocialMinutes(minutes: Int) {
        simulatedSocialBonusSeconds = (simulatedSocialBonusSeconds + minutes * 60L).coerceAtLeast(0L)
    }

    fun syncSnapshot(snapshot: UsageSnapshot): List<DuelState> {
        val now = System.currentTimeMillis()
        for (i in duels.indices) {
            val duel = duels[i]
            val missingMandatory = duel.config.mandatorySocialPackages - snapshot.trackedSocialPackages
            val healthy = snapshot.permissionsHealthy && missingMandatory.isEmpty()

            val updatedParticipants = duel.participants.map { p ->
                if (p.userId != userId || p.status == ParticipantStatus.FORFEITED) {
                    p
                } else {
                    p.copy(
                        status = if (healthy) ParticipantStatus.ACTIVE else ParticipantStatus.TAMPER_ALERT,
                        ankiEffectiveSeconds = if (healthy) snapshot.ankiEffectiveSeconds else p.ankiEffectiveSeconds,
                        socialSeconds = snapshot.socialForegroundSeconds,
                        cardsReviewed = if (healthy) snapshot.cardsReviewed else p.cardsReviewed,
                        lastSyncEpochMs = now,
                        ankiApiConnected = snapshot.ankiApiConnected
                    )
                }
            }
            duels[i] = DuelDeltaCalculator.recalculateDuel(duel.copy(participants = updatedParticipants))
        }
        return duels.toList()
    }

    fun createDuel(config: DuelConfig): DuelState {
        val now = System.currentTimeMillis()
        val duel = DuelDeltaCalculator.recalculateDuel(
            DuelState(
                duelId = "demo-${UUID.randomUUID().toString().take(8)}",
                inviteCode = "ANKI-${(1000..9999).random()}",
                creatorId = userId,
                config = config,
                status = DuelStatus.ACTIVE,
                cycleKey = LocalDate.now().toString(),
                referenceScoreSeconds = 0L,
                participants = listOf(
                    ParticipantState(userId = userId, username = username, lastSyncEpochMs = now),
                    ParticipantState(
                        userId = "bot-rival-${(1..99).random()}",
                        username = "Rival (Bot)",
                        ankiEffectiveSeconds = 18 * 60L,
                        socialSeconds = 2 * 60L,
                        cardsReviewed = 35,
                        lastSyncEpochMs = now,
                        ankiApiConnected = true
                    )
                ),
                createdAtEpochMs = now
            )
        )
        duels.add(0, duel)
        return duel
    }

    fun activateJoker(duelId: String): Boolean {
        val idx = duels.indexOfFirst { it.duelId == duelId }
        if (idx < 0) return false
        val duel = duels[idx]
        if (!duel.config.jokerEnabled) return false
        val now = System.currentTimeMillis()

        var activated = false
        val updated = duel.participants.map { p ->
            if (p.userId == userId && p.jokersRemainingThisWeek > 0) {
                activated = true
                p.copy(
                    jokerActiveUntilEpochMs = now + duel.config.jokerDurationMinutes * 60_000L,
                    jokersRemainingThisWeek = p.jokersRemainingThisWeek - 1
                )
            } else p
        }
        if (activated) {
            duels[idx] = DuelDeltaCalculator.recalculateDuel(duel.copy(participants = updated))
        }
        return activated
    }

    fun forfeitDuel(duelId: String): DuelState? {
        val idx = duels.indexOfFirst { it.duelId == duelId }
        if (idx < 0) return null
        val duel = duels[idx]
        val updated = duel.participants.map { p ->
            if (p.userId == userId) p.copy(status = ParticipantStatus.FORFEITED) else p
        }
        val recalculated = DuelDeltaCalculator.recalculateDuel(
            duel.copy(status = DuelStatus.COMPLETED, participants = updated)
        )
        duels[idx] = recalculated
        return recalculated
    }

    fun listDuels(): List<DuelState> = duels.toList()
}
