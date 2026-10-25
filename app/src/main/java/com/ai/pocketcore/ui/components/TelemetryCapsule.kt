package com.ai.pocketcore.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.ui.theme.CapsuleBackground
import com.ai.pocketcore.ui.theme.CapsuleBorder
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CobaltDark
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import com.ai.pocketcore.ui.theme.VulkanGreen
import java.util.Locale

/**
 * Snapshot of real-time hardware inference telemetry.
 */
data class TelemetryState(
    val tokensPerSec: Double = 0.0,
    val ttftMs: Long = 0L,
    val ramUsageGb: Double = 0.0,
    val gpuBackend: String = "Vulkan",
    val isGenerating: Boolean = false
) {
    constructor(
        tokensPerSec: Double,
        ttftMs: Long,
        ramUsageBytes: Long,
        gpuBackend: String = "Vulkan",
        isGenerating: Boolean = false
    ) : this(
        tokensPerSec = tokensPerSec,
        ttftMs = ttftMs,
        ramUsageGb = ramUsageBytes.toDouble() / (1024.0 * 1024.0 * 1024.0),
        gpuBackend = gpuBackend,
        isGenerating = isGenerating
    )
}

/**
 * Telemetry formatting utilities for the HUD capsule.
 */
object TelemetryFormatter {
    fun formatTokPerSec(tokPerSec: Double): String {
        return String.format(Locale.US, "%.1f tok/s", tokPerSec)
    }

    fun formatTtft(ttftMs: Long): String {
        return if (ttftMs > 0) "${ttftMs}ms" else "-- ms"
    }

    fun formatRam(ramUsageGb: Double): String {
        return String.format(Locale.US, "%.1f GB", ramUsageGb)
    }

    fun formatRam(ramBytes: Long): String {
        val gb = ramBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return formatRam(gb)
    }
}

/**
 * Top-right floating capsule HUD showing:
 * ⚡ XX.X tok/s | ⏱ TTFT: XX ms | 💾 X.X GB | GPU: Vulkan
 */
@Composable
fun TelemetryCapsule(
    state: TelemetryState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(OneUiTokens.PillCornerRadius))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
        color = CapsuleBackground,
        border = BorderStroke(1.dp, CapsuleBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Speed (tok/s) + pulse indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (state.isGenerating) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(CyberCyan)
                    )
                } else {
                    Text(
                        text = "⚡",
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = TelemetryFormatter.formatTokPerSec(state.tokensPerSec),
                    style = TelemetryMono,
                    color = if (state.tokensPerSec > 0.0) CyberCyan else TextMuted
                )
            }

            // Divider
            CapsuleDivider()

            // TTFT
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "⏱",
                    fontSize = 11.sp
                )
                Text(
                    text = "TTFT: ${TelemetryFormatter.formatTtft(state.ttftMs)}",
                    style = TelemetryMono,
                    color = if (state.ttftMs > 0) TextPrimary else TextMuted
                )
            }

            // Divider
            CapsuleDivider()

            // RAM
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "💾",
                    fontSize = 11.sp
                )
                Text(
                    text = TelemetryFormatter.formatRam(state.ramUsageGb),
                    style = TelemetryMono,
                    color = TextSecondary
                )
            }

            // Divider
            CapsuleDivider()

            // GPU Badge (Vulkan)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(OneUiTokens.PillCornerRadius))
                    .background(CobaltDark.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(VulkanGreen)
                    )
                    Text(
                        text = state.gpuBackend,
                        style = TelemetryMono.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = VulkanGreen
                    )
                }
            }
        }
    }
}

/**
 * Convenience overload for direct parameter passing.
 */
@Composable
fun TelemetryCapsule(
    tokensPerSec: Double,
    ttftMs: Long,
    ramUsageGb: Double,
    gpuBackend: String = "Vulkan",
    isGenerating: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    TelemetryCapsule(
        state = TelemetryState(
            tokensPerSec = tokensPerSec,
            ttftMs = ttftMs,
            ramUsageGb = ramUsageGb,
            gpuBackend = gpuBackend,
            isGenerating = isGenerating
        ),
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
private fun CapsuleDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(10.dp)
            .background(CardBorder)
    )
}
