package com.ankiduel.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ankiduel.android.ui.components.AmbientGlowBackdrop
import com.ankiduel.android.ui.components.AnkiDuelMark
import com.ankiduel.android.ui.components.ObsidianCard
import com.ankiduel.android.ui.components.ObsidianTextField
import com.ankiduel.android.ui.components.SegmentedControl
import com.ankiduel.android.ui.theme.AnkiBlue
import com.ankiduel.android.ui.theme.HairlineBorderSubtle
import com.ankiduel.android.ui.theme.SafeEmerald
import com.ankiduel.android.ui.theme.SurfaceCard
import com.ankiduel.android.ui.theme.TextMuted
import com.ankiduel.android.ui.theme.TextPrimary
import com.ankiduel.android.ui.theme.TextSecondary

@Composable
fun AuthScreen(
    isBusy: Boolean,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, username: String, password: String) -> Unit,
    onStartDemo: () -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val canSubmit = !isBusy &&
        email.isNotBlank() &&
        password.length >= 6 &&
        (!isRegisterMode || username.trim().length in 3..24)

    fun submit() {
        if (!canSubmit) return
        focusManager.clearFocus()
        if (isRegisterMode) onRegister(email.trim(), username.trim(), password)
        else onLogin(email.trim(), password)
    }

    AmbientGlowBackdrop(accentColor = AnkiBlue, glowAlpha = 0.16f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            AnkiDuelMark(size = 72.dp)

            Spacer(Modifier.height(16.dp))

            Text(
                text = "AnkiDuel",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Révisez sur AnkiDroid. Gagnez votre temps d'écran.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(30.dp))

            ObsidianCard(
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedControl(
                    options = listOf("Connexion", "Inscription"),
                    selectedIndex = if (isRegisterMode) 1 else 0,
                    onSelect = { isRegisterMode = it == 1 }
                )

                Spacer(Modifier.height(18.dp))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ObsidianTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Adresse email",
                        leadingIcon = Icons.Outlined.Email,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                    )

                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        ObsidianTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = "Pseudo (3–24 car.)",
                            leadingIcon = Icons.Outlined.Person,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            )
                        )
                    }

                    ObsidianTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Mot de passe (6+ car.)",
                        leadingIcon = Icons.Outlined.Lock,
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passwordVisible) "Masquer" else "Afficher",
                                    tint = TextMuted,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { submit() },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnkiBlue,
                        contentColor = Color.White,
                        disabledContainerColor = AnkiBlue.copy(alpha = 0.28f),
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = if (isRegisterMode) "Créer mon compte" else "Se connecter",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = HairlineBorderSubtle
                )
                Text(
                    text = "ou",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = HairlineBorderSubtle
                )
            }

            Spacer(Modifier.height(18.dp))

            ObsidianCard(
                shape = RoundedCornerShape(18.dp),
                containerColor = SurfaceCard,
                borderTint = SafeEmerald,
                borderAlpha = 0.22f,
                accentGradientColor = SafeEmerald,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                onClick = if (isBusy) null else onStartDemo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SafeEmerald.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = SafeEmerald,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Essayer en Mode Démo",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Testez avec des bots locaux, sans compte",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = SafeEmerald.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
