package com.ankiduel.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankiduel.android.ui.theme.AmberJoker
import com.ankiduel.android.ui.theme.AmberJokerSoft
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.AnkiBlueSoft
import com.ankiduel.android.ui.theme.HairlineBorder
import com.ankiduel.android.ui.theme.HairlineBorderStrong
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.ObsidianBg
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SocialCoral
import com.ankiduel.android.ui.theme.SocialCoralSoft
import com.ankiduel.android.ui.theme.SurfaceBase
import com.ankiduel.android.ui.theme.SurfaceCard
import com.ankiduel.android.ui.theme.SurfaceCardElevated
import com.ankiduel.android.ui.theme.TabularFeatures
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary
import kotlin.math.abs

/**
 * Deep obsidian backdrop with a delicate, smoothly animated ambient radial bloom at the top edge.
 */
@Composable
fun AmbientGlowBackdrop(
    accentColor: Color,
    modifier: Modifier = Modifier,
    glowAlpha: Float = 0.14f,
    content: @Composable BoxScope.() -> Unit
) {
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 650),
        label = "ambientAccent"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.width * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedAccent.copy(alpha = glowAlpha),
                        animatedAccent.copy(alpha = glowAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.5f, -size.width * 0.18f),
                    radius = radius
                ),
                radius = radius,
                center = Offset(size.width * 0.5f, -size.width * 0.18f)
            )
        }
        content()
    }
}

/**
 * Refined obsidian surface with a subtle directional 1dp hairline border and optional click ripple.
 */
@Composable
fun ObsidianCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = SurfaceCard,
    borderTint: Color = Color.White,
    borderAlpha: Float = 0.09f,
    accentGradientColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = Brush.verticalGradient(
        colors = listOf(
            borderTint.copy(alpha = (borderAlpha * 1.45f).coerceAtMost(0.45f)),
            borderTint.copy(alpha = borderAlpha * 0.45f)
        )
    )

    val cardModifier = modifier
        .clip(shape)
        .background(containerColor)
        .then(
            if (accentGradientColor != null) {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentGradientColor.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
            } else {
                Modifier
            }
        )
        .border(BorderStroke(1.dp, borderBrush), shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Color.White.copy(alpha = 0.12f)),
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Column(
        modifier = cardModifier.padding(contentPadding),
        content = content
    )
}

/**
 * Compact semantic status pill with subtle tinted background and hairline border.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    showDot: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TabularFeatures
            ),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Clean section header with optional trailing element.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        trailing?.invoke()
    }
}

/**
 * Circular hairline-bordered icon button for minimal top bars.
 */
@Composable
fun ObsidianIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    containerColor: Color = SurfaceCard
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, HairlineBorder, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = Color.White.copy(alpha = 0.16f)),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}

/**
 * Signature proportional AnkiDroid vs Réseaux Sociaux balance gauge with smooth spring motion.
 */
@Composable
fun DuelBalanceBar(
    ankiMinutes: Int,
    socialMinutes: Int,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp
) {
    val isEmpty = ankiMinutes <= 0 && socialMinutes <= 0
    val onlyAnki = ankiMinutes > 0 && socialMinutes <= 0
    val onlySocial = socialMinutes > 0 && ankiMinutes <= 0
    val targetFraction = when {
        isEmpty -> 0.5f
        onlyAnki -> 1f
        onlySocial -> 0f
        else -> {
            val total = (ankiMinutes + socialMinutes).coerceAtLeast(1).toFloat()
            (ankiMinutes.toFloat() / total).coerceIn(0.08f, 0.92f)
        }
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessLow
        ),
        label = "balanceBarFraction"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
    ) {
        val corner = CornerRadius(size.height / 2f, size.height / 2f)

        // Base recessed track
        drawRoundRect(
            color = Color(0xFF1A2233),
            cornerRadius = corner
        )

        if (isEmpty) {
            // Subtle equilibrium tick in empty state
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(size.width / 2f, size.height * 0.2f),
                end = Offset(size.width / 2f, size.height * 0.8f),
                strokeWidth = 1.5.dp.toPx()
            )
            return@Canvas
        }

        val hasBoth = animatedFraction in 0.01f..0.99f
        val gap = if (hasBoth) 3.dp.toPx() else 0f

        // Left: AnkiDroid cobalt gradient
        if (animatedFraction > 0.01f) {
            val leftWidth = if (!hasBoth) {
                size.width
            } else {
                ((size.width - gap) * animatedFraction).coerceAtLeast(size.height)
            }
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF2563EB), AnkiBlue, AnkiBlueSoft),
                    startX = 0f,
                    endX = leftWidth
                ),
                topLeft = Offset.Zero,
                size = Size(leftWidth, size.height),
                cornerRadius = corner
            )
        }

        // Right: Social rose-coral gradient
        if (animatedFraction < 0.99f) {
            val rightStart = if (!hasBoth) {
                0f
            } else {
                val leftWidth = ((size.width - gap) * animatedFraction).coerceAtLeast(size.height)
                (leftWidth + gap).coerceAtMost(size.width - size.height)
            }
            val rightWidth = (size.width - rightStart).coerceAtLeast(0f)
            if (rightWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(SocialCoralSoft, SocialCoral, Color(0xFFE11D48)),
                        startX = rightStart,
                        endX = size.width
                    ),
                    topLeft = Offset(rightStart, 0f),
                    size = Size(rightWidth, size.height),
                    cornerRadius = corner
                )
            }
        }

        // Subtle top specular sheen
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.55f
            ),
            cornerRadius = corner
        )

        // Hairline 50% equilibrium marker
        drawLine(
            color = Color.White.copy(alpha = 0.35f),
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/**
 * Sculptural geometric brand emblem matching the app icon.
 */
@Composable
fun AnkiDuelMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        // Soft outer radial halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    AnkiBlue.copy(alpha = 0.28f),
                    AnkiBlue.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = w * 0.56f
            ),
            radius = w * 0.56f,
            center = Offset(cx, cy)
        )

        // Shield geometry
        val shield = Path().apply {
            moveTo(cx, h * 0.10f)
            lineTo(w * 0.83f, h * 0.23f)
            lineTo(w * 0.83f, h * 0.51f)
            cubicTo(w * 0.83f, h * 0.72f, w * 0.68f, h * 0.85f, cx, h * 0.91f)
            cubicTo(w * 0.32f, h * 0.85f, w * 0.17f, h * 0.72f, w * 0.17f, h * 0.51f)
            lineTo(w * 0.17f, h * 0.23f)
            close()
        }

        drawPath(
            path = shield,
            brush = Brush.linearGradient(
                colors = listOf(AnkiBlueSoft, AnkiBlue, Color(0xFF1D4ED8)),
                start = Offset(w * 0.2f, h * 0.1f),
                end = Offset(w * 0.8f, h * 0.9f)
            )
        )

        drawPath(
            path = shield,
            color = Color.White.copy(alpha = 0.28f),
            style = Stroke(width = w * 0.022f)
        )

        // Luminous 4-point focus spark inside shield
        val sy = h * 0.48f
        val sr = w * 0.19f
        val inner = sr * 0.24f
        val spark = Path().apply {
            moveTo(cx, sy - sr)
            quadraticTo(cx + inner, sy - inner, cx + sr, sy)
            quadraticTo(cx + inner, sy + inner, cx, sy + sr)
            quadraticTo(cx - inner, sy + inner, cx - sr, sy)
            quadraticTo(cx - inner, sy - inner, cx, sy - sr)
            close()
        }
        drawPath(path = spark, color = Color(0xFFF8FAFC))
    }
}

