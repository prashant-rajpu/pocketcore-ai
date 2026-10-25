package com.ai.pocketcore.ui.vault.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CharcoalSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import com.ai.pocketcore.ui.vault.StorageBreakdown

/**
 * One UI Pro style horizontal segmented usage bar displaying storage consumed by
 * local AI models vs other apps/system and remaining free disk space.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageUsageBar(
    storageBreakdown: StorageBreakdown,
    modifier: Modifier = Modifier
) {
    val usedBytes = (storageBreakdown.modelsBytes + storageBreakdown.otherAppsBytes).coerceAtLeast(0L)
    val usedFormatted = StorageBreakdown.formatStorageSize(usedBytes)
    val totalFormatted = storageBreakdown.formattedTotalSize()

    val modelsColor = CyberCyan
    val otherAppsColor = Color(0xFF48484A)
    val freeColor = CharcoalSurface

    val accessibilityDesc = "Storage usage: Models ${storageBreakdown.formattedModelsSize()}, " +
            "Other apps ${storageBreakdown.formattedOtherAppsSize()}, " +
            "Free ${storageBreakdown.formattedFreeSize()} of $totalFormatted"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityDesc }
    ) {
        // Header Row: Title & Total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Internal Storage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = "$usedFormatted / $totalFormatted",
                style = TelemetryMono.copy(fontSize = 12.sp),
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Segmented Bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(OneUiTokens.SmallCornerRadius))
        ) {
            val totalWidth = size.width
            val total = storageBreakdown.totalBytes.toFloat().takeIf { it > 0f } ?: 1f
            val modelsWidth = (storageBreakdown.modelsBytes.toFloat() / total) * totalWidth
            val otherWidth = (storageBreakdown.otherAppsBytes.toFloat() / total) * totalWidth

            // Base: Free disk space
            drawRect(color = freeColor)

            // Middle: Other applications & OS data
            if (otherWidth > 0f || modelsWidth > 0f) {
                drawRect(
                    color = otherAppsColor,
                    topLeft = Offset.Zero,
                    size = Size(
                        width = (modelsWidth + otherWidth).coerceAtMost(totalWidth),
                        height = size.height
                    )
                )
            }

            // Top: Local AI Models
            if (modelsWidth > 0f) {
                drawRect(
                    color = modelsColor,
                    topLeft = Offset.Zero,
                    size = Size(
                        width = modelsWidth.coerceAtMost(totalWidth),
                        height = size.height
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legend: Models, Other, Free
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendItem(
                dotColor = modelsColor,
                label = "AI Models",
                value = storageBreakdown.formattedModelsSize()
            )
            LegendItem(
                dotColor = otherAppsColor,
                label = "System & Apps",
                value = storageBreakdown.formattedOtherAppsSize()
            )
            LegendItem(
                dotColor = TextMuted,
                label = "Free Space",
                value = storageBreakdown.formattedFreeSize()
            )
        }
    }
}

@Composable
private fun LegendItem(
    dotColor: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = TelemetryMono.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
    }
}
