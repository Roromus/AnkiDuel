package com.ankiduel.android.blocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ankiduel.android.AnkiDuelApplication
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiDuelTheme
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.shared.model.SocialAppCatalog
import kotlinx.coroutines.launch

class LockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Le bouton Retour renvoie toujours à l'écran d'accueil pour empêcher de contourner le blocage
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goHome()
                }
            }
        )

        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
            ?: BlockStateHolder.lastBlockedPackage.value.orEmpty()

        val appContainer = (application as AnkiDuelApplication).container

        setContent {
            AnkiDuelTheme {
                val lockStatus by BlockStateHolder.lockStatus.collectAsState()
                val scope = rememberCoroutineScope()

                if (!lockStatus.isLocked) {
                    finish()
                }

                LockScreenContent(
                    blockedAppName = SocialAppCatalog.displayNameFor(blockedPkg),
                    debtMinutes = lockStatus.maxDebtMinutes,
                    duelTitle = lockStatus.blockingDuelTitle ?: "Duel AnkiDuel",
                    leaderName = lockStatus.leaderUsername ?: "Votre adversaire",
                    canUseJoker = lockStatus.blockingDuelId != null,
                    onOpenAnkiDroid = { openAnkiDroid() },
                    onActivateJoker = {
                        val duelId = lockStatus.blockingDuelId ?: return@LockScreenContent
                        scope.launch {
                            val activated = appContainer.repository.activateJoker(duelId)
                            if (activated) {
                                finish()
                            }
                        }
                    },
                    onGoHome = { goHome() }
                )
            }
        }
    }

    private fun openAnkiDroid() {
        val ankiIntent = packageManager.getLaunchIntentForPackage(SocialAppCatalog.ANKIDROID_PACKAGE)
        if (ankiIntent != null) {
            ankiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(ankiIntent)
            finish()
        } else {
            goHome()
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
private fun LockScreenContent(
    blockedAppName: String,
    debtMinutes: Int,
    duelTitle: String,
    leaderName: String,
    canUseJoker: Boolean,
    onOpenAnkiDroid: () -> Unit,
    onActivateJoker: () -> Unit,
    onGoHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1A0D14), Color(0xFF0E1420), Color(0xFF0A0F1A))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = SocialCoral,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "$blockedAppName est verrouillé",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2436)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DETTE ANKIDROID ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = SocialCoral
                    )
                    Text(
                        text = "$debtMinutes min à rattraper",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "$duelTitle • $leaderName est devant vous",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0BEC5),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = onOpenAnkiDroid,
                colors = ButtonDefaults.buttonColors(containerColor = AnkiBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Icon(Icons.Default.School, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Ouvrir AnkiDroid maintenant", fontWeight = FontWeight.Bold)
            }

            if (canUseJoker) {
                OutlinedButton(
                    onClick = onActivateJoker,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = AmberJoker)
                    Spacer(Modifier.size(8.dp))
                    Text("Utiliser le Joker d'urgence (5 min)", color = AmberJoker)
                }
            }

            TextButton(onClick = onGoHome) {
                Text("Retourner à l'accueil", color = Color(0xFF90A4AE))
            }
        }
    }
}
