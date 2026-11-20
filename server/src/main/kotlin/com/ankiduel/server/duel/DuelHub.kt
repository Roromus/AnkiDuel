package com.ankiduel.server.duel

import com.ankiduel.shared.protocol.ServerEvent
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class DuelHub(
    private val json: Json = Json { encodeDefaults = true }
) {
    private val sessionsByUser = ConcurrentHashMap<String, CopyOnWriteArraySet<DefaultWebSocketSession>>()

    fun register(userId: String, session: DefaultWebSocketSession) {
        sessionsByUser.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
    }

    fun unregister(userId: String, session: DefaultWebSocketSession) {
        sessionsByUser.computeIfPresent(userId) { _, set ->
            set.remove(session)
            if (set.isEmpty()) null else set
        }
    }

    suspend fun broadcastToUsers(userIds: Collection<String>, event: ServerEvent) {
        val payload = json.encodeToString(event)
        for (userId in userIds.distinct()) {
            val sessions = sessionsByUser[userId] ?: continue
            for (session in sessions) {
                runCatching {
                    session.send(Frame.Text(payload))
                }.onFailure {
                    unregister(userId, session)
                }
            }
        }
    }
}
