package com.ai.pocketcore.ui.vault

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.data.db.entity.ModelEntity
import com.ai.pocketcore.ui.components.OneUiCard
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CharcoalSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.ErrorRed
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.PitchBlack
import com.ai.pocketcore.ui.theme.SuccessGreen
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import com.ai.pocketcore.ui.theme.WarningOrange
import com.ai.pocketcore.ui.vault.components.StorageUsageBar

/**
 * Vault Screen for local model inventory and storage management.
 * Follows Samsung One UI Pro ergonomics:
 * - Upper viewing zone: Storage breakdown bar, active loaded model capsule, RAM safety indicator
 * - Lower interaction zone: List of installed model cards with Active/Load/Unload/Delete actions
 *   and SAF custom GGUF model import FAB
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val models by viewModel.models.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val storageBreakdown by viewModel.storageBreakdown.collectAsState()
    val availableRamBytes by viewModel.availableRamBytes.collectAsState()
    val totalRamBytes by viewModel.totalRamBytes.collectAsState()
    val loadingModelId by viewModel.loadingModelId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    var modelToDelete by remember { mutableStateOf<ModelEntity?>(null) }
    var modelToConfirmLoad by remember { mutableStateOf<ModelEntity?>(null) }

    // SAF File Picker Launcher
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelFromUri(uri, context.contentResolver)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PitchBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    safLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                },
                containerColor = ElectricCobalt,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Import GGUF",
                        tint = TextPrimary
                    )
                    Text(
                        text = "Import GGUF",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Upper Viewing Zone: Header, Storage Card, Active Model Capsule & RAM Status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiTokens.PaddingMedium, vertical = OneUiTokens.PaddingSmall)
            ) {
                Text(
                    text = "Model Vault",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Local Storage & In-Memory Execution",
                    style = MaterialTheme.typography.labelMedium,
                    color = CyberCyan
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Storage Breakdown Card
                OneUiCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = CardSurface,
                    borderColor = CardBorder
                ) {
                    StorageUsageBar(storageBreakdown = storageBreakdown)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Loaded Model Capsule Card
                ActiveModelCapsule(
                    activeModel = activeModel,
                    isLoading = loadingModelId != null && loadingModelId == activeModel?.id,
                    onUnload = { viewModel.unloadActiveModel() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // RAM Safety Guard Status Indicator
                RamSafetyIndicator(
                    availableRamBytes = availableRamBytes,
                    totalRamBytes = totalRamBytes
                )
            }

            // Error or Import Banner
            AnimatedVisibility(
                visible = errorMessage != null || isImporting,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = OneUiTokens.PaddingMedium, vertical = 4.dp)
                        .clip(RoundedCornerShape(OneUiTokens.SmallCornerRadius))
                        .background(if (errorMessage != null) ErrorRed.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = CyberCyan,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Importing & verifying GGUF file...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CyberCyan
                                )
                            } else if (errorMessage != null) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                        if (errorMessage != null) {
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Lower Interaction Zone: Installed Models List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = OneUiTokens.PaddingMedium)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed Models (${models.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                if (models.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty Vault",
                                tint = TextMuted,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "No local models found",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                            Text(
                                text = "Download models from the Model Hub or import custom GGUFs from device storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    safLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                                },
                                shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardSurface,
                                    contentColor = CyberCyan
                                ),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Import GGUF File")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(models, key = { it.id }) { model ->
                            val isRisky = VaultViewModel.isMemoryRisky(model.fileSizeBytes, availableRamBytes)
                            val isLoading = loadingModelId == model.id

                            VaultModelCard(
                                model = model,
                                isRisky = isRisky,
                                isLoading = isLoading,
                                onLoad = {
                                    if (isRisky) {
                                        modelToConfirmLoad = model
                                    } else {
                                        viewModel.loadModel(model.id, force = false)
                                    }
                                },
                                onUnload = { viewModel.unloadActiveModel() },
                                onDelete = { modelToDelete = model }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog: Delete Model
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            containerColor = CharcoalSurface,
            title = {
                Text(
                    text = "Delete Model?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${model.fileName}\"? This will permanently free ${model.formattedSizeGb()} from local storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteModel(model)
                        modelToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                ) {
                    Text(text = "Delete", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { modelToDelete = null }
                ) {
                    Text(text = "Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Confirmation Dialog: RAM Safety Warning on Risky Model Load
    modelToConfirmLoad?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToConfirmLoad = null },
            containerColor = CharcoalSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = WarningOrange,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "High RAM Risk Warning",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                val reqGb = model.formattedSizeGb()
                val availGb = StorageBreakdown.formatStorageSize(availableRamBytes)
                Text(
                    text = "This model requires $reqGb which exceeds the 85% safe headroom of available RAM ($availGb). Loading it into memory may cause Android OS to terminate background applications or abort inference. Do you want to load it anyway?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.loadModel(model.id, force = true)
                        modelToConfirmLoad = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningOrange),
                    shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                ) {
                    Text(text = "Load Anyway", color = PitchBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { modelToConfirmLoad = null }
                ) {
                    Text(text = "Cancel", color = TextSecondary)
                }
            }
        )
    }
}

/**
 * Capsule HUD displaying the currently active in-memory model.
 */
