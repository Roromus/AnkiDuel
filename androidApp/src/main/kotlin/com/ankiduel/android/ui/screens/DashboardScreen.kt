package com.ankiduel.android.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ankiduel.android.blocker.LockOverlayActivity
import com.ankiduel.android.tracking.PermissionHealth
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.DuelBalanceBar
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.ObsidianIconButton
import com.ankiduel.android.ui.components.SectionHeader
import com.ankiduel.android.ui.components.StatusPill
import com.ankiduel.android.ui.components.formatCompactMinutes
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SafeEmeraldSoft
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SocialCoralSoft
import com.ankiduel.android.ui.theme.SurfaceBase
import com.ankiduel.android.ui.theme.TabularFeatures
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.GlobalLockStatus
import com.ankiduel.shared.model.ReferenceMode
import com.ankiduel.shared.model.SocialAppCatalog
import com.ankiduel.shared.model.UsageSnapshot
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    username: String,
    userId: String,
    demoMode: Boolean,
    usage: UsageSnapshot,
    lockStatus: GlobalLockStatus,
    duels: List<DuelState>,
    health: PermissionHealth,
    isIdleFlagged: Boolean,
    onRefresh: () -> Unit,
    onOpenPermissions: () -> Unit,
    onDuelClick: (String) -> Unit,
    onActivateJoker: (String) -> Unit,
    onAddDemoAnki: (Int) -> Unit,
    onAddDemoSocial: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshRotation = remember { Animatable(0f) }

    val ankiMin = (usage.ankiEffectiveSeconds / 60L).toInt()
    val socialMin = (usage.socialForegroundSeconds / 60L).toInt()
    val netMin = ankiMin - socialMin

    val isLocked = lockStatus.isLocked
    val nowMs = System.currentTimeMillis()
    val jokerActive = lockStatus.jokerActiveUntilEpochMs > nowMs
    val jokerRemainingMin = if (jokerActive) {
        (((lockStatus.jokerActiveUntilEpochMs - nowMs) + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
    } else 0

    val statusAccent = when {
        isLocked -> SocialCoral
        jokerActive -> AmberJoker
        else -> SafeEmerald
    }

    AmbientGlowBackdrop(accentColor = statusAccent, glowAlpha = 0.14f) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Minimal Editorial Top Bar ─────────────────────────────────────
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Bonjour, $username",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (demoMode) AmberJokerSoft else SafeEmerald)
                            )
                            Text(
                                text = if (demoMode) "Mode Démo local" else "Suivi en temps réel",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    ObsidianIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Actualiser",
                        modifier = Modifier.rotate(refreshRotation.value),
                        onClick = {
                            onRefresh()
                            scope.launch {
                                refreshRotation.snapTo(0f)
                                refreshRotation.animateTo(
                                    targetValue = 360f,
                                    animationSpec = tween(520, easing = FastOutSlowInEasing)
                                )
                            }
                        }
                    )
                }
            }

            // ── Discreet Permission Nudge (only if needed) ────────────────────
            if (!health.allEnforcementPermissionsHealthy && !demoMode) {
                item(key = "permissions_nudge") {
                    ObsidianCard(
                        shape = RoundedCornerShape(18.dp),
                        borderTint = AmberJoker,
                        borderAlpha = 0.28f,
                        accentGradientColor = AmberJoker,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        onClick = onOpenPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AmberJoker.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = AmberJokerSoft,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Autorisations incomplètes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AmberJokerSoft
                                )
                                Text(
                                    text = "Activez le suivi et le bloqueur pour éviter l'alerte anti-triche.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ── Hero Lock Status Card ─────────────────────────────────────────
            item(key = "lock_hero") {
                ObsidianCard(
                    shape = RoundedCornerShape(24.dp),
                    borderTint = statusAccent,
                    borderAlpha = 0.26f,
                    accentGradientColor = statusAccent,
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = when {
                                isLocked -> "Réseaux verrouillés"
                                jokerActive -> "Joker · $jokerRemainingMin min"
                                else -> "Réseaux débloqués"
                            },
                            color = when {
                                isLocked -> SocialCoralSoft
                                jokerActive -> AmberJokerSoft
                                else -> SafeEmeraldSoft
                            },
                            showDot = true
                        )

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusAccent.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when {
                                    isLocked -> Icons.Rounded.Lock
                                    jokerActive -> Icons.Rounded.Bolt
                                    else -> Icons.Rounded.LockOpen
                                },
                                contentDescription = null,
                                tint = statusAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    when {
                        isLocked -> {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatCompactMinutes(lockStatus.maxDebtMinutes),
                                    style = MaterialTheme.typography.displaySmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "à rattraper",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            Spacer(Modifier.height(4.dp))

                            Text(
                                text = "${lockStatus.blockingDuelTitle ?: "Duel en cours"} · ${lockStatus.leaderUsername ?: "Adversaire"} est en tête",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val ankiIntent = context.packageManager
                                            .getLaunchIntentForPackage(SocialAppCatalog.ANKIDROID_PACKAGE)
                                        if (ankiIntent != null) context.startActivity(ankiIntent)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AnkiBlue,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.School,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Ouvrir AnkiDroid",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }

                                lockStatus.blockingDuelId?.let { duelId ->
                                    OutlinedButton(
                                        onClick = { onActivateJoker(duelId) },
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, AmberJoker.copy(alpha = 0.45f)),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bolt,
                                            contentDescription = null,
                                            tint = AmberJokerSoft,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Joker",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = AmberJokerSoft
                                        )
                                    }
                                }
                            }
                        }

                        jokerActive -> {
                            Text(
                                text = "Pause temporaire active",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Vos applications sociales sont accessibles encore ~$jokerRemainingMin min avant le retour du verrou.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        else -> {
                            Text(
                                text = "Vous êtes dans les clous",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Votre rythme AnkiDroid couvre votre temps social. Continuez ainsi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // ── Comparator Card: AnkiDroid vs Réseaux Sociaux ─────────────────
            item(key = "comparator") {
                ObsidianCard(
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Équilibre du jour",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        StatusPill(
                            text = formatCompactMinutes(netMin, showSign = true),
                            color = if (netMin >= 0) SafeEmeraldSoft else SocialCoralSoft
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(AnkiBlue)
                                )
                                Text(
                                    text = "AnkiDroid",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AnkiBlueSoft
                                )
                            }
                            Text(
                                text = formatCompactMinutes(ankiMin),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFeatureSettings = TabularFeatures
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = if (usage.ankiApiConnected) {
                                    "${usage.cardsReviewed} cartes révisées"
                                } else {
                                    "Suivi temps écran"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Réseaux",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SocialCoralSoft
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(SocialCoral)
                                )
                            }
                            Text(
                                text = formatCompactMinutes(socialMin),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFeatureSettings = TabularFeatures
                                ),
                                color = TextPrimary
                            )
                            Text(
                                text = "${usage.trackedSocialPackages.size} apps suivies",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    DuelBalanceBar(
                        ankiMinutes = ankiMin,
                        socialMinutes = socialMin,
                        height = 11.dp
                    )

                    if (isIdleFlagged) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmberJoker.copy(alpha = 0.10f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = AmberJokerSoft,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Anti-inactivité : aucune carte révisée récemment, compteur plafonné.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberJokerSoft
                            )
                        }
                    }

                    if (usage.socialBreakdownSeconds.isNotEmpty()) {
                        val topEntries = remember(usage.socialBreakdownSeconds) {
                            usage.socialBreakdownSeconds.entries
                                .filter { it.value > 0L }
                                .sortedByDescending { it.value }
                                .take(4)
                        }
                        if (topEntries.isNotEmpty()) {
                            val maxSec = topEntries.first().value.coerceAtLeast(1L).toFloat()

                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(color = HairlineBorderSubtle)
                            Spacer(Modifier.height(14.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "PRINCIPALES DISTRACTIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                topEntries.forEach { (pkg, sec) ->
                                    val mins = (sec / 60L).toInt()
                                    val fraction = (sec.toFloat() / maxSec).coerceIn(0.08f, 1f)
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = SocialAppCatalog.displayNameFor(pkg),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = formatCompactMinutes(mins),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFeatureSettings = TabularFeatures
                                                ),
                                                color = SocialCoralSoft
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(SurfaceBase)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction)
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(SocialCoral.copy(alpha = 0.72f))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Active Duels Section ──────────────────────────────────────────
            item(key = "duels_header") {
                Spacer(Modifier.height(2.dp))
                SectionHeader(
                    title = "Duels actifs",
                    trailing = {
                        StatusPill(
                            text = "${duels.size}",
                            color = AnkiBlueSoft
                        )
                    }
                )
            }

            if (duels.isEmpty()) {
                item(key = "duels_empty") {
                    ObsidianCard(
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AnkiBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = AnkiBlueSoft,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Aucun duel en cours",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Créez ou rejoignez un duel dans l'onglet Duels.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            } else {
                items(duels, key = { it.duelId }) { duel ->
                    val me = duel.participants.firstOrNull { it.userId == userId }
                    val debt = me?.debtMinutes ?: 0
                    val modeLabel = if (duel.config.referenceMode == ReferenceMode.LEADER) "Leader" else "Moyenne"

                    ObsidianCard(
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        onClick = { onDuelClick(duel.duelId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = duel.config.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${duel.participants.size}/${duel.config.maxPlayers} joueurs · Mode $modeLabel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isCompleted = duel.status == DuelStatus.COMPLETED || duel.status == DuelStatus.CANCELLED
                                StatusPill(
                                    text = when {
                                        isCompleted -> "Terminé"
                                        debt > 0 -> "-$debt min"
                                        else -> "À jour"
                                    },
                                    color = when {
                                        isCompleted -> TextMuted
                                        debt > 0 -> SocialCoralSoft
                                        else -> SafeEmeraldSoft
                                    }
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Tasteful Low-Key Demo Lab ─────────────────────────────────────
            if (demoMode) {
                item(key = "demo_lab") {
                    Spacer(Modifier.height(2.dp))
                    ObsidianCard(
                        shape = RoundedCornerShape(20.dp),
                        containerColor = SurfaceBase,
                        borderTint = AmberJoker,
                        borderAlpha = 0.22f,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SIMULATEUR DÉMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberJokerSoft
                            )
                            Text(
                                text = "Test instantané",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onAddDemoAnki(10) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AnkiBlue.copy(alpha = 0.16f),
                                    contentColor = AnkiBlueSoft
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+10m Anki", style = MaterialTheme.typography.labelLarge)
                            }
                            Button(
                                onClick = { onAddDemoSocial(10) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SocialCoral.copy(alpha = 0.16f),
                                    contentColor = SocialCoralSoft
                                ),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+10m Social", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        if (lockStatus.isLocked) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val i = Intent(context, LockOverlayActivity::class.java).apply {
                                        putExtra(LockOverlayActivity.EXTRA_BLOCKED_PACKAGE, "com.instagram.android")
                                    }
                                    context.startActivity(i)
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, HairlineBorderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Aperçu de l'écran de verrouillage",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
