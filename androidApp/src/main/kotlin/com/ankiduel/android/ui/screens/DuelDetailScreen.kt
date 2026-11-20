package com.ankiduel.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.DuelBalanceBar
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.ObsidianIconButton
import com.ankiduel.android.ui.components.PlayerAvatar
import com.ankiduel.android.ui.components.StatusPill
import com.ankiduel.android.ui.components.formatCompactMinutes
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorder
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SafeEmeraldSoft
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SocialCoralSoft
import com.ankiduel.android.ui.theme.SurfaceBase
import com.ankiduel.android.ui.theme.SurfaceCard
import com.ankiduel.android.ui.theme.SurfaceCardElevated
import com.ankiduel.android.ui.theme.TabularFeatures
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.ReferenceMode
import com.ankiduel.shared.model.SocialAppCatalog
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuelDetailScreen(
    duel: DuelState,
    userId: String,
    onBack: () -> Unit,
    onActivateJoker: (String) -> Unit,
    onForfeit: (String) -> Unit
) {
    val context = LocalContext.current
    var showForfeitConfirm by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            delay(1600L)
            copiedCode = false
        }
    }

    val sortedParticipants = remember(duel) {
        duel.participants.sortedWith(
            compareBy<com.ankiduel.shared.model.ParticipantState> { it.status == ParticipantStatus.FORFEITED }
                .thenByDescending { it.netScoreSeconds }
        )
    }
    val me = duel.participants.firstOrNull { it.userId == userId }
    val myDebt = me?.debtMinutes ?: 0
    val nowMs = System.currentTimeMillis()
    val myJokerActive = (me?.jokerActiveUntilEpochMs ?: 0L) > nowMs

    val statusLabel = when (duel.status) {
        DuelStatus.ACTIVE -> "Actif"
        DuelStatus.WAITING -> "En attente"
        DuelStatus.COMPLETED -> "Terminé"
        DuelStatus.CANCELLED -> "Annulé"
    }
    val statusColor = when (duel.status) {
        DuelStatus.ACTIVE -> SafeEmeraldSoft
        DuelStatus.WAITING -> AmberJokerSoft
        DuelStatus.COMPLETED -> TextSecondary
        DuelStatus.CANCELLED -> SocialCoralSoft
    }

    AmbientGlowBackdrop(
        accentColor = if (myDebt > 0) SocialCoral else AnkiBlue,
        glowAlpha = 0.13f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ── Top Bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ObsidianIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                    onClick = onBack
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = duel.config.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusPill(text = statusLabel, color = statusColor, showDot = true)
                    }
                }

                // Tactile copy invite code pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (copiedCode) SafeEmerald.copy(alpha = 0.14f) else SurfaceCard)
                        .border(
                            1.dp,
                            if (copiedCode) SafeEmerald.copy(alpha = 0.35f) else HairlineBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Code AnkiDuel", duel.inviteCode))
                            copiedCode = true
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedContent(targetState = copiedCode, label = "copyCodeState") { isCopied ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = "Copier le code",
                                tint = if (isCopied) SafeEmeraldSoft else AnkiBlueSoft,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCopied) "Copié" else duel.inviteCode,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = if (isCopied) FontFamily.SansSerif else FontFamily.Monospace
                                ),
                                color = if (isCopied) SafeEmeraldSoft else TextPrimary
                            )
                        }
                    }
                }
            }

            // ── Compact Rules Strip ───────────────────────────────────────────
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleChip(
                    if (duel.config.referenceMode == ReferenceMode.LEADER) "Mode Leader" else "Mode Moyenne"
                )
                RuleChip("Tolérance ${duel.config.toleranceMinutes}m")
                RuleChip(
                    when (duel.config.cycle) {
                        DuelCycle.DAILY_MIDNIGHT -> "Reset 00h"
                        DuelCycle.CONTINUOUS -> "Continu"
                        DuelCycle.CUSTOM_DAYS -> "${duel.config.durationDays ?: 7} jours"
                    }
                )
                if (duel.config.mandatorySocialPackages.isNotEmpty()) {
                    val names = duel.config.mandatorySocialPackages.joinToString(", ") {
                        SocialAppCatalog.displayNameFor(it)
                    }
                    RuleChip("Obligatoire : $names")
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Leaderboard List ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(sortedParticipants, key = { _, p -> p.userId }) { index, p ->
                    val rank = index + 1
                    val isMe = p.userId == userId
                    val rankColor = when (rank) {
                        1 -> AmberJokerSoft
                        2 -> Color(0xFFCBD5E1)
                        3 -> Color(0xFFFB923C)
                        else -> TextMuted
                    }

                    ObsidianCard(
                        shape = RoundedCornerShape(20.dp),
                        borderTint = if (isMe) AnkiBlue else Color.White,
                        borderAlpha = if (isMe) 0.34f else 0.08f,
                        accentGradientColor = if (isMe) AnkiBlue else null,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "#$rank",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFeatureSettings = TabularFeatures
                                ),
                                color = rankColor,
                                modifier = Modifier.width(26.dp)
                            )

                            PlayerAvatar(
                                name = p.username,
                                size = 38.dp,
                                rank = rank
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = p.username,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isMe) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AnkiBlue.copy(alpha = 0.16f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Vous",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AnkiBlueSoft
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${p.cardsReviewed} cartes révisées",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFeatureSettings = TabularFeatures
                                    ),
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(4.dp))
                                val isHealthOk = p.appHealthOk && p.status != ParticipantStatus.TAMPER_ALERT
                                StatusPill(
                                    text = if (isHealthOk) {
                                        "App OK"
                                    } else {
                                        p.appHealthIssue ?: "Problème de config"
                                    },
                                    color = if (isHealthOk) SafeEmeraldSoft else SocialCoralSoft
                                )
                            }

                            Text(
                                text = formatCompactMinutes(p.netScoreMinutes, showSign = true),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = TabularFeatures
                                ),
                                color = if (p.netScoreSeconds >= 0) SafeEmeraldSoft else SocialCoralSoft
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        DuelBalanceBar(
                            ankiMinutes = p.ankiMinutes,
                            socialMinutes = p.socialMinutes,
                            height = 6.dp
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Anki · ${formatCompactMinutes(p.ankiMinutes)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = TabularFeatures
                                ),
                                color = AnkiBlueSoft
                            )
                            Text(
                                text = "Réseaux · ${formatCompactMinutes(p.socialMinutes)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFeatureSettings = TabularFeatures
                                ),
                                color = SocialCoralSoft
                            )
                        }

                        when (p.status) {
                            ParticipantStatus.TAMPER_ALERT -> {
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AmberJoker.copy(alpha = 0.10f))
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = AmberJokerSoft,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Permissions révoquées · score Anki gelé",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AmberJokerSoft
                                    )
                                }
                            }

                            ParticipantStatus.FORFEITED -> {
                                Spacer(Modifier.height(10.dp))
                                StatusPill(text = "Forfait", color = TextMuted)
                            }

                            ParticipantStatus.ACTIVE -> {
                                if (p.debtMinutes > 0) {
                                    Spacer(Modifier.height(10.dp))
                                    StatusPill(
                                        text = "Bloqué · ${p.debtMinutes} min à rattraper",
                                        color = SocialCoralSoft
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Bottom Control Dock ───────────────────────────────────────────
            val canActOnDuel = (duel.status == DuelStatus.ACTIVE || duel.status == DuelStatus.WAITING) &&
                me != null && me.status != ParticipantStatus.FORFEITED
            if (canActOnDuel) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceBase.copy(alpha = 0.96f))
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = HairlineBorderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (duel.status == DuelStatus.ACTIVE && duel.config.jokerEnabled) {
                            val remaining = me?.jokersRemainingThisWeek ?: 0
                            Button(
                                onClick = { onActivateJoker(duel.duelId) },
                                enabled = remaining > 0 && !myJokerActive,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AmberJoker,
                                    contentColor = Color(0xFF1E1302),
                                    disabledContainerColor = AmberJoker.copy(alpha = 0.16f),
                                    disabledContentColor = TextMuted
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (myJokerActive) "Joker actif" else "Joker ($remaining)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showForfeitConfirm = true },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SocialCoral.copy(alpha = 0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SocialCoral.copy(alpha = 0.08f),
                                contentColor = SocialCoralSoft
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (duel.status == DuelStatus.WAITING) "Annuler le duel" else "Forfait",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showForfeitConfirm) {
        AlertDialog(
            onDismissRequest = { showForfeitConfirm = false },
            containerColor = SurfaceCardElevated,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Déclarer forfait ?",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Abandonner ce duel en cours sera enregistré comme une défaite par forfait.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SocialCoral),
                    onClick = {
                        showForfeitConfirm = false
                        onForfeit(duel.duelId)
                    }
                ) {
                    Text("Confirmer le forfait", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForfeitConfirm = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun RuleChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}
