package com.ankiduel.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class ReferenceMode {
    /** Le score de référence est celui du 1er du duel (mode compétitif strict). */
    LEADER,
    /** Le score de référence est la moyenne des autres participants actifs. */
    AVERAGE
}

@Serializable
enum class DuelCycle {
    /** Remise à zéro des compteurs chaque jour à minuit (heure locale du serveur/duel). */
    DAILY_MIDNIGHT,
    /** Duel continu sans remise à zéro jusqu'à la fin ou l'annulation. */
    CONTINUOUS,
    /** Duel sur un nombre de jours défini. */
    CUSTOM_DAYS
}

@Serializable
enum class DuelStatus {
    WAITING,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

@Serializable
enum class ParticipantStatus {
    ACTIVE,
    TAMPER_ALERT,
    FORFEITED
}

@Serializable
data class DuelConfig(
    val title: String,
    val maxPlayers: Int = 2,
    val referenceMode: ReferenceMode = ReferenceMode.LEADER,
    val cycle: DuelCycle = DuelCycle.DAILY_MIDNIGHT,
    val durationDays: Int? = null,
    val toleranceMinutes: Int = 10,
    val dailyDebtCapMinutes: Int? = 60,
    val socialPenaltyRatio: Double = 1.0,
    val jokerEnabled: Boolean = true,
    val jokerDurationMinutes: Int = 5,
    val jokersPerWeek: Int = 1,
    val mandatorySocialPackages: Set<String> = emptySet()
)

@Serializable
data class UsageSnapshot(
    val ankiForegroundSeconds: Long,
    val ankiEffectiveSeconds: Long,
    val cardsReviewed: Int,
    val socialForegroundSeconds: Long,
    val socialBreakdownSeconds: Map<String, Long> = emptyMap(),
    val permissionsHealthy: Boolean = true,
    val trackedSocialPackages: Set<String> = emptySet(),
    val ankiApiConnected: Boolean = false,
    val capturedAtEpochMs: Long
)

@Serializable
data class ParticipantState(
    val userId: String,
    val username: String,
    val status: ParticipantStatus = ParticipantStatus.ACTIVE,
    val ankiEffectiveSeconds: Long = 0L,
    val socialSeconds: Long = 0L,
    val cardsReviewed: Int = 0,
    val netScoreSeconds: Long = 0L,
    val debtSeconds: Long = 0L,
    val debtMinutes: Int = 0,
    val jokerActiveUntilEpochMs: Long = 0L,
    val jokersRemainingThisWeek: Int = 1,
    val lastSyncEpochMs: Long = 0L,
    val ankiApiConnected: Boolean = false
) {
    val ankiMinutes: Int get() = (ankiEffectiveSeconds / 60L).toInt()
    val socialMinutes: Int get() = (socialSeconds / 60L).toInt()
    val netScoreMinutes: Int get() = (netScoreSeconds / 60.0).toInt()
}

@Serializable
data class DuelState(
    val duelId: String,
    val inviteCode: String,
    val creatorId: String,
    val config: DuelConfig,
    val status: DuelStatus,
    val cycleKey: String,
    val referenceScoreSeconds: Long,
    val participants: List<ParticipantState>,
    val createdAtEpochMs: Long
)

@Serializable
data class GlobalLockStatus(
    val isLocked: Boolean,
    val maxDebtSeconds: Long,
    val maxDebtMinutes: Int,
    val blockingDuelId: String?,
    val blockingDuelTitle: String?,
    val leaderUsername: String?,
    val jokerActiveUntilEpochMs: Long
)
