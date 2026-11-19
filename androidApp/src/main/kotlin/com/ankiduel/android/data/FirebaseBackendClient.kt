package com.ankiduel.android.data

import com.ankiduel.shared.engine.DuelDeltaCalculator
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
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

    private val usersCol get() = firestore.collection("users")
    private val duelsCol get() = firestore.collection("duels")

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
        val duelId = UUID.randomUUID().toString()
        val inviteCode = generateInviteCode()
        val now = System.currentTimeMillis()
        val cycleKey = computeCycleKey(config)
        val initialStatus = if (config.maxPlayers <= 1) DuelStatus.ACTIVE else DuelStatus.WAITING

        val creator = ParticipantState(
            userId = userId,
            username = username,
            status = ParticipantStatus.ACTIVE,
            jokersRemainingThisWeek = config.jokersPerWeek,
            lastSyncEpochMs = now
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

            val joinedParticipant = ParticipantState(
                userId = userId,
                username = username,
                status = ParticipantStatus.ACTIVE,
                jokersRemainingThisWeek = current.config.jokersPerWeek,
                lastSyncEpochMs = System.currentTimeMillis()
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

            val targetCycleKey = computeCycleKey(current.config)
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
                            debtMinutes = 0
                        )
                    }
                )
            } else {
                current
            }

            val updatedParticipants = baseDuel.participants.map { p ->
                if (p.userId != userId || p.status == ParticipantStatus.FORFEITED) {
                    p
                } else {
                    p.copy(
                        ankiEffectiveSeconds = snapshot.ankiEffectiveSeconds,
                        socialSeconds = snapshot.socialForegroundSeconds,
                        cardsReviewed = snapshot.cardsReviewed,
                        ankiApiConnected = snapshot.ankiApiConnected,
                        lastSyncEpochMs = snapshot.capturedAtEpochMs,
                        status = if (snapshot.permissionsHealthy) {
                            ParticipantStatus.ACTIVE
                        } else {
                            ParticipantStatus.TAMPER_ALERT
                        }
                    )
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
            if (!current.config.jokerEnabled) {
                error("Le joker est désactivé sur ce duel.")
            }

            val now = System.currentTimeMillis()
            val updatedParticipants = current.participants.map { p ->
                if (p.userId != userId) {
                    p
                } else {
                    if (p.jokersRemainingThisWeek <= 0) {
                        error("Plus de joker disponible cette semaine.")
                    }
                    val activeUntil = now + current.config.jokerDurationMinutes * 60_000L
                    p.copy(
                        jokerActiveUntilEpochMs = activeUntil,
                        jokersRemainingThisWeek = p.jokersRemainingThisWeek - 1
                    )
                }
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
            val updatedParticipants = current.participants.map { p ->
                if (p.userId == userId) p.copy(status = ParticipantStatus.FORFEITED) else p
            }
            val activeCount = updatedParticipants.count { it.status != ParticipantStatus.FORFEITED }
            val nextStatus = if (activeCount < 2) DuelStatus.COMPLETED else current.status

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

        @Suppress("UNCHECKED_CAST")
        val rawParticipants = doc.get("participants") as? Map<String, String> ?: emptyMap()
        val participants = rawParticipants.values.mapNotNull { raw ->
            runCatching { json.decodeFromString<ParticipantState>(raw) }.getOrNull()
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

    private fun computeCycleKey(config: DuelConfig): String = when (config.cycle) {
        DuelCycle.DAILY_MIDNIGHT -> LocalDate.now().toString()
        DuelCycle.CONTINUOUS -> "CONTINUOUS"
        DuelCycle.CUSTOM_DAYS -> "CUSTOM_${config.durationDays ?: 7}"
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
