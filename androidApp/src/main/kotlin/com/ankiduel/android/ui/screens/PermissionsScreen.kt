package com.ankiduel.android.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.android.data.TrackingDiagnostics
import com.ankiduel.android.tracking.AnkiApiStatus
import com.ankiduel.android.tracking.AnkiDroidTracker
import com.ankiduel.android.tracking.PermissionHealth
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.ObsidianIconButton
import com.ankiduel.android.ui.components.StatusPill
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorder
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SafeEmeraldSoft
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SurfaceBase
import com.ankiduel.android.ui.theme.SurfaceCard
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary

@Composable
fun PermissionsScreen(
    health: PermissionHealth,
    diagnostics: TrackingDiagnostics = TrackingDiagnostics(),
    onRefresh: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }
    val ankiApiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onRefresh() }

    val essentialGranted = listOf(
        health.usageAccessGranted,
        health.accessibilityGranted,
        health.overlayGranted
    ).count { it }
    val progress by animateFloatAsState(
        targetValue = essentialGranted / 3f,
        animationSpec = tween(420),
        label = "permProgress"
    )
    val allReady = health.allEnforcementPermissionsHealthy

    AmbientGlowBackdrop(
        accentColor = if (allReady) SafeEmerald else AmberJoker,
        glowAlpha = 0.14f
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
                    onClick = onContinue
                )

                Text(
                    text = "Autorisations",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                StatusPill(
                    text = "$essentialGranted/3 actives",
                    color = if (allReady) SafeEmeraldSoft else AmberJokerSoft,
                    showDot = true
                )
            }

            // ── Scrollable Body ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Activez ces accès système pour mesurer vos révisions AnkiDroid et verrouiller vos réseaux en cas de dette.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                // Subtle readiness bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(SurfaceCard)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceAtLeast(0.04f))
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (allReady) SafeEmerald else AmberJoker)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "ESSENTIELLES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                PermissionCard(
                    icon = Icons.Rounded.QueryStats,
                    title = "Données d'utilisation",
                    description = "Mesure précisément le temps passé sur AnkiDroid et vos réseaux sociaux.",
                    granted = health.usageAccessGranted,
                    required = true,
                    actionLabel = "Autoriser",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                )

                PermissionCard(
                    icon = Icons.Rounded.AccessibilityNew,
                    title = "Service d'accessibilité",
                    description = "Détecte en direct l'ouverture d'un réseau bloqué pour afficher le verrou.",
                    granted = health.accessibilityGranted,
                    required = true,
                    actionLabel = "Activer",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                PermissionCard(
                    icon = Icons.Rounded.Layers,
                    title = "Superposition d'écran",
                    description = "Affiche l'écran de verrouillage par-dessus les applications sociales.",
                    granted = health.overlayGranted,
                    required = true,
                    actionLabel = "Autoriser",
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "RECOMMANDÉES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermissionCard(
                        icon = Icons.Rounded.NotificationsNone,
                        title = "Notifications",
                        description = "Affiche le suivi permanent et les alertes de duel.",
                        granted = health.notificationsGranted,
                        required = false,
                        actionLabel = "Autoriser",
                        onClick = {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }

                PermissionCard(
                    icon = Icons.Rounded.School,
                    title = "API AnkiDroid (Anti-triche)",
                    description = when {
                        !health.ankiDroidInstalled ->
                            "AnkiDroid non détecté sur cet appareil (optionnel en mode Démo)."
                        health.ankiDroidApiGranted && diagnostics.ankiApiSnapshot.status == AnkiApiStatus.PROVIDER_UNAVAILABLE ->
                            "Permission accordée, mais l'API est désactivée dans AnkiDroid (Paramètres > Avancé > Activer l'API AnkiDroid)."
                        else ->
                            "Vérifie le nombre de cartes réellement révisées pour empêcher l'inactivité."
                    },
                    granted = health.ankiDroidApiGranted,
                    required = false,
                    actionLabel = "Connecter",
                    onClick = {
                        val perm = health.ankiDroidPackage
                            ?.takeIf { it != com.ankiduel.shared.model.SocialAppCatalog.ANKIDROID_PACKAGE }
                            ?.let { "$it.permission.READ_WRITE_DATABASE" }
                            ?: AnkiDroidTracker.ANKI_READ_WRITE_PERMISSION
                        ankiApiLauncher.launch(perm)
                    }
                )

                PermissionCard(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Batterie sans restriction",
                    description = "Empêche le téléphone (Xiaomi, Samsung, Oppo…) de couper le suivi AnkiDroid en arrière-plan.",
                    granted = health.ignoringBatteryOptimizations,
                    required = false,
                    actionLabel = "Autoriser",
                    onClick = {
                        val directIntent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        )
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        runCatching { context.startActivity(directIntent) }
                            .onFailure { context.startActivity(fallbackIntent) }
                    }
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "DIAGNOSTIC SUIVI ANKI",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                TrackingDiagnosticCard(
                    health = health,
                    diagnostics = diagnostics
                )

                Spacer(Modifier.height(12.dp))
            }

            // ── Bottom Dock ───────────────────────────────────────────────────
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
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceCard,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(0.42f)
                            .height(50.dp)
                    ) {
                        Text("Actualiser", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = onContinue,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allReady) SafeEmerald else AnkiBlue,
                            contentColor = if (allReady) Color(0xFF032217) else Color.White
                        ),
                        modifier = Modifier
                            .weight(0.58f)
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (allReady) "Continuer" else "Plus tard",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    required: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    val accent = when {
        granted -> SafeEmeraldSoft
        required -> SocialCoral
        else -> AnkiBlueSoft
    }

    ObsidianCard(
        shape = RoundedCornerShape(18.dp),
        borderTint = if (granted) SafeEmerald else Color.White,
        borderAlpha = if (granted) 0.22f else 0.08f,
        contentPadding = PaddingValues(15.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (granted) {
                StatusPill(
                    text = "Actif",
                    color = SafeEmeraldSoft,
                    icon = Icons.Rounded.Check
                )
            } else {
                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnkiBlue.copy(alpha = 0.16f),
                        contentColor = AnkiBlueSoft
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TrackingDiagnosticCard(
    health: PermissionHealth,
    diagnostics: TrackingDiagnostics
) {
    val apiSnap = diagnostics.ankiApiSnapshot
    val apiSummary = when (apiSnap.status) {
        AnkiApiStatus.CONNECTED -> "Connectée (${apiSnap.cardsReviewedToday} cartes aujourd'hui)"
        AnkiApiStatus.NOT_INSTALLED -> "AnkiDroid non installé"
        AnkiApiStatus.PERMISSION_MISSING -> "Permission non accordée"
        AnkiApiStatus.PROVIDER_UNAVAILABLE -> "Fournisseur muet (activer l'API dans AnkiDroid)"
        AnkiApiStatus.QUERY_FAILED -> "Erreur requête (${apiSnap.diagnosticDetail ?: "inconnue"})"
    }

    val rawMin = diagnostics.rawAnkiSeconds / 60L
    val rawRemSec = diagnostics.rawAnkiSeconds % 60L
    val effMin = diagnostics.effectiveAnkiSeconds / 60L
    val effRemSec = diagnostics.effectiveAnkiSeconds % 60L
    val sourceLabel = if (diagnostics.usedDailyStatsFallback) {
        "UsageStats quotidien (secours)"
    } else {
        "${diagnostics.ankiEventCount} événements aujourd'hui"
    }

    ObsidianCard(
        shape = RoundedCornerShape(18.dp),
        borderTint = Color.White,
        borderAlpha = 0.08f,
        contentPadding = PaddingValues(15.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DiagnosticRow(
                label = "Paquet AnkiDroid",
                value = health.ankiDroidPackage ?: "Non détecté"
            )
            DiagnosticRow(
                label = "Temps Anki brut",
                value = "${rawMin}m ${rawRemSec}s ($sourceLabel)"
            )
            DiagnosticRow(
                label = "Temps Anki validé",
                value = "${effMin}m ${effRemSec}s"
            )
            DiagnosticRow(
                label = "API AnkiDroid",
                value = apiSummary
            )
            if (!apiSnap.diagnosticDetail.isNullOrBlank() && apiSnap.status != AnkiApiStatus.CONNECTED) {
                Text(
                    text = apiSnap.diagnosticDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = AmberJokerSoft
                )
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
