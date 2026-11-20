package com.ankiduel.android.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ankiduel.shared.model.SocialAppCatalog
import java.time.Instant
import java.time.ZoneId

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val isDefaultSocial: Boolean
)

data class ForegroundEvent(
    val packageName: String,
    val eventType: Int,
    val timestampMs: Long,
    val className: String = ""
)

data class DailyUsageMeasurement(
    val ankiForegroundSeconds: Long,
    val socialForegroundSeconds: Long,
    val socialBreakdownSeconds: Map<String, Long>,
    val ankiEventCount: Int = 0,
    val usedDailyStatsFallback: Boolean = false
)

class UsageStatsTracker(private val context: Context) {

    fun measureToday(
        trackedSocialPackages: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): DailyUsageMeasurement {
        val zone = ZoneId.systemDefault()
        val midnightMs = Instant.ofEpochMilli(nowMs)
            .atZone(zone)
            .toLocalDate()
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return DailyUsageMeasurement(0L, 0L, emptyMap())

        // Inclure une fenêtre avant minuit pour capter les sessions Anki/réseaux démarrées avant 00h00
        val lookbackStartMs = (midnightMs - LOOKBACK_BEFORE_MIDNIGHT_MS).coerceAtLeast(0L)
        val usageEvents = runCatching {
            usageStatsManager.queryEvents(lookbackStartMs, nowMs)
        }.getOrNull() ?: return DailyUsageMeasurement(0L, 0L, emptyMap())

        val extracted = mutableListOf<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            extracted.add(
                ForegroundEvent(
                    packageName = event.packageName.orEmpty(),
                    eventType = event.eventType,
                    timestampMs = event.timeStamp,
                    className = event.className.orEmpty()
                )
            )
        }

        val detectedAnkiPackages = extracted
            .asSequence()
            .map { it.packageName }
            .filter { SocialAppCatalog.isAnkiDroidPackage(it) }
            .toSet()
        val ankiPackages = SocialAppCatalog.ankiDroidPackages.toSet() + detectedAnkiPackages

        val targetPackages = trackedSocialPackages + ankiPackages
        val secondsByPackage = computeForegroundSecondsFromEvents(
            events = extracted,
            targetPackages = targetPackages,
            windowStartMs = midnightMs,
            windowEndMs = nowMs
        ).toMutableMap()

        val ankiEventCount = extracted.count {
            it.packageName in ankiPackages && it.timestampMs >= midnightMs
        }

        var ankiSec = ankiPackages.sumOf { secondsByPackage[it] ?: 0L }
        var usedDailyFallback = false

        // Secours sur certains appareils OEM dont queryEvents omet des événements RESUMED :
        // on ne consulte queryUsageStats que si queryEvents a renvoyé 0s et sur un bucket strictement du jour.
        if (ankiSec == 0L || trackedSocialPackages.any { (secondsByPackage[it] ?: 0L) == 0L }) {
            val dailyBuckets = queryStrictTodayUsageStats(usageStatsManager, midnightMs, nowMs)
            if (ankiSec == 0L) {
                val dailyAnkiSec = ankiPackages.sumOf { dailyBuckets[it] ?: 0L }
                if (dailyAnkiSec > 0L) {
                    ankiSec = dailyAnkiSec
                    usedDailyFallback = true
                }
            }
            for (pkg in trackedSocialPackages) {
                if ((secondsByPackage[pkg] ?: 0L) == 0L) {
                    val dailyPkgSec = dailyBuckets[pkg] ?: 0L
                    if (dailyPkgSec > 0L) {
                        secondsByPackage[pkg] = dailyPkgSec
                    }
                }
            }
        }

        val socialMap = trackedSocialPackages
            .associateWith { pkg -> secondsByPackage[pkg] ?: 0L }
            .filterValues { it > 0L }
        val totalSocial = socialMap.values.sum()

