package com.ai.pocketcore.ui.studio.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.domain.model.InferenceParams
import com.ai.pocketcore.ui.components.HapticSlider
import com.ai.pocketcore.ui.studio.StudioViewModel
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CharcoalSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.PitchBlack
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Samsung One UI pull-up bottom sheet for fine-tuning on-device inference parameters:
 * - Temperature & Top-P sampling dials with tactile haptic steps
 * - Context Length KV cache allocation
 * - Vulkan GPU Layer Offload dial
 * - CPU Threads compute allocation
 * - System prompt preset selector and custom text editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProParametersSheet(
    params: InferenceParams,
    systemPrompt: String,
    onParamsChange: (InferenceParams) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = CharcoalSurface,
        scrimColor = PitchBlack.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = OneUiTokens.CardCornerRadius, topEnd = OneUiTokens.CardCornerRadius),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardBorder)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Parameters",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Inference Parameters",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                TextButton(
                    onClick = {
                        onParamsChange(InferenceParams.DEFAULT)
                        onSystemPromptChange(StudioViewModel.DEFAULT_SYSTEM_PROMPT)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ElectricCobalt)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reset", style = TelemetryMono.copy(fontSize = 12.sp))
                }
            }

            // Temperature Slider
            ParameterControl(
                title = "Temperature",
                valueText = String.format(Locale.US, "%.2f", params.temperature),
                description = "Controls randomness (0.0 = deterministic, 1.0+ = creative)"
            ) {
                HapticSlider(
                    value = params.temperature,
                    onValueChange = { onParamsChange(params.copy(temperature = it)) },
                    valueRange = 0.0f..2.0f,
                    stepIncrement = 0.05f
                )
            }

            // Top-P Nucleus Sampling Slider
            ParameterControl(
                title = "Top-P (Nucleus)",
                valueText = String.format(Locale.US, "%.2f", params.topP),
                description = "Limits token choices to top probability mass"
            ) {
                HapticSlider(
                    value = params.topP,
                    onValueChange = { onParamsChange(params.copy(topP = it)) },
                    valueRange = 0.05f..1.0f,
                    stepIncrement = 0.05f
                )
            }

            // Context Window Length
            ParameterControl(
                title = "Context Window",
                valueText = "${params.contextLength} tokens",
                description = "Active KV cache memory pre-allocated in RAM"
            ) {
                HapticSlider(
                    value = params.contextLength.toFloat(),
                    onValueChange = { onParamsChange(params.copy(contextLength = it.roundToInt())) },
                    valueRange = 512f..8192f,
                    stepIncrement = 512f
                )
            }

            // Vulkan GPU Layer Offload
            ParameterControl(
                title = "Vulkan GPU Offload",
                valueText = "${params.gpuLayers} layers",
                description = "Transformer layers offloaded to Adreno 830 GPU"
            ) {
                HapticSlider(
                    value = params.gpuLayers.toFloat(),
                    onValueChange = { onParamsChange(params.copy(gpuLayers = it.roundToInt())) },
                    valueRange = 0f..64f,
                    stepIncrement = 1f
                )
            }

            // CPU Thread Count
            ParameterControl(
                title = "CPU Threads",
                valueText = "${params.threads} threads",
                description = "Oryon performance core compute threads"
            ) {
                HapticSlider(
                    value = params.threads.toFloat(),
                    onValueChange = { onParamsChange(params.copy(threads = it.roundToInt())) },
                    valueRange = 1f..8f,
                    stepIncrement = 1f
                )
            }

            // System Prompt Presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "System Prompt Presets",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioViewModel.SYSTEM_PROMPT_PRESETS.forEach { preset ->
                        val isSelected = systemPrompt == preset.prompt
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSystemPromptChange(preset.prompt) },
                            label = {
                                Text(
                                    text = preset.name,
                                    style = TelemetryMono.copy(fontSize = 11.sp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = CardSurface,
                                labelColor = TextSecondary,
                                selectedContainerColor = ElectricCobalt,
                                selectedLabelColor = TextPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = CardBorder,
                                selectedBorderColor = ElectricCobalt
                            )
                        )
                    }
                }
            }

            // Custom System Prompt TextField
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "System Prompt",
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = onSystemPromptChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(OneUiTokens.SmallCornerRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCobalt,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = PitchBlack,
                        unfocusedContainerColor = PitchBlack,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun ParameterControl(
    title: String,
    valueText: String,
    description: String,
    slider: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            )
            Text(
                text = valueText,
                style = TelemetryMono.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = CyberCyan
            )
        }

        Text(
            text = description,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp
            ),
            color = TextMuted
        )

        slider()
    }
}
