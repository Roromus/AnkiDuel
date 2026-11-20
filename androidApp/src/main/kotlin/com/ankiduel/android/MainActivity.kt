package com.ankiduel.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.ankiduel.android.service.DuelMonitorService
import com.ankiduel.android.ui.AppViewModel
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.AnkiDuelMark
import com.ankiduel.android.ui.components.AnkiDuelSnackbarHost
import com.ankiduel.android.ui.screens.AppsSettingsScreen
import com.ankiduel.android.ui.screens.AuthScreen
import com.ankiduel.android.ui.screens.DashboardScreen
import com.ankiduel.android.ui.screens.DuelDetailScreen
import com.ankiduel.android.ui.screens.DuelsScreen
import com.ankiduel.android.ui.screens.PermissionsScreen
import com.ankiduel.android.ui.screens.StatsScreen
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.AnkiDuelTheme
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.ObsidianBg
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SurfaceBase
import com.ankiduel.android.ui.theme.SurfaceCardElevated
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.update.UpdateState
import com.ankiduel.shared.model.DuelStatus

private enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Accueil", Icons.Rounded.SpaceDashboard, Icons.Outlined.SpaceDashboard),
    DUELS("Duels", Icons.Rounded.EmojiEvents, Icons.Outlined.EmojiEvents),
    STATS("Stats", Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    SETTINGS("Paramètres", Icons.Rounded.Tune, Icons.Outlined.Tune)
}

