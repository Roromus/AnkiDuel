package com.ankiduel.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ankiduel.android.tracking.InstalledAppItem
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.ObsidianTextField
import com.ankiduel.android.ui.components.PlayerAvatar
import com.ankiduel.android.ui.components.SectionHeader
import com.ankiduel.android.ui.components.StatusPill
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorder
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SafeEmeraldSoft
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SocialCoralSoft
import com.ankiduel.android.ui.theme.SurfaceBase
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import com.ankiduel.shared.model.SocialAppCatalog

@Composable
fun AppsSettingsScreen(
    installedApps: List<InstalledAppItem>,
    trackedPackages: Set<String>,
    mandatoryPackages: Set<String>,
    hasActiveDuel: Boolean,
    userEmail: String?,
    demoMode: Boolean,
    onTogglePackage: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onLogout: () -> Unit
) {
    var search by remember { mutableStateOf("") }

    val mergedApps = remember(installedApps, trackedPackages) {
        val knownMap = installedApps.associateBy { it.packageName }.toMutableMap()
        SocialAppCatalog.defaultSocialApps.forEach { def ->
            knownMap.putIfAbsent(
                def.packageName,
                InstalledAppItem(def.packageName, def.displayName, isDefaultSocial = true)
            )
        }
        trackedPackages.forEach { pkg ->
            knownMap.putIfAbsent(
                pkg,
                InstalledAppItem(pkg, SocialAppCatalog.displayNameFor(pkg), isDefaultSocial = false)
            )
        }
        knownMap.values.sortedWith(
            compareByDescending<InstalledAppItem> { it.packageName in trackedPackages }
                .thenByDescending { it.isDefaultSocial }
                .thenBy { it.label.lowercase() }
        )
    }

    val filtered = remember(mergedApps, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) mergedApps
        else mergedApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

    AmbientGlowBackdrop(accentColor = AnkiBlue, glowAlpha = 0.10f) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Paramètres",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Compte, autorisations et applications surveillées.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // ── Account & Permissions Card ────────────────────────────────────
            item(key = "account_card") {
                ObsidianCard(
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlayerAvatar(
                            name = if (demoMode) "DM" else (userEmail ?: "AD"),
                            size = 42.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (demoMode) "Mode Démo local" else (userEmail ?: "Compte connecté"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (demoMode) "Données simulées hors ligne" else "Synchronisé sur le cloud",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        StatusPill(
                            text = if (demoMode) "Démo" else "Actif",
                            color = if (demoMode) AmberJokerSoft else SafeEmeraldSoft,
                            showDot = true
                        )
                    }

                    HorizontalDivider(color = HairlineBorderSubtle)

                    SettingsActionRow(
                        icon = Icons.Rounded.VerifiedUser,
                        iconTint = AnkiBlueSoft,
                        title = "Autorisations système",
                        subtitle = "Données d'utilisation, accessibilité et verrouillage",
                        onClick = onOpenPermissions
                    )

                    HorizontalDivider(
                        color = HairlineBorderSubtle,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsActionRow(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        iconTint = SocialCoralSoft,
                        title = if (demoMode) "Quitter le mode Démo" else "Se déconnecter",
                        titleColor = SocialCoralSoft,
                        subtitle = if (demoMode) "Revenir à l'écran de connexion" else "Fermer votre session actuelle",
                        onClick = onLogout
                    )
                }
            }

            // ── Tracked Apps Header + Search ──────────────────────────────────
            item(key = "apps_header") {
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = "Applications surveillées",
                        trailing = {
                            StatusPill(
                                text = "${trackedPackages.size} actives",
                                color = SocialCoralSoft
                            )
                        }
                    )

                    if (hasActiveDuel) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AmberJoker.copy(alpha = 0.10f))
                                .border(1.dp, AmberJoker.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = AmberJokerSoft,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Liste verrouillée pendant un duel actif (anti-triche).",
                                style = MaterialTheme.typography.bodySmall,
                                color = AmberJokerSoft
                            )
                        }
                    }

                    ObsidianTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = "Rechercher une application",
                        leadingIcon = Icons.Rounded.Search,
                        trailingIcon = if (search.isNotEmpty()) {
                            {
                                IconButton(onClick = { search = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Effacer",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null
                    )
                }
            }

            // ── Apps List ─────────────────────────────────────────────────────
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in trackedPackages
                val mandatory = app.packageName in mandatoryPackages
                val toggleable = !hasActiveDuel && !mandatory

                ObsidianCard(
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    onClick = if (toggleable) {
                        { onTogglePackage(app.packageName) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Clean app monogram square
                        val monogramColor = if (checked) SocialCoralSoft else TextMuted
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(monogramColor.copy(alpha = 0.12f))
                                .border(1.dp, monogramColor.copy(alpha = 0.24f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.label.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = monogramColor
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (mandatory) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AmberJoker.copy(alpha = 0.14f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Obligatoire",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberJokerSoft
                                        )
                                    }
                                }
                            }
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        Switch(
                            checked = checked,
                            enabled = toggleable,
                            onCheckedChange = { onTogglePackage(app.packageName) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SocialCoral,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceBase,
                                uncheckedBorderColor = HairlineBorder,
                                disabledCheckedTrackColor = SocialCoral.copy(alpha = 0.42f),
                                disabledCheckedThumbColor = Color.White.copy(alpha = 0.8f),
                                disabledUncheckedTrackColor = SurfaceBase.copy(alpha = 0.6f),
                                disabledUncheckedThumbColor = TextMuted.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(19.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(19.dp)
        )
    }
}
