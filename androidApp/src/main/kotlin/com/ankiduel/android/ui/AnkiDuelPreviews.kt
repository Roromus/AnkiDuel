package com.ankiduel.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ankiduel.android.blocker.LockScreenContent
import com.ankiduel.android.tracking.InstalledAppItem
import com.ankiduel.android.tracking.PermissionHealth
import com.ankiduel.android.ui.screens.AppsSettingsScreen
import com.ankiduel.android.ui.screens.AuthScreen
import com.ankiduel.android.ui.screens.DashboardScreen
import com.ankiduel.android.ui.screens.DuelDetailScreen
import com.ankiduel.android.ui.screens.DuelsScreen
import com.ankiduel.android.ui.screens.PermissionsScreen
import com.ankiduel.android.ui.theme.AnkiDuelTheme
import com.ankiduel.shared.model.DuelConfig
import com.ankiduel.shared.model.DuelCycle
import com.ankiduel.shared.model.DuelState
import com.ankiduel.shared.model.DuelStatus
import com.ankiduel.shared.model.GlobalLockStatus
import com.ankiduel.shared.model.ParticipantState
import com.ankiduel.shared.model.ReferenceMode
import com.ankiduel.shared.model.UsageSnapshot

private val sampleParticipants = listOf(
    ParticipantState(
        userId = "bot-camille",
        username = "Camille",
        ankiEffectiveSeconds = 35 * 60L,
        socialSeconds = 8 * 60L,
        cardsReviewed = 92,
        netScoreSeconds = 27 * 60L,
        debtMinutes = 0,
        ankiApiConnected = true
    ),
    ParticipantState(
        userId = "me",
        username = "Romain",
        ankiEffectiveSeconds = 22 * 60L,
        socialSeconds = 10 * 60L,
        cardsReviewed = 54,
        netScoreSeconds = 12 * 60L,
        debtMinutes = 10,
        jokersRemainingThisWeek = 1,
        ankiApiConnected = true
    ),
    ParticipantState(
        userId = "bot-lucas",
        username = "Lucas",
        ankiEffectiveSeconds = 14 * 60L,
        socialSeconds = 18 * 60L,
        cardsReviewed = 28,
        netScoreSeconds = -4 * 60L,
        debtMinutes = 26,
        ankiApiConnected = true
    )
)

private val sampleDuels = listOf(
    DuelState(
        duelId = "duel-1",
        inviteCode = "ANKI-7F3K",
        creatorId = "bot-camille",
        config = DuelConfig(
            title = "Duel Médecine 1v1",
            maxPlayers = 2,
            referenceMode = ReferenceMode.LEADER,
            cycle = DuelCycle.DAILY_MIDNIGHT,
            toleranceMinutes = 5,
            mandatorySocialPackages = setOf("com.instagram.android")
        ),
        status = DuelStatus.ACTIVE,
        cycleKey = "2026-11-19",
        referenceScoreSeconds = 27 * 60L,
        participants = sampleParticipants.take(2),
        createdAtEpochMs = 0L
    ),
    DuelState(
        duelId = "duel-2",
        inviteCode = "ANKI-GRPE",
        creatorId = "me",
        config = DuelConfig(
            title = "Sprint Japonais",
            maxPlayers = 5,
            referenceMode = ReferenceMode.AVERAGE,
            cycle = DuelCycle.DAILY_MIDNIGHT,
            toleranceMinutes = 10
        ),
        status = DuelStatus.ACTIVE,
        cycleKey = "2026-11-19",
        referenceScoreSeconds = 12 * 60L,
        participants = sampleParticipants,
        createdAtEpochMs = 0L
    )
)

private val sampleUsage = UsageSnapshot(
    ankiForegroundSeconds = 25 * 60L,
    ankiEffectiveSeconds = 25 * 60L,
    cardsReviewed = 64,
    socialForegroundSeconds = 10 * 60L,
    socialBreakdownSeconds = mapOf(
        "com.instagram.android" to 6 * 60L,
        "com.zhiliaoapp.musically" to 3 * 60L,
        "com.google.android.youtube" to 1 * 60L
    ),
    trackedSocialPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically", "com.google.android.youtube"),
    ankiApiConnected = true,
    capturedAtEpochMs = 0L
)