private enum class RootRoute {
    AUTH,
    PERMISSIONS,
    DUEL_DETAIL,
    DASHBOARD,
    DUELS,
    STATS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        val container = (application as AnkiDuelApplication).container
        AppViewModel.Factory(container.repository, container.updateManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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
        viewModel.onResumeUpdateCheck()
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
    val trackingDiagnostics by viewModel.trackingDiagnostics.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val feedback by viewModel.feedbackMessage.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val todayDevices by viewModel.todayDevices.collectAsState()
    val firestoreSyncWarning by viewModel.firestoreSyncWarning.collectAsState()
    val personalStats by viewModel.personalStats.collectAsState()
    val opponentStats by viewModel.opponentStats.collectAsState()
    val selectedOpponentId by viewModel.selectedOpponentId.collectAsState()
    val statsLoading by viewModel.statsLoading.collectAsState()

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

    BackHandler(enabled = showPermissions) {
        showPermissions = false
    }

    BackHandler(enabled = !showPermissions && selectedDuelId != null) {
        selectedDuelId = null
    }

    val currentSession = session
    if (currentSession == null) {
        AmbientGlowBackdrop(accentColor = AnkiBlue, glowAlpha = 0.14f) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AnkiDuelMark(size = 72.dp)
            }
        }
        return
    }

    val isAuthenticated = currentSession.demoMode || !currentSession.userId.isNullOrBlank()
    LaunchedEffect(isAuthenticated) {
        if (!isAuthenticated) {
            selectedDuelId = null
            showPermissions = false
            currentTab = MainTab.DASHBOARD
        }
    }

    val mandatoryPackages = remember(duels) {
        duels
            .filter { it.status == DuelStatus.ACTIVE }
            .flatMap { it.config.mandatorySocialPackages }
            .toSet()
    }
    val hasActiveDuel = remember(duels) {
        duels.any { it.status == DuelStatus.ACTIVE }
    }

    val showBottomBar = isAuthenticated && !showPermissions && selectedDuelId == null

    val activeRoute = when {
        !isAuthenticated -> RootRoute.AUTH
        showPermissions -> RootRoute.PERMISSIONS
        selectedDuelId != null -> RootRoute.DUEL_DETAIL
        currentTab == MainTab.DASHBOARD -> RootRoute.DASHBOARD
        currentTab == MainTab.DUELS -> RootRoute.DUELS
        currentTab == MainTab.STATS -> RootRoute.STATS
        else -> RootRoute.SETTINGS
    }

    LaunchedEffect(activeRoute, isAuthenticated) {
        if (isAuthenticated && activeRoute == RootRoute.STATS) {
            viewModel.loadStats()
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            AnkiDuelSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.then(
                    if (!showBottomBar) Modifier.navigationBarsPadding() else Modifier
                )
            )
        },
        bottomBar = {
            if (showBottomBar) {
                AnkiDuelBottomBar(
                    currentTab = currentTab,
                    hasActiveDebt = lockStatus.isLocked,
                    onSelectTab = { currentTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeRoute,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) +
                        slideInVertically(animationSpec = tween(220)) { it / 32 })
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                },
                label = "rootNavigation"
            ) { route ->
                when (route) {
                    RootRoute.AUTH -> {
                        AuthScreen(
                            isBusy = isBusy,
                            onLogin = viewModel::login,
                            onRegister = viewModel::register,
                            onStartDemo = viewModel::startDemoMode
                        )
                    }

                    RootRoute.PERMISSIONS -> {
                        PermissionsScreen(
                            health = health,
                            diagnostics = trackingDiagnostics,
                            onRefresh = { viewModel.refreshAll() },
                            onContinue = { showPermissions = false }
                        )
                    }

                    RootRoute.DUEL_DETAIL -> {
                        val duel = duels.firstOrNull { it.duelId == selectedDuelId }
                        if (duel == null) {
                            LaunchedEffect(Unit) { selectedDuelId = null }
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

                    RootRoute.DASHBOARD -> {
                        DashboardScreen(
                            username = currentSession.username ?: "Joueur",
                            userId = currentSession.userId.orEmpty(),
                            demoMode = currentSession.demoMode,
                            usage = usage,
                            lockStatus = lockStatus,
                            duels = duels,
                            health = health,
                            isIdleFlagged = isIdleFlagged,
                            todayDevices = todayDevices,
                            onRefresh = { viewModel.refreshAll() },
                            onOpenPermissions = { showPermissions = true },
                            onDuelClick = { selectedDuelId = it },
                            onActivateJoker = { viewModel.activateJoker(it) },
                            onAddDemoAnki = { viewModel.addDemoAnkiMinutes(it) },
                            onAddDemoSocial = { viewModel.addDemoSocialMinutes(it) },
                            onMessage = { viewModel.showFeedback(it) }
                        )
                    }

                    RootRoute.DUELS -> {
                        DuelsScreen(
                            userId = currentSession.userId.orEmpty(),
                            duels = duels,
                            onCreateDuel = { config -> viewModel.createDuel(config) },
                            onJoinDuel = { code -> viewModel.joinDuel(code) },
                            onSelectDuel = { selectedDuelId = it }
                        )
                    }

                    RootRoute.STATS -> {
                        StatsScreen(
                            selfUserId = if (currentSession.demoMode) "local-demo-user" else currentSession.userId.orEmpty(),
                            duels = duels,
                            personalStats = personalStats,
                            opponentStats = opponentStats,
                            selectedOpponentId = selectedOpponentId,
                            isLoading = statsLoading,
                            firestoreSyncWarning = firestoreSyncWarning,
                            onSelectOpponent = { viewModel.selectStatsOpponent(it) },
                            onRefresh = { viewModel.loadStats() }
                        )
                    }

                    RootRoute.SETTINGS -> {
                        AppsSettingsScreen(
                            installedApps = installedApps,
                            trackedPackages = currentSession.trackedSocialPackages,
                            mandatoryPackages = mandatoryPackages,
                            hasActiveDuel = hasActiveDuel,
                            userEmail = currentSession.email,
                            demoMode = currentSession.demoMode,
                            todayDevices = todayDevices,
                            firestoreSyncWarning = firestoreSyncWarning,
                            onTogglePackage = viewModel::toggleSocialPackage,
                            onOpenPermissions = { showPermissions = true },
                            onLogout = {
                                selectedDuelId = null
                                showPermissions = false
                                currentTab = MainTab.DASHBOARD
                                viewModel.logout()
                            }
                        )
                    }
                }
            }

            AppUpdateOverlay(
                updateState = updateState,
                onInstallClick = viewModel::installDownloadedUpdate,
                onDismiss = viewModel::dismissUpdatePrompt,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AppUpdateOverlay(
    updateState: UpdateState,
    onInstallClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (updateState) {
        is UpdateState.Downloading -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCardElevated.copy(alpha = 0.96f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = AnkiBlueSoft,
                        modifier = Modifier.size(18.dp)
                    )
                    val pctText = updateState.progressPercent?.let { " • $it%" } ?: ""
                    Text(
                        text = "Mise à jour v${updateState.versionName} en téléchargement$pctText",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
                val progress = updateState.progressPercent
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AnkiBlueSoft,
                        trackColor = HairlineBorderSubtle
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AnkiBlueSoft,
                        trackColor = HairlineBorderSubtle
                    )
                }
            }
        }

        is UpdateState.ReadyToInstall -> {
            if (updateState.needsInstallPermission) {
                AlertDialog(
                    onDismissRequest = onDismiss,
                    containerColor = SurfaceCardElevated,
                    title = {
                        Text(
                            text = "Mise à jour v${updateState.versionName} prête",
                            color = TextPrimary
                        )
                    },
                    text = {
                        Text(
                            text = "Autorisez AnkiDuel à installer des mises à jour pour appliquer automatiquement cette nouvelle version.",
                            color = TextMuted
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = onInstallClick,
                            colors = ButtonDefaults.buttonColors(containerColor = AnkiBlue)
                        ) {
                            Text("Autoriser et installer")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismiss) {
                            Text("Plus tard", color = TextMuted)
                        }
                    }
                )
            } else {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCardElevated.copy(alpha = 0.96f))
                        .clickable(onClick = onInstallClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = AnkiBlueSoft,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Mise à jour v${updateState.versionName} prête — Appuyer pour installer",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
            }
        }

        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}

@Composable
private fun AnkiDuelBottomBar(
    currentTab: MainTab,
    hasActiveDebt: Boolean,
    onSelectTab: (MainTab) -> Unit
) {
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val contentColor by animateColorAsState(
                    targetValue = if (selected) AnkiBlueSoft else TextMuted,
                    animationSpec = tween(200),
                    label = "tabColor"
                )
                val pillBg by animateColorAsState(
                    targetValue = if (selected) AnkiBlue.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = tween(200),
                    label = "tabPill"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectTab(tab) }
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(pillBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                        if (tab == MainTab.DUELS && hasActiveDebt && !selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 12.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SocialCoral)
                            )
                        }
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = contentColor
                    )
                }
            }
        }
    }
}
