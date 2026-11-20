package com.ankiduel.android.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.ankiduel.android.AnkiDuelApplication
import com.ankiduel.shared.model.SocialAppCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service d'accessibilité détectant en temps réel l'ouverture d'une application sociale surveillée
 * et affichant immédiatement l'écran de verrouillage AnkiDuel si une dette de duel est active.
 */
class SocialBlockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastTriggerTimeMs: Long = 0L
    private var lastTriggeredPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            runCatching {
                (application as? AnkiDuelApplication)?.container?.repository?.refreshAndSync()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Ne jamais bloquer AnkiDuel, AnkiDroid ou le lanceur système
        if (pkg == packageName || SocialAppCatalog.isAnkiDroidPackage(pkg)) return

        val lockStatus = BlockStateHolder.lockStatus.value
        val now = System.currentTimeMillis()
        val isJokerActive = lockStatus.jokerActiveUntilEpochMs > now
        // Vérification en temps réel : dès que le joker expire, une dette > 0 redevient bloquante
        // même avant le prochain tick du service de monitoring.
        val effectivelyLocked = (lockStatus.isLocked || lockStatus.maxDebtSeconds > 0L) && !isJokerActive

        if (!effectivelyLocked) return
        if (pkg !in BlockStateHolder.trackedPackages.value) return

        // Anti-rebond court (500ms) pour éviter les lancements multiples sur la même transition
        if (pkg == lastTriggeredPkg && now - lastTriggerTimeMs < 500L) return
        lastTriggeredPkg = pkg
        lastTriggerTimeMs = now

        BlockStateHolder.recordBlockedLaunch(pkg)

        val intent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            putExtra(LockOverlayActivity.EXTRA_BLOCKED_PACKAGE, pkg)
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            // Repli sûr si la superposition est indisponible : renvoyer à l'écran d'accueil
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
        // Rien à nettoyer
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
