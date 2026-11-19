package com.ankiduel.server

import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.ReferenceMode
import com.ankiduel.shared.model.UsageSnapshot
import com.ankiduel.shared.protocol.AuthResponse
import com.ankiduel.shared.protocol.CreateDuelRequest
import com.ankiduel.shared.protocol.DuelListResponse
import com.ankiduel.shared.protocol.JoinDuelRequest
import com.ankiduel.shared.protocol.LoginRequest
import com.ankiduel.shared.protocol.RegisterRequest
import com.ankiduel.shared.protocol.SyncUsageRequest
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerIntegrationTest {

    @Test
    fun `full duel lifecycle auth join sync tamper joker forfeit and midnight reset`() = testApplication {
        val tempDb = Files.createTempFile("ankiduel-test", ".db")
        val fakeNow = AtomicLong(1_763_550_000_000L) // Fixed day

        application {
            configureAnkiDuelServer(
                jdbcUrl = "jdbc:sqlite:${tempDb.toAbsolutePath()}",
                jwtSecret = "test-secret",
                nowProvider = { fakeNow.get() }
            )
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }

        // 1. Register Alice & Bob
        val aliceAuth = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Alice", "pass1234"))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<AuthResponse>()

        val bobAuth = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Bob", "pass1234"))
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<AuthResponse>()

        // Login works
        val aliceLogin = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("Alice", "pass1234"))
        }
        assertEquals(HttpStatusCode.OK, aliceLogin.status)

        // 2. Alice creates duel with mandatory Instagram tracking & 10m tolerance
        val createdDuel = client.post("/api/duels") {
            bearerAuth(aliceAuth.token)
            contentType(ContentType.Application.Json)
            setBody(
                CreateDuelRequest(
                    DuelConfig(
                        title = "Sprint Anki",
                        maxPlayers = 4,
                        referenceMode = ReferenceMode.LEADER,
                        cycle = DuelCycle.DAILY_MIDNIGHT,
                        toleranceMinutes = 10,
                        dailyDebtCapMinutes = 60,
                        jokerEnabled = true,
                        jokerDurationMinutes = 5,
                        jokersPerWeek = 1,
                        mandatorySocialPackages = setOf("com.instagram.android")
                    )
                )
            )
        }.also { assertEquals(HttpStatusCode.Created, it.status) }.body<DuelState>()

        assertEquals(DuelStatus.WAITING, createdDuel.status)
        assertTrue(createdDuel.inviteCode.startsWith("ANKI-"))

        // 3. Bob joins via invite code -> duel becomes ACTIVE
        val joinedDuel = client.post("/api/duels/join") {
            bearerAuth(bobAuth.token)
            contentType(ContentType.Application.Json)
            setBody(JoinDuelRequest(createdDuel.inviteCode))
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body<DuelState>()

        assertEquals(DuelStatus.ACTIVE, joinedDuel.status)
        assertEquals(2, joinedDuel.participants.size)

        // 4. Alice studies 30m Anki, 0m social -> net 30m
        val afterAliceSync = client.post("/api/duels/${createdDuel.duelId}/sync") {
            bearerAuth(aliceAuth.token)
            contentType(ContentType.Application.Json)
            setBody(
                SyncUsageRequest(
                    UsageSnapshot(
                        ankiForegroundSeconds = 1800L,
                        ankiEffectiveSeconds = 1800L,
                        cardsReviewed = 45,
                        socialForegroundSeconds = 0L,
                        permissionsHealthy = true,
                        trackedSocialPackages = setOf("com.instagram.android"),
                        ankiApiConnected = true,
                        capturedAtEpochMs = fakeNow.get()
                    )
                )
            )
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body<DuelState>()

        val bobAfterAlice = afterAliceSync.participants.first { it.userId == bobAuth.userId }
        // Delta = 30m, tolerance = 10m => Bob owes 20m!
        assertEquals(20, bobAfterAlice.debtMinutes)

        // 5. Bob tries to drop mandatory Instagram -> triggers TAMPER_ALERT & freezes Anki credit
        val afterTamper = client.post("/api/duels/${createdDuel.duelId}/sync") {
            bearerAuth(bobAuth.token)
            contentType(ContentType.Application.Json)
            setBody(
                SyncUsageRequest(
                    UsageSnapshot(
                        ankiForegroundSeconds = 1800L,
                        ankiEffectiveSeconds = 1800L,
                        cardsReviewed = 30,
                        socialForegroundSeconds = 300L,
                        permissionsHealthy = true,
                        trackedSocialPackages = emptySet(),
                        ankiApiConnected = true,
                        capturedAtEpochMs = fakeNow.get()
                    )
                )
            )
        }.body<DuelState>()

        val bobTampered = afterTamper.participants.first { it.userId == bobAuth.userId }
        assertEquals(ParticipantStatus.TAMPER_ALERT, bobTampered.status)
        assertEquals(0L, bobTampered.ankiEffectiveSeconds) // Frozen!
        assertEquals(300L, bobTampered.socialSeconds)

        // 6. Bob uses his emergency joker (1 per week)
        val afterJoker = client.post("/api/duels/${createdDuel.duelId}/joker") {
            bearerAuth(bobAuth.token)
        }.also { assertEquals(HttpStatusCode.OK, it.status) }.body<DuelState>()

        val bobJoker = afterJoker.participants.first { it.userId == bobAuth.userId }
        assertEquals(0, bobJoker.jokersRemainingThisWeek)
        assertTrue(bobJoker.jokerActiveUntilEpochMs > fakeNow.get())

        // Advance clock 6 minutes so first joker expires; second joker attempt in same week fails with 409 Conflict
        fakeNow.addAndGet(6L * 60 * 1000)
        val secondJoker = client.post("/api/duels/${createdDuel.duelId}/joker") {
            bearerAuth(bobAuth.token)
        }
        assertEquals(HttpStatusCode.Conflict, secondJoker.status)

        // 7. Advance clock +25 hours -> midnight daily reset clears scores & debts
        fakeNow.addAndGet(25L * 60 * 60 * 1000)
        val duelsAfterMidnight = client.get("/api/duels") {
            bearerAuth(bobAuth.token)
        }.body<DuelListResponse>().duels

        val resetDuel = duelsAfterMidnight.first()
        assertTrue(resetDuel.participants.all { it.ankiEffectiveSeconds == 0L && it.debtMinutes == 0 })

        // 8. Bob forfeits -> duel completes
        val afterForfeit = client.post("/api/duels/${createdDuel.duelId}/forfeit") {
            bearerAuth(bobAuth.token)
        }.body<DuelState>()
        assertEquals(DuelStatus.COMPLETED, afterForfeit.status)
        assertEquals(ParticipantStatus.FORFEITED, afterForfeit.participants.first { it.userId == bobAuth.userId }.status)

        Files.deleteIfExists(tempDb)
    }
}
