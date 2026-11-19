package com.ankiduel.android

import android.app.Application
import com.ankiduel.android.data.AnkiDuelApiClient
import com.ankiduel.android.data.AnkiDuelRepository
import com.ankiduel.android.data.PreferencesStore
import com.ankiduel.android.service.DuelSyncWorker
import com.ankiduel.android.tracking.AnkiDroidTracker
import com.ankiduel.android.tracking.PermissionHealthChecker
import com.ankiduel.android.tracking.UsageStatsTracker

class AppContainer(application: Application) {
    val prefs = PreferencesStore(application)
    val apiClient = AnkiDuelApiClient()
    val usageTracker = UsageStatsTracker(application)
    val ankiTracker = AnkiDroidTracker(application)
    val permissionChecker = PermissionHealthChecker(application)
    val repository = AnkiDuelRepository(
        prefs = prefs,
        api = apiClient,
        usageTracker = usageTracker,
        ankiTracker = ankiTracker,
        permissionChecker = permissionChecker
    )
}

class AnkiDuelApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DuelSyncWorker.schedule(this)
    }
}
