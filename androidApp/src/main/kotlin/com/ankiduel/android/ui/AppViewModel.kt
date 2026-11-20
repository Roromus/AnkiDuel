package com.ankiduel.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ankiduel.android.data.AnkiDuelRepository
import com.ankiduel.android.data.StoredSession
import com.ankiduel.android.data.TrackingDiagnostics
import com.ankiduel.android.tracking.InstalledAppItem
import com.ankiduel.android.tracking.PermissionHealth
import com.ankiduel.android.update.AppUpdateManager
import com.ankiduel.android.update.UpdateState
import com.ankiduel.shared.model.DailyStatRecord
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.GlobalLockStatus
import com.ankiduel.shared.model.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class AppViewModel(
    private val repository: AnkiDuelRepository,
    private val updateManager: AppUpdateManager
) : ViewModel() {

    val session: StateFlow<StoredSession?> = repository.session
    val duels: StateFlow<List<DuelState>> = repository.duels
    val usageSnapshot: StateFlow<UsageSnapshot> = repository.usageSnapshot
    val permissionHealth: StateFlow<PermissionHealth> = repository.permissionHealth
    val lockStatus: StateFlow<GlobalLockStatus> = repository.lockStatus
    val isIdleFlagged: StateFlow<Boolean> = repository.isIdleFlagged
    val trackingDiagnostics: StateFlow<TrackingDiagnostics> = repository.trackingDiagnostics
    val updateState: StateFlow<UpdateState> = updateManager.state

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val activeActionsCount = AtomicInteger(0)

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _personalStats = MutableStateFlow<List<DailyStatRecord>>(emptyList())
    val personalStats: StateFlow<List<DailyStatRecord>> = _personalStats.asStateFlow()

    private val _opponentStats = MutableStateFlow<List<DailyStatRecord>>(emptyList())
    val opponentStats: StateFlow<List<DailyStatRecord>> = _opponentStats.asStateFlow()

    private val _selectedOpponentId = MutableStateFlow<String?>(null)
    val selectedOpponentId: StateFlow<String?> = _selectedOpponentId.asStateFlow()

    private val _statsLoading = MutableStateFlow(false)
    val statsLoading: StateFlow<Boolean> = _statsLoading.asStateFlow()

    init {
        loadInstalledApps()
        checkForUpdates()
    }

    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch {
            updateManager.checkAndUpdateIfAvailable(force = force)
        }
    }

    fun onResumeUpdateCheck() {
        updateManager.onResumeCheckPermission()
        checkForUpdates()
    }

    fun installDownloadedUpdate() {
        val ready = updateState.value as? UpdateState.ReadyToInstall ?: return
        if (ready.needsInstallPermission) {
            updateManager.openInstallPermissionSettings()
        } else {
            updateManager.launchInstaller(ready.apkFile)
        }
    }

    fun dismissUpdatePrompt() {
        updateManager.dismissPrompt()
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = withContext(Dispatchers.IO) {
                repository.listInstalledApps()
            }
        }
    }

    fun showFeedback(message: String) {
        _feedbackMessage.value = message
    }

    fun refreshAll() {
        viewModelScope.launch {
            repository.refreshAndSync()
        }
    }

    fun login(email: String, password: String) {
        launchAction {
            repository.login(email, password)
        }
    }

    fun register(email: String, username: String, password: String) {
        launchAction {
            repository.register(email, username, password)
        }
    }

    fun startDemoMode() {
        launchAction {
            repository.enterDemoMode()
            _feedbackMessage.value = "Mode Démo activé : affrontez les bots localement !"
        }
    }

    fun logout() {
        launchAction {
            repository.logout()
        }
    }

    fun createDuel(config: DuelConfig, onSuccess: (DuelState) -> Unit = {}) {
        launchAction {
            val created = repository.createDuel(config)
            _feedbackMessage.value = "Duel créé (${created.inviteCode})"
            onSuccess(created)
        }
    }

    fun joinDuel(inviteCode: String, onSuccess: (DuelState) -> Unit = {}) {
        launchAction {
            val joined = repository.joinDuel(inviteCode)
            _feedbackMessage.value = "Duel rejoint : ${joined.config.title}"
            onSuccess(joined)
        }
    }

    fun activateJoker(duelId: String) {
        launchAction {
            val ok = repository.activateJoker(duelId)
            _feedbackMessage.value = if (ok) {
                "Joker activé ! Réseaux débloqués temporairement."
            } else {
                "Impossible d'activer le joker (quota épuisé ou désactivé)."
            }
        }
    }

    fun forfeitDuel(duelId: String) {
        launchAction {
            repository.forfeitDuel(duelId)
            _feedbackMessage.value = "Vous avez déclaré forfait."
        }
    }

    fun toggleSocialPackage(packageName: String) {
        val currentSession = session.value ?: return
        if (repository.hasActiveDuel()) {
            _feedbackMessage.value = "Liste verrouillée pendant un duel actif (anti-triche)."
            return
        }
        val next = currentSession.trackedSocialPackages.toMutableSet()
        if (packageName in next) next.remove(packageName) else next.add(packageName)
        launchAction {
            repository.updateTrackedPackages(next)
        }
    }

    fun addDemoAnkiMinutes(minutes: Int) {
        launchAction { repository.addDemoAnkiMinutes(minutes) }
    }

    fun addDemoSocialMinutes(minutes: Int) {
        launchAction { repository.addDemoSocialMinutes(minutes) }
    }

    fun activeMandatoryPackages(): Set<String> = repository.activeMandatoryPackages()

    fun hasActiveDuel(): Boolean = repository.hasActiveDuel()

    fun selectStatsOpponent(opponentUserId: String?) {
        _selectedOpponentId.value = opponentUserId
        loadStats(opponentUserId)
    }

    fun loadStats(opponentUserId: String? = _selectedOpponentId.value) {
        val current = session.value ?: return
        val selfId = if (current.demoMode) "local-demo-user" else current.userId ?: return
        viewModelScope.launch {
            _statsLoading.value = true
            try {
                _personalStats.value = withContext(Dispatchers.IO) {
                    repository.fetchDailyStats(selfId, days = 30)
                }
                _opponentStats.value = if (!opponentUserId.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        repository.fetchDailyStats(opponentUserId, days = 30)
                    }
                } else {
                    emptyList()
                }
            } finally {
                _statsLoading.value = false
            }
        }
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            activeActionsCount.incrementAndGet()
            _isBusy.value = true
            try {
                runCatching { block() }
                    .onFailure { err ->
                        _feedbackMessage.value = err.message ?: "Une erreur est survenue."
                    }
            } finally {
                if (activeActionsCount.decrementAndGet() <= 0) {
                    activeActionsCount.set(0)
                    _isBusy.value = false
                }
            }
        }
    }

    class Factory(
        private val repository: AnkiDuelRepository,
        private val updateManager: AppUpdateManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(repository, updateManager) as T
        }
    }
}
