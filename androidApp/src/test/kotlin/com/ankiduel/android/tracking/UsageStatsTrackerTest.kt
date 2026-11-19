package com.ankiduel.android.tracking

import com.ankiduel.shared.model.SocialAppCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageStatsTrackerTest {

    @Test
    fun `computes exact foreground seconds and closes open sessions on screen off`() {
        val t0 = 1_000_000L
        val events = listOf(
            // AnkiDroid open for 120s
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 120_000L),
            // Instagram open for 90s then screen turns off
            ForegroundEvent("com.instagram.android", 1, t0 + 180_000L),
            ForegroundEvent("android", 16, t0 + 270_000L),
            // TikTok open at t+300s and still running at windowEnd t+360s -> 60s
            ForegroundEvent("com.zhiliaoapp.musically", 1, t0 + 300_000L)
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(
                SocialAppCatalog.ANKIDROID_PACKAGE,
                "com.instagram.android",
                "com.zhiliaoapp.musically"
            ),
            windowStartMs = t0,
            windowEndMs = t0 + 360_000L
        )

        assertEquals(120L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
        assertEquals(90L, result["com.instagram.android"])
        assertEquals(60L, result["com.zhiliaoapp.musically"])
    }
}
