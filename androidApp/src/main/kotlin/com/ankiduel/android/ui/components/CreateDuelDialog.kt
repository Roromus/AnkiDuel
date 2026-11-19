package com.ankiduel.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.ReferenceMode
import com.ankiduel.shared.model.SocialAppCatalog
import kotlin.math.roundToInt

@Composable
fun CreateDuelDialog(
    onDismiss: () -> Unit,
    onConfirm: (DuelConfig) -> Unit
) {
    var title by remember { mutableStateOf("Duel Anki Quotidien") }
    var maxPlayers by remember { mutableFloatStateOf(2f) }
    var referenceMode by remember { mutableStateOf(ReferenceMode.LEADER) }
    var cycle by remember { mutableStateOf(DuelCycle.DAILY_MIDNIGHT) }
    var toleranceMinutes by remember { mutableFloatStateOf(10f) }
    var dailyCapMinutes by remember { mutableFloatStateOf(60f) }
    var capEnabled by remember { mutableStateOf(true) }
    var jokerEnabled by remember { mutableStateOf(true) }
    var mandatoryPackages by remember {
        mutableStateOf(setOf("com.instagram.android", "com.zhiliaoapp.musically"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau Duel", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nom du duel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Nombre max de joueurs : ${maxPlayers.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = maxPlayers,
                    onValueChange = { maxPlayers = it },
                    valueRange = 2f..10f,
                    steps = 7
                )

                Text("Référence de blocage", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = referenceMode == ReferenceMode.LEADER,
                        onClick = { referenceMode = ReferenceMode.LEADER },
                        label = { Text("Leader (1er)") }
                    )
                    FilterChip(
                        selected = referenceMode == ReferenceMode.AVERAGE,
                        onClick = { referenceMode = ReferenceMode.AVERAGE },
                        label = { Text("Moyenne groupe") }
                    )
                }

                Text("Cycle de réinitialisation", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = cycle == DuelCycle.DAILY_MIDNIGHT,
                        onClick = { cycle = DuelCycle.DAILY_MIDNIGHT },
                        label = { Text("Quotidien (00h)") }
                    )
                    FilterChip(
                        selected = cycle == DuelCycle.CONTINUOUS,
                        onClick = { cycle = DuelCycle.CONTINUOUS },
                        label = { Text("Continu") }
                    )
                }

                Text("Marge de tolérance : ${toleranceMinutes.roundToInt()} min", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = toleranceMinutes,
                    onValueChange = { toleranceMinutes = it },
                    valueRange = 0f..30f,
                    steps = 5
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Plafond de dette quotidien")
                    Switch(checked = capEnabled, onCheckedChange = { capEnabled = it })
                }
                if (capEnabled) {
                    Text("Plafond : ${dailyCapMinutes.roundToInt()} min/jour", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = dailyCapMinutes,
                        onValueChange = { dailyCapMinutes = it },
                        valueRange = 15f..180f,
                        steps = 10
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Joker d'urgence (5 min / sem.)")
                    Switch(checked = jokerEnabled, onCheckedChange = { jokerEnabled = it })
                }

                Text("Apps obligatoires pour ce duel :", style = MaterialTheme.typography.labelLarge)
                SocialAppCatalog.defaultSocialApps.take(6).forEach { app ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = app.packageName in mandatoryPackages,
                            onCheckedChange = { checked ->
                                mandatoryPackages = if (checked) {
                                    mandatoryPackages + app.packageName
                                } else {
                                    mandatoryPackages - app.packageName
                                }
                            }
                        )
                        Text(app.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().length >= 2,
                onClick = {
                    onConfirm(
                        DuelConfig(
                            title = title.trim(),
                            maxPlayers = maxPlayers.roundToInt(),
                            referenceMode = referenceMode,
                            cycle = cycle,
                            toleranceMinutes = toleranceMinutes.roundToInt(),
                            dailyDebtCapMinutes = if (capEnabled) dailyCapMinutes.roundToInt() else null,
                            jokerEnabled = jokerEnabled,
                            mandatorySocialPackages = mandatoryPackages
                        )
                    )
                }
            ) {
                Text("Créer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
