package com.ankiduel.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AnkiBlue = Color(0xFF2979FF)
val SocialCoral = Color(0xFFFF5252)
val SafeEmerald = Color(0xFF00E676)
val AmberJoker = Color(0xFFFFB300)

val DeepBg = Color(0xFF0B111C)
val SurfaceDark = Color(0xFF131C2B)
val CardDark = Color(0xFF1A2538)
val SubtleText = Color(0xFF94A3B8)

private val AnkiDuelColorScheme = darkColorScheme(
    primary = AnkiBlue,
    onPrimary = Color.White,
    secondary = SafeEmerald,
    onSecondary = Color(0xFF002110),
    tertiary = AmberJoker,
    error = SocialCoral,
    background = DeepBg,
    onBackground = Color(0xFFF1F5F9),
    surface = SurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = CardDark,
    onSurfaceVariant = SubtleText
)

@Composable
fun AnkiDuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnkiDuelColorScheme,
        content = content
    )
}
