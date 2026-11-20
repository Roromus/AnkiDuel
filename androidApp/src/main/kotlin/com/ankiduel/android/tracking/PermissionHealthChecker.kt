package com.ankiduel.android.tracking

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ankiduel.android.blocker.SocialBlockAccessibilityService
import com.ankiduel.shared.model.SocialAppCatalog

data class PermissionHealth(
    val usageAccessGranted: Boolean,
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
    val ankiDroidInstalled: Boolean,
    val ankiDroidApiGranted: Boolean,
    val ignoringBatteryOptimizations: Boolean = true,
    val ankiDroidPackage: String? = null
) {
    val allEnforcementPermissionsHealthy: Boolean
        get() = usageAccessGranted && accessibilityGranted && overlayGranted

    fun evaluateRemoteHealth(): Pair<Boolean, String?> = when {
        !usageAccessGranted -> false to "Accès utilisation manquant"
        !accessibilityGranted -> false to "Accessibilité coupée"
        !overlayGranted -> false to "Superposition manquante"
        !ankiDroidInstalled -> false to "AnkiDroid non installé"
        !ankiDroidApiGranted -> false to "API AnkiDroid non autorisée"
        !ignoringBatteryOptimizations -> false to "Optimisation batterie active"
        else -> true to null
    }
}

class PermissionHealthChecker(private val context: Context) {

    fun check(): PermissionHealth {
        val detectedAnkiPkg = SocialAppCatalog.ankiDroidPackages.firstOrNull { isPackageInstalled(it) }
        val apiPermGranted = buildList {
            add(AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION)
            if (detectedAnkiPkg != null && detectedAnkiPkg != SocialAppCatalog.ANKIDROID_PACKAGE) {
                add("$detectedAnkiPkg.permission.READ_WRITE_DATABASE")
            }
        }.any { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

        return PermissionHealth(
            usageAccessGranted = hasUsageStatsPermission(),
            accessibilityGranted = isAccessibilityServiceEnabled(),
            overlayGranted = Settings.canDrawOverlays(context),
            notificationsGranted = hasNotificationPermission(),
            ankiDroidInstalled = detectedAnkiPkg != null,
            ankiDroidApiGranted = apiPermGranted,
            ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(),
            ankiDroidPackage = detectedAnkiPkg
        )
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, SocialBlockAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }
}
