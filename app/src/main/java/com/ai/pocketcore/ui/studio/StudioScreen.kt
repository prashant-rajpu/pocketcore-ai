package com.ai.pocketcore.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.data.db.entity.ChatMessageEntity
import com.ai.pocketcore.data.db.entity.ChatRole
import com.ai.pocketcore.ui.components.OneUiCard
import com.ai.pocketcore.ui.components.TelemetryCapsule
import com.ai.pocketcore.ui.studio.components.ChatBubble
import com.ai.pocketcore.ui.studio.components.ProParametersSheet
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

/**
 * Studio Screen:
 * - Top telemetry capsule HUD: `⚡ tok/s | ⏱ TTFT | 💾 RAM | Vulkan`
 * - Conversation stream with One UI Pro chat bubbles & copyable code blocks
 * - Auto-scroll on token streaming
 * - Bottom ergonomic interaction bar with Send/Stop controls
 * - Pro inference parameters pull-up sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
    onNavigateToVault: (() -> Unit)? = null
) {
    val activeModel by viewModel.activeModel.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val activeStreamingText by viewModel.activeStreamingText.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()
    val params by viewModel.params.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var showParamsSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll to latest token or message instantly during rapid streaming
    LaunchedEffect(messages.size, activeStreamingText) {
        val totalItems = messages.size + if (isGenerating && activeStreamingText.isNotEmpty()) 1 else 0
        if (totalItems > 0) {
            listState.scrollToItem(totalItems - 1)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = PitchBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Bar: Model Status, Telemetry HUD, Action Icons
            StudioTopBar(
                modelName = activeModel?.fileName ?: activeModel?.id,
                quantization = activeModel?.quantization,
                telemetryState = telemetryState,
                onOpenParams = { showParamsSheet = true },
                onClearChat = { showClearDialog = true },
                onModelClick = onNavigateToVault
            )

            // Error Banner
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                errorMessage?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        color = ErrorRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(OneUiTokens.SmallCornerRadius),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Chat Messages Stream / Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    StudioEmptyState(
                        hasActiveModel = activeModel != null,
                        onSelectPrompt = { prompt ->
                            inputPrompt = prompt
                        },
                        onNavigateToVault = onNavigateToVault
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            ChatBubble(message = message)
                        }

                        // In-flight streaming response
                        if (isGenerating) {
                            item(key = "active_stream") {
                                val inProgressMessage = ChatMessageEntity(
                                    sessionId = viewModel.sessionId,
                                    role = ChatRole.ASSISTANT,
                                    content = activeStreamingText
                                )
                                ChatBubble(
                                    message = inProgressMessage,
                                    isStreaming = true
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Ergonomic Interaction Bar
            StudioBottomInputBar(
                inputPrompt = inputPrompt,
                onInputPromptChange = { inputPrompt = it },
                isGenerating = isGenerating,
                hasActiveModel = activeModel != null,
                onSendMessage = {
                    if (inputPrompt.isNotBlank() && activeModel != null) {
                        val sent = viewModel.sendMessage(inputPrompt)
                        if (sent) {
                            inputPrompt = ""
                        }
                    }
                },
                onStopGeneration = {
                    viewModel.stopGeneration()
                },
                onOpenParams = { showParamsSheet = true }
            )
        }
    }

    // Pro Parameters Sheet
    if (showParamsSheet) {
        ProParametersSheet(
            params = params,
            systemPrompt = systemPrompt,
            onParamsChange = { viewModel.updateParams(it) },
            onSystemPromptChange = { viewModel.setSystemPrompt(it) },
            onDismissRequest = { showParamsSheet = false }
        )
    }

    // Clear Chat Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear Conversation?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all messages from this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearSession()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text(text = "Clear", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = "Cancel", color = TextSecondary)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(OneUiTokens.CardCornerRadius)
        )
    }
}

@Composable
private fun StudioTopBar(
    modelName: String?,
    quantization: String?,
    telemetryState: com.ai.pocketcore.ui.components.TelemetryState,
    onOpenParams: () -> Unit,
    onClearChat: () -> Unit,
    onModelClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PitchBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Model Status Pill
            Surface(
                onClick = { onModelClick?.invoke() },
                enabled = onModelClick != null,
                shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                color = CardSurface,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (modelName != null) SuccessGreen else WarningOrange)
                    )
                    Text(
                        text = if (modelName != null) {
                            val shortName = modelName.substringBeforeLast(".").take(18)
                            if (quantization != null) "$shortName ($quantization)" else shortName
                        } else {
                            "No model active"
                        },
                        style = TelemetryMono.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (modelName != null) TextPrimary else WarningOrange,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onOpenParams,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Parameters",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Chat",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Telemetry Capsule HUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TelemetryCapsule(
                state = telemetryState,
                onClick = onOpenParams
            )
        }
    }
}

@Composable
private fun StudioEmptyState(
    hasActiveModel: Boolean,
    onSelectPrompt: (String) -> Unit,
    onNavigateToVault: (() -> Unit)? = null
) {
    val samplePrompts = listOf(
        "Explain quantum computing in simple terms",
        "Write a Kotlin Flow backpressure handler",
        "What are the architectural highlights of Snapdragon 8 Elite?",
        "Compose a short poem about on-device machine intelligence"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OneUiCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "⚡",
                    fontSize = 32.sp
                )
                Text(
                    text = "PocketCore AI Studio",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = if (hasActiveModel) {
                        "Local Vulkan-accelerated inference ready on Snapdragon 8 Elite. Type a prompt below to begin chatting completely offline."
                    } else {
                        "No AI model currently loaded into RAM. Visit the Vault to activate an installed GGUF model."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 18.sp,
                        color = TextSecondary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (!hasActiveModel && onNavigateToVault != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onNavigateToVault,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCobalt),
                        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                    ) {
                        Text(text = "Go to Vault", color = TextPrimary)
                    }
                }
            }
        }

        if (hasActiveModel) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Try asking:",
                style = TelemetryMono.copy(fontSize = 11.sp, color = TextMuted),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(samplePrompts) { prompt ->
                    Surface(
                        onClick = { onSelectPrompt(prompt) },
                        shape = RoundedCornerShape(OneUiTokens.PillCornerRadius),
                        color = CardSurface,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                color = TextSecondary
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioBottomInputBar(
    inputPrompt: String,
    onInputPromptChange: (String) -> Unit,
    isGenerating: Boolean,
    hasActiveModel: Boolean,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    onOpenParams: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PitchBlack,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onOpenParams,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Tune parameters",
                    tint = CyberCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            OutlinedTextField(
                value = inputPrompt,
                onValueChange = onInputPromptChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = when {
                            isGenerating -> "Generating response..."
                            !hasActiveModel -> "Select a model in Vault to chat..."
                            else -> "Message local model..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCobalt,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = CharcoalSurface,
                    unfocusedContainerColor = CharcoalSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 20.sp
                )
            )

            val canSend = inputPrompt.isNotBlank() && hasActiveModel

            if (isGenerating) {
                // Stop Generation Button
                IconButton(
                    onClick = onStopGeneration,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(WarningOrange)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop generation",
                        tint = PitchBlack,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                // Send Prompt Button
                IconButton(
                    onClick = onSendMessage,
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (canSend) ElectricCobalt else CardSurface)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (hasActiveModel) "Send prompt" else "Load a model from Vault to send",
                        tint = if (canSend) TextPrimary else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
