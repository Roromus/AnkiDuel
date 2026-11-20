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

    @Test
    fun `keeps AnkiDroid session alive when switching activities with overlapping RESUMED and PAUSED`() {
        val t0 = 10_000_000L
        val events = listOf(
            // User opens DeckPicker at t0
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.DeckPicker"),
            // User opens Reviewer at t0+5s: Reviewer RESUMED fires BEFORE DeckPicker PAUSED/STOPPED
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0 + 5_000L, "com.ichi2.anki.Reviewer"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 5_020L, "com.ichi2.anki.DeckPicker"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 23, t0 + 5_500L, "com.ichi2.anki.DeckPicker"),
            // User studies 20 minutes (1200s) then exits Reviewer
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 1_205_000L, "com.ichi2.anki.Reviewer")
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 1_500_000L
        )

        assertEquals(1_205L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `clips cross-midnight session to start at windowStartMs`() {
        val midnight = 50_000_000L
        val events = listOf(
            // Opened 10 minutes before midnight, closed 15 minutes after midnight
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, midnight - 600_000L, "com.ichi2.anki.Reviewer"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, midnight + 900_000L, "com.ichi2.anki.Reviewer")
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = midnight,
            windowEndMs = midnight + 1_800_000L
        )

        assertEquals(900L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `closes previous package cleanly when another app resumes at the exact same timestamp`() {
        val t0 = 1_000_000L
        val events = listOf(
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.DeckPicker"),
            ForegroundEvent("com.instagram.android", 1, t0, "com.instagram.mainactivity.InstagramMainActivity"),
            ForegroundEvent("com.instagram.android", 2, t0 + 60_000L, "com.instagram.mainactivity.InstagramMainActivity")
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE, "com.instagram.android"),
            windowStartMs = t0,
            windowEndMs = t0 + 120_000L
        )

        assertEquals(null, result[SocialAppCatalog.ANKIDROID_PACKAGE])
        assertEquals(60L, result["com.instagram.android"])
    }

    @Test
    fun `does not leak session when duplicate RESUMED events fire for the same activity class`() {
        val t0 = 2_000_000L
        val events = listOf(
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.Reviewer"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0 + 10_000L, "com.ichi2.anki.Reviewer"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 60_000L, "com.ichi2.anki.Reviewer")
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 300_000L
        )

        assertEquals(60L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `closes package cleanly when PAUSED event omits className`() {
        val t0 = 3_000_000L
        val events = listOf(
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.Reviewer"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 120_000L, "")
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 600_000L
        )

        assertEquals(120L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }
}
