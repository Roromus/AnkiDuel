package com.ankiduel.android.blocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankiduel.android.AnkiDuelApplication
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.StatusPill
import com.ankiduel.android.ui.components.formatCompactMinutes
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiDuelTheme
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SocialCoralSoft
import com.ankiduel.android.ui.theme.TabularFeatures
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.SocialAppCatalog
import kotlinx.coroutines.launch

class LockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    private var blockedPackageState by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        // Le bouton Retour renvoie toujours à l'écran d'accueil pour empêcher de contourner le blocage
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goHome()
                }
            }
        )

        blockedPackageState = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
            ?: BlockStateHolder.lastBlockedPackage.value.orEmpty()

        val appContainer = (application as AnkiDuelApplication).container

        setContent {
            AnkiDuelTheme {
                val lockStatus by BlockStateHolder.lockStatus.collectAsState()
                val duels by appContainer.repository.duels.collectAsState()
                val session by appContainer.repository.session.collectAsState()
                val scope = rememberCoroutineScope()
                var jokerBusy by remember { mutableStateOf(false) }
                var jokerError by remember { mutableStateOf<String?>(null) }

                val now = System.currentTimeMillis()
                val effectivelyLocked = (lockStatus.isLocked || lockStatus.maxDebtSeconds > 0L) &&
                    lockStatus.jokerActiveUntilEpochMs <= now

                LaunchedEffect(effectivelyLocked) {
                    if (!effectivelyLocked) {
                        finish()
                    }
                }

                val blockingDuel = duels.firstOrNull { it.duelId == lockStatus.blockingDuelId }
                val currentUserId = session?.userId.orEmpty()
                val me = blockingDuel?.participants?.firstOrNull { it.userId == currentUserId }
                val canUseJoker = lockStatus.blockingDuelId != null && (
                    blockingDuel == null || (
                        blockingDuel.config.jokerEnabled &&
                            me?.status != ParticipantStatus.FORFEITED &&
                            (me?.jokersRemainingThisWeek ?: 0) > 0
                        )
                    )
                val jokerDurationMinutes = blockingDuel?.config?.jokerDurationMinutes ?: 5

                LockScreenContent(
                    blockedAppName = SocialAppCatalog.displayNameFor(blockedPackageState.ifBlank { "Application" }),
                    debtMinutes = lockStatus.maxDebtMinutes,
                    duelTitle = lockStatus.blockingDuelTitle ?: "Duel AnkiDuel",
                    leaderName = lockStatus.leaderUsername ?: "Votre adversaire",
                    canUseJoker = canUseJoker,
                    jokerDurationMinutes = jokerDurationMinutes,
                    jokerBusy = jokerBusy,
                    jokerError = jokerError,
                    onOpenAnkiDroid = { openAnkiDroid() },
                    onActivateJoker = {
                        val duelId = lockStatus.blockingDuelId ?: return@LockScreenContent
                        if (jokerBusy) return@LockScreenContent
                        jokerBusy = true
                        jokerError = null
                        scope.launch {
                            val result = runCatching { appContainer.repository.activateJoker(duelId) }
                            jokerBusy = false
                            result.onSuccess { activated ->
                                if (activated) {
                                    finish()
                                } else {
                                    jokerError = "Aucun joker disponible cette semaine."
                                }
                            }.onFailure { err ->
                                jokerError = err.message ?: "Impossible d'activer le joker."
                            }
                        }
                    },
                    onGoHome = { goHome() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
            ?: BlockStateHolder.lastBlockedPackage.value.orEmpty()
        if (pkg.isNotBlank()) {
            blockedPackageState = pkg
        }
    }

    private fun openAnkiDroid() {
        val ankiIntent = SocialAppCatalog.ankiDroidPackages
            .firstNotNullOfOrNull { pkg -> packageManager.getLaunchIntentForPackage(pkg) }
        if (ankiIntent != null) {
            ankiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(ankiIntent)
            finish()
        } else {
            goHome()
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
internal fun LockScreenContent(
    blockedAppName: String,
    debtMinutes: Int,
    duelTitle: String,
    leaderName: String,
    canUseJoker: Boolean,
    jokerDurationMinutes: Int = 5,
    jokerBusy: Boolean = false,
    jokerError: String? = null,
    onOpenAnkiDroid: () -> Unit,
    onActivateJoker: () -> Unit,
    onGoHome: () -> Unit
) {
    val pulseTransition = rememberInfiniteTransition(label = "lockPulse")
    val outerScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockScale"
    )
    val outerAlpha by pulseTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockAlpha"
    )

    AmbientGlowBackdrop(accentColor = SocialCoral, glowAlpha = 0.22f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            // Concentric breathing lock emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(116.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(outerScale)
                        .clip(CircleShape)
                        .background(SocialCoral.copy(alpha = outerAlpha * 0.45f))
                        .border(1.dp, SocialCoral.copy(alpha = outerAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SocialCoral.copy(alpha = 0.14f))
                        .border(1.dp, SocialCoral.copy(alpha = 0.34f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = SocialCoralSoft,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            StatusPill(
                text = "$blockedAppName verrouillé",
                color = SocialCoralSoft,
                showDot = true
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Pause révision requise",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // Sculptural Debt Card
            ObsidianCard(
                shape = RoundedCornerShape(26.dp),
                borderTint = SocialCoral,
                borderAlpha = 0.28f,
                accentGradientColor = SocialCoral,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DETTE ANKIDROID ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = SocialCoralSoft
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = formatCompactMinutes(debtMinutes),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 44.sp,
                            lineHeight = 50.sp,
                            fontFeatureSettings = TabularFeatures
                        ),
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "à rattraper pour débloquer",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = HairlineBorderSubtle)
                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "$duelTitle · $leaderName est en tête",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Action Stack
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenAnkiDroid,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnkiBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Ouvrir AnkiDroid",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (canUseJoker) {
                    OutlinedButton(
                        onClick = onActivateJoker,
                        enabled = !jokerBusy,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, AmberJoker.copy(alpha = 0.42f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AmberJoker.copy(alpha = 0.08f),
                            contentColor = AmberJokerSoft
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (jokerBusy) "Activation du joker…" else "Joker d'urgence ($jokerDurationMinutes min)",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (!jokerError.isNullOrBlank()) {
                    Text(
                        text = jokerError,
                        style = MaterialTheme.typography.bodySmall,
                        color = SocialCoralSoft,
                        textAlign = TextAlign.Center
                    )
                }

                TextButton(
                    onClick = onGoHome,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "Retourner à l'accueil",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
