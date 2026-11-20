package com.ankiduel.shared.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParticipantSerializationTest {

    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val defaultJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `participant with 3 devices fits within 2048 byte firestore rule limit and roundtrips`() {
        val devices = (1..3).associate { i ->
            val id = "device-uuid-0000-0000-0000-00000000000$i"
            id to DeviceDailyContribution(
                deviceId = id,
                deviceName = "Samsung Galaxy Tab S9 $i",
                dayKey = "2026-11-20",
                ankiEffectiveSeconds = 1800L * i,
                cardsReviewed = 50 * i,
                socialSeconds = 300L * i,
                permissionsHealthy = true,
                appHealthOk = i != 2,
                appHealthIssue = if (i == 2) "Optimisation batterie active" else null,
                lastSyncEpochMs = 1_795_171_555_013L + i
            )
        }
        val participant = ParticipantState(
            userId = "firebase-uid-1234567890abcdef",
            username = "roro",
            status = ParticipantStatus.ACTIVE,
            ankiEffectiveSeconds = 10800L,
            socialSeconds = 1800L,
            cardsReviewed = 150,
            netScoreSeconds = 9000L,
            lastSyncEpochMs = 1_795_171_555_016L,
            lastSyncDayKey = "2026-11-20",
            ankiApiConnected = true,
            appHealthOk = false,
            appHealthIssue = "Samsung Galaxy Tab S9 2: Optimisation batterie active",
            deviceContributions = devices
        )

        val encoded = compactJson.encodeToString(participant)
        assertTrue(
            encoded.length <= 2048,
            "Encoded ParticipantState length (${encoded.length}) must stay <= 2048 for Firestore rules"
        )

        val decoded = defaultJson.decodeFromString<ParticipantState>(encoded)
        assertEquals(participant, decoded)
    }
}
