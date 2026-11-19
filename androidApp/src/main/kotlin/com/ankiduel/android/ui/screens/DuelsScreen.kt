package com.ankiduel.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.android.ui.components.CreateDuelDialog
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.CardDark
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SubtleText
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelState
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Arène des Duels",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Défiez vos amis en 1v1 ou en groupe jusqu'à 10 joueurs.",
            style = MaterialTheme.typography.bodySmall,
            color = SubtleText
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = AnkiBlue),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Créer un duel")
            }
            OutlinedButton(
                onClick = { showJoinDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Group, contentDescription = null)
                Text(" Rejoindre")
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(duels, key = { it.duelId }) { duel ->
                val me = duel.participants.firstOrNull { it.userId == userId }
                val leader = duel.participants.maxByOrNull { it.netScoreSeconds }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDuel(duel.duelId) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(duel.config.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = duel.inviteCode,
                                style = MaterialTheme.typography.labelMedium,
                                color = AnkiBlue
                            )
                        }
                        Text(
                            text = "${duel.participants.size}/${duel.config.maxPlayers} joueurs • " +
                                (if (duel.config.referenceMode == ReferenceMode.LEADER) "Mode Leader" else "Mode Moyenne") +
                                " • Tolérance ${duel.config.toleranceMinutes}m",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Leader : ${leader?.username ?: "-"} (${leader?.netScoreMinutes ?: 0}m)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            val debt = me?.debtMinutes ?: 0
                            Text(
                                text = if (debt > 0) "Dette : $debt min" else "Aucune dette",
                                fontWeight = FontWeight.Bold,
                                color = if (debt > 0) SocialCoral else SafeEmerald
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
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
            title = { Text("Rejoindre un duel") },
            text = {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase() },
                    label = { Text("Code d'invitation (ex. ANKI-7F3K)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = joinCode.trim().length >= 4,
                    onClick = {
                        showJoinDialog = false
                        onJoinDuel(joinCode.trim())
                        joinCode = ""
                    }
                ) { Text("Rejoindre") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) { Text("Annuler") }
            }
        )
    }
}