private val sampleHealth = PermissionHealth(
    usageAccessGranted = true,
    accessibilityGranted = true,
    overlayGranted = true,
    notificationsGranted = true,
    ankiDroidInstalled = true,
    ankiDroidApiGranted = false
)

@Preview(name = "Auth", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun AuthScreenPreview() {
    AnkiDuelTheme {
        AuthScreen(isBusy = false, onLogin = { _, _ -> }, onRegister = { _, _, _ -> }, onStartDemo = {})
    }
}

@Preview(name = "Dashboard Unlocked", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun DashboardUnlockedPreview() {
    AnkiDuelTheme {
        DashboardScreen(
            username = "Romain",
            userId = "me",
            demoMode = false,
            usage = sampleUsage,
            lockStatus = GlobalLockStatus(false, 0L, 0, null, null, null, 0L),
            duels = sampleDuels,
            health = sampleHealth,
            isIdleFlagged = false,
            onRefresh = {},
            onOpenPermissions = {},
            onDuelClick = {},
            onActivateJoker = {},
            onAddDemoAnki = {},
            onAddDemoSocial = {}
        )
    }
}

@Preview(name = "Dashboard Locked", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun DashboardLockedPreview() {
    AnkiDuelTheme {
        DashboardScreen(
            username = "Romain",
            userId = "me",
            demoMode = true,
            usage = sampleUsage,
            lockStatus = GlobalLockStatus(
                isLocked = true,
                maxDebtSeconds = 15 * 60L,
                maxDebtMinutes = 15,
                blockingDuelId = "duel-1",
                blockingDuelTitle = "Duel Médecine 1v1",
                leaderUsername = "Camille",
                jokerActiveUntilEpochMs = 0L
            ),
            duels = sampleDuels,
            health = sampleHealth,
            isIdleFlagged = false,
            onRefresh = {},
            onOpenPermissions = {},
            onDuelClick = {},
            onActivateJoker = {},
            onAddDemoAnki = {},
            onAddDemoSocial = {}
        )
    }
}

@Preview(name = "Duels Arena", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun DuelsScreenPreview() {
    AnkiDuelTheme {
        DuelsScreen(
            userId = "me",
            duels = sampleDuels,
            onCreateDuel = {},
            onJoinDuel = {},
            onSelectDuel = {}
        )
    }
}

@Preview(name = "Duel Detail", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun DuelDetailScreenPreview() {
    AnkiDuelTheme {
        DuelDetailScreen(
            duel = sampleDuels.last(),
            userId = "me",
            onBack = {},
            onActivateJoker = {},
            onForfeit = {}
        )
    }
}

@Preview(name = "Settings", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun AppsSettingsPreview() {
    AnkiDuelTheme {
        AppsSettingsScreen(
            installedApps = listOf(
                InstalledAppItem("com.instagram.android", "Instagram", true),
                InstalledAppItem("com.zhiliaoapp.musically", "TikTok", true),
                InstalledAppItem("com.google.android.youtube", "YouTube", true)
            ),
            trackedPackages = setOf("com.instagram.android", "com.zhiliaoapp.musically"),
            mandatoryPackages = setOf("com.instagram.android"),
            hasActiveDuel = false,
            userEmail = "romain@ankiduel.app",
            demoMode = false,
            onTogglePackage = {},
            onOpenPermissions = {},
            onLogout = {}
        )
    }
}

@Preview(name = "Permissions", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun PermissionsPreview() {
    AnkiDuelTheme {
        PermissionsScreen(
            health = sampleHealth.copy(overlayGranted = false),
            onRefresh = {},
            onContinue = {}
        )
    }
}

@Preview(name = "Lock Overlay", showSystemUi = true, backgroundColor = 0xFF070A10)
@Composable
fun LockOverlayPreview() {
    AnkiDuelTheme {
        LockScreenContent(
            blockedAppName = "Instagram",
            debtMinutes = 15,
            duelTitle = "Duel Médecine 1v1",
            leaderName = "Camille",
            canUseJoker = true,
            onOpenAnkiDroid = {},
            onActivateJoker = {},
            onGoHome = {}
        )
    }
}
