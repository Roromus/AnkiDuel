package com.ankiduel.android.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.ankiduel.shared.model.SocialAppCatalog

/**
 * Service d'accessibilité détectant en temps réel l'ouverture d'une application sociale surveillée
 * et affichant immédiatement l'écran de verrouillage AnkiDuel si une dette de duel est active.
 */
class SocialBlockAccessibilityService : AccessibilityService() {

    private var lastTriggerTimeMs: Long = 0L
    private var lastTriggeredPkg: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Ne jamais bloquer AnkiDuel, AnkiDroid ou le lanceur système
        if (pkg == packageName || pkg == SocialAppCatalog.ANKIDROID_PACKAGE) return

        val lockStatus = BlockStateHolder.lockStatus.value
        val now = System.currentTimeMillis()
        val isJokerActive = lockStatus.jokerActiveUntilEpochMs > now

        if (!lockStatus.isLocked || isJokerActive) return
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
        startActivity(intent)
    }

    override fun onInterrupt() {
        // Rien à nettoyer
    }
}
