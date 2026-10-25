package com.ai.pocketcore.ui.studio.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.PitchBlack
import com.ai.pocketcore.ui.theme.SuccessGreen
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * One UI Pro syntax-highlighted / monospace code block container:
 * - Top header with detected language label and one-tap copy button
 * - AMOLED Pitch Black background with subtle 1dp border
 * - Horizontally scrollable code content with monospace styling
 */
@Composable
fun CodeBlockView(
    code: String,
    modifier: Modifier = Modifier,
    language: String = ""
) {
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2000L)
            isCopied = false
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiTokens.SmallCornerRadius)),
        shape = RoundedCornerShape(OneUiTokens.SmallCornerRadius),
        color = PitchBlack,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSurface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayLang = language.trim().ifBlank { "text" }.uppercase(Locale.US)
                Text(
                    text = displayLang,
                    style = TelemetryMono.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = CyberCyan
                )

                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                    },
                    modifier = Modifier.padding(0.dp)
                ) {
                    AnimatedContent(
                        targetState = isCopied,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "copyButtonContent"
                    ) { copied ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (copied) "Copied" else "Copy code",
                                tint = if (copied) SuccessGreen else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (copied) "Copied" else "Copy",
                                style = TelemetryMono.copy(fontSize = 12.sp),
                                color = if (copied) SuccessGreen else TextSecondary
                            )
                        }
                    }
                }
            }

            // Code Text Body (horizontal scrollable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code.trimEnd(),
                    style = TelemetryMono.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}
