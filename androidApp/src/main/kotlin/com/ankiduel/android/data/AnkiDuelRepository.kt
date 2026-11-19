package com.ankiduel.android.data

import com.ankiduel.android.blocker.BlockStateHolder
import com.ankiduel.android.tracking.AnkiDroidTracker
import com.ankiduel.android.tracking.InstalledAppItem
import com.ankiduel.android.tracking.PermissionHealth
import com.ankiduel.android.tracking.PermissionHealthChecker
import com.ankiduel.android.tracking.UsageStatsTracker
import com.ankiduel.shared.engine.AntiIdleValidator
import com.ankiduel.shared.engine.DuelDeltaCalculator
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.GlobalLockStatus
import com.ankiduel.shared.model.UsageSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AnkiDuelRepository(
    private val prefs: PreferencesStore,
    private val api: AnkiDuelApiClient,
    private val usageTracker: UsageStatsTracker,
    private val ankiTracker: AnkiDroidTracker,
    private val permissionChecker: PermissionHealthChecker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val antiIdleValidator = AntiIdleValidator()
    private val demoBot = DemoBotEngine()

    private val _session = MutableStateFlow<StoredSession?>(null)
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    private val _duels = MutableStateFlow<List<DuelState>>(emptyList())
    val duels: StateFlow<List<DuelState>> = _duels.asStateFlow()

    private val _usageSnapshot = MutableStateFlow(
        UsageSnapshot(
            ankiForegroundSeconds = 0L,
            ankiEffectiveSeconds = 0L,
            cardsReviewed = 0,
            socialForegroundSeconds = 0L,
            capturedAtEpochMs = System.currentTimeMillis()
        )
    )
    val usageSnapshot: StateFlow<UsageSnapshot> = _usageSnapshot.asStateFlow()

    private val _permissionHealth = MutableStateFlow(permissionChecker.check())
    val permissionHealth: StateFlow<PermissionHealth> = _permissionHealth.asStateFlow()

    private val _isIdleFlagged = MutableStateFlow(false)
    val isIdleFlagged: StateFlow<Boolean> = _isIdleFlagged.asStateFlow()

    private val _lockStatus = MutableStateFlow(
        GlobalLockStatus(false, 0L, 0, null, null, null, 0L)
    )
    val lockStatus: StateFlow<GlobalLockStatus> = _lockStatus.asStateFlow()

    private var wsJob: Job? = null

    init {
        scope.launch {
            prefs.sessionFlow.collect { stored ->
                _session.value = stored
                if (stored.demoMode) {
                    wsJob?.cancel()
                    refreshAndSync()
                } else if (!stored.token.isNullOrBlank()) {
                    startWebSocketListener(stored.serverUrl, stored.token)
                    refreshAndSync()
                } else {
                    wsJob?.cancel()
                    _duels.value = emptyList()
                    updateLockStatus(emptyList(), stored.userId.orEmpty(), stored.trackedSocialPackages)
                }
            }
        }
    }

    suspend fun login(serverUrl: String, username: String, password: String) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val res = api.login(cleanUrl, username, password)
        prefs.saveAuth(res.token, res.userId, res.username, cleanUrl)
    }

    suspend fun register(serverUrl: String, username: String, password: String) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val res = api.register(cleanUrl, username, password)
        prefs.saveAuth(res.token, res.userId, res.username, cleanUrl)
    }

    suspend fun enterDemoMode() {
        demoBot.resetDefaultDemoDuels()
        prefs.enableDemoMode("Moi")
    }

    suspend fun logout() {
        wsJob?.cancel()
        prefs.logout()
    }

    suspend fun updateServerUrl(url: String) {
        prefs.setServerUrl(url)
    }

    suspend fun updateTrackedPackages(packages: Set<String>) {
        // Impossible de désélectionner les applications obligatoires d'un duel actif
        val mandatory = activeMandatoryPackages()
        prefs.setTrackedPackages(packages + mandatory)
        refreshAndSync()
    }

    fun activeMandatoryPackages(): Set<String> =
        _duels.value
            .filter { it.status == DuelStatus.ACTIVE }
            .flatMap { it.config.mandatorySocialPackages }
            .toSet()

    fun hasActiveDuel(): Boolean =
        _duels.value.any { it.status == DuelStatus.ACTIVE }

    fun listInstalledApps(): List<InstalledAppItem> = usageTracker.listLaunchableApps()

    fun refreshPermissionHealth(): PermissionHealth {
        val health = permissionChecker.check()
        _permissionHealth.value = health
        return health
    }

    suspend fun createDuel(config: DuelConfig): DuelState {
        val current = prefs.getSession()
        val duel = if (current.demoMode) {
            demoBot.createDuel(config)
        } else {
            val token = current.token ?: error("Non connecté")
            api.createDuel(current.serverUrl, token, config)
        }
        // S'assurer que les apps obligatoires sont suivies
        if (config.mandatorySocialPackages.isNotEmpty()) {
            prefs.setTrackedPackages(current.trackedSocialPackages + config.mandatorySocialPackages)
        }
        refreshAndSync()
        return duel
    }

    suspend fun joinDuel(inviteCode: String): DuelState {
        val current = prefs.getSession()
        if (current.demoMode) {
            error("Le mode Démo utilise des duels locaux. Passez en mode Serveur pour rejoindre par code.")
        }
        val token = current.token ?: error("Non connecté")
        val duel = api.joinDuel(current.serverUrl, token, inviteCode)
        if (duel.config.mandatorySocialPackages.isNotEmpty()) {
            prefs.setTrackedPackages(current.trackedSocialPackages + duel.config.mandatorySocialPackages)
        }
        refreshAndSync()
        return duel
    }

    suspend fun activateJoker(duelId: String): Boolean {
        val current = prefs.getSession()
        return if (current.demoMode) {
            val ok = demoBot.activateJoker(duelId)
            if (ok) refreshAndSync()
            ok
        } else {
            val token = current.token ?: return false
            val updated = api.activateJoker(current.serverUrl, token, duelId)
            mergeDuel(updated)
            true
        }
    }

    suspend fun forfeitDuel(duelId: String) {
        val current = prefs.getSession()
        if (current.demoMode) {
            demoBot.forfeitDuel(duelId)
            refreshAndSync()
        } else {
            val token = current.token ?: return
            val updated = api.forfeitDuel(current.serverUrl, token, duelId)
            mergeDuel(updated)
        }
    }

    suspend fun addDemoAnkiMinutes(minutes: Int) {
        demoBot.addSimulatedAnkiMinutes(minutes)
        refreshAndSync()
    }

    suspend fun addDemoSocialMinutes(minutes: Int) {
        demoBot.addSimulatedSocialMinutes(minutes)
        refreshAndSync()
    }

    suspend fun refreshAndSync() {
        val current = prefs.getSession()
        val health = refreshPermissionHealth()
        val tracked = current.trackedSocialPackages + activeMandatoryPackages()

        val rawMeasurement = if (health.usageAccessGranted) {
            usageTracker.measureToday(tracked)
        } else {
            com.ankiduel.android.tracking.DailyUsageMeasurement(0L, 0L, emptyMap())
        }

        val ankiApi = ankiTracker.queryTodayCardsReviewed()

        val rawAnki = rawMeasurement.ankiForegroundSeconds +
            if (current.demoMode) demoBot.simulatedAnkiBonusSeconds else 0L
        val rawSocial = rawMeasurement.socialForegroundSeconds +
            if (current.demoMode) demoBot.simulatedSocialBonusSeconds else 0L

        val prevIdle = prefs.loadAntiIdleState()
        val idleResult = antiIdleValidator.evaluate(
            previous = prevIdle,
            rawAnkiSeconds = rawAnki,
            cardsReviewed = ankiApi.cardsReviewedToday,
            ankiApiConnected = ankiApi.apiConnected && !current.demoMode
        )
        prefs.saveAntiIdleState(idleResult.nextState)
        _isIdleFlagged.value = idleResult.isIdleFlagged

        val snapshot = UsageSnapshot(
            ankiForegroundSeconds = rawAnki,
            ankiEffectiveSeconds = idleResult.effectiveSeconds,
            cardsReviewed = ankiApi.cardsReviewedToday,
            socialForegroundSeconds = rawSocial,
            socialBreakdownSeconds = rawMeasurement.socialBreakdownSeconds,
            permissionsHealthy = if (current.demoMode) true else health.allEnforcementPermissionsHealthy,
            trackedSocialPackages = tracked,
            ankiApiConnected = ankiApi.apiConnected,
            capturedAtEpochMs = System.currentTimeMillis()
        )
        _usageSnapshot.value = snapshot

        val updatedDuels = if (current.demoMode) {
            demoBot.syncSnapshot(snapshot)
        } else if (!current.token.isNullOrBlank()) {
            runCatching {
                val remoteDuels = api.fetchDuels(current.serverUrl, current.token)
                remoteDuels.map { duel ->
                    if (duel.status == DuelStatus.ACTIVE) {
                        runCatching {
                            api.syncUsage(current.serverUrl, current.token, duel.duelId, snapshot)
                        }.getOrDefault(duel)
                    } else duel
                }
            }.getOrElse { _duels.value }
        } else {
            emptyList()
        }

        _duels.value = updatedDuels
        updateLockStatus(updatedDuels, current.userId.orEmpty(), tracked)
    }

    private fun mergeDuel(updated: DuelState) {
        val list = _duels.value.toMutableList()
        val idx = list.indexOfFirst { it.duelId == updated.duelId }
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        _duels.value = list
        val current = _session.value ?: return
        updateLockStatus(list, current.userId.orEmpty(), current.trackedSocialPackages + activeMandatoryPackages())
    }

    private fun updateLockStatus(
        duels: List<DuelState>,
        userId: String,
        trackedPackages: Set<String>
    ) {
        val status = DuelDeltaCalculator.computeGlobalLockStatus(
            userId = userId,
            duels = duels,
            nowEpochMs = System.currentTimeMillis()
        )
        _lockStatus.value = status
        BlockStateHolder.updateLockState(status, trackedPackages)
    }

    private fun startWebSocketListener(serverUrl: String, token: String) {
        wsJob?.cancel()
        wsJob = scope.launch {
            while (true) {
                runCatching {
                    api.observeEvents(serverUrl, token).collect { event ->
                        when (event) {
                            is com.ankiduel.shared.protocol.ServerEvent.DuelUpdated -> {
                                mergeDuel(event.duel)
                            }
                            else -> {
                                val latest = api.fetchDuels(serverUrl, token)
                                _duels.value = latest
                                val current = _session.value
                                if (current != null) {
                                    updateLockStatus(latest, current.userId.orEmpty(), current.trackedSocialPackages)
                                }
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(5_000L)
            }
        }
    }
}
