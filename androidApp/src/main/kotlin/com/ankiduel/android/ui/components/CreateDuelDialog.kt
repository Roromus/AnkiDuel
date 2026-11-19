package com.ankiduel.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorder
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SurfaceBase
import com.ankiduel.android.ui.theme.SurfaceCard
import com.ankiduel.android.ui.theme.SurfaceCardElevated
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.ReferenceMode
import com.ankiduel.shared.model.SocialAppCatalog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCardElevated,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            Text(
                text = "Nouveau duel",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Définissez les règles partagées par tous les participants.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ObsidianTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Nom du duel"
                )

                // Joueurs
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nombre max de joueurs",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        StatusPill(
                            text = "${maxPlayers.roundToInt()} joueurs",
                            color = AnkiBlueSoft
                        )
                    }
                    Slider(
                        value = maxPlayers,
                        onValueChange = { maxPlayers = it },
                        valueRange = 2f..10f,
                        steps = 7,
                        colors = obsidianSliderColors()
                    )
                }

                // Mode de référence
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Référence de blocage",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    SegmentedControl(
                        options = listOf("Leader (1er)", "Moyenne groupe"),
                        selectedIndex = if (referenceMode == ReferenceMode.LEADER) 0 else 1,
                        onSelect = {
                            referenceMode = if (it == 0) ReferenceMode.LEADER else ReferenceMode.AVERAGE
                        }
                    )
                }

                // Cycle
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cycle de réinitialisation",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    SegmentedControl(
                        options = listOf("Quotidien (00h)", "Continu"),
                        selectedIndex = if (cycle == DuelCycle.DAILY_MIDNIGHT) 0 else 1,
                        onSelect = {
                            cycle = if (it == 0) DuelCycle.DAILY_MIDNIGHT else DuelCycle.CONTINUOUS
                        }
                    )
                }

                // Tolérance
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Marge de tolérance",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        StatusPill(
                            text = "${toleranceMinutes.roundToInt()} min",
                            color = AnkiBlueSoft
                        )
                    }
                    Slider(
                        value = toleranceMinutes,
                        onValueChange = { toleranceMinutes = it },
                        valueRange = 0f..30f,
                        steps = 5,
                        colors = obsidianSliderColors()
                    )
                }

                HorizontalDivider(color = HairlineBorderSubtle)

                // Plafond quotidien
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Plafond de dette quotidien",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Limite le rattrapage maximal sur une journée.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = capEnabled,
                            onCheckedChange = { capEnabled = it },
                            colors = obsidianSwitchColors()
                        )
                    }

                    AnimatedVisibility(
                        visible = capEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Limite journalière",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                StatusPill(
                                    text = "${dailyCapMinutes.roundToInt()} min / jour",
                                    color = AnkiBlueSoft
                                )
                            }
                            Slider(
                                value = dailyCapMinutes,
                                onValueChange = { dailyCapMinutes = it },
                                valueRange = 15f..180f,
                                steps = 10,
                                colors = obsidianSliderColors()
                            )
                        }
                    }
                }

                // Joker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Joker d'urgence",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "5 min de déblocage exceptionnel, 1 fois par semaine.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = jokerEnabled,
                        onCheckedChange = { jokerEnabled = it },
                        colors = obsidianSwitchColors()
                    )
                }

                HorizontalDivider(color = HairlineBorderSubtle)

                // Apps obligatoires en FlowRow compact
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Applications obligatoires",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SocialAppCatalog.defaultSocialApps.take(8).forEach { app ->
                            val selected = app.packageName in mandatoryPackages
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) AnkiBlue.copy(alpha = 0.16f) else SurfaceBase
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) AnkiBlue.copy(alpha = 0.45f) else HairlineBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        mandatoryPackages = if (selected) {
                                            mandatoryPackages - app.packageName
                                        } else {
                                            mandatoryPackages + app.packageName
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = AnkiBlueSoft,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = app.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) AnkiBlueSoft else TextSecondary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = HairlineBorderSubtle)
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceCard,
                        contentColor = TextSecondary
                    ),
                    modifier = Modifier
                        .weight(0.42f)
                        .height(50.dp)
                ) {
                    Text("Annuler", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    enabled = title.trim().length >= 2,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnkiBlue,
                        contentColor = Color.White
                    ),
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
                    },
                    modifier = Modifier
                        .weight(0.58f)
                        .height(50.dp)
                ) {
                    Text("Créer le duel", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun obsidianSliderColors() = SliderDefaults.colors(
    thumbColor = TextPrimary,
    activeTrackColor = AnkiBlue,
    inactiveTrackColor = SurfaceBase,
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent
)

@Composable
private fun obsidianSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = AnkiBlue,
    uncheckedThumbColor = TextSecondary,
    uncheckedTrackColor = SurfaceBase,
    uncheckedBorderColor = HairlineBorder
)
