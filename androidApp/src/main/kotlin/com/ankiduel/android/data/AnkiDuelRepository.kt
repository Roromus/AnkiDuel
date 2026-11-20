package com.ankiduel.android.data

import com.ankiduel.android.blocker.BlockStateHolder
import com.ankiduel.android.tracking.AnkiActivitySnapshot
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

data class TrackingDiagnostics(
    val rawAnkiSeconds: Long = 0L,
    val effectiveAnkiSeconds: Long = 0L,
    val ankiEventCount: Int = 0,
    val usedDailyStatsFallback: Boolean = false,
    val ankiApiSnapshot: AnkiActivitySnapshot = AnkiActivitySnapshot(
        apiConnected = false,
        cardsReviewedToday = 0
    ),
    val capturedAtEpochMs: Long = 0L
)

class AnkiDuelRepository(
    private val prefs: PreferencesStore,
    private val firebase: FirebaseBackendClient,
    private val usageTracker: UsageStatsTracker,
    private val ankiTracker: AnkiDroidTracker,
    private val permissionChecker: PermissionHealthChecker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
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

    private val _trackingDiagnostics = MutableStateFlow(TrackingDiagnostics())
    val trackingDiagnostics: StateFlow<TrackingDiagnostics> = _trackingDiagnostics.asStateFlow()

    private val _lockStatus = MutableStateFlow(
        GlobalLockStatus(false, 0L, 0, null, null, null, 0L)
    )
    val lockStatus: StateFlow<GlobalLockStatus> = _lockStatus.asStateFlow()

    private var realtimeJob: Job? = null
    private var listeningUserId: String? = null

    init {
        scope.launch {
            runCatching { prefs.loadCachedLockState() }.getOrNull()?.let { (cachedStatus, cachedTracked) ->
                _lockStatus.value = cachedStatus
                BlockStateHolder.updateLockState(cachedStatus, cachedTracked)
            }

            prefs.sessionFlow.collect { stored ->
                if (!stored.demoMode && !stored.userId.isNullOrBlank()) {
                    val authUid = runCatching { firebase.currentUserId() }.getOrNull()
                    if (authUid == null || authUid != stored.userId) {
                        realtimeJob?.cancel()
                        listeningUserId = null
                        _duels.value = emptyList()
                        prefs.clearCachedLockState()
                        prefs.resetAntiIdleState()
                        prefs.logout()
                        return@collect
                    }
                }

                _session.value = stored
                if (stored.demoMode) {
                    realtimeJob?.cancel()
                    listeningUserId = null
                    refreshAndSync()
                } else if (!stored.userId.isNullOrBlank()) {
                    if (listeningUserId != stored.userId) {
                        startRealtimeListener(stored.userId)
                    }
                    refreshAndSync()
                } else {
                    realtimeJob?.cancel()
                    listeningUserId = null
                    _duels.value = emptyList()
                    updateLockStatus(emptyList(), "", stored.trackedSocialPackages)
                }
            }
        }
    }

    suspend fun login(email: String, password: String) {
        val res = firebase.login(email, password)
        prefs.resetAntiIdleState()
        prefs.saveAuth(res.userId, res.username, res.email)
    }

    suspend fun register(email: String, username: String, password: String) {
        val res = firebase.register(email, username, password)
        prefs.resetAntiIdleState()
        prefs.saveAuth(res.userId, res.username, res.email)
    }

    suspend fun enterDemoMode() {
        demoBot.resetDefaultDemoDuels()
        prefs.resetAntiIdleState()
        prefs.enableDemoMode("Moi")
    }

    suspend fun logout() {
        realtimeJob?.cancel()
        listeningUserId = null
        firebase.logout()
        prefs.resetAntiIdleState()
        prefs.clearCachedLockState()
        prefs.logout()
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
            val userId = current.userId ?: error("Non connecté")
            val username = current.username ?: "Joueur"
            firebase.createDuel(userId, username, config)
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
            error("Le mode Démo utilise des duels locaux. Connectez-vous avec Firebase pour rejoindre par code.")
        }
        val userId = current.userId ?: error("Non connecté")
        val username = current.username ?: "Joueur"
        val duel = firebase.joinDuel(userId, username, inviteCode)
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
            val userId = current.userId ?: return false
            val updated = firebase.activateJoker(duelId, userId)
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
            val userId = current.userId ?: return
            val updated = firebase.forfeitDuel(duelId, userId)
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

    suspend fun refreshAndSync() = syncMutex.withLock {
        val current = prefs.getSession()
        val health = refreshPermissionHealth()
        val tracked = current.trackedSocialPackages + activeMandatoryPackages()
        val nowMs = System.currentTimeMillis()
        val todayKey = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

        val rawMeasurement = if (health.usageAccessGranted) {
            usageTracker.measureToday(tracked, nowMs = nowMs)
        } else {
            com.ankiduel.android.tracking.DailyUsageMeasurement(0L, 0L, emptyMap())
        }

        val ankiApi = ankiTracker.queryTodayCardsReviewed()

        val rawAnki = rawMeasurement.ankiForegroundSeconds +
            if (current.demoMode) demoBot.simulatedAnkiBonusSeconds else 0L
        val rawSocial = rawMeasurement.socialForegroundSeconds +
            if (current.demoMode) demoBot.simulatedSocialBonusSeconds else 0L

        val prevIdle = prefs.loadAntiIdleState(todayKey)
        val idleResult = antiIdleValidator.evaluate(
            previous = prevIdle,
            rawAnkiSeconds = rawAnki,
            cardsReviewed = ankiApi.cardsReviewedToday,
            ankiApiConnected = ankiApi.apiConnected && !current.demoMode
        )
        prefs.saveAntiIdleState(idleResult.nextState, todayKey)
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
            capturedAtEpochMs = nowMs
        )
        _usageSnapshot.value = snapshot
        _trackingDiagnostics.value = TrackingDiagnostics(
            rawAnkiSeconds = rawAnki,
            effectiveAnkiSeconds = idleResult.effectiveSeconds,
            ankiEventCount = rawMeasurement.ankiEventCount,
            usedDailyStatsFallback = rawMeasurement.usedDailyStatsFallback,
            ankiApiSnapshot = ankiApi,
            capturedAtEpochMs = nowMs
        )

        val updatedDuels = if (current.demoMode) {
            demoBot.syncSnapshot(snapshot)
        } else if (!current.userId.isNullOrBlank()) {
            runCatching {
                val remoteDuels = firebase.fetchDuels(current.userId)
                remoteDuels.map { duel ->
                    if (duel.status == DuelStatus.ACTIVE) {
                        runCatching {
                            firebase.syncUsage(duel.duelId, current.userId, snapshot)
                        }.getOrDefault(duel)
                    } else duel
                }
            }.getOrElse { _duels.value }
        } else {
            emptyList()
        }

        _duels.value = updatedDuels
        val finalTracked = current.trackedSocialPackages + updatedDuels
            .filter { it.status == DuelStatus.ACTIVE }
            .flatMap { it.config.mandatorySocialPackages }
            .toSet()
        updateLockStatus(updatedDuels, current.userId.orEmpty(), finalTracked)
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
        scope.launch {
            runCatching { prefs.saveCachedLockState(status, trackedPackages) }
        }
    }

    private fun startRealtimeListener(userId: String) {
        realtimeJob?.cancel()
        listeningUserId = userId
        realtimeJob = scope.launch {
            firebase.observeDuels(userId).collect { latest ->
                _duels.value = latest
                val baseTracked = _session.value?.trackedSocialPackages ?: emptySet()
                val mergedTracked = baseTracked + activeMandatoryPackages()
                updateLockStatus(latest, userId, mergedTracked)
            }
        }
    }
}
