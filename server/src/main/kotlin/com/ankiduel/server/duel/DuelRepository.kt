package com.ankiduel.server.duel

import com.ankiduel.server.db.DuelParticipantsTable
import com.ankiduel.server.db.DuelsTable
import com.ankiduel.server.db.UsersTable
import com.ankiduel.shared.engine.DuelDeltaCalculator
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantState
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.UsageSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.UUID

class DuelRepository(
    private val db: Database,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val random = SecureRandom()

    fun currentDayKey(): String {
        return Instant.ofEpochMilli(nowProvider()).atZone(zoneId).toLocalDate().toString()
    }

    fun currentWeekKey(): String {
        val date: LocalDate = Instant.ofEpochMilli(nowProvider()).atZone(zoneId).toLocalDate()
        val wf = WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    fun createDuel(creatorId: String, config: DuelConfig): DuelState {
        require(config.title.trim().length in 2..50) { "Le nom du duel doit contenir entre 2 et 50 caractères." }
        require(config.maxPlayers in 2..10) { "Un duel doit accueillir entre 2 et 10 joueurs." }
        require(config.toleranceMinutes in 0..180) { "La tolérance doit être comprise entre 0 et 180 minutes." }

        val duelId = UUID.randomUUID().toString()
        val now = nowProvider()
        val dayKey = currentDayKey()
        val weekKey = currentWeekKey()

        transaction(db) {
            val inviteCode = generateUniqueInviteCode()
            DuelsTable.insert {
                it[id] = duelId
                it[DuelsTable.inviteCode] = inviteCode
                it[DuelsTable.creatorId] = creatorId
                it[configJson] = json.encodeToString(config)
                it[status] = DuelStatus.WAITING.name
                it[currentCycleKey] = dayKey
                it[createdAt] = now
            }

            DuelParticipantsTable.insert {
                it[DuelParticipantsTable.duelId] = duelId
                it[userId] = creatorId
                it[status] = ParticipantStatus.ACTIVE.name
                it[ankiEffectiveSeconds] = 0L
                it[socialSeconds] = 0L
                it[cardsReviewed] = 0
                it[jokerActiveUntilEpochMs] = 0L
                it[jokersUsedThisWeek] = 0
                it[DuelParticipantsTable.weekKey] = weekKey
                it[lastSyncEpochMs] = now
                it[ankiApiConnected] = false
                it[trackedSocialPackagesJson] = "[]"
            }
        }

        return getDuel(duelId)!!
    }

    fun joinDuelByCode(userId: String, rawCode: String): DuelState {
        val code = rawCode.trim().uppercase()
        val duelId = transaction(db) {
            val duelRow = DuelsTable.selectAll()
                .where { DuelsTable.inviteCode eq code }
                .firstOrNull() ?: throw NoSuchElementException("Code d'invitation introuvable.")

            val id = duelRow[DuelsTable.id]
            val config = json.decodeFromString<DuelConfig>(duelRow[DuelsTable.configJson])
            val status = DuelStatus.valueOf(duelRow[DuelsTable.status])
            if (status == DuelStatus.CANCELLED || status == DuelStatus.COMPLETED) {
                throw IllegalStateException("Ce duel est déjà terminé.")
            }

            val participants = DuelParticipantsTable.selectAll()
                .where { DuelParticipantsTable.duelId eq id }
                .toList()

            if (participants.any { it[DuelParticipantsTable.userId] == userId }) {
                return@transaction id
            }
            if (participants.size >= config.maxPlayers) {
                throw IllegalStateException("Ce duel est déjà complet (${config.maxPlayers} joueurs max).")
            }

            val now = nowProvider()
            val weekKey = currentWeekKey()
            DuelParticipantsTable.insert {
                it[DuelParticipantsTable.duelId] = id
                it[DuelParticipantsTable.userId] = userId
                it[DuelParticipantsTable.status] = ParticipantStatus.ACTIVE.name
                it[ankiEffectiveSeconds] = 0L
                it[socialSeconds] = 0L
                it[cardsReviewed] = 0
                it[jokerActiveUntilEpochMs] = 0L
                it[jokersUsedThisWeek] = 0
                it[DuelParticipantsTable.weekKey] = weekKey
                it[lastSyncEpochMs] = now
                it[ankiApiConnected] = false
                it[trackedSocialPackagesJson] = "[]"
            }

            if (status == DuelStatus.WAITING && participants.size + 1 >= 2) {
                DuelsTable.update({ DuelsTable.id eq id }) {
                    it[DuelsTable.status] = DuelStatus.ACTIVE.name
                }
            }
            id
        }
        return getDuel(duelId)!!
    }

    fun getDuelsForUser(userId: String): List<DuelState> {
        val duelIds = transaction(db) {
            DuelParticipantsTable.selectAll()
                .where { DuelParticipantsTable.userId eq userId }
                .map { it[DuelParticipantsTable.duelId] }
        }
        return duelIds.mapNotNull { getDuel(it) }
    }

    fun getDuel(duelId: String): DuelState? {
        val duel = transaction(db) {
            val duelRow = DuelsTable.selectAll()
                .where { DuelsTable.id eq duelId }
                .firstOrNull() ?: return@transaction null

            val config = json.decodeFromString<DuelConfig>(duelRow[DuelsTable.configJson])
            var cycleKey = duelRow[DuelsTable.currentCycleKey]
            val today = currentDayKey()

            // Remise à zéro automatique à minuit pour les duels quotidiens
            if (config.cycle == DuelCycle.DAILY_MIDNIGHT && cycleKey != today) {
                cycleKey = today
                DuelsTable.update({ DuelsTable.id eq duelId }) {
                    it[currentCycleKey] = today
                }
                DuelParticipantsTable.update({ DuelParticipantsTable.duelId eq duelId }) {
                    it[ankiEffectiveSeconds] = 0L
                    it[socialSeconds] = 0L
                    it[cardsReviewed] = 0
                }
            }

            val weekKeyNow = currentWeekKey()
            val participantRows = (DuelParticipantsTable innerJoin UsersTable)
                .selectAll()
                .where { DuelParticipantsTable.duelId eq duelId }
                .toList()

            val participants = participantRows.map { row ->
                val storedWeek = row[DuelParticipantsTable.weekKey]
                val jokersUsed = if (storedWeek == weekKeyNow) {
                    row[DuelParticipantsTable.jokersUsedThisWeek]
                } else {
                    0
                }
                ParticipantState(
                    userId = row[DuelParticipantsTable.userId],
                    username = row[UsersTable.username],
                    status = ParticipantStatus.valueOf(row[DuelParticipantsTable.status]),
                    ankiEffectiveSeconds = row[DuelParticipantsTable.ankiEffectiveSeconds],
                    socialSeconds = row[DuelParticipantsTable.socialSeconds],
                    cardsReviewed = row[DuelParticipantsTable.cardsReviewed],
                    jokerActiveUntilEpochMs = row[DuelParticipantsTable.jokerActiveUntilEpochMs],
                    jokersRemainingThisWeek = (config.jokersPerWeek - jokersUsed).coerceAtLeast(0),
                    lastSyncEpochMs = row[DuelParticipantsTable.lastSyncEpochMs],
                    ankiApiConnected = row[DuelParticipantsTable.ankiApiConnected]
                )
            }

            DuelState(
                duelId = duelId,
                inviteCode = duelRow[DuelsTable.inviteCode],
                creatorId = duelRow[DuelsTable.creatorId],
                config = config,
                status = DuelStatus.valueOf(duelRow[DuelsTable.status]),
                cycleKey = cycleKey,
                referenceScoreSeconds = 0L,
                participants = participants,
                createdAtEpochMs = duelRow[DuelsTable.createdAt]
            )
        } ?: return null

        return DuelDeltaCalculator.recalculateDuel(duel)
    }

    fun syncUsage(duelId: String, userId: String, snapshot: UsageSnapshot): SyncResult {
        val currentDuel = getDuel(duelId) ?: throw NoSuchElementException("Duel introuvable.")
        if (currentDuel.status != DuelStatus.ACTIVE) {
            throw IllegalStateException("Ce duel n'est pas actif.")
        }
        val existingParticipant = currentDuel.participants.firstOrNull { it.userId == userId }
            ?: throw IllegalAccessException("Vous ne participez pas à ce duel.")

        if (existingParticipant.status == ParticipantStatus.FORFEITED) {
            throw IllegalStateException("Vous avez déclaré forfait sur ce duel.")
        }

        val missingMandatory = currentDuel.config.mandatorySocialPackages - snapshot.trackedSocialPackages
        val healthy = snapshot.permissionsHealthy && missingMandatory.isEmpty()
        val now = nowProvider()

        transaction(db) {
            DuelParticipantsTable.update(
                where = { (DuelParticipantsTable.duelId eq duelId) and (DuelParticipantsTable.userId eq userId) }
            ) {
                if (healthy) {
                    it[status] = ParticipantStatus.ACTIVE.name
                    it[ankiEffectiveSeconds] = snapshot.ankiEffectiveSeconds.coerceAtLeast(0L)
                    it[cardsReviewed] = snapshot.cardsReviewed.coerceAtLeast(0)
                } else {
                    // Gel du score Anki en cas de révocation de permission ou d'app obligatoire manquante
                    it[status] = ParticipantStatus.TAMPER_ALERT.name
                }
                // Les minutes réseaux sociaux restent comptabilisées même en alerte
                it[socialSeconds] = maxOf(existingParticipant.socialSeconds, snapshot.socialForegroundSeconds.coerceAtLeast(0L))
                it[lastSyncEpochMs] = now
                it[ankiApiConnected] = snapshot.ankiApiConnected
                it[trackedSocialPackagesJson] = json.encodeToString(snapshot.trackedSocialPackages.toList())
            }
        }

        val updated = getDuel(duelId)!!
        val tamperReason = when {
            !snapshot.permissionsHealthy -> "Permissions Android révoquées pendant le duel"
            missingMandatory.isNotEmpty() -> "Applications obligatoires désactivées : ${missingMandatory.joinToString()}"
            else -> null
        }
        return SyncResult(duel = updated, tamperTriggered = !healthy, tamperReason = tamperReason)
    }

    fun reportTamper(duelId: String, userId: String, reason: String): Pair<DuelState, String> {
        val duel = getDuel(duelId) ?: throw NoSuchElementException("Duel introuvable.")
        val p = duel.participants.firstOrNull { it.userId == userId }
            ?: throw IllegalAccessException("Participant introuvable.")

        transaction(db) {
            DuelParticipantsTable.update(
                where = { (DuelParticipantsTable.duelId eq duelId) and (DuelParticipantsTable.userId eq userId) }
            ) {
                it[status] = ParticipantStatus.TAMPER_ALERT.name
                it[lastSyncEpochMs] = nowProvider()
            }
        }
        return getDuel(duelId)!! to p.username
    }

    fun activateJoker(duelId: String, userId: String): Pair<DuelState, Long> {
        val duel = getDuel(duelId) ?: throw NoSuchElementException("Duel introuvable.")
        if (duel.status != DuelStatus.ACTIVE) {
            throw IllegalStateException("Ce duel n'est pas actif.")
        }
        if (!duel.config.jokerEnabled) {
            throw IllegalStateException("Les jokers d'urgence sont désactivés pour ce duel.")
        }
        val participant = duel.participants.firstOrNull { it.userId == userId }
            ?: throw IllegalAccessException("Participant introuvable.")
        if (participant.status == ParticipantStatus.FORFEITED) {
            throw IllegalStateException("Participant forfait.")
        }

        val now = nowProvider()
        if (participant.jokerActiveUntilEpochMs > now) {
            return duel to participant.jokerActiveUntilEpochMs
        }

        val weekKeyNow = currentWeekKey()
        val activeUntil = now + duel.config.jokerDurationMinutes * 60_000L

        transaction(db) {
            val row = DuelParticipantsTable.selectAll()
                .where { (DuelParticipantsTable.duelId eq duelId) and (DuelParticipantsTable.userId eq userId) }
                .first()
            val storedWeek = row[DuelParticipantsTable.weekKey]
            val used = if (storedWeek == weekKeyNow) row[DuelParticipantsTable.jokersUsedThisWeek] else 0
            if (used >= duel.config.jokersPerWeek) {
                throw IllegalStateException("Quota de jokers épuisé cette semaine (${duel.config.jokersPerWeek} max).")
            }

            DuelParticipantsTable.update(
                where = { (DuelParticipantsTable.duelId eq duelId) and (DuelParticipantsTable.userId eq userId) }
            ) {
                it[jokerActiveUntilEpochMs] = activeUntil
                it[jokersUsedThisWeek] = used + 1
                it[weekKey] = weekKeyNow
            }
        }

        return getDuel(duelId)!! to activeUntil
    }

    fun forfeitDuel(duelId: String, userId: String): DuelState {
        val duel = getDuel(duelId) ?: throw NoSuchElementException("Duel introuvable.")
        if (duel.participants.none { it.userId == userId }) {
            throw IllegalAccessException("Participant introuvable.")
        }

        transaction(db) {
            DuelParticipantsTable.update(
                where = { (DuelParticipantsTable.duelId eq duelId) and (DuelParticipantsTable.userId eq userId) }
            ) {
                it[status] = ParticipantStatus.FORFEITED.name
            }

            val remainingActive = DuelParticipantsTable.selectAll()
                .where { (DuelParticipantsTable.duelId eq duelId) and (DuelParticipantsTable.status neq ParticipantStatus.FORFEITED.name) }
                .count()
            if (remainingActive <= 1) {
                DuelsTable.update({ DuelsTable.id eq duelId }) {
                    it[status] = DuelStatus.COMPLETED.name
                }
            }
        }
        return getDuel(duelId)!!
    }

    private fun generateUniqueInviteCode(): String {
        repeat(10) {
            val candidate = generateInviteCode()
            val exists = DuelsTable.selectAll()
                .where { DuelsTable.inviteCode eq candidate }
                .any()
            if (!exists) return candidate
        }
        return generateInviteCode()
    }

    private fun generateInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = (1..4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        return "ANKI-$suffix"
    }

    data class SyncResult(
        val duel: DuelState,
        val tamperTriggered: Boolean,
        val tamperReason: String?
    )
}
