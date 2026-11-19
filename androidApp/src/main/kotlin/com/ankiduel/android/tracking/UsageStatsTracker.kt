package com.ankiduel.android.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.ankiduel.shared.model.SocialAppCatalog
import java.time.LocalDate
import java.time.ZoneId

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val isDefaultSocial: Boolean
)

data class ForegroundEvent(
    val packageName: String,
    val eventType: Int,
    val timestampMs: Long
)

data class DailyUsageMeasurement(
    val ankiForegroundSeconds: Long,
    val socialForegroundSeconds: Long,
    val socialBreakdownSeconds: Map<String, Long>
)

class UsageStatsTracker(private val context: Context) {

    fun measureToday(
        trackedSocialPackages: Set<String>,
        nowMs: Long = System.currentTimeMillis()
    ): DailyUsageMeasurement {
        val midnightMs = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return DailyUsageMeasurement(0L, 0L, emptyMap())

        val usageEvents = runCatching {
            usageStatsManager.queryEvents(midnightMs, nowMs)
        }.getOrNull() ?: return DailyUsageMeasurement(0L, 0L, emptyMap())

        val extracted = mutableListOf<ForegroundEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            extracted.add(
                ForegroundEvent(
                    packageName = event.packageName.orEmpty(),
                    eventType = event.eventType,
                    timestampMs = event.timeStamp
                )
            )
        }

        val targetPackages = trackedSocialPackages + SocialAppCatalog.ANKIDROID_PACKAGE
        val secondsByPackage = computeForegroundSecondsFromEvents(
            events = extracted,
            targetPackages = targetPackages,
            windowStartMs = midnightMs,
            windowEndMs = nowMs
        )

        val ankiSec = secondsByPackage[SocialAppCatalog.ANKIDROID_PACKAGE] ?: 0L
        val socialMap = trackedSocialPackages
            .associateWith { pkg -> secondsByPackage[pkg] ?: 0L }
            .filterValues { it > 0L }
        val totalSocial = socialMap.values.sum()

        return DailyUsageMeasurement(
            ankiForegroundSeconds = ankiSec,
            socialForegroundSeconds = totalSocial,
            socialBreakdownSeconds = socialMap
        )
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
                    info.packageName != SocialAppCatalog.ANKIDROID_PACKAGE &&
                    (pm.getLaunchIntentForPackage(info.packageName) != null || info.packageName in defaultSocialSet)
            }
            .filter { info ->
                (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || info.packageName in defaultSocialSet
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
        private const val EVENT_DEVICE_SHUTDOWN = 26

        fun computeForegroundSecondsFromEvents(
            events: List<ForegroundEvent>,
            targetPackages: Set<String>,
            windowStartMs: Long,
            windowEndMs: Long
        ): Map<String, Long> {
            val activeStartByPkg = mutableMapOf<String, Long>()
            val accumulatedMs = mutableMapOf<String, Long>()

            for (ev in events) {
                val ts = ev.timestampMs.coerceIn(windowStartMs, windowEndMs)
                when (ev.eventType) {
                    EVENT_ACTIVITY_RESUMED -> {
                        // Clôturer toute autre session restée ouverte
                        activeStartByPkg.entries.toList().forEach { (openPkg, startTs) ->
                            if (openPkg != ev.packageName && ts > startTs) {
                                accumulatedMs[openPkg] = (accumulatedMs[openPkg] ?: 0L) + (ts - startTs)
                                activeStartByPkg.remove(openPkg)
                            }
                        }
                        if (ev.packageName in targetPackages && !activeStartByPkg.containsKey(ev.packageName)) {
                            activeStartByPkg[ev.packageName] = ts
                        }
                    }

                    EVENT_ACTIVITY_PAUSED -> {
                        val startTs = activeStartByPkg.remove(ev.packageName)
                        if (startTs != null && ts > startTs) {
                            accumulatedMs[ev.packageName] = (accumulatedMs[ev.packageName] ?: 0L) + (ts - startTs)
                        }
                    }

                    EVENT_SCREEN_NON_INTERACTIVE, EVENT_KEYGUARD_SHOWN, EVENT_DEVICE_SHUTDOWN -> {
                        activeStartByPkg.forEach { (openPkg, startTs) ->
                            if (ts > startTs) {
                                accumulatedMs[openPkg] = (accumulatedMs[openPkg] ?: 0L) + (ts - startTs)
                            }
                        }
                        activeStartByPkg.clear()
                    }
                }
            }

            // Clôturer les sessions encore au premier plan à windowEndMs
            activeStartByPkg.forEach { (openPkg, startTs) ->
                if (windowEndMs > startTs) {
                    accumulatedMs[openPkg] = (accumulatedMs[openPkg] ?: 0L) + (windowEndMs - startTs)
                }
            }

            return accumulatedMs.mapValues { (_, ms) -> ms / 1000L }
        }
    }
}
