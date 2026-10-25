package com.ai.pocketcore.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ai.pocketcore.ui.components.HapticStepCalculator
import com.ai.pocketcore.ui.components.TelemetryFormatter
import com.ai.pocketcore.ui.components.TelemetryState
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CharcoalSurface
import com.ai.pocketcore.ui.theme.CodeBlockMono
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiDarkColorScheme
import com.ai.pocketcore.ui.theme.OneUiShapes
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.PitchBlack
import com.ai.pocketcore.ui.theme.TelemetryMono
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun amoledTokensArePitchBlack() {
        assertEquals(0xFF000000.toLong(), OneUiTokens.AmoledBackground.value.toLong())
        assertEquals(24, OneUiTokens.CardCornerRadiusDp)
    }

    @Test
    fun verifyOneUiColorPaletteValues() {
        assertEquals(PitchBlack, Color(0xFF000000))
        assertEquals(CharcoalSurface, Color(0xFF121214))
        assertEquals(CardSurface, Color(0xFF1C1C1E))
        assertEquals(CardBorder, Color(0xFF2C2C2E))
        assertEquals(ElectricCobalt, Color(0xFF0D6EFD))
        assertEquals(CyberCyan, Color(0xFF00F2FE))
    }

    @Test
    fun verifyOneUiThemeColorSchemeTokens() {
        assertEquals(ElectricCobalt, OneUiDarkColorScheme.primary)
        assertEquals(CyberCyan, OneUiDarkColorScheme.secondary)
        assertEquals(PitchBlack, OneUiDarkColorScheme.background)
        assertEquals(CharcoalSurface, OneUiDarkColorScheme.surface)
        assertEquals(CardSurface, OneUiDarkColorScheme.surfaceVariant)
        assertEquals(CardBorder, OneUiDarkColorScheme.outline)
    }

    @Test
    fun verifyDimensionTokensAndErgonomics() {
        assertEquals(24.dp, OneUiTokens.CardCornerRadius)
        assertEquals(999, OneUiTokens.PillCornerRadiusDp)
        assertEquals(999.dp, OneUiTokens.PillCornerRadius)
        assertEquals(48.dp, OneUiTokens.MinTouchTarget)
        assertEquals(8.dp, OneUiTokens.PaddingSmall)
        assertEquals(16.dp, OneUiTokens.PaddingMedium)
        assertEquals(24.dp, OneUiTokens.PaddingLarge)
    }

    @Test
    fun verifyTypographyMonospaceForHardwareTelemetry() {
        assertEquals(FontFamily.Monospace, TelemetryMono.fontFamily)
        assertEquals(FontFamily.Monospace, CodeBlockMono.fontFamily)
    }

    @Test
    fun verifyHapticStepCalculatorStepTransitions() {
        val range = 0f..100f
        val steps = 4 // 5 intervals: 0, 20, 40, 60, 80, 100

        assertEquals(0, HapticStepCalculator.calculateStep(0f, range, steps))
        assertEquals(1, HapticStepCalculator.calculateStep(20f, range, steps))
        assertEquals(2, HapticStepCalculator.calculateStep(40f, range, steps))
        assertEquals(3, HapticStepCalculator.calculateStep(60f, range, steps))
        assertEquals(4, HapticStepCalculator.calculateStep(80f, range, steps))
        assertEquals(5, HapticStepCalculator.calculateStep(100f, range, steps))

        // Clamping edge cases
        assertEquals(0, HapticStepCalculator.calculateStep(-10f, range, steps))
        assertEquals(5, HapticStepCalculator.calculateStep(150f, range, steps))
    }

    @Test
    fun verifyHapticStepCalculatorIncrementTransitions() {
        val range = 0.0f..2.0f
        val increment = 0.1f

        assertEquals(0, HapticStepCalculator.calculateStep(0.0f, range, steps = 0, increment = increment))
        assertEquals(7, HapticStepCalculator.calculateStep(0.72f, range, steps = 0, increment = increment))
        assertEquals(10, HapticStepCalculator.calculateStep(1.0f, range, steps = 0, increment = increment))
        assertEquals(20, HapticStepCalculator.calculateStep(2.0f, range, steps = 0, increment = increment))
    }

    @Test
    fun verifyTelemetryFormatterOutputs() {
        assertEquals("45.2 tok/s", TelemetryFormatter.formatTokPerSec(45.24))
        assertEquals("0.0 tok/s", TelemetryFormatter.formatTokPerSec(0.0))
        assertEquals("125ms", TelemetryFormatter.formatTtft(125L))
        assertEquals("-- ms", TelemetryFormatter.formatTtft(0L))
        assertEquals("5.4 GB", TelemetryFormatter.formatRam(5.42))

        val fourGbInBytes = 4L * 1024L * 1024L * 1024L
        assertEquals("4.0 GB", TelemetryFormatter.formatRam(fourGbInBytes))
    }

    @Test
    fun verifyTelemetryStateByteConversion() {
        val bytes = (6.5 * 1024 * 1024 * 1024).toLong()
        val state = TelemetryState(
            tokensPerSec = 52.8,
            ttftMs = 110L,
            ramUsageBytes = bytes,
            gpuBackend = "Vulkan",
            isGenerating = true
        )

        assertEquals(52.8, state.tokensPerSec, 0.001)
        assertEquals(110L, state.ttftMs)
        assertEquals(6.5, state.ramUsageGb, 0.01)
        assertEquals("Vulkan", state.gpuBackend)
        assertTrue(state.isGenerating)
    }
}
