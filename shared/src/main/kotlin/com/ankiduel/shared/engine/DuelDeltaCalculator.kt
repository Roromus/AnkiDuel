package com.ankiduel.shared.engine

import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.GlobalLockStatus
import com.ankiduel.shared.model.ParticipantState
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.ReferenceMode
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object DuelDeltaCalculator {

    fun computeNetScoreSeconds(
        ankiEffectiveSeconds: Long,
        socialSeconds: Long,
        socialPenaltyRatio: Double
    ): Long {
        val penalty = (socialSeconds.coerceAtLeast(0L) * socialPenaltyRatio.coerceAtLeast(0.0)).roundToLong()
        return ankiEffectiveSeconds.coerceAtLeast(0L) - penalty
    }

    /**
     * Recalcule les scores nets, la référence du duel et la dette en secondes/minutes de chaque participant.
     */
    fun recalculateDuel(duel: DuelState): DuelState {
        val updatedScores = duel.participants.map { p ->
            val net = computeNetScoreSeconds(
                ankiEffectiveSeconds = p.ankiEffectiveSeconds,
                socialSeconds = p.socialSeconds,
                socialPenaltyRatio = duel.config.socialPenaltyRatio
            )
            p.copy(netScoreSeconds = net)
        }

        val activeParticipants = updatedScores.filter { it.status != ParticipantStatus.FORFEITED }
        val globalLeaderScore = activeParticipants.maxOfOrNull { it.netScoreSeconds } ?: 0L

        val recalculatedParticipants = updatedScores.map { participant ->
            if (duel.status != DuelStatus.ACTIVE || participant.status == ParticipantStatus.FORFEITED) {
                participant.copy(debtSeconds = 0L, debtMinutes = 0)
            } else {
                val referenceForParticipant = when (duel.config.referenceMode) {
                    ReferenceMode.LEADER -> globalLeaderScore
                    ReferenceMode.AVERAGE -> {
                        val others = activeParticipants.filter { it.userId != participant.userId }
                        if (others.isEmpty()) participant.netScoreSeconds
                        else others.map { it.netScoreSeconds }.average().roundToLong()
                    }
                }
                val rawDeltaSeconds = max(0L, referenceForParticipant - participant.netScoreSeconds)
                val toleranceSeconds = duel.config.toleranceMinutes.coerceAtLeast(0) * 60L
                val excessSeconds = max(0L, rawDeltaSeconds - toleranceSeconds)
                val cappedDebtSeconds = duel.config.dailyDebtCapMinutes?.let { capMin ->
                    min(capMin.coerceAtLeast(0) * 60L, excessSeconds)
                } ?: excessSeconds
                val debtMinutes = if (cappedDebtSeconds <= 0L) 0 else ceil(cappedDebtSeconds / 60.0).toInt()
                participant.copy(
                    debtSeconds = cappedDebtSeconds,
                    debtMinutes = debtMinutes
                )
            }
        }

        return duel.copy(
            referenceScoreSeconds = globalLeaderScore,
            participants = recalculatedParticipants
        )
    }

    /**
     * Calcule le statut de blocage global d'un utilisateur participant à un ou plusieurs duels simultanés.
     * Le blocage est actif dès qu'au moins un duel génère une dette > 0 (la dette retenue est le pire delta).
     */
    fun computeGlobalLockStatus(
        userId: String,
        duels: List<DuelState>,
        nowEpochMs: Long
    ): GlobalLockStatus {
        var worstDebtSeconds = 0L
        var worstDebtMinutes = 0
        var worstDuel: DuelState? = null
        var activeJokerUntil = 0L

        for (duel in duels) {
            if (duel.status != DuelStatus.ACTIVE) continue
            val me = duel.participants.firstOrNull { it.userId == userId } ?: continue
            if (me.status == ParticipantStatus.FORFEITED) continue

            if (me.jokerActiveUntilEpochMs > activeJokerUntil) {
                activeJokerUntil = me.jokerActiveUntilEpochMs
            }

            if (me.debtSeconds > worstDebtSeconds) {
                worstDebtSeconds = me.debtSeconds
                worstDebtMinutes = me.debtMinutes
                worstDuel = duel
            }
        }

        val jokerCurrentlyActive = activeJokerUntil > nowEpochMs
        val leaderUsername = worstDuel?.participants
            ?.filter { it.status != ParticipantStatus.FORFEITED }
            ?.maxByOrNull { it.netScoreSeconds }
            ?.username

        return GlobalLockStatus(
            isLocked = worstDebtSeconds > 0L && !jokerCurrentlyActive,
            maxDebtSeconds = worstDebtSeconds,
            maxDebtMinutes = worstDebtMinutes,
            blockingDuelId = worstDuel?.duelId,
            blockingDuelTitle = worstDuel?.config?.title,
            leaderUsername = leaderUsername,
            jokerActiveUntilEpochMs = if (jokerCurrentlyActive) activeJokerUntil else 0L
        )
    }
}
