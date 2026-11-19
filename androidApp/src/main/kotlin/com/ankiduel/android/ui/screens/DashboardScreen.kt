package com.ankiduel.android.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.android.blocker.LockOverlayActivity
import com.ankiduel.android.tracking.PermissionHealth
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.CardDark
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SubtleText
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.GlobalLockStatus
import com.ankiduel.shared.model.SocialAppCatalog
import com.ankiduel.shared.model.UsageSnapshot

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
    val ankiMin = (usage.ankiEffectiveSeconds / 60L).toInt()
    val socialMin = (usage.socialForegroundSeconds / 60L).toInt()
    val netMin = ankiMin - socialMin

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bonjour, $username",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (demoMode) "Mode Démo local actif" else "Synchronisation temps réel",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleText
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                }
            }
        }

        if (!health.allEnforcementPermissionsHealthy && !demoMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332211)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPermissions() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AmberJoker)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permissions incomplètes", fontWeight = FontWeight.Bold, color = AmberJoker)
                            Text(
                                "Vos adversaires verront une alerte anti-triche tant que le suivi et le bloqueur ne sont pas activés.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Carte Statut Verrou
        item {
            val isLocked = lockStatus.isLocked
            val jokerActive = lockStatus.jokerActiveUntilEpochMs > System.currentTimeMillis()
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isLocked -> Color(0xFF31141C)
                        jokerActive -> Color(0xFF2D240F)
                        else -> Color(0xFF102A22)
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = when {
                                isLocked -> SocialCoral
                                jokerActive -> AmberJoker
                                else -> SafeEmerald
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = when {
                                isLocked -> "Réseaux sociaux VERROUILLÉS"
                                jokerActive -> "Joker actif — Déblocage temporaire"
                                else -> "Réseaux sociaux DÉBLOQUÉS"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isLocked) {
                        Text(
                            text = "Dette active : ${lockStatus.maxDebtMinutes} min d'AnkiDroid à rattraper (${lockStatus.blockingDuelTitle ?: "Duel"}, leader : ${lockStatus.leaderUsername ?: "Adversaire"}).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val ankiIntent = context.packageManager
                                        .getLaunchIntentForPackage(SocialAppCatalog.ANKIDROID_PACKAGE)
                                    if (ankiIntent != null) context.startActivity(ankiIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AnkiBlue)
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Ouvrir AnkiDroid")
                            }
                            lockStatus.blockingDuelId?.let { duelId ->
                                OutlinedButton(onClick = { onActivateJoker(duelId) }) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberJoker, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Joker", color = AmberJoker)
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Vous êtes dans les clous face à vos adversaires. Continuez vos révisions sur AnkiDroid !",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText
                        )
                    }
                }
            }
        }

        // Comparateur visuel AnkiDroid vs Réseaux Sociaux
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AnkiDroid vs Réseaux Sociaux",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Score net : ${if (netMin >= 0) "+$netMin" else "$netMin"} min",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (netMin >= 0) SafeEmerald else SocialCoral
                        )
                    }

                    // Barre de comparaison proportionnelle
                    val total = (ankiMin + socialMin).coerceAtLeast(1)
                    val ankiWeight = (ankiMin.toFloat() / total.toFloat()).coerceIn(0.05f, 0.95f)
                    val socialWeight = (1f - ankiWeight).coerceAtLeast(0.05f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(if (ankiMin == 0 && socialMin == 0) 1f else ankiWeight)
                                .fillMaxHeight()
                                .background(AnkiBlue)
                        )
                        Box(
                            modifier = Modifier
                                .weight(if (ankiMin == 0 && socialMin == 0) 1f else socialWeight)
                                .fillMaxHeight()
                                .background(SocialCoral)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("AnkiDroid", color = AnkiBlue, style = MaterialTheme.typography.labelMedium)
                            Text("$ankiMin min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                if (usage.ankiApiConnected) "${usage.cardsReviewed} cartes révisées" else "Suivi temps écran",
                                style = MaterialTheme.typography.bodySmall,
                                color = SubtleText
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Réseaux Sociaux", color = SocialCoral, style = MaterialTheme.typography.labelMedium)
                            Text("$socialMin min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "${usage.trackedSocialPackages.size} apps surveillées",
                                style = MaterialTheme.typography.bodySmall,
                                color = SubtleText
                            )
                        }
                    }

                    if (isIdleFlagged) {
                        Text(
                            text = "Anti-inactivité : aucune carte révisée récemment, le compteur Anki est plafonné.",
                            color = AmberJoker,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (usage.socialBreakdownSeconds.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Détail réseaux aujourd'hui :", style = MaterialTheme.typography.labelMedium, color = SubtleText)
                            usage.socialBreakdownSeconds.entries
                                .sortedByDescending { it.value }
                                .take(4)
                                .forEach { (pkg, sec) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(SocialAppCatalog.displayNameFor(pkg), style = MaterialTheme.typography.bodySmall)
                                        Text("${sec / 60} min", style = MaterialTheme.typography.bodySmall, color = SocialCoral)
                                    }
                                }
                        }
                    }
                }
            }
        }

        // Simulateur rapide en Mode Démo
        if (demoMode) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162235)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Simulateur Démo", fontWeight = FontWeight.Bold, color = AmberJoker)
                        Text(
                            "Testez instantanément l'impact des temps AnkiDroid et Réseaux Sociaux sur vos duels :",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onAddDemoAnki(10) },
                                colors = ButtonDefaults.buttonColors(containerColor = AnkiBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+10m Anki")
                            }
                            Button(
                                onClick = { onAddDemoSocial(10) },
                                colors = ButtonDefaults.buttonColors(containerColor = SocialCoral),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+10m Social")
                            }
                        }
                        if (lockStatus.isLocked) {
                            OutlinedButton(
                                onClick = {
                                    val i = Intent(context, LockOverlayActivity::class.java).apply {
                                        putExtra(LockOverlayActivity.EXTRA_BLOCKED_PACKAGE, "com.instagram.android")
                                    }
                                    context.startActivity(i)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Aperçu de l'écran de verrouillage Instagram")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Duels actifs (${duels.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(duels, key = { it.duelId }) { duel ->
            val me = duel.participants.firstOrNull { it.userId == userId }
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDuelClick(duel.duelId) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(duel.config.title, fontWeight = FontWeight.Bold)
                        Text(
                            "${duel.participants.size}/${duel.config.maxPlayers} joueurs • ${duel.config.referenceMode.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText
                        )
                    }
                    val debt = me?.debtMinutes ?: 0
                    Text(
                        text = if (debt > 0) "-$debt min dû" else "À jour",
                        fontWeight = FontWeight.Bold,
                        color = if (debt > 0) SocialCoral else SafeEmerald
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
