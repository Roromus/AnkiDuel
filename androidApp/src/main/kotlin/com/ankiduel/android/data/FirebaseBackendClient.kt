package com.ankiduel.android.data

import com.ankiduel.shared.engine.DuelDeltaCalculator
import com.ankiduel.shared.model.DailyStatRecord
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantState
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.UsageSnapshot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.UUID

data class FirebaseAuthSession(
    val userId: String,
    val username: String,
    val email: String
)

class FirebaseBackendClient(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val secureRandom = SecureRandom()

    private val usersCol get() = firestore.collection("users")
    private val duelsCol get() = firestore.collection("duels")

    fun currentUserId(): String? = auth.currentUser?.uid

    suspend fun login(email: String, password: String): FirebaseAuthSession {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank()) { "Email requis." }
        require(password.length >= 6) { "Mot de passe : 6 caractères minimum." }

        val res = auth.signInWithEmailAndPassword(cleanEmail, password).await()
        val user = res.user ?: error("Échec de connexion Firebase.")
        val profileDoc = runCatching { usersCol.document(user.uid).get().await() }.getOrNull()
        val username = profileDoc?.getString("username")
            ?: user.displayName?.takeIf { it.isNotBlank() }
            ?: cleanEmail.substringBefore('@')

        return FirebaseAuthSession(
            userId = user.uid,
            username = username,
            email = user.email ?: cleanEmail
        )
    }

    suspend fun register(email: String, username: String, password: String): FirebaseAuthSession {
        val cleanEmail = email.trim()
        val cleanUsername = username.trim()
        require(cleanEmail.isNotBlank()) { "Email requis." }
        require(cleanUsername.length in 3..24) { "Pseudo : entre 3 et 24 caractères." }
        require(password.length >= 6) { "Mot de passe : 6 caractères minimum." }

        val res = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val user = res.user ?: error("Échec de création du compte Firebase.")

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(cleanUsername)
            .build()
        runCatching { user.updateProfile(profileUpdates).await() }

        usersCol.document(user.uid).set(
            mapOf(
                "userId" to user.uid,
                "username" to cleanUsername,
                "email" to cleanEmail,
                "createdAtEpochMs" to System.currentTimeMillis()
            )
        ).await()

        return FirebaseAuthSession(
            userId = user.uid,
            username = cleanUsername,
            email = cleanEmail
        )
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun createDuel(userId: String, username: String, config: DuelConfig): DuelState {
        require(config.title.trim().length in 2..50) { "Le nom du duel doit contenir entre 2 et 50 caractères." }
        require(config.maxPlayers in 2..10) { "Un duel doit accueillir entre 2 et 10 joueurs." }
        require(config.toleranceMinutes in 0..180) { "La tolérance doit être comprise entre 0 et 180 minutes." }

        val duelId = UUID.randomUUID().toString()
        val inviteCode = generateUniqueInviteCode()
        val now = System.currentTimeMillis()
        val todayKey = currentDayKey(now)
        val weekKey = currentWeekKey(now)
        val cycleKey = computeCycleKey(config, now)
        val initialStatus = DuelStatus.WAITING

        val creator = ParticipantState(
            userId = userId,
            username = username,
            status = ParticipantStatus.ACTIVE,
            jokersRemainingThisWeek = config.jokersPerWeek,
            jokerWeekKey = weekKey,
            lastSyncEpochMs = now,
            lastSyncDayKey = todayKey
        )

        val duel = DuelDeltaCalculator.recalculateDuel(
            DuelState(
                duelId = duelId,
                inviteCode = inviteCode,
                creatorId = userId,
                config = config,
                status = initialStatus,
                cycleKey = cycleKey,
                referenceScoreSeconds = 0L,
                participants = listOf(creator),
                createdAtEpochMs = now
            )
        )

        duelsCol.document(duelId).set(duelToMap(duel)).await()
        return duel
    }

    suspend fun joinDuel(userId: String, username: String, inviteCode: String): DuelState {
        val code = inviteCode.trim().uppercase()
        require(code.isNotBlank()) { "Code d'invitation requis." }

        val querySnap = duelsCol
            .whereEqualTo("inviteCode", code)
            .limit(1)
            .get()
            .await()

        val doc = querySnap.documents.firstOrNull()
            ?: error("Aucun duel trouvé avec le code $code.")

        return firestore.runTransaction { tx ->
            val fresh = tx.get(doc.reference)
            val current = docToDuel(fresh) ?: error("Duel introuvable.")

            if (current.participants.any { it.userId == userId }) {
                return@runTransaction current
            }
            if (current.status == DuelStatus.COMPLETED || current.status == DuelStatus.CANCELLED) {
                error("Ce duel est déjà terminé.")
            }
            if (current.participants.size >= current.config.maxPlayers) {
                error("Ce duel est complet.")
            }

            val now = System.currentTimeMillis()
            val joinedParticipant = ParticipantState(
                userId = userId,
                username = username,
                status = ParticipantStatus.ACTIVE,
                jokersRemainingThisWeek = current.config.jokersPerWeek,
                jokerWeekKey = currentWeekKey(now),
                lastSyncEpochMs = now,
                lastSyncDayKey = currentDayKey(now)
            )
            val updatedParticipants = current.participants + joinedParticipant
            val nextStatus = if (updatedParticipants.size >= 2) DuelStatus.ACTIVE else current.status
            val updated = DuelDeltaCalculator.recalculateDuel(
                current.copy(
                    status = nextStatus,
                    participants = updatedParticipants
                )
            )
            tx.set(doc.reference, duelToMap(updated))
            updated
        }.await()
    }

    suspend fun fetchDuels(userId: String): List<DuelState> {
        val snap = duelsCol
            .whereArrayContains("participantIds", userId)
            .get()
            .await()

        return snap.documents
            .mapNotNull { docToDuel(it) }
            .sortedByDescending { it.createdAtEpochMs }
    }

    fun observeDuels(userId: String): Flow<List<DuelState>> = callbackFlow {
        val registration = duelsCol
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents
                    .mapNotNull { docToDuel(it) }
                    .sortedByDescending { it.createdAtEpochMs }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun syncUsage(duelId: String, userId: String, snapshot: UsageSnapshot): DuelState {
        val docRef = duelsCol.document(duelId)
        return firestore.runTransaction { tx ->
            val doc = tx.get(docRef)
            val current = docToDuel(doc) ?: error("Duel introuvable.")
            if (current.status != DuelStatus.ACTIVE) {
                return@runTransaction current
            }

            val now = snapshot.capturedAtEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis()
            val todayKey = currentDayKey(now)
            val weekKey = currentWeekKey(now)

            // Vérifier la fin d'un duel à durée personnalisée
            if (current.config.cycle == DuelCycle.CUSTOM_DAYS) {
                val days = (current.config.durationDays ?: 7).coerceAtLeast(1)
                val endMs = current.createdAtEpochMs + days * 24L * 3600L * 1000L
                if (now >= endMs) {
                    val completed = DuelDeltaCalculator.recalculateDuel(
                        current.copy(status = DuelStatus.COMPLETED)
                    )
                    tx.set(docRef, duelToMap(completed))
                    return@runTransaction completed
                }
            }

            val targetCycleKey = computeCycleKey(current.config, now)
            val baseDuel = if (current.config.cycle == DuelCycle.DAILY_MIDNIGHT && current.cycleKey != targetCycleKey) {
                current.copy(
                    cycleKey = targetCycleKey,
                    participants = current.participants.map { p ->
                        p.copy(
                            ankiEffectiveSeconds = 0L,
                            socialSeconds = 0L,
                            cardsReviewed = 0,
                            netScoreSeconds = 0L,
                            debtSeconds = 0L,
                            debtMinutes = 0,
                            lastSyncDayKey = todayKey,
                            carryOverAnkiSeconds = 0L,
                            carryOverSocialSeconds = 0L,
                            carryOverCardsReviewed = 0
                        )
                    }
                )
            } else {
                current
            }

            val missingMandatory = baseDuel.config.mandatorySocialPackages - snapshot.trackedSocialPackages
            val healthy = snapshot.permissionsHealthy && missingMandatory.isEmpty()

            val updatedParticipants = baseDuel.participants.map { p ->
                val normalizedJoker = normalizeWeeklyJoker(p, baseDuel.config.jokersPerWeek, weekKey)
                if (normalizedJoker.userId != userId || normalizedJoker.status == ParticipantStatus.FORFEITED) {
                    normalizedJoker
                } else {
                    val isMultiDayCycle = baseDuel.config.cycle != DuelCycle.DAILY_MIDNIGHT
                    val prevDayKey = normalizedJoker.lastSyncDayKey.ifBlank {
                        if (normalizedJoker.lastSyncEpochMs > 0L) currentDayKey(normalizedJoker.lastSyncEpochMs) else todayKey
                    }
                    val rolledOver = isMultiDayCycle && prevDayKey != todayKey
                    val carryAnki = when {
                        !isMultiDayCycle -> 0L
                        rolledOver -> normalizedJoker.ankiEffectiveSeconds
                        else -> normalizedJoker.carryOverAnkiSeconds
                    }
                    val carrySocial = when {
                        !isMultiDayCycle -> 0L
                        rolledOver -> normalizedJoker.socialSeconds
                        else -> normalizedJoker.carryOverSocialSeconds
                    }
                    val carryCards = when {
                        !isMultiDayCycle -> 0
                        rolledOver -> normalizedJoker.cardsReviewed
                        else -> normalizedJoker.carryOverCardsReviewed
                    }

                    val todaySocial = snapshot.socialForegroundSeconds.coerceAtLeast(0L)
                    val candidateSocial = carrySocial + todaySocial
                    val nextSocial = if (rolledOver) candidateSocial else maxOf(normalizedJoker.socialSeconds, candidateSocial)

                    if (healthy) {
                        val todayAnki = snapshot.ankiEffectiveSeconds.coerceAtLeast(0L)
                        val todayCards = snapshot.cardsReviewed.coerceAtLeast(0)
                        normalizedJoker.copy(
                            status = ParticipantStatus.ACTIVE,
                            ankiEffectiveSeconds = carryAnki + todayAnki,
                            socialSeconds = nextSocial,
                            cardsReviewed = carryCards + todayCards,
                            ankiApiConnected = snapshot.ankiApiConnected,
                            appHealthOk = snapshot.appHealthOk,
                            appHealthIssue = snapshot.appHealthIssue,
                            lastSyncEpochMs = now,
                            lastSyncDayKey = todayKey,
                            carryOverAnkiSeconds = carryAnki,
                            carryOverSocialSeconds = carrySocial,
                            carryOverCardsReviewed = carryCards
                        )
                    } else {
                        // Gel du score Anki en cas de révocation de permission ou d'app obligatoire manquante
                        normalizedJoker.copy(
                            status = ParticipantStatus.TAMPER_ALERT,
                            socialSeconds = nextSocial,
                            ankiApiConnected = snapshot.ankiApiConnected,
                            appHealthOk = snapshot.appHealthOk,
                            appHealthIssue = snapshot.appHealthIssue ?: "Permissions manquantes",
                            lastSyncEpochMs = now,
                            lastSyncDayKey = todayKey,
                            carryOverAnkiSeconds = carryAnki,
                            carryOverSocialSeconds = carrySocial,
                            carryOverCardsReviewed = carryCards
                        )
                    }
                }
            }

            val recalculated = DuelDeltaCalculator.recalculateDuel(
                baseDuel.copy(participants = updatedParticipants)
            )
            tx.set(docRef, duelToMap(recalculated))
            recalculated
        }.await()
    }

    suspend fun activateJoker(duelId: String, userId: String): DuelState {
        val docRef = duelsCol.document(duelId)
        return firestore.runTransaction { tx ->
            val doc = tx.get(docRef)
            val current = docToDuel(doc) ?: error("Duel introuvable.")
            if (current.status != DuelStatus.ACTIVE) {
                error("Ce duel n'est pas actif.")
            }
            if (!current.config.jokerEnabled) {
                error("Le joker est désactivé sur ce duel.")
            }
            if (current.participants.none { it.userId == userId }) {
                error("Vous ne participez pas à ce duel.")
            }

            val now = System.currentTimeMillis()
            val weekKey = currentWeekKey(now)
            var alreadyActive = false

            val updatedParticipants = current.participants.map { raw ->
                val p = normalizeWeeklyJoker(raw, current.config.jokersPerWeek, weekKey)
                if (p.userId != userId) {
                    p
                } else {
                    if (p.status == ParticipantStatus.FORFEITED) {
                        error("Impossible d'utiliser un joker après forfait.")
                    }
                    if (p.jokerActiveUntilEpochMs > now) {
                        alreadyActive = true
                        p
                    } else {
                        if (p.jokersRemainingThisWeek <= 0) {
                            error("Plus de joker disponible cette semaine.")
                        }
                        val activeUntil = now + current.config.jokerDurationMinutes * 60_000L
                        p.copy(
                            jokerActiveUntilEpochMs = activeUntil,
                            jokersRemainingThisWeek = p.jokersRemainingThisWeek - 1,
                            jokerWeekKey = weekKey
                        )
                    }
                }
            }

            if (alreadyActive) {
                return@runTransaction current
            }

            val updated = DuelDeltaCalculator.recalculateDuel(
                current.copy(participants = updatedParticipants)
            )
            tx.set(docRef, duelToMap(updated))
            updated
        }.await()
    }

    suspend fun forfeitDuel(duelId: String, userId: String): DuelState {
        val docRef = duelsCol.document(duelId)
        return firestore.runTransaction { tx ->
            val doc = tx.get(docRef)
            val current = docToDuel(doc) ?: error("Duel introuvable.")
            if (current.status == DuelStatus.COMPLETED || current.status == DuelStatus.CANCELLED) {
                return@runTransaction current
            }
            val updatedParticipants = current.participants.map { p ->
                if (p.userId == userId) p.copy(status = ParticipantStatus.FORFEITED) else p
            }
            val activeCount = updatedParticipants.count { it.status != ParticipantStatus.FORFEITED }
            val nextStatus = when {
                current.status == DuelStatus.WAITING && activeCount == 0 -> DuelStatus.CANCELLED
                activeCount < 2 -> DuelStatus.COMPLETED
                else -> current.status
            }

            val updated = DuelDeltaCalculator.recalculateDuel(
                current.copy(status = nextStatus, participants = updatedParticipants)
            )
            tx.set(docRef, duelToMap(updated))
            updated
        }.await()
    }

    private fun duelToMap(duel: DuelState): Map<String, Any> {
        val participantsMap = duel.participants.associate { p ->
            p.userId to json.encodeToString(p)
        }
        return mapOf(
            "duelId" to duel.duelId,
            "inviteCode" to duel.inviteCode,
            "creatorId" to duel.creatorId,
            "configJson" to json.encodeToString(duel.config),
            "status" to duel.status.name,
            "cycleKey" to duel.cycleKey,
            "referenceScoreSeconds" to duel.referenceScoreSeconds,
            "participantIds" to duel.participants.map { it.userId },
            "participants" to participantsMap,
            "createdAtEpochMs" to duel.createdAtEpochMs
        )
    }

    private fun docToDuel(doc: DocumentSnapshot): DuelState? {
        if (!doc.exists()) return null
        val duelId = doc.getString("duelId") ?: doc.id
        val inviteCode = doc.getString("inviteCode") ?: return null
        val creatorId = doc.getString("creatorId") ?: return null
        val configJson = doc.getString("configJson") ?: return null
        val config = runCatching { json.decodeFromString<DuelConfig>(configJson) }.getOrNull() ?: return null
        val status = runCatching {
            DuelStatus.valueOf(doc.getString("status") ?: DuelStatus.WAITING.name)
        }.getOrDefault(DuelStatus.WAITING)
        val cycleKey = doc.getString("cycleKey") ?: computeCycleKey(config)
        val referenceScore = doc.getLong("referenceScoreSeconds") ?: 0L
        val createdAt = doc.getLong("createdAtEpochMs") ?: 0L

        val weekKey = currentWeekKey(System.currentTimeMillis())
        @Suppress("UNCHECKED_CAST")
        val rawParticipants = doc.get("participants") as? Map<String, String> ?: emptyMap()
        val participants = rawParticipants.values.mapNotNull { raw ->
            runCatching { json.decodeFromString<ParticipantState>(raw) }.getOrNull()
        }.map { p ->
            normalizeWeeklyJoker(p, config.jokersPerWeek, weekKey)
        }.sortedBy { it.userId }

        val rawState = DuelState(
            duelId = duelId,
            inviteCode = inviteCode,
            creatorId = creatorId,
            config = config,
            status = status,
            cycleKey = cycleKey,
            referenceScoreSeconds = referenceScore,
            participants = participants,
            createdAtEpochMs = createdAt
        )
        return DuelDeltaCalculator.recalculateDuel(rawState)
    }

    private fun normalizeWeeklyJoker(
        participant: ParticipantState,
        jokersPerWeek: Int,
        weekKey: String
    ): ParticipantState {
        val effectiveWeek = participant.jokerWeekKey.ifBlank {
            if (participant.lastSyncEpochMs > 0L) currentWeekKey(participant.lastSyncEpochMs) else weekKey
        }
        return if (effectiveWeek != weekKey) {
            participant.copy(
                jokersRemainingThisWeek = jokersPerWeek.coerceAtLeast(0),
                jokerWeekKey = weekKey
            )
        } else {
            participant
        }
    }

    private fun currentDayKey(nowMs: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    private fun currentWeekKey(nowMs: Long = System.currentTimeMillis()): String {
        val date: LocalDate = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val wf = WeekFields.ISO
        val week = date.get(wf.weekOfWeekBasedYear())
        val year = date.get(wf.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    private fun computeCycleKey(config: DuelConfig, nowMs: Long = System.currentTimeMillis()): String = when (config.cycle) {
        DuelCycle.DAILY_MIDNIGHT -> currentDayKey(nowMs)
        DuelCycle.CONTINUOUS -> "CONTINUOUS"
        DuelCycle.CUSTOM_DAYS -> "CUSTOM_${config.durationDays ?: 7}"
    }

    private suspend fun generateUniqueInviteCode(): String {
        repeat(5) {
            val candidate = generateInviteCode()
            val exists = runCatching {
                duelsCol.whereEqualTo("inviteCode", candidate).limit(1).get().await().documents.isNotEmpty()
            }.getOrDefault(false)
            if (!exists) return candidate
        }
        return generateInviteCode()
    }

    private fun userStatsDaysCol(userId: String) =
        firestore.collection("userStats").document(userId).collection("days")

    suspend fun upsertDailyStat(userId: String, record: DailyStatRecord) {
        val data = mapOf(
            "userId" to userId,
            "dayKey" to record.dayKey,
            "ankiEffectiveSeconds" to record.ankiEffectiveSeconds.coerceIn(0L, 86400L),
            "cardsReviewed" to record.cardsReviewed.coerceIn(0, 100000),
            "socialSeconds" to record.socialSeconds.coerceIn(0L, 86400L),
            "netScoreSeconds" to record.netScoreSeconds.coerceIn(-864000L, 864000L),
            "updatedAtEpochMs" to record.updatedAtEpochMs.coerceAtLeast(0L)
        )
        userStatsDaysCol(userId).document(record.dayKey).set(data).await()
    }

    suspend fun fetchDailyStats(userId: String, days: Int = 30): List<DailyStatRecord> {
        val cutoffKey = LocalDate.now().minusDays((days - 1).coerceAtLeast(0).toLong()).toString()
        val snap = userStatsDaysCol(userId)
            .whereGreaterThanOrEqualTo("dayKey", cutoffKey)
            .orderBy("dayKey", Query.Direction.ASCENDING)
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            val dayKey = doc.getString("dayKey") ?: return@mapNotNull null
            DailyStatRecord(
                dayKey = dayKey,
                userId = doc.getString("userId") ?: userId,
                ankiEffectiveSeconds = doc.getLong("ankiEffectiveSeconds") ?: 0L,
                cardsReviewed = (doc.getLong("cardsReviewed") ?: 0L).toInt(),
                socialSeconds = doc.getLong("socialSeconds") ?: 0L,
                netScoreSeconds = doc.getLong("netScoreSeconds") ?: 0L,
                updatedAtEpochMs = doc.getLong("updatedAtEpochMs") ?: 0L
            )
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars[secureRandom.nextInt(chars.length)] }.joinToString("")
    }
}
