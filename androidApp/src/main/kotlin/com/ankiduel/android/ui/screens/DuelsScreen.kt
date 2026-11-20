package com.ankiduel.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GroupAdd
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.CreateDuelDialog
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.ObsidianTextField
import com.ankiduel.android.ui.components.PlayerAvatar
import com.ankiduel.android.ui.components.StatusPill
import com.ankiduel.android.ui.components.formatCompactMinutes
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorder
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SafeEmeraldSoft
import com.ankiduel.android.ui.theme.SocialCoralSoft
import com.ankiduel.android.ui.theme.SurfaceCard
import com.ankiduel.android.ui.theme.SurfaceCardElevated
import com.ankiduel.android.ui.theme.TabularFeatures
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.ReferenceMode

@Composable
fun DuelsScreen(
    userId: String,
    duels: List<DuelState>,
    onCreateDuel: (DuelConfig) -> Unit,
    onJoinDuel: (String) -> Unit,
    onSelectDuel: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }

    AmbientGlowBackdrop(accentColor = AnkiBlue, glowAlpha = 0.12f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Arène des Duels",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Défiez vos amis en 1v1 ou jusqu'à 10 joueurs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                if (duels.isNotEmpty()) {
                    StatusPill(text = "${duels.size}", color = AnkiBlueSoft)
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnkiBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Créer un duel", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceCard,
                        contentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GroupAdd,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Rejoindre", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(18.dp))

            if (duels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    ObsidianCard(
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(28.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AnkiBlue.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.EmojiEvents,
                                    contentDescription = null,
                                    tint = AnkiBlueSoft,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Text(
                                text = "Aucun duel actif",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Lancez un premier duel ou entrez un code d'invitation pour comparer vos révisions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(duels, key = { it.duelId }) { duel ->
                        val me = duel.participants.firstOrNull { it.userId == userId }
                        val leader = duel.participants
                            .filter { it.status != ParticipantStatus.FORFEITED }
                            .maxByOrNull { it.netScoreSeconds }
                            ?: duel.participants.maxByOrNull { it.netScoreSeconds }
                        val debt = me?.debtMinutes ?: 0
                        val modeLabel = if (duel.config.referenceMode == ReferenceMode.LEADER) "Leader" else "Moyenne"

                        ObsidianCard(
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(18.dp),
                            onClick = { onSelectDuel(duel.duelId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = duel.config.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AnkiBlue.copy(alpha = 0.12f))
                                        .border(1.dp, AnkiBlue.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = duel.inviteCode,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = AnkiBlueSoft
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "${duel.participants.size}/${duel.config.maxPlayers} joueurs · Mode $modeLabel · Tolérance ${duel.config.toleranceMinutes}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )

                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = HairlineBorderSubtle)
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    PlayerAvatar(
                                        name = leader?.username ?: "?",
                                        size = 26.dp,
                                        rank = 1
                                    )
                                    Column {
                                        Text(
                                            text = leader?.username ?: "En attente",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "En tête · ${formatCompactMinutes(leader?.netScoreMinutes ?: 0, showSign = true)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFeatureSettings = TabularFeatures,
                                                fontWeight = FontWeight.Normal
                                            ),
                                            color = TextMuted
                                        )
                                    }
                                }

                                val isCompleted = duel.status == DuelStatus.COMPLETED || duel.status == DuelStatus.CANCELLED
                                val isWaiting = duel.status == DuelStatus.WAITING
                                StatusPill(
                                    text = when {
                                        isCompleted -> "Terminé"
                                        isWaiting -> "En attente"
                                        debt > 0 -> "Dette $debt min"
                                        else -> "À jour"
                                    },
                                    color = when {
                                        isCompleted -> TextMuted
                                        isWaiting -> AmberJokerSoft
                                        debt > 0 -> SocialCoralSoft
                                        else -> SafeEmeraldSoft
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDuelDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { config ->
                showCreateDialog = false
                onCreateDuel(config)
            }
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            containerColor = SurfaceCardElevated,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "Rejoindre un duel",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Entrez le code partagé par le créateur du duel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    ObsidianTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase() },
                        label = "Code d'invitation",
                        placeholder = "ANKI-7F3K",
                        leadingIcon = Icons.Outlined.Key
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = joinCode.trim().length >= 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AnkiBlue),
                    onClick = {
                        showJoinDialog = false
                        onJoinDuel(joinCode.trim())
                        joinCode = ""
                    }
                ) {
                    Text("Rejoindre", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            }
        )
    }
}
