package com.ankiduel.server

import com.ankiduel.server.auth.AuthService
import com.ankiduel.server.db.DatabaseFactory
import com.ankiduel.server.duel.DuelHub
import com.ankiduel.server.duel.DuelRepository
import com.ankiduel.shared.protocol.CreateDuelRequest
import com.ankiduel.shared.protocol.DuelListResponse
import com.ankiduel.shared.protocol.ErrorResponse
import com.ankiduel.shared.protocol.JoinDuelRequest
import com.ankiduel.shared.protocol.LoginRequest
import com.ankiduel.shared.protocol.RegisterRequest
import com.ankiduel.shared.protocol.ServerEvent
import com.ankiduel.shared.protocol.SyncUsageRequest
import com.ankiduel.shared.protocol.TamperReportRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import java.time.Duration

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:sqlite:ankiduel.db"
    val jwtSecret = System.getenv("JWT_SECRET") ?: "ankiduel-dev-secret-change-in-prod"

    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        configureAnkiDuelServer(
            jdbcUrl = jdbcUrl,
            jwtSecret = jwtSecret
        )
    }.start(wait = true)
}

fun Application.configureAnkiDuelServer(
    jdbcUrl: String = "jdbc:sqlite:ankiduel.db",
    jwtSecret: String = "ankiduel-dev-secret-change-in-prod",
    nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val db = DatabaseFactory.init(jdbcUrl)
    val authService = AuthService(db, jwtSecret)
    val duelRepo = DuelRepository(db, json = json, nowProvider = nowProvider)
    val hub = DuelHub(json)

    install(ContentNegotiation) {
        json(json)
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }

    install(WebSockets) {
        pingPeriodMillis = 20_000L
        timeoutMillis = 30_000L
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Requête invalide"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("conflict", cause.message ?: "Opération impossible"))
        }
        exception<SecurityException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", cause.message ?: "Non autorisé"))
        }
        exception<IllegalAccessException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("forbidden", cause.message ?: "Accès refusé"))
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", cause.message ?: "Ressource introuvable"))
        }
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(authService.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                if (!userId.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Jeton JWT manquant ou invalide"))
            }
        }
    }

    routing {
        get("/api/health") {
            call.respond(mapOf("status" to "ok", "service" to "ankiduel-server"))
        }

        post("/api/auth/register") {
            val req = call.receive<RegisterRequest>()
            call.respond(HttpStatusCode.Created, authService.register(req.username, req.password))
        }

        post("/api/auth/login") {
            val req = call.receive<LoginRequest>()
            call.respond(HttpStatusCode.OK, authService.login(req.username, req.password))
        }

        authenticate("auth-jwt") {
            get("/api/duels") {
                val userId = call.requireUserId()
                call.respond(DuelListResponse(duelRepo.getDuelsForUser(userId)))
            }

            post("/api/duels") {
                val userId = call.requireUserId()
                val req = call.receive<CreateDuelRequest>()
                val duel = duelRepo.createDuel(userId, req.config)
                call.respond(HttpStatusCode.Created, duel)
            }

            post("/api/duels/join") {
                val userId = call.requireUserId()
                val req = call.receive<JoinDuelRequest>()
                val duel = duelRepo.joinDuelByCode(userId, req.inviteCode)
                hub.broadcastToUsers(duel.participants.map { it.userId }, ServerEvent.DuelUpdated(duel))
                call.respond(HttpStatusCode.OK, duel)
            }

            get("/api/duels/{id}") {
                val userId = call.requireUserId()
                val duelId = call.parameters["id"] ?: throw IllegalArgumentException("ID manquant")
                val duel = duelRepo.getDuel(duelId) ?: throw NoSuchElementException("Duel introuvable")
                if (duel.participants.none { it.userId == userId }) {
                    throw IllegalAccessException("Accès interdit à ce duel.")
                }
                call.respond(duel)
            }

            post("/api/duels/{id}/sync") {
                val userId = call.requireUserId()
                val username = call.requireUsername()
                val duelId = call.parameters["id"] ?: throw IllegalArgumentException("ID manquant")
                val req = call.receive<SyncUsageRequest>()
                val result = duelRepo.syncUsage(duelId, userId, req.snapshot)
                val targetUserIds = result.duel.participants.map { it.userId }
                hub.broadcastToUsers(targetUserIds, ServerEvent.DuelUpdated(result.duel))
                if (result.tamperTriggered) {
                    hub.broadcastToUsers(
                        targetUserIds,
                        ServerEvent.TamperDetected(duelId, userId, username, result.tamperReason ?: "Alerte")
                    )
                }
                call.respond(result.duel)
            }

            post("/api/duels/{id}/tamper") {
                val userId = call.requireUserId()
                val duelId = call.parameters["id"] ?: throw IllegalArgumentException("ID manquant")
                val req = call.receive<TamperReportRequest>()
                val (duel, username) = duelRepo.reportTamper(duelId, userId, req.reason)
                val targets = duel.participants.map { it.userId }
                hub.broadcastToUsers(targets, ServerEvent.TamperDetected(duelId, userId, username, req.reason))
                hub.broadcastToUsers(targets, ServerEvent.DuelUpdated(duel))
                call.respond(duel)
            }

            post("/api/duels/{id}/joker") {
                val userId = call.requireUserId()
                val username = call.requireUsername()
                val duelId = call.parameters["id"] ?: throw IllegalArgumentException("ID manquant")
                val (duel, activeUntil) = duelRepo.activateJoker(duelId, userId)
                val targets = duel.participants.map { it.userId }
                hub.broadcastToUsers(targets, ServerEvent.JokerUsed(duelId, userId, username, activeUntil))
                hub.broadcastToUsers(targets, ServerEvent.DuelUpdated(duel))
                call.respond(duel)
            }

            post("/api/duels/{id}/forfeit") {
                val userId = call.requireUserId()
                val username = call.requireUsername()
                val duelId = call.parameters["id"] ?: throw IllegalArgumentException("ID manquant")
                val duel = duelRepo.forfeitDuel(duelId, userId)
                val targets = duel.participants.map { it.userId }
                hub.broadcastToUsers(targets, ServerEvent.ParticipantForfeited(duelId, userId, username))
                hub.broadcastToUsers(targets, ServerEvent.DuelUpdated(duel))
                call.respond(duel)
            }
        }

        webSocket("/ws/duels") {
            val token = call.request.queryParameters["token"]
                ?: call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
            val principal = runCatching { token?.let { authService.verifier.verify(it) } }.getOrNull()
            val userId = principal?.getClaim("userId")?.asString()
            if (userId.isNullOrBlank()) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                return@webSocket
            }

            hub.register(userId, this)
            try {
                for (frame in incoming) {
                    // Keep-alive / client pings handled automatically by WebSockets plugin
                }
            } finally {
                hub.unregister(userId, this)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireUserId(): String =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
        ?: throw SecurityException("Utilisateur non authentifié")

private fun io.ktor.server.application.ApplicationCall.requireUsername(): String =
    principal<JWTPrincipal>()?.payload?.getClaim("username")?.asString() ?: "Joueur"
