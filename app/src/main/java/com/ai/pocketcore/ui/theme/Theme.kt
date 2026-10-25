package com.ai.pocketcore.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val OneUiDarkColorScheme = darkColorScheme(
    primary = ElectricCobalt,
    onPrimary = TextPrimary,
    primaryContainer = CobaltDark,
    onPrimaryContainer = TextPrimary,
    secondary = CyberCyan,
    onSecondary = PitchBlack,
    secondaryContainer = CyanDark,
    onSecondaryContainer = TextPrimary,
    tertiary = SuccessGreen,
    onTertiary = PitchBlack,
    background = PitchBlack,
    onBackground = TextPrimary,
    surface = CharcoalSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = Color(0xFF3A3A3C),
    error = ErrorRed,
    onError = TextPrimary
)

val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(OneUiTokens.PaddingExtraSmall),
    small = RoundedCornerShape(OneUiTokens.SmallCornerRadius),
    medium = RoundedCornerShape(OneUiTokens.CardCornerRadius),
    large = RoundedCornerShape(OneUiTokens.CardCornerRadius),
    extraLarge = RoundedCornerShape(OneUiTokens.PillCornerRadius)
)

/**
 * PocketCore One UI Pro Theme wrapper.
 * Enforces true AMOLED pitch black surfaces and high-contrast hardware accents.
 */
@Composable
fun OneUiTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = PitchBlack.toArgb()
                window.navigationBarColor = PitchBlack.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = OneUiDarkColorScheme,
        typography = OneUiTypography,
        shapes = OneUiShapes,
        content = content
    )
}