@Composable
private fun ActiveModelCapsule(
    activeModel: ModelEntity?,
    isLoading: Boolean,
    onUnload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiTokens.SmallCornerRadius),
        color = CardSurface,
        border = BorderStroke(1.dp, if (activeModel != null) CyberCyan.copy(alpha = 0.5f) else CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (activeModel != null) SuccessGreen else TextMuted)
                )

                Column {
                    Text(
                        text = if (activeModel != null) "Active in Memory" else "No Model Loaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeModel != null) SuccessGreen else TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeModel?.fileName ?: "Select a model below to activate",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeModel != null) TextPrimary else TextSecondary,
                        maxLines = 1
                    )
                }
            }

            if (activeModel != null) {
                OutlinedButton(
                    onClick = onUnload,
                    shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                    border = BorderStroke(1.dp, CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = CyberCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Unload",
                            tint = WarningOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Unload", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * RAM Safety Indicator showing available memory and the 85% safe threshold.
 */
@Composable
private fun RamSafetyIndicator(
    availableRamBytes: Long,
    totalRamBytes: Long,
    modifier: Modifier = Modifier
) {
    val safeBytes = (availableRamBytes * VaultViewModel.RAM_SAFETY_THRESHOLD).toLong()
    val availGb = StorageBreakdown.formatStorageSize(availableRamBytes)
    val totalGb = StorageBreakdown.formatStorageSize(totalRamBytes)
    val safeGb = StorageBreakdown.formatStorageSize(safeBytes)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiTokens.SmallCornerRadius))
            .background(CharcoalSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = "RAM Guard",
                tint = CyberCyan,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "RAM Available: $availGb / $totalGb",
                style = TelemetryMono.copy(fontSize = 11.sp),
                color = TextSecondary
            )
        }

        Text(
            text = "Safe Guard: ≤ $safeGb",
            style = TelemetryMono.copy(fontSize = 11.sp),
            color = CyberCyan
        )
    }
}

/**
 * Individual installed model card with One UI 24dp squircle styling,
 * architecture metadata, RAM safety warning badge, and Load/Unload/Delete actions.
 */
@Composable
private fun VaultModelCard(
    model: ModelEntity,
    isRisky: Boolean,
    isLoading: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    OneUiCard(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = CardSurface,
        borderColor = if (model.isActive) CyberCyan else CardBorder,
        borderWidth = if (model.isActive) 1.5.dp else 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: File Name & Active Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = model.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )

                if (model.isActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(OneUiTokens.PillCornerRadius))
                            .background(ElectricCobalt.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan)
                            )
                            Text(
                                text = "ACTIVE",
                                style = TelemetryMono.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = CyberCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Chips: Quantization, Size, Context, Architecture
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetadataBadge(text = model.quantization, color = ElectricCobalt)
                MetadataBadge(text = model.formattedSizeGb(), color = Color(0xFF3A3A3C))
                MetadataBadge(text = "${model.contextLength} ctx", color = Color(0xFF3A3A3C))
                MetadataBadge(text = model.architecture, color = Color(0xFF2C2C2E))
            }

            // RAM Safety Warning
            if (isRisky) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(WarningOrange.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Risk",
                        tint = WarningOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "High RAM Footprint: exceeds 85% safe headroom",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons: Load / Unload & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (model.isActive) {
                    Button(
                        onClick = onUnload,
                        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningOrange,
                            contentColor = PitchBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = PitchBlack
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Unload Model",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    Button(
                        onClick = onLoad,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCobalt,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = TextPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Load Model",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Model",
                        tint = ErrorRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = TelemetryMono.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
            color = TextPrimary
        )
    }
}