/**
 * Minimalist monogram avatar with subtle deterministic hue or podium rank accent.
 */
@Composable
fun PlayerAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    rank: Int? = null
) {
    val palette = listOf(
        AnkiBlueSoft,
        SafeEmerald,
        Color(0xFF818CF8), // Indigo
        Color(0xFF38BDF8), // Sky
        AmberJokerSoft,
        Color(0xFFA78BFA)  // Violet
    )
    val accent = when (rank) {
        1 -> AmberJokerSoft
        2 -> Color(0xFFCBD5E1)
        3 -> Color(0xFFFB923C)
        else -> palette[abs(name.hashCode()) % palette.size]
    }

    val initials = remember(name) {
        val cleaned = name.trim().replace("(Bot)", "", ignoreCase = true).trim()
        val parts = cleaned.split(" ").filter { it.isNotBlank() }
        when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            cleaned.length >= 2 -> cleaned.take(2).uppercase()
            cleaned.isNotEmpty() -> cleaned.first().uppercase()
            else -> "?"
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.38f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.34f).sp
            ),
            color = accent
        )
    }
}

/**
 * Clean recessed segmented toggle control.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceBase)
            .border(1.dp, HairlineBorderSubtle, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) SurfaceCardElevated else Color.Transparent,
                animationSpec = tween(200),
                label = "segBg"
            )
            val border by animateColorAsState(
                targetValue = if (selected) HairlineBorderStrong else Color.Transparent,
                animationSpec = tween(200),
                label = "segBorder"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) TextPrimary else TextSecondary,
                animationSpec = tween(200),
                label = "segText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Unified obsidian text field with refined borders and comfortable spacing.
 */
@Composable
fun ObsidianTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = placeholder?.let {
            { Text(it, style = MaterialTheme.typography.bodyMedium, color = TextMuted) }
        },
        leadingIcon = leadingIcon?.let {
            {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AnkiBlue,
            unfocusedBorderColor = HairlineBorder,
            focusedContainerColor = SurfaceBase,
            unfocusedContainerColor = SurfaceBase,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = AnkiBlueSoft,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AnkiBlueSoft
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Subtle floating obsidian toast snackbar host.
 */
@Composable
fun AnkiDuelSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    ) { data ->
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCardElevated,
            border = BorderStroke(1.dp, HairlineBorderStrong),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AnkiBlueSoft)
                )
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Formats minutes into clean compact French duration (e.g. "1h 15m" or "25 min").
 */
fun formatCompactMinutes(minutes: Int, showSign: Boolean = false): String {
    val sign = when {
        showSign && minutes > 0 -> "+"
        minutes < 0 -> "-"
        else -> ""
    }
    val absMin = abs(minutes)
    return if (absMin >= 60) {
        val h = absMin / 60
        val m = absMin % 60
        if (m == 0) "$sign${h}h" else "$sign${h}h ${m}m"
    } else {
        "$sign$absMin min"
    }
}
