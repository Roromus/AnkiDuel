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
    val className: String = "",
    val instanceId: Int = 0
)

data class DailyBucketEntry(
    val packageName: String,
    val firstTimeStamp: Long,
    val lastTimeStamp: Long,
    val lastTimeUsed: Long,
    val totalTimeInForegroundMs: Long
)

data class DailyUsageMeasurement(
    val ankiForegroundSeconds: Long,
    val socialForegroundSeconds: Long,
    val socialBreakdownSeconds: Map<String, Long>,
    val ankiEventCount: Int = 0,
    val usedDailyStatsFallback: Boolean = false,
    val eventsAnkiSeconds: Long = ankiForegroundSeconds,
    val dailyStatsAnkiSeconds: Long = 0L
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
            val instId = 0
            extracted.add(
                ForegroundEvent(
                    packageName = event.packageName.orEmpty(),
                    eventType = event.eventType,
                    timestampMs = event.timeStamp,
                    className = event.className.orEmpty(),
                    instanceId = instId
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

        val eventsAnkiSec = ankiPackages.sumOf { secondsByPackage[it] ?: 0L }

        // Toujours croiser avec les buckets quotidiens strictement du jour :
        // si queryEvents a évincé des événements plus tôt dans la journée ou omis un RESUMED
        // (ex: OEM, changement d'écran, redémarrage), le bucket officiel Android conserve le vrai cumul.
        val dailyBuckets = queryStrictTodayUsageStats(usageStatsManager, midnightMs, nowMs)
        val dailyAnkiSec = ankiPackages.sumOf { dailyBuckets[it] ?: 0L }
        val usedDailyFallback = dailyAnkiSec > eventsAnkiSec
        val ankiSec = maxOf(eventsAnkiSec, dailyAnkiSec)

        for (pkg in trackedSocialPackages) {
            val eventsPkgSec = secondsByPackage[pkg] ?: 0L
            val dailyPkgSec = dailyBuckets[pkg] ?: 0L
            if (dailyPkgSec > eventsPkgSec) {
                secondsByPackage[pkg] = dailyPkgSec
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
            usedDailyStatsFallback = usedDailyFallback,
            eventsAnkiSeconds = eventsAnkiSec,
            dailyStatsAnkiSeconds = dailyAnkiSec
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

        val entries = stats.mapNotNull { stat ->
            val pkg = stat.packageName ?: return@mapNotNull null
            DailyBucketEntry(
                packageName = pkg,
                firstTimeStamp = stat.firstTimeStamp,
                lastTimeStamp = stat.lastTimeStamp,
                lastTimeUsed = stat.lastTimeUsed,
                totalTimeInForegroundMs = stat.totalTimeInForeground
            )
        }
        return aggregateStrictTodayBuckets(entries, midnightMs, nowMs)
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
        private const val EVENT_KEYGUARD_HIDDEN = 18
        private const val EVENT_ACTIVITY_STOPPED = 23
        private const val EVENT_DEVICE_SHUTDOWN = 26

        private const val LOOKBACK_BEFORE_MIDNIGHT_MS = 6L * 3600L * 1000L
        private const val DAILY_BUCKET_TOLERANCE_MS = 5L * 60L * 1000L
        private const val STALE_STOPPED_WINDOW_MS = 30L * 1000L
        private const val DEFAULT_CLASS_KEY = "<default>"

        fun aggregateStrictTodayBuckets(
            buckets: List<DailyBucketEntry>,
            midnightMs: Long,
            nowMs: Long
        ): Map<String, Long> {
            val maxElapsedSec = ((nowMs - midnightMs) / 1000L).coerceAtLeast(0L)
            if (maxElapsedSec == 0L || buckets.isEmpty()) return emptyMap()

            val validByPkg = buckets
                .filter { b ->
                    val startedToday = b.firstTimeStamp >= midnightMs - DAILY_BUCKET_TOLERANCE_MS
                    val usedToday = b.lastTimeUsed >= midnightMs
                    val notFuture = b.lastTimeStamp <= nowMs + DAILY_BUCKET_TOLERANCE_MS
                    startedToday && usedToday && notFuture && b.totalTimeInForegroundMs > 0L
                }
                .groupBy { it.packageName }

            val totalsByPkg = mutableMapOf<String, Long>()
            for ((pkg, pkgBuckets) in validByPkg) {
                val sorted = pkgBuckets.sortedWith(
                    compareBy<DailyBucketEntry> { it.firstTimeStamp }
                        .thenByDescending { it.lastTimeStamp }
                )
                var totalMs = 0L
                var currentSpanEnd = Long.MIN_VALUE
                var currentSpanMaxMs = 0L

                for (bucket in sorted) {
                    val bucketEnd = maxOf(bucket.lastTimeStamp, bucket.firstTimeStamp)
                    if (currentSpanEnd == Long.MIN_VALUE) {
                        currentSpanEnd = bucketEnd
                        currentSpanMaxMs = bucket.totalTimeInForegroundMs
                    } else if (bucket.firstTimeStamp >= currentSpanEnd - 1_000L) {
                        // Bucket distinct non chevauchant (ex: après redémarrage en cours de journée)
                        totalMs += currentSpanMaxMs
                        currentSpanEnd = bucketEnd
                        currentSpanMaxMs = bucket.totalTimeInForegroundMs
                    } else {
                        // Buckets chevauchants : garder le maximum pour éviter un double comptage
                        currentSpanEnd = maxOf(currentSpanEnd, bucketEnd)
                        currentSpanMaxMs = maxOf(currentSpanMaxMs, bucket.totalTimeInForegroundMs)
                    }
                }
                if (currentSpanEnd != Long.MIN_VALUE) {
                    totalMs += currentSpanMaxMs
                }

                val sec = (totalMs / 1000L).coerceAtMost(maxElapsedSec)
                if (sec > 0L) {
                    totalsByPkg[pkg] = sec
                }
            }
            return totalsByPkg
        }

        private fun activityKey(ev: ForegroundEvent): String {
            return if (ev.instanceId != 0) {
                "inst:${ev.instanceId}"
            } else {
                "cls:${ev.className.ifBlank { DEFAULT_CLASS_KEY }}"
            }
        }

        fun computeForegroundSecondsFromEvents(
            events: List<ForegroundEvent>,
            targetPackages: Set<String>,
            windowStartMs: Long,
            windowEndMs: Long
        ): Map<String, Long> {
            val activeStartByPkg = mutableMapOf<String, Long>()
            val activeInstancesByPkg = mutableMapOf<String, MutableSet<String>>()
            val suspendedByKeyguard = mutableMapOf<String, MutableSet<String>>()
            val pendingStopsByPkgClass = mutableMapOf<Pair<String, String>, ArrayDeque<Long>>()
            val accumulatedMs = mutableMapOf<String, Long>()

            fun closePackage(pkg: String, endTs: Long) {
                val startTs = activeStartByPkg.remove(pkg)
                activeInstancesByPkg.remove(pkg)
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
                        // Une vraie reprise d'activité remplace toute suspension par l'écran de verrouillage
                        suspendedByKeyguard.clear()

                        // Clôturer toute autre application restée ouverte
                        activeStartByPkg.keys.toList().forEach { openPkg ->
                            if (openPkg != ev.packageName) {
                                closePackage(openPkg, ev.timestampMs)
                            }
                        }
                        if (ev.packageName in targetPackages) {
                            val key = activityKey(ev)
                            val instances = activeInstancesByPkg.getOrPut(ev.packageName) { mutableSetOf() }
                            instances.add(key)
                            if (!activeStartByPkg.containsKey(ev.packageName)) {
                                activeStartByPkg[ev.packageName] = ev.timestampMs
                            }
                        }
                    }

                    EVENT_ACTIVITY_PAUSED, EVENT_ACTIVITY_STOPPED -> {
                        val pkg = ev.packageName
                        val key = activityKey(ev)
                        val clsKey = ev.className.ifBlank { DEFAULT_CLASS_KEY }

                        // Si l'activité s'est réellement mise en pause/arrêtée sous le keyguard, ne pas la rouvrir au déverrouillage
                        suspendedByKeyguard[pkg]?.let { suspended ->
                            if (ev.eventType == EVENT_ACTIVITY_PAUSED && ev.instanceId == 0 && ev.className.isBlank()) {
                                suspendedByKeyguard.remove(pkg)
                            } else {
                                suspended.remove(key)
                                if (suspended.isEmpty()) suspendedByKeyguard.remove(pkg)
                            }
                        }

                        if (ev.eventType == EVENT_ACTIVITY_PAUSED && ev.instanceId == 0) {
                            val queue = pendingStopsByPkgClass.getOrPut(pkg to clsKey) { ArrayDeque() }
                            queue.addLast(ev.timestampMs)
                            while (queue.size > 16) queue.removeFirst()
                        }

                        if (activeStartByPkg.containsKey(pkg)) {
                            val instances = activeInstancesByPkg[pkg]
                            if (ev.eventType == EVENT_ACTIVITY_PAUSED && ev.instanceId == 0 && ev.className.isBlank()) {
                                // Certains appareils OEM omettent className sur ACTIVITY_PAUSED
                                closePackage(pkg, ev.timestampMs)
                            } else if (ev.eventType == EVENT_ACTIVITY_STOPPED && ev.instanceId == 0) {
                                // Un STOPPED tardif correspondant à un PAUSED récent d'une ancienne instance
                                // de la même classe (ex: Reviewer deck 1 -> DeckPicker -> Reviewer deck 2)
                                // ne doit pas clôturer la nouvelle instance active.
                                val queue = pendingStopsByPkgClass[pkg to clsKey]
                                while (queue != null && queue.isNotEmpty() && ev.timestampMs - queue.first() > STALE_STOPPED_WINDOW_MS) {
                                    queue.removeFirst()
                                }
                                if (queue != null && queue.isNotEmpty()) {
                                    queue.removeFirst()
                                } else if (instances != null && instances.remove(key)) {
                                    if (instances.isEmpty()) {
                                        closePackage(pkg, ev.timestampMs)
                                    }
                                }
                            } else if (instances != null && instances.remove(key)) {
                                if (instances.isEmpty()) {
                                    closePackage(pkg, ev.timestampMs)
                                }
                            } else if (ev.eventType == EVENT_ACTIVITY_PAUSED && (instances == null || instances.isEmpty())) {
                                closePackage(pkg, ev.timestampMs)
                            }
                        } else if (ev.eventType == EVENT_ACTIVITY_STOPPED && ev.instanceId == 0) {
                            // Consommer le PAUSED correspondant même si le package est déjà fermé
                            val queue = pendingStopsByPkgClass[pkg to clsKey]
                            while (queue != null && queue.isNotEmpty() && ev.timestampMs - queue.first() > STALE_STOPPED_WINDOW_MS) {
                                queue.removeFirst()
                            }
                            if (queue != null && queue.isNotEmpty()) {
                                queue.removeFirst()
                            }
                        }
                    }

                    EVENT_KEYGUARD_SHOWN -> {
                        // Sauvegarder les activités encore actives (non pausées) pour récupérer en cas de race
                        // KEYGUARD_SHOWN -> KEYGUARD_HIDDEN sans nouveau ACTIVITY_RESUMED (déverrouillage rapide).
                        suspendedByKeyguard.clear()
                        for ((pkg, instances) in activeInstancesByPkg) {
                            if (instances.isNotEmpty()) {
                                suspendedByKeyguard[pkg] = instances.toMutableSet()
                            }
                        }
                        closeAllPackages(ev.timestampMs)
                    }

                    EVENT_KEYGUARD_HIDDEN -> {
                        if (activeStartByPkg.isEmpty() && suspendedByKeyguard.isNotEmpty()) {
                            for ((pkg, instances) in suspendedByKeyguard) {
                                if (pkg in targetPackages && instances.isNotEmpty()) {
                                    activeStartByPkg[pkg] = ev.timestampMs
                                    activeInstancesByPkg[pkg] = instances.toMutableSet()
                                }
                            }
                        }
                        suspendedByKeyguard.clear()
                    }

                    EVENT_SCREEN_NON_INTERACTIVE, EVENT_DEVICE_SHUTDOWN -> {
                        suspendedByKeyguard.clear()
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
