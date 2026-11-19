package com.ankiduel.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ankiduel.android.service.DuelMonitorService
import com.ankiduel.android.ui.AppViewModel
import com.ankiduel.android.ui.screens.AppsSettingsScreen
import com.ankiduel.android.ui.screens.AuthScreen
import com.ankiduel.android.ui.screens.DashboardScreen
import com.ankiduel.android.ui.screens.DuelDetailScreen
import com.ankiduel.android.ui.screens.DuelsScreen
import com.ankiduel.android.ui.screens.PermissionsScreen
import com.ankiduel.android.ui.theme.AnkiDuelTheme

private enum class MainTab {
    DASHBOARD,
    DUELS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as AnkiDuelApplication).container.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { DuelMonitorService.start(this) }

        setContent {
            AnkiDuelTheme {
                AnkiDuelAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAll()
    }
}

@Composable
private fun AnkiDuelAppRoot(viewModel: AppViewModel) {
    val session by viewModel.session.collectAsState()
    val duels by viewModel.duels.collectAsState()
    val usage by viewModel.usageSnapshot.collectAsState()
    val health by viewModel.permissionHealth.collectAsState()
    val lockStatus by viewModel.lockStatus.collectAsState()
    val isIdleFlagged by viewModel.isIdleFlagged.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val feedback by viewModel.feedbackMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPermissions by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var selectedDuelId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedback) {
        feedback?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val currentSession = session
    if (currentSession == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isAuthenticated = currentSession.demoMode || !currentSession.token.isNullOrBlank()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isAuthenticated && !showPermissions && selectedDuelId == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == MainTab.DASHBOARD,
                        onClick = { currentTab = MainTab.DASHBOARD },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text("Comparateur") }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.DUELS,
                        onClick = { currentTab = MainTab.DUELS },
                        icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                        label = { Text("Duels") }
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.SETTINGS,
                        onClick = { currentTab = MainTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Apps") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !isAuthenticated -> {
                    AuthScreen(
                        initialServerUrl = currentSession.serverUrl,
                        isBusy = isBusy,
                        onLogin = viewModel::login,
                        onRegister = viewModel::register,
                        onStartDemo = viewModel::startDemoMode
                    )
                }

                showPermissions -> {
                    PermissionsScreen(
                        health = health,
                        onRefresh = { viewModel.refreshAll() },
                        onContinue = { showPermissions = false }
                    )
                }

                selectedDuelId != null -> {
                    val duel = duels.firstOrNull { it.duelId == selectedDuelId }
                    if (duel == null) {
                        selectedDuelId = null
                    } else {
                        DuelDetailScreen(
                            duel = duel,
                            userId = currentSession.userId.orEmpty(),
                            onBack = { selectedDuelId = null },
                            onActivateJoker = { viewModel.activateJoker(it) },
                            onForfeit = {
                                viewModel.forfeitDuel(it)
                                selectedDuelId = null
                            }
                        )
                    }
                }

                currentTab == MainTab.DASHBOARD -> {
                    DashboardScreen(
                        username = currentSession.username ?: "Joueur",
                        userId = currentSession.userId.orEmpty(),
                        demoMode = currentSession.demoMode,
                        usage = usage,
                        lockStatus = lockStatus,
                        duels = duels,
                        health = health,
                        isIdleFlagged = isIdleFlagged,
                        onRefresh = { viewModel.refreshAll() },
                        onOpenPermissions = { showPermissions = true },
                        onDuelClick = { selectedDuelId = it },
                        onActivateJoker = { viewModel.activateJoker(it) },
                        onAddDemoAnki = { viewModel.addDemoAnkiMinutes(it) },
                        onAddDemoSocial = { viewModel.addDemoSocialMinutes(it) }
                    )
                }

                currentTab == MainTab.DUELS -> {
                    DuelsScreen(
                        userId = currentSession.userId.orEmpty(),
                        duels = duels,
                        onCreateDuel = { config -> viewModel.createDuel(config) },
                        onJoinDuel = { code -> viewModel.joinDuel(code) },
                        onSelectDuel = { selectedDuelId = it }
                    )
                }

                currentTab == MainTab.SETTINGS -> {
                    AppsSettingsScreen(
                        installedApps = installedApps,
                        trackedPackages = currentSession.trackedSocialPackages,
                        mandatoryPackages = viewModel.activeMandatoryPackages(),
                        hasActiveDuel = viewModel.hasActiveDuel(),
                        serverUrl = currentSession.serverUrl,
                        demoMode = currentSession.demoMode,
                        onTogglePackage = viewModel::toggleSocialPackage,
                        onSaveServerUrl = viewModel::updateServerUrl,
                        onOpenPermissions = { showPermissions = true },
                        onLogout = {
                            selectedDuelId = null
                            viewModel.logout()
                        }
                    )
                }
            }
        }
    }
}
