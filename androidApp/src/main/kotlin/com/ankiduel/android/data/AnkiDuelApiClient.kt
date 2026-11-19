package com.ankiduel.android.data

import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.UsageSnapshot
import com.ankiduel.shared.protocol.AuthResponse
import com.ankiduel.shared.protocol.CreateDuelRequest
import com.ankiduel.shared.protocol.DuelListResponse
import com.ankiduel.shared.protocol.ErrorResponse
import com.ankiduel.shared.protocol.JoinDuelRequest
import com.ankiduel.shared.protocol.LoginRequest
import com.ankiduel.shared.protocol.RegisterRequest
import com.ankiduel.shared.protocol.ServerEvent
import com.ankiduel.shared.protocol.SyncUsageRequest
import com.ankiduel.shared.protocol.TamperReportRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class AnkiDuelApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets)
    }

    suspend fun register(baseUrl: String, username: String, password: String): AuthResponse {
        val response = client.post("${baseUrl.trimEnd('/')}/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username, password))
        }
        return parseOrThrow(response)
    }

    suspend fun login(baseUrl: String, username: String, password: String): AuthResponse {
        val response = client.post("${baseUrl.trimEnd('/')}/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        return parseOrThrow(response)
    }

    suspend fun fetchDuels(baseUrl: String, token: String): List<DuelState> {
        val response = client.get("${baseUrl.trimEnd('/')}/api/duels") {
            bearerAuth(token)
        }
        return parseOrThrow<DuelListResponse>(response).duels
    }

    suspend fun createDuel(baseUrl: String, token: String, config: DuelConfig): DuelState {
        val response = client.post("${baseUrl.trimEnd('/')}/api/duels") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(CreateDuelRequest(config))
        }
        return parseOrThrow(response)
    }

    suspend fun joinDuel(baseUrl: String, token: String, inviteCode: String): DuelState {
        val response = client.post("${baseUrl.trimEnd('/')}/api/duels/join") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(JoinDuelRequest(inviteCode))
        }
        return parseOrThrow(response)
    }

    suspend fun syncUsage(baseUrl: String, token: String, duelId: String, snapshot: UsageSnapshot): DuelState {
        val response = client.post("${baseUrl.trimEnd('/')}/api/duels/$duelId/sync") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(SyncUsageRequest(snapshot))
        }
        return parseOrThrow(response)
    }

    suspend fun reportTamper(baseUrl: String, token: String, duelId: String, reason: String): DuelState {
        val response = client.post("${baseUrl.trimEnd('/')}/api/duels/$duelId/tamper") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(TamperReportRequest(reason))
        }
        return parseOrThrow(response)
    }

    suspend fun activateJoker(baseUrl: String, token: String, duelId: String): DuelState {
        val response = client.post("${baseUrl.trimEnd('/')}/api/duels/$duelId/joker") {
            bearerAuth(token)
        }
        return parseOrThrow(response)
    }

    suspend fun forfeitDuel(baseUrl: String, token: String, duelId: String): DuelState {
        val response = client.post("${baseUrl.trimEnd('/')}/api/duels/$duelId/forfeit") {
            bearerAuth(token)
        }
        return parseOrThrow(response)
    }

    fun observeEvents(baseUrl: String, token: String): Flow<ServerEvent> = flow {
        val wsUrl = baseUrl.trimEnd('/')
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://") + "/ws/duels?token=$token"

        client.webSocket(wsUrl) {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val event = runCatching {
                        json.decodeFromString<ServerEvent>(frame.readText())
                    }.getOrNull()
                    if (event != null) emit(event)
                }
            }
        }
    }

    private suspend inline fun <reified T> parseOrThrow(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            return response.body()
        }
        val err = runCatching { response.body<ErrorResponse>() }.getOrNull()
        val msg = err?.message ?: if (response.status == HttpStatusCode.Unauthorized) {
            "Session expirée, veuillez vous reconnecter."
        } else {
            "Erreur serveur (${response.status.value})"
        }
        throw IllegalStateException(msg)
    }
}