        return DailyUsageMeasurement(
            ankiForegroundSeconds = ankiSec,
            socialForegroundSeconds = totalSocial,
            socialBreakdownSeconds = socialMap,
            ankiEventCount = ankiEventCount,
            usedDailyStatsFallback = usedDailyFallback
        )
    }

    private fun queryStrictTodayUsageStats(
        usageStatsManager: UsageStatsManager,
        midnightMs: Long,
        nowMs: Long
    ): Map<String, Long> {
        val stats = runCatching {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnightMs, nowMs)
        }.getOrNull().orEmpty()

        if (stats.isEmpty()) return emptyMap()

        val totalsByPkg = mutableMapOf<String, Long>()
        for (stat in stats) {
            val pkg = stat.packageName ?: continue
            // Ne jamais inclure un bucket ayant commencé hier (risque de fausser la remise à zéro à minuit)
            val startedToday = stat.firstTimeStamp >= midnightMs - DAILY_BUCKET_TOLERANCE_MS
            val usedToday = stat.lastTimeUsed >= midnightMs
            if (startedToday && usedToday && stat.totalTimeInForeground > 0L) {
                val sec = stat.totalTimeInForeground / 1000L
                val existing = totalsByPkg[pkg] ?: 0L
                if (sec > existing) {
                    totalsByPkg[pkg] = sec
                }
            }
        }
        return totalsByPkg
    }

    fun listLaunchableApps(): List<InstalledAppItem> {
        val pm = context.packageManager
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        val defaultSocialSet = SocialAppCatalog.defaultSocialApps.map { it.packageName }.toSet()

        return apps.asSequence()
            .filter { info ->
                info.packageName != context.packageName &&
                    !SocialAppCatalog.isAnkiDroidPackage(info.packageName) &&
                    (pm.getLaunchIntentForPackage(info.packageName) != null || info.packageName in defaultSocialSet)
            }
            .filter { info ->
                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                    info.packageName in defaultSocialSet
            }
            .map { info ->
                InstalledAppItem(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    isDefaultSocial = info.packageName in defaultSocialSet
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<InstalledAppItem> { it.isDefaultSocial }.thenBy { it.label.lowercase() })
            .toList()
    }

    companion object {
        private const val EVENT_ACTIVITY_RESUMED = 1
        private const val EVENT_ACTIVITY_PAUSED = 2
        private const val EVENT_SCREEN_NON_INTERACTIVE = 16
        private const val EVENT_KEYGUARD_SHOWN = 17
        private const val EVENT_ACTIVITY_STOPPED = 23
        private const val EVENT_DEVICE_SHUTDOWN = 26

        private const val LOOKBACK_BEFORE_MIDNIGHT_MS = 6L * 3600L * 1000L
        private const val DAILY_BUCKET_TOLERANCE_MS = 5L * 60L * 1000L
        private const val DEFAULT_CLASS_KEY = "<default>"

        fun computeForegroundSecondsFromEvents(
            events: List<ForegroundEvent>,
            targetPackages: Set<String>,
            windowStartMs: Long,
            windowEndMs: Long
        ): Map<String, Long> {
            val activeStartByPkg = mutableMapOf<String, Long>()
            val activeClassesByPkg = mutableMapOf<String, MutableSet<String>>()
            val accumulatedMs = mutableMapOf<String, Long>()

            fun closePackage(pkg: String, endTs: Long) {
                val startTs = activeStartByPkg.remove(pkg)
                activeClassesByPkg.remove(pkg)
                if (startTs != null) {
                    val effectiveStart = maxOf(startTs, windowStartMs)
                    val effectiveEnd = minOf(endTs, windowEndMs)
                    if (effectiveEnd > effectiveStart) {
                        accumulatedMs[pkg] = (accumulatedMs[pkg] ?: 0L) + (effectiveEnd - effectiveStart)
                    }
                }
            }

            fun closeAllPackages(endTs: Long) {
                activeStartByPkg.keys.toList().forEach { pkg ->
                    closePackage(pkg, endTs)
                }
            }

            val orderedEvents = events.sortedBy { it.timestampMs }

            for (ev in orderedEvents) {
                if (ev.timestampMs > windowEndMs) continue

                when (ev.eventType) {
                    EVENT_ACTIVITY_RESUMED -> {
                        // Clôturer toute autre application restée ouverte
                        activeStartByPkg.keys.toList().forEach { openPkg ->
                            if (openPkg != ev.packageName) {
                                closePackage(openPkg, ev.timestampMs)
                            }
                        }
                        if (ev.packageName in targetPackages) {
                            val clsKey = ev.className.ifBlank { DEFAULT_CLASS_KEY }
                            val classes = activeClassesByPkg.getOrPut(ev.packageName) { mutableSetOf() }
                            classes.add(clsKey)
                            if (!activeStartByPkg.containsKey(ev.packageName)) {
                                activeStartByPkg[ev.packageName] = ev.timestampMs
                            }
                        }
                    }

                    EVENT_ACTIVITY_PAUSED, EVENT_ACTIVITY_STOPPED -> {
                        val pkg = ev.packageName
                        if (activeStartByPkg.containsKey(pkg)) {
                            val classes = activeClassesByPkg[pkg]
                            val clsKey = ev.className.ifBlank { DEFAULT_CLASS_KEY }
                            if (ev.eventType == EVENT_ACTIVITY_PAUSED && ev.className.isBlank()) {
                                // Certains appareils OEM omettent className sur ACTIVITY_PAUSED
                                closePackage(pkg, ev.timestampMs)
                            } else if (classes != null && classes.remove(clsKey)) {
                                if (classes.isEmpty()) {
                                    closePackage(pkg, ev.timestampMs)
                                }
                            } else if (ev.eventType == EVENT_ACTIVITY_PAUSED && (classes == null || classes.isEmpty())) {
                                closePackage(pkg, ev.timestampMs)
                            }
                        }
                    }

                    EVENT_SCREEN_NON_INTERACTIVE, EVENT_KEYGUARD_SHOWN, EVENT_DEVICE_SHUTDOWN -> {
                        closeAllPackages(ev.timestampMs)
                    }
                }
            }

            // Clôturer les sessions encore au premier plan à windowEndMs
            closeAllPackages(windowEndMs)

            return accumulatedMs.mapValues { (_, ms) -> ms / 1000L }
        }
    }
}
