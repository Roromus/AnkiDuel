package com.ankiduel.android.tracking

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ankiduel.android.blocker.SocialBlockAccessibilityService

data class PermissionHealth(
    val usageAccessGranted: Boolean,
    val accessibilityGranted: Boolean,
    val overlayGranted: Boolean,
    val notificationsGranted: Boolean,
    val ankiDroidInstalled: Boolean,
    val ankiDroidApiGranted: Boolean
) {
    val allEnforcementPermissionsHealthy: Boolean
        get() = usageAccessGranted && accessibilityGranted && overlayGranted
}

class PermissionHealthChecker(private val context: Context) {

    fun check(): PermissionHealth {
        return PermissionHealth(
            usageAccessGranted = hasUsageStatsPermission(),
            accessibilityGranted = isAccessibilityServiceEnabled(),
            overlayGranted = Settings.canDrawOverlays(context),
            notificationsGranted = hasNotificationPermission(),
            ankiDroidInstalled = isPackageInstalled("com.ichi2.anki"),
            ankiDroidApiGranted = ContextCompat.checkSelfPermission(
                context,
                "com.ichi2.anki.permission.READ_WRITE_DATABASE"
            ) == PackageManager.PERMISSION_GRANTED
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
