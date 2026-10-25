package com.ai.pocketcore.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// One UI Pro Colors
val PitchBlack = Color(0xFF000000)
val AmoledBlack = Color(0xFF000000)
val CharcoalSurface = Color(0xFF121214)
val CardSurface = Color(0xFF1C1C1E)
val CardBorder = Color(0xFF2C2C2E)
val ElectricCobalt = Color(0xFF0D6EFD)
val CyberCyan = Color(0xFF00F2FE)
val CobaltDark = Color(0xFF003882)
val CyanDark = Color(0xFF004D53)

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val TextMuted = Color(0xFF636366)

// Status & Accents
val SuccessGreen = Color(0xFF30D158)
val WarningOrange = Color(0xFFFF9F0A)
val ErrorRed = Color(0xFFFF453A)
val VulkanGreen = Color(0xFF00E676)

// Capsule HUD
val CapsuleBackground = Color(0xE6121214)
val CapsuleBorder = Color(0x4000F2FE)

/**
 * Design system tokens for Samsung One UI Pro AMOLED experience.
 */
object OneUiTokens {
    /**
     * Amoled pitch black token for OLED panels (0% backlight power).
     * Note: ULong representation is 0xFF000000UL matching the 32-bit ARGB constant.
     */
    val AmoledBackground = Color(0xFF000000UL)
    val PitchBlackColor = PitchBlack
    val CharcoalSurfaceColor = CharcoalSurface
    val CardSurfaceColor = CardSurface
    val CardBorderColor = CardBorder
    val ElectricCobaltColor = ElectricCobalt
    val CyberCyanColor = CyberCyan
    val TextPrimaryColor = TextPrimary
    val TextSecondaryColor = TextSecondary
    val TextMutedColor = TextMuted

    // Corner Radiuses
    const val CardCornerRadiusDp: Int = 24
    val CardCornerRadius: Dp = 24.dp

    const val PillCornerRadiusDp: Int = 999
    val PillCornerRadius: Dp = 999.dp

    val SmallCornerRadius: Dp = 12.dp

    // Spacing & Padding
    val PaddingExtraSmall: Dp = 4.dp
    val PaddingSmall: Dp = 8.dp
    val PaddingMedium: Dp = 16.dp
    val PaddingLarge: Dp = 24.dp
    val PaddingXLarge: Dp = 32.dp

    // Touch Targets
    val MinTouchTarget: Dp = 48.dp
}
