package com.ankiduel.android.blocker

import com.ankiduel.shared.model.GlobalLockStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * État mémoire partagé entre le Service d'accessibilité, le Service de monitoring et l'écran de verrouillage.
 */
object BlockStateHolder {
    private val _lockStatus = MutableStateFlow(
        GlobalLockStatus(
            isLocked = false,
            maxDebtSeconds = 0L,
            maxDebtMinutes = 0,
            blockingDuelId = null,
            blockingDuelTitle = null,
            leaderUsername = null,
            jokerActiveUntilEpochMs = 0L
        )
    )
    val lockStatus: StateFlow<GlobalLockStatus> = _lockStatus.asStateFlow()

    private val _trackedPackages = MutableStateFlow<Set<String>>(emptySet())
    val trackedPackages: StateFlow<Set<String>> = _trackedPackages.asStateFlow()

    private val _lastBlockedPackage = MutableStateFlow<String?>(null)
    val lastBlockedPackage: StateFlow<String?> = _lastBlockedPackage.asStateFlow()

    fun updateLockState(status: GlobalLockStatus, trackedSocialPackages: Set<String>) {
        _lockStatus.value = status
        _trackedPackages.value = trackedSocialPackages
    }

    fun recordBlockedLaunch(packageName: String) {
        _lastBlockedPackage.value = packageName
    }
}
