package com.ankiduel.android.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.android.tracking.PermissionHealth
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.CardDark
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SubtleText

@Composable
fun PermissionsScreen(
    health: PermissionHealth,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Autorisations requises",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Pour mesurer AnkiDroid vs les réseaux sociaux et verrouiller ces derniers en cas de dette de duel, activez ces accès système.",
            style = MaterialTheme.typography.bodyMedium,
            color = SubtleText
        )

        PermissionCard(
            title = "1. Données d'utilisation (Usage Access)",
            description = "Mesure précisément le temps passé sur AnkiDroid et vos réseaux sociaux.",
            granted = health.usageAccessGranted,
            required = true,
            actionLabel = "Autoriser",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )

        PermissionCard(
            title = "2. Service d'accessibilité",
            description = "Détecte en direct l'ouverture d'un réseau social bloqué pour afficher le verrou.",
            granted = health.accessibilityGranted,
            required = true,
            actionLabel = "Activer",
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        PermissionCard(
            title = "3. Superposition d'écran (Overlay)",
            description = "Permet d'afficher l'écran de blocage par-dessus les applications sociales.",
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = "4. Notifications",
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
            title = "5. API AnkiDroid (Anti-triche)",
            description = if (health.ankiDroidInstalled) {
                "Vérifie le nombre de cartes réellement révisées pour empêcher de laisser l'écran allumé."
            } else {
                "AnkiDroid non détecté sur cet appareil (optionnel en mode Démo)."
            },
            granted = health.ankiDroidApiGranted,
            required = false,
            actionLabel = "Connecter",
            onClick = {
                ankiApiLauncher.launch("com.ichi2.anki.permission.READ_WRITE_DATABASE")
            }
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.weight(1f)
            ) {
                Text("Actualiser")
            }

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = AnkiBlue),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (health.allEnforcementPermissionsHealthy) "Continuer" else "Continuer quand même")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    required: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = when {
                    granted -> SafeEmerald
                    required -> SocialCoral
                    else -> AmberJoker
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = SubtleText)
            }
            if (!granted) {
                Button(onClick = onClick) {
                    Text(actionLabel)
                }
            } else {
                Text("OK", color = SafeEmerald, fontWeight = FontWeight.Bold)
            }
        }
    }
}
