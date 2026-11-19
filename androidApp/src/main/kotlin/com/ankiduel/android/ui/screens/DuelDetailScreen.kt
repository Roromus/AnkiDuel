package com.ankiduel.android.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.CardDark
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SubtleText
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.ParticipantStatus
import com.ankiduel.shared.model.SocialAppCatalog

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
    val sortedParticipants = remember(duel) {
        duel.participants.sortedByDescending { it.netScoreSeconds }
    }
    val me = duel.participants.firstOrNull { it.userId == userId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(duel.config.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Code : ${duel.inviteCode} • ${duel.status.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleText
                )
            }
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Code AnkiDuel", duel.inviteCode))
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copier code")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (duel.config.mandatorySocialPackages.isNotEmpty()) {
            Text(
                text = "Apps obligatoires : " + duel.config.mandatorySocialPackages.joinToString {
                    SocialAppCatalog.displayNameFor(it)
                },
                style = MaterialTheme.typography.bodySmall,
                color = SubtleText
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(sortedParticipants, key = { _, p -> p.userId }) { index, p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${index + 1} ${p.username}" + if (p.userId == userId) " (Vous)" else "",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Net : ${p.netScoreMinutes} min",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (p.netScoreSeconds >= 0) SafeEmerald else SocialCoral
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("AnkiDroid : ${p.ankiMinutes}m (${p.cardsReviewed} cartes)", color = AnkiBlue, style = MaterialTheme.typography.bodySmall)
                            Text("Réseaux : ${p.socialMinutes}m", color = SocialCoral, style = MaterialTheme.typography.bodySmall)
                        }

                        when (p.status) {
                            ParticipantStatus.TAMPER_ALERT -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberJoker)
                                    Text("Alerte anti-triche : permissions révoquées (score Anki gelé)", color = AmberJoker, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            ParticipantStatus.FORFEITED -> {
                                Text("Forfait", color = SubtleText, style = MaterialTheme.typography.bodySmall)
                            }
                            ParticipantStatus.ACTIVE -> {
                                if (p.debtMinutes > 0) {
                                    Text(
                                        text = "Bloqué : ${p.debtMinutes} min d'AnkiDroid à rattraper",
                                        color = SocialCoral,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (duel.status == DuelStatus.ACTIVE && me?.status != ParticipantStatus.FORFEITED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (duel.config.jokerEnabled) {
                    Button(
                        onClick = { onActivateJoker(duel.duelId) },
                        enabled = (me?.jokersRemainingThisWeek ?: 0) > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberJoker),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Text(" Joker (${me?.jokersRemainingThisWeek ?: 0})")
                    }
                }
                OutlinedButton(
                    onClick = { showForfeitConfirm = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, tint = SocialCoral)
                    Text(" Forfait", color = SocialCoral)
                }
            }
        }
    }

    if (showForfeitConfirm) {
        AlertDialog(
            onDismissRequest = { showForfeitConfirm = false },
            title = { Text("Déclarer forfait ?") },
            text = { Text("Abandonner un duel en cours est enregistré comme une défaite par forfait.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForfeitConfirm = false
                        onForfeit(duel.duelId)
                    }
                ) { Text("Confirmer le forfait", color = SocialCoral) }
            },
            dismissButton = {
                TextButton(onClick = { showForfeitConfirm = false }) { Text("Annuler") }
            }
        )
    }
}
