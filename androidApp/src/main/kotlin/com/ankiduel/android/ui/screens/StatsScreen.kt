package com.ankiduel.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.ObsidianIconButton
import com.ankiduel.android.ui.components.SectionHeader
import com.ankiduel.android.ui.components.formatCompactMinutes
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorder
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SafeEmeraldSoft
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SocialCoralSoft
import com.ankiduel.android.ui.theme.SurfaceCard
import com.ankiduel.android.ui.theme.SurfaceCardElevated
import com.ankiduel.android.ui.theme.TabularFeatures
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import com.ankiduel.shared.model.DailyStatRecord
import com.ankiduel.shared.model.DuelState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class StatsPeriod(val label: String, val days: Int) {
    WEEK("7 jours", 7),
    MONTH("30 jours", 30)
}

private enum class StatsMetric(val label: String, val color: Color) {
    ANKI_MINUTES("Temps Anki", AnkiBlue),
    CARDS("Cartes", SafeEmeraldSoft),
    SOCIAL_MINUTES("Réseaux", SocialCoral)
}

@Composable
fun StatsScreen(
    selfUserId: String,
    duels: List<DuelState>,
    personalStats: List<DailyStatRecord>,
    opponentStats: List<DailyStatRecord>,
    selectedOpponentId: String?,
    isLoading: Boolean,
    firestoreSyncWarning: String? = null,
    onSelectOpponent: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }
    var metric by remember { mutableStateOf(StatsMetric.ANKI_MINUTES) }

    val opponents = remember(duels, selfUserId) {
        duels.flatMap { it.participants }
            .filter { it.userId != selfUserId }
            .distinctBy { it.userId }
    }
    val selectedOpponentName = remember(opponents, selectedOpponentId) {
        opponents.firstOrNull { it.userId == selectedOpponentId }?.username
    }

    val slicedPersonal = remember(personalStats, period) {
        personalStats.takeLast(period.days)
    }
    val slicedOpponent = remember(opponentStats, period, selectedOpponentId) {
        if (selectedOpponentId == null) emptyList() else opponentStats.takeLast(period.days)
    }

    val totalAnkiMin = remember(slicedPersonal) { slicedPersonal.sumOf { it.ankiMinutes } }
    val totalCards = remember(slicedPersonal) { slicedPersonal.sumOf { it.cardsReviewed } }
    val totalSocialMin = remember(slicedPersonal) { slicedPersonal.sumOf { it.socialMinutes } }
    val activeDaysCount = remember(slicedPersonal) {
        slicedPersonal.count { it.ankiEffectiveSeconds > 0 || it.cardsReviewed > 0 }
    }
    val currentStreak = remember(personalStats) {
        var streak = 0
        for (record in personalStats.asReversed()) {
            if (record.ankiEffectiveSeconds > 0 || record.cardsReviewed > 0) {
                streak++
            } else if (streak > 0) {
                break
            }
        }
        streak
    }

    AmbientGlowBackdrop(accentColor = AnkiBlue, glowAlpha = 0.12f) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Statistiques",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Évolution sur ${period.label} · Moyennes & duel",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = AnkiBlueSoft
                        )
                    } else {
                        ObsidianIconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Actualiser les stats",
                            onClick = onRefresh
                        )
                    }
                }
            }

            if (!firestoreSyncWarning.isNullOrBlank()) {
                item(key = "firestore_warning") {
                    ObsidianCard(
                        shape = RoundedCornerShape(18.dp),
                        borderTint = AmberJoker,
                        borderAlpha = 0.35f,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Historique cloud bloqué par Firestore",
                            style = MaterialTheme.typography.titleSmall,
                            color = AmberJokerSoft
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = firestoreSyncWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            item(key = "period_selector") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatsPeriod.entries.forEach { option ->
                        val selected = period == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AnkiBlue.copy(alpha = 0.20f) else Color.Transparent)
                                .border(
                                    width = if (selected) 1.dp else 0.dp,
                                    color = if (selected) AnkiBlue.copy(alpha = 0.45f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { period = option }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (option == StatsPeriod.WEEK) "Semaine (7j)" else "Mois (30j)",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) AnkiBlueSoft else TextMuted
                            )
                        }
                    }
                }
            }

            if (opponents.isNotEmpty()) {
                item(key = "opponent_selector") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "COMPARER AVEC UN ADVERSAIRE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OpponentFilterChip(
                                label = "Moi seul",
                                selected = selectedOpponentId == null,
                                onClick = { onSelectOpponent(null) }
                            )
                            opponents.forEach { opp ->
                                OpponentFilterChip(
                                    label = opp.username,
                                    selected = selectedOpponentId == opp.userId,
                                    onClick = { onSelectOpponent(opp.userId) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "kpis") {
                val daysDiv = slicedPersonal.size.coerceAtLeast(1)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatKpiCard(
                            title = "TEMPS ANKI",
                            value = formatCompactMinutes(totalAnkiMin),
                            subtitle = "Moy. ${totalAnkiMin / daysDiv} min/j",
                            accent = AnkiBlueSoft,
                            modifier = Modifier.weight(1f)
                        )
                        StatKpiCard(
                            title = "CARTES RÉVISÉES",
                            value = "$totalCards",
                            subtitle = "Moy. ${totalCards / daysDiv} /j",
                            accent = SafeEmeraldSoft,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatKpiCard(
                            title = "RÉSEAUX SOCIAUX",
                            value = formatCompactMinutes(totalSocialMin),
                            subtitle = "Moy. ${totalSocialMin / daysDiv} min/j",
                            accent = SocialCoralSoft,
                            modifier = Modifier.weight(1f)
                        )
                        StatKpiCard(
                            title = "SÉRIE ACTIVE",
                            value = "$currentStreak j",
                            subtitle = "$activeDaysCount/$daysDiv jours actifs",
                            accent = AmberJokerSoft,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = AmberJokerSoft,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item(key = "chart_card") {
                ObsidianCard(
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SectionHeader(
                        title = "Activité quotidienne",
                        trailing = {
                            if (selectedOpponentName != null) {
                                Text(
                                    text = "vs $selectedOpponentName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberJokerSoft
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatsMetric.entries.forEach { m ->
                            val selected = metric == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) m.color.copy(alpha = 0.16f) else SurfaceCardElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) m.color.copy(alpha = 0.45f) else HairlineBorderSubtle,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { metric = m }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = m.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) m.color else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    DailyComparisonBarChart(
                        personal = slicedPersonal,
                        opponent = slicedOpponent,
                        metric = metric,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            LegendDot(color = metric.color, label = "Moi")
                            if (selectedOpponentName != null) {
                                LegendDot(color = AmberJoker, label = selectedOpponentName)
                            }
                        }
                        val peakVal = slicedPersonal.maxOfOrNull { metricValue(it, metric) } ?: 0
                        val unit = if (metric == StatsMetric.CARDS) "cartes" else "min"
                        Text(
                            text = "Pic : $peakVal $unit",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = TabularFeatures
                            ),
                            color = TextMuted
                        )
                    }
                }
            }

            if (selectedOpponentName != null && slicedOpponent.isNotEmpty()) {
                item(key = "opponent_summary") {
                    val oppAnki = slicedOpponent.sumOf { it.ankiMinutes }
                    val oppCards = slicedOpponent.sumOf { it.cardsReviewed }
                    val oppSocial = slicedOpponent.sumOf { it.socialMinutes }

                    ObsidianCard(
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SectionHeader(title = "Bilan vs $selectedOpponentName (${period.label})")
                        Spacer(Modifier.height(12.dp))
                        ComparisonRow(
                            label = "Temps Anki",
                            mine = formatCompactMinutes(totalAnkiMin),
                            theirs = formatCompactMinutes(oppAnki),
                            mineAhead = totalAnkiMin >= oppAnki
                        )
                        Spacer(Modifier.height(8.dp))
                        ComparisonRow(
                            label = "Cartes révisées",
                            mine = "$totalCards",
                            theirs = "$oppCards",
                            mineAhead = totalCards >= oppCards
                        )
                        Spacer(Modifier.height(8.dp))
                        ComparisonRow(
                            label = "Réseaux sociaux",
                            mine = formatCompactMinutes(totalSocialMin),
                            theirs = formatCompactMinutes(oppSocial),
                            mineAhead = totalSocialMin <= oppSocial
                        )
                    }
                }
            }
        }
    }
}

private fun metricValue(record: DailyStatRecord, metric: StatsMetric): Int = when (metric) {
    StatsMetric.ANKI_MINUTES -> record.ankiMinutes
    StatsMetric.CARDS -> record.cardsReviewed
    StatsMetric.SOCIAL_MINUTES -> record.socialMinutes
}

@Composable
private fun DailyComparisonBarChart(
    personal: List<DailyStatRecord>,
    opponent: List<DailyStatRecord>,
    metric: StatsMetric,
    modifier: Modifier = Modifier
) {
    val oppByDay = remember(opponent) { opponent.associateBy { it.dayKey } }
    val hasOpponent = opponent.isNotEmpty()
    val maxVal = remember(personal, opponent, metric) {
        val myMax = personal.maxOfOrNull { metricValue(it, metric) } ?: 0
        val oppMax = opponent.maxOfOrNull { metricValue(it, metric) } ?: 0
        maxOf(myMax, oppMax, 1)
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (personal.isEmpty()) return@Canvas
            val count = personal.size
            val slotWidth = size.width / count.toFloat()
            val barMaxHeight = size.height - 6.dp.toPx()

            // Subtle baseline
            drawLine(
                color = HairlineBorder,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )

            personal.forEachIndexed { idx, record ->
                val myVal = metricValue(record, metric)
                val myRatio = (myVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val myHeight = if (myVal > 0) maxOf(4.dp.toPx(), barMaxHeight * myRatio) else 2.dp.toPx()

                if (hasOpponent) {
                    val oppVal = oppByDay[record.dayKey]?.let { metricValue(it, metric) } ?: 0
                    val oppRatio = (oppVal.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                    val oppHeight = if (oppVal > 0) maxOf(4.dp.toPx(), barMaxHeight * oppRatio) else 2.dp.toPx()

                    val singleBarWidth = (slotWidth * 0.34f).coerceAtLeast(2.dp.toPx())
                    val gap = (slotWidth * 0.08f).coerceAtLeast(1.dp.toPx())
                    val groupStart = idx * slotWidth + (slotWidth - (singleBarWidth * 2 + gap)) / 2f

                    drawRoundRect(
                        color = if (myVal > 0) metric.color else HairlineBorderSubtle,
                        topLeft = Offset(groupStart, size.height - myHeight),
                        size = Size(singleBarWidth, myHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (oppVal > 0) AmberJoker else HairlineBorderSubtle,
                        topLeft = Offset(groupStart + singleBarWidth + gap, size.height - oppHeight),
                        size = Size(singleBarWidth, oppHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                } else {
                    val barWidth = (slotWidth * 0.56f).coerceIn(3.dp.toPx(), 26.dp.toPx())
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    drawRoundRect(
                        color = if (myVal > 0) metric.color else HairlineBorderSubtle,
                        topLeft = Offset(left, size.height - myHeight),
                        size = Size(barWidth, myHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        if (personal.isNotEmpty()) {
            val labelIndices = if (personal.size <= 7) {
                personal.indices.toList()
            } else {
                listOf(0, personal.size / 3, (personal.size * 2) / 3, personal.lastIndex)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labelIndices.forEach { i ->
                    Text(
                        text = formatShortDay(personal[i].dayKey, compact = personal.size > 7),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = TabularFeatures
                        ),
                        color = TextMuted
                    )
                }
            }
        }
    }
}

private fun formatShortDay(dayKey: String, compact: Boolean): String {
    return runCatching {
        val date = LocalDate.parse(dayKey)
        if (compact) {
            date.format(DateTimeFormatter.ofPattern("dd/MM", Locale.FRENCH))
        } else {
            date.format(DateTimeFormatter.ofPattern("EEE", Locale.FRENCH))
                .replace(".", "")
                .replaceFirstChar { it.uppercase() }
        }
    }.getOrDefault(dayKey.takeLast(5))
}

@Composable
private fun StatKpiCard(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    ObsidianCard(
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp),
        modifier = modifier
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (leadingIcon != null) leadingIcon()
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TabularFeatures
                ),
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFeatureSettings = TabularFeatures
            ),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OpponentFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AmberJoker.copy(alpha = 0.18f) else SurfaceCard)
            .border(
                width = 1.dp,
                color = if (selected) AmberJoker.copy(alpha = 0.50f) else HairlineBorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) AmberJokerSoft else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    mine: String,
    theirs: String,
    mineAhead: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = mine,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = TabularFeatures
            ),
            color = if (mineAhead) SafeEmeraldSoft else TextPrimary
        )
        Text(
            text = "  vs  ",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Text(
            text = theirs,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = TabularFeatures
            ),
            color = AmberJokerSoft
        )
    }
}
