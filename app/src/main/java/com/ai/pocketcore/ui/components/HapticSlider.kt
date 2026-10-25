package com.ai.pocketcore.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CharcoalSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Pure calculation logic for discrete haptic ticks on slider progression.
 */
object HapticStepCalculator {
    fun calculateStep(
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        increment: Float? = null
    ): Int {
        if (range.endInclusive <= range.start) return 0

        if (increment != null && increment > 0f) {
            val clamped = value.coerceIn(range.start, range.endInclusive)
            return ((clamped - range.start) / increment).roundToInt()
        }

        if (steps > 0) {
            val totalIntervals = steps + 1
            val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            return (fraction * totalIntervals).roundToInt()
        }

        return 0
    }
}

/**
 * Samsung-style slider that delivers tactile haptic tick feedback
 * ([HapticFeedbackConstants.CLOCK_TICK]) on discrete step transitions.
 */
@Composable
fun HapticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    stepIncrement: Float? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        thumbColor = ElectricCobalt,
        activeTrackColor = ElectricCobalt,
        inactiveTrackColor = CardSurface,
        activeTickColor = CyberCyan,
        inactiveTickColor = CardBorder,
        disabledThumbColor = TextMuted,
        disabledActiveTrackColor = CharcoalSurface,
        disabledInactiveTrackColor = CardSurface
    ),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hapticFeedbackProvider: (() -> Unit)? = null
) {
    val view = LocalView.current
    val performHaptic = hapticFeedbackProvider ?: {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } catch (_: Throwable) {
            // Safe fallback for previews or headless unit tests
        }
    }

    var lastHapticStep by remember(valueRange, steps, stepIncrement) {
        mutableIntStateOf(HapticStepCalculator.calculateStep(value, valueRange, steps, stepIncrement))
    }

    Slider(
        value = value,
        onValueChange = { newValue ->
            val newStep = HapticStepCalculator.calculateStep(newValue, valueRange, steps, stepIncrement)
            if (newStep != lastHapticStep) {
                lastHapticStep = newStep
                performHaptic()
            }
            onValueChange(newValue)
        },
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource
    )
}

/**
 * Labeled parameter slider adhering to Samsung One UI Pro ergonomics.
 */
@Composable
fun LabeledHapticSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueDisplay: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    stepIncrement: Float? = null,
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = OneUiTokens.PaddingSmall)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = valueDisplay,
                style = TelemetryMono,
                color = CyberCyan
            )
        }

        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = OneUiTokens.PaddingExtraSmall)
            )
        }

        HapticSlider(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            stepIncrement = stepIncrement,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}
