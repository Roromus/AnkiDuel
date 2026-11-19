package com.ankiduel.shared.protocol

import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.UsageSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val username: String
)

@Serializable
data class CreateDuelRequest(
    val config: DuelConfig
)

@Serializable
data class JoinDuelRequest(
    val inviteCode: String
)

@Serializable
data class SyncUsageRequest(
    val snapshot: UsageSnapshot
)

@Serializable
data class TamperReportRequest(
    val reason: String
)

@Serializable
data class DuelListResponse(
    val duels: List<DuelState>
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String
)

@Serializable
sealed interface ServerEvent {
    @Serializable
    @SerialName("duel_updated")
    data class DuelUpdated(val duel: DuelState) : ServerEvent

    @Serializable
    @SerialName("tamper_detected")
    data class TamperDetected(
        val duelId: String,
        val userId: String,
        val username: String,
        val reason: String
    ) : ServerEvent

    @Serializable
    @SerialName("joker_used")
    data class JokerUsed(
        val duelId: String,
        val userId: String,
        val username: String,
        val activeUntilEpochMs: Long
    ) : ServerEvent

    @Serializable
    @SerialName("participant_forfeited")
    data class ParticipantForfeited(
        val duelId: String,
        val userId: String,
        val username: String
    ) : ServerEvent
}
