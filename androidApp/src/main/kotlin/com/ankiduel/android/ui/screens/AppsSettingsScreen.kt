package com.ankiduel.android.ui.screens

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ankiduel.android.tracking.InstalledAppItem
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.CardDark
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SubtleText
import com.ankiduel.shared.model.SocialAppCatalog

@Composable
fun AppsSettingsScreen(
    installedApps: List<InstalledAppItem>,
    trackedPackages: Set<String>,
    mandatoryPackages: Set<String>,
    hasActiveDuel: Boolean,
    serverUrl: String,
    demoMode: Boolean,
    onTogglePackage: (String) -> Unit,
    onSaveServerUrl: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    onLogout: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    var editableUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Apps & Paramètres", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Connexion & Permissions", fontWeight = FontWeight.Bold)
                    if (!demoMode) {
                        OutlinedTextField(
                            value = editableUrl,
                            onValueChange = { editableUrl = it },
                            label = { Text("URL serveur Ktor") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onSaveServerUrl(editableUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mettre à jour l'URL") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.weight(1f)) {
                            Text("Permissions")
                        }
                        OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                            Text(if (demoMode) "Quitter Démo" else "Déconnexion", color = SocialCoral)
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Réseaux sociaux surveillés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (hasActiveDuel) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = AmberJoker)
                        Text("Verrouillé (duel actif)", color = AmberJoker, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Rechercher une application") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }

        items(filtered, key = { it.packageName }) { app ->
            val checked = app.packageName in trackedPackages
            val mandatory = app.packageName in mandatoryPackages
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        enabled = !hasActiveDuel && !mandatory,
                        onCheckedChange = { onTogglePackage(app.packageName) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = SubtleText)
                    }
                    if (mandatory) {
                        Text("Obligatoire", color = AmberJoker, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
