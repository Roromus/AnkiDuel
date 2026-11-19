package com.ankiduel.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Obsidian Studio Surface Hierarchy ─────────────────────────────────────────
val ObsidianBg = Color(0xFF070A10)
val SurfaceBase = Color(0xFF0D121B)
val SurfaceCard = Color(0xFF121824)
val SurfaceCardElevated = Color(0xFF172030)
val SurfaceOverlay = Color(0xFF1C2638)

val HairlineBorder = Color(0x16FFFFFF)       // ~8.5% white
val HairlineBorderSubtle = Color(0x0DFFFFFF) // ~5% white
val HairlineBorderStrong = Color(0x26FFFFFF) // ~15% white

// ── Typography Ink Tokens ─────────────────────────────────────────────────────
val TextPrimary = Color(0xFFF3F6FA)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// ── Refined Semantic Accents ──────────────────────────────────────────────────
val AnkiBlue = Color(0xFF3B82F6)
val AnkiBlueSoft = Color(0xFF60A5FA)
val AnkiBlueSurface = Color(0x1F3B82F6)

val SocialCoral = Color(0xFFF43F5E)
val SocialCoralSoft = Color(0xFFFB7185)
val SocialCoralSurface = Color(0x1FF43F5E)

val SafeEmerald = Color(0xFF10B981)
val SafeEmeraldSoft = Color(0xFF34D399)
val SafeEmeraldSurface = Color(0x1F10B981)

val AmberJoker = Color(0xFFF59E0B)
val AmberJokerSoft = Color(0xFFFBBF24)
val AmberJokerSurface = Color(0x1FF59E0B)

// Backwards-compatible aliases
val DeepBg = ObsidianBg
val SurfaceDark = SurfaceBase
val CardDark = SurfaceCard
val SubtleText = TextSecondary

val TabularFeatures = "tnum"

private val AnkiDuelColorScheme = darkColorScheme(
    primary = AnkiBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF172C4D),
    onPrimaryContainer = AnkiBlueSoft,
    secondary = SafeEmerald,
    onSecondary = Color(0xFF032217),
    secondaryContainer = Color(0xFF0D2E23),
    onSecondaryContainer = SafeEmeraldSoft,
    tertiary = AmberJoker,
    onTertiary = Color(0xFF2B1B03),
    tertiaryContainer = Color(0xFF33240B),
    onTertiaryContainer = AmberJokerSoft,
    error = SocialCoral,
    onError = Color.White,
    errorContainer = Color(0xFF3A131E),
    onErrorContainer = SocialCoralSoft,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SurfaceBase,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceCardElevated,
    surfaceContainerHighest = SurfaceOverlay,
    outline = HairlineBorderStrong,
    outlineVariant = HairlineBorder
)

val AnkiDuelTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.0).sp,
        fontFeatureSettings = TabularFeatures
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.75).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.35).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.15).sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)

val AnkiDuelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AnkiDuelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnkiDuelColorScheme,
        typography = AnkiDuelTypography,
        shapes = AnkiDuelShapes,
        content = content
    )
}
