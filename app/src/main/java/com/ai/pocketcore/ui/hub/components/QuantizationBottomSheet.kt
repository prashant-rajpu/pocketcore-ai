package com.ai.pocketcore.ui.hub.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.data.api.model.GgufFileInfo
import com.ai.pocketcore.data.api.model.HfModelSummary
import com.ai.pocketcore.data.downloader.DownloadState
import com.ai.pocketcore.ui.components.OneUiCard
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CharcoalSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.SuccessGreen
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import com.ai.pocketcore.ui.theme.WarningOrange
import java.util.Locale

/**
 * Bottom Sheet displaying available GGUF quantizations for a model,
 * calculated RAM requirements for Samsung Galaxy S25 (12GB), and direct download actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantizationBottomSheet(
    model: HfModelSummary,
    files: List<GgufFileInfo>,
    isLoading: Boolean,
    downloadState: DownloadState,
    activeDownloadFileName: String?,
    onDismiss: () -> Unit,
    onDownload: (GgufFileInfo) -> Unit,
    onPauseDownload: () -> Unit = {},
    onResumeDownload: () -> Unit = {},
    isFileDownloaded: (String) -> Boolean = { false },
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalSurface,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = OneUiTokens.CardCornerRadius, topEnd = OneUiTokens.CardCornerRadius),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OneUiTokens.PaddingMedium, vertical = OneUiTokens.PaddingSmall)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${model.repoId} • Galaxy S25 12GB Optimized",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body: Loading, Empty, or List
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = CyberCyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Scanning GGUF quantizations...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
                files.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No GGUF quantization files found for this repository.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Available Quantizations (${files.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(files, key = { it.name }) { file ->
                            val currentDownloading = downloadState as? DownloadState.Downloading
                            val currentPaused = downloadState as? DownloadState.Paused
                            val isCurrentDownloading = activeDownloadFileName == file.name && currentDownloading != null
                            val isCurrentPaused = activeDownloadFileName == file.name && currentPaused != null
                            val isDownloaded = isFileDownloaded(file.name)

                            QuantizationFileCard(
                                file = file,
                                isDownloaded = isDownloaded,
                                isDownloading = isCurrentDownloading,
                                isPaused = isCurrentPaused,
                                downloadProgress = when {
                                    isCurrentDownloading -> currentDownloading?.progress ?: 0f
                                    isCurrentPaused -> currentPaused?.progress ?: 0f
                                    else -> 0f
                                },
                                downloadSpeedMbS = if (isCurrentDownloading) currentDownloading?.speedMbS ?: 0.0 else 0.0,
                                onDownload = { onDownload(file) },
                                onPause = onPauseDownload,
                                onResume = onResumeDownload
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Individual Card representing one GGUF quantization option with RAM calculations.
 */
@Composable
fun QuantizationFileCard(
    file: GgufFileInfo,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPaused: Boolean,
    downloadProgress: Float,
    downloadSpeedMbS: Double,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecommended = file.quantization.equals("Q4_K_M", ignoreCase = true) ||
            file.quantization.equals("Q5_K_M", ignoreCase = true)
    val ramEstimate = calculateEstimatedRam(file.sizeBytes)
    val isRamHigh = (file.sizeBytes * 1.2) > (10.5 * 1024 * 1024 * 1024)

    OneUiCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiTokens.SmallCornerRadius),
        backgroundColor = CardSurface,
        borderColor = if (isRecommended) CyberCyan.copy(alpha = 0.5f) else CardBorder,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = file.quantization.ifBlank { "GGUF" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRecommended) CyberCyan else TextPrimary
                        )

                        if (isRecommended) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = CyberCyan.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = CyberCyan.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Sweet Spot",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan
                                )
                            }
                        }

                        if (isRamHigh) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = WarningOrange.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "High RAM",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = WarningOrange
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${file.formattedSize} • $ramEstimate",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Action
                when {
                    isDownloaded -> {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = SuccessGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Installed",
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Installed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SuccessGreen
                                )
                            }
                        }
                    }
                    isDownloading -> {
                        IconButton(onClick = onPause) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = CyberCyan
                            )
                        }
                    }
                    isPaused -> {
                        IconButton(onClick = onResume) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = WarningOrange
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCobalt,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Get",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Progress bar when actively downloading or paused
            if (isDownloading || isPaused) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress },
                    color = if (isPaused) WarningOrange else CyberCyan,
                    trackColor = Color(0xFF2C2C2E),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isPaused) "Paused" else "${(downloadProgress * 100).toInt()}%",
                        style = TelemetryMono,
                        color = if (isPaused) WarningOrange else CyberCyan
                    )
                    if (isDownloading && downloadSpeedMbS > 0.0) {
                        Text(
                            text = String.format(Locale.US, "%.1f MB/s", downloadSpeedMbS),
                            style = TelemetryMono,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calculates estimated RAM required to load this GGUF file with KV cache headroom.
 */
fun calculateEstimatedRam(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "~4.0 GB RAM"
    val sizeGb = sizeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val estimatedRamGb = sizeGb * 1.2
    return String.format(Locale.US, "~%.1f GB RAM", estimatedRamGb)
}
