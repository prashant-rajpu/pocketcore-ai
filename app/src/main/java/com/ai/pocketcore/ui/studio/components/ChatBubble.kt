package com.ai.pocketcore.ui.studio.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.data.db.entity.ChatMessageEntity
import com.ai.pocketcore.data.db.entity.ChatRole
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CharcoalSurface
import com.ai.pocketcore.ui.theme.CobaltDark
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.TelemetryMono
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import java.util.Locale

/**
 * Parsed segments of markdown chat responses.
 */
sealed interface MarkdownSegment {
    data class Text(val content: String) : MarkdownSegment
    data class Code(val language: String, val code: String) : MarkdownSegment
}

/**
 * Parser for separating fenced code blocks from markdown prose.
 */
object MarkdownParser {
    private val CODE_BLOCK_REGEX = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")

    fun parse(markdown: String): List<MarkdownSegment> {
        if (markdown.isEmpty()) return emptyList()

        val segments = mutableListOf<MarkdownSegment>()
        var lastIndex = 0

        for (match in CODE_BLOCK_REGEX.findAll(markdown)) {
            if (match.range.first > lastIndex) {
                val preText = markdown.substring(lastIndex, match.range.first)
                if (preText.isNotEmpty()) {
                    segments.add(MarkdownSegment.Text(preText))
                }
            }
            val lang = match.groupValues[1]
            val code = match.groupValues[2]
            segments.add(MarkdownSegment.Code(lang, code))
            lastIndex = match.range.last + 1
        }

        if (lastIndex < markdown.length) {
            val remaining = markdown.substring(lastIndex)
            // Handle potentially unclosed code block during active streaming
            val unclosedIndex = remaining.indexOf("```")
            if (unclosedIndex >= 0) {
                if (unclosedIndex > 0) {
                    segments.add(MarkdownSegment.Text(remaining.substring(0, unclosedIndex)))
                }
                val codeAfterFence = remaining.substring(unclosedIndex + 3)
                val newlineIndex = codeAfterFence.indexOf('\n')
                val (lang, code) = if (newlineIndex >= 0) {
                    codeAfterFence.substring(0, newlineIndex).trim() to codeAfterFence.substring(newlineIndex + 1)
                } else {
                    "" to codeAfterFence
                }
                segments.add(MarkdownSegment.Code(lang, code))
            } else {
                segments.add(MarkdownSegment.Text(remaining))
            }
        }

        return segments.ifEmpty { listOf(MarkdownSegment.Text(markdown)) }
    }

    /**
     * Parses inline markdown tokens (bold **...**, inline code `...`, italic *...*).
     */
    fun buildInlineMarkdown(text: String, isUserMessage: Boolean): AnnotatedString {
        val inlineRegex = Regex("(\\*\\*.*?\\*\\*|`.*?`|\\*.*?\\*)")
        val builder = AnnotatedString.Builder()
        var lastIdx = 0

        val baseColor = if (isUserMessage) TextPrimary else TextPrimary
        val inlineCodeBg = if (isUserMessage) CobaltDark else CharcoalSurface
        val inlineCodeColor = if (isUserMessage) TextPrimary else CyberCyan

        for (match in inlineRegex.findAll(text)) {
            if (match.range.first > lastIdx) {
                builder.withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(lastIdx, match.range.first))
                }
            }
            val token = match.value
            when {
                token.startsWith("**") && token.endsWith("**") && token.length >= 4 -> {
                    val inner = token.substring(2, token.length - 2)
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(inner)
                    }
                }
                token.startsWith("`") && token.endsWith("`") && token.length >= 2 -> {
                    val inner = token.substring(1, token.length - 1)
                    builder.withStyle(
                        SpanStyle(
                            background = inlineCodeBg,
                            color = inlineCodeColor,
                            fontFamily = TelemetryMono.fontFamily,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(" $inner ")
                    }
                }
                token.startsWith("*") && token.endsWith("*") && token.length >= 2 -> {
                    val inner = token.substring(1, token.length - 1)
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(inner)
                    }
                }
                else -> {
                    builder.withStyle(SpanStyle(color = baseColor)) {
                        append(token)
                    }
                }
            }
            lastIdx = match.range.last + 1
        }

        if (lastIdx < text.length) {
            builder.withStyle(SpanStyle(color = baseColor)) {
                append(text.substring(lastIdx))
            }
        }

        return builder.toAnnotatedString()
    }
}

/**
 * One UI Pro Chat Bubble:
 * - User: `#0D6EFD` Electric Cobalt, squircle, right-aligned
 * - Assistant: `#121214` Deep Charcoal with 24dp squircle, left-aligned
 * - Markdown text rendering and integrated [CodeBlockView] containers
 * - Subtle telemetry footnote badge (tok/s and TTFT) for assistant completions
 */
@Composable
fun ChatBubble(
    message: ChatMessageEntity,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val isUser = message.role == ChatRole.USER

    // Blinking cursor transition for active streaming
    val cursorTransition = rememberInfiniteTransition(label = "cursorTransition")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 340.dp),
            shape = if (isUser) {
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 6.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 20.dp
                )
            } else {
                RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = OneUiTokens.CardCornerRadius,
                    bottomStart = OneUiTokens.CardCornerRadius,
                    bottomEnd = OneUiTokens.CardCornerRadius
                )
            },
            color = if (isUser) ElectricCobalt else CharcoalSurface,
            border = if (isUser) null else BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            lineHeight = 22.sp
                        )
                    )
                } else {
                    // Assistant message with Markdown segments
                    val segments = remember(message.content) {
                        MarkdownParser.parse(message.content)
                    }

                    if (segments.isEmpty() && isStreaming) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .alpha(cursorAlpha)
                                    .clip(CircleShape)
                                    .background(CyberCyan)
                            )
                            Text(
                                text = "Thinking...",
                                style = TelemetryMono.copy(fontSize = 12.sp),
                                color = TextSecondary
                            )
                        }
                    } else {
                        segments.forEach { segment ->
                            when (segment) {
                                is MarkdownSegment.Text -> {
                                    val annotated = remember(segment.content) {
                                        MarkdownParser.buildInlineMarkdown(segment.content, isUserMessage = false)
                                    }
                                    Text(
                                        text = annotated,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextPrimary,
                                            lineHeight = 22.sp
                                        )
                                    )
                                }
                                is MarkdownSegment.Code -> {
                                    CodeBlockView(
                                        code = segment.code,
                                        language = segment.language
                                    )
                                }
                            }
                        }

                        // Streaming cursor at the end
                        if (isStreaming) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .alpha(cursorAlpha)
                                        .clip(CircleShape)
                                        .background(CyberCyan)
                                )
                                Text(
                                    text = "streaming",
                                    style = TelemetryMono.copy(fontSize = 10.sp),
                                    color = CyberCyan
                                )
                            }
                        }
                    }

                    // Telemetry Footer Badge
                    if (!isStreaming && (message.tokensPerSec > 0.0 || message.ttftMs > 0L)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (message.tokensPerSec > 0.0) {
                                Text(
                                    text = String.format(Locale.US, "⚡ %.1f tok/s", message.tokensPerSec),
                                    style = TelemetryMono.copy(fontSize = 11.sp),
                                    color = CyberCyan
                                )
                            }
                            if (message.tokensPerSec > 0.0 && message.ttftMs > 0L) {
                                Text(
                                    text = "•",
                                    style = TelemetryMono.copy(fontSize = 11.sp),
                                    color = TextMuted
                                )
                            }
                            if (message.ttftMs > 0L) {
                                Text(
                                    text = "⏱ ${message.ttftMs}ms",
                                    style = TelemetryMono.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
