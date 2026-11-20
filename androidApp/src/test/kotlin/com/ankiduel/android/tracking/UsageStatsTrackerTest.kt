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

    @Test
    fun `does not lose second deck session when delayed STOPPED from previous Reviewer arrives (with instanceId)`() {
        val t0 = 10_000_000L
        val events = listOf(
            // Deck 1 in Reviewer instance 101 for 6 minutes (360s)
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.Reviewer", instanceId = 101),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 360_000L, "com.ichi2.anki.Reviewer", instanceId = 101),
            // Back to DeckPicker briefly (300ms) then immediately open Deck 2 in Reviewer instance 102
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0 + 360_050L, "com.ichi2.anki.DeckPicker", instanceId = 100),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 360_350L, "com.ichi2.anki.DeckPicker", instanceId = 100),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0 + 360_400L, "com.ichi2.anki.Reviewer", instanceId = 102),
            // Delayed STOPPED from old Reviewer instance 101 arrives 400ms later!
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 23, t0 + 360_800L, "com.ichi2.anki.Reviewer", instanceId = 101),
            // User studies Deck 2 for 10 minutes (600s)
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 960_400L, "com.ichi2.anki.Reviewer", instanceId = 102)
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 1_200_000L
        )

        // 360s + 0.3s + 600s = 960s (16 minutes, NOT 6 minutes!)
        assertEquals(960L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `does not lose second deck session when delayed STOPPED arrives without instanceId (API 26-28)`() {
        val t0 = 10_000_000L
        val events = listOf(
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.Reviewer"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 360_000L, "com.ichi2.anki.Reviewer"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0 + 360_050L, "com.ichi2.anki.DeckPicker"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 360_350L, "com.ichi2.anki.DeckPicker"),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0 + 360_400L, "com.ichi2.anki.Reviewer"),
            // Stale STOPPED of first Reviewer instance without instanceId
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 23, t0 + 360_800L, "com.ichi2.anki.Reviewer"),
            // 10 minutes in second Reviewer
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 960_400L, "com.ichi2.anki.Reviewer")
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 1_200_000L
        )

        assertEquals(960L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `keeps session alive when overlapping SingleFragmentActivity instances share same className`() {
        val t0 = 20_000_000L
        val events = listOf(
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.SingleFragmentActivity", instanceId = 201),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0 + 30_000L, "com.ichi2.anki.SingleFragmentActivity", instanceId = 202),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 30_050L, "com.ichi2.anki.SingleFragmentActivity", instanceId = 201),
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 630_000L, "com.ichi2.anki.SingleFragmentActivity", instanceId = 202)
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 900_000L
        )

        assertEquals(630L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `recovers AnkiDroid session after transient KEYGUARD_SHOWN and KEYGUARD_HIDDEN race without PAUSED`() {
        val t0 = 30_000_000L
        val events = listOf(
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.Reviewer", instanceId = 301),
            ForegroundEvent("android", 17, t0 + 1_000L), // KEYGUARD_SHOWN race
            ForegroundEvent("android", 18, t0 + 2_000L), // KEYGUARD_HIDDEN
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 602_000L, "com.ichi2.anki.Reviewer", instanceId = 301)
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 900_000L
        )

        assertEquals(601L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `does not reopen session on KEYGUARD_HIDDEN if activity actually paused under keyguard`() {
        val t0 = 30_000_000L
        val events = listOf(
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 1, t0, "com.ichi2.anki.Reviewer", instanceId = 301),
            ForegroundEvent("android", 17, t0 + 60_000L), // KEYGUARD_SHOWN
            ForegroundEvent(SocialAppCatalog.ANKIDROID_PACKAGE, 2, t0 + 60_100L, "com.ichi2.anki.Reviewer", instanceId = 301),
            ForegroundEvent("android", 18, t0 + 300_000L) // KEYGUARD_HIDDEN later (user went to launcher)
        )

        val result = UsageStatsTracker.computeForegroundSecondsFromEvents(
            events = events,
            targetPackages = setOf(SocialAppCatalog.ANKIDROID_PACKAGE),
            windowStartMs = t0,
            windowEndMs = t0 + 900_000L
        )

        assertEquals(60L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }

    @Test
    fun `aggregates strict today UsageStats buckets without leaking yesterday and sums post-reboot buckets`() {
        val midnight = 100_000_000L
        val now = midnight + 10L * 3600L * 1000L
        val buckets = listOf(
            // Yesterday's bucket: MUST be ignored
            DailyBucketEntry(
                packageName = SocialAppCatalog.ANKIDROID_PACKAGE,
                firstTimeStamp = midnight - 24L * 3600L * 1000L,
                lastTimeStamp = midnight + 1_000L,
                lastTimeUsed = midnight + 500L,
                totalTimeInForegroundMs = 3_600_000L
            ),
            // Morning bucket before reboot: 10 min (600s)
            DailyBucketEntry(
                packageName = SocialAppCatalog.ANKIDROID_PACKAGE,
                firstTimeStamp = midnight,
                lastTimeStamp = midnight + 5L * 3600L * 1000L,
                lastTimeUsed = midnight + 4L * 3600L * 1000L,
                totalTimeInForegroundMs = 600_000L
            ),
            // Mid-morning bucket after reboot: 6 min (360s)
            DailyBucketEntry(
                packageName = SocialAppCatalog.ANKIDROID_PACKAGE,
                firstTimeStamp = midnight + 6L * 3600L * 1000L,
                lastTimeStamp = now,
                lastTimeUsed = now - 60_000L,
                totalTimeInForegroundMs = 360_000L
            )
        )

        val result = UsageStatsTracker.aggregateStrictTodayBuckets(buckets, midnight, now)
        assertEquals(960L, result[SocialAppCatalog.ANKIDROID_PACKAGE])
    }
}
