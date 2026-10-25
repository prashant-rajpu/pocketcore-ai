package com.ai.pocketcore.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.TextPrimary

/**
 * Standard One UI Pro 24dp squircle container.
 * Provides ergonomic touch targets, AMOLED charcoal contrast, and subtle border delineation.
 */
@Composable
fun OneUiCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(OneUiTokens.CardCornerRadius),
    backgroundColor: Color = CardSurface,
    contentColor: Color = TextPrimary,
    borderColor: Color? = CardBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(OneUiTokens.PaddingMedium),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.defaultMinSize(minHeight = OneUiTokens.MinTouchTarget),
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        border = borderColor?.let { BorderStroke(borderWidth, it) }
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Clickable One UI Pro 24dp squircle container with tactile ripple interaction.
 */
@Composable
fun OneUiCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(OneUiTokens.CardCornerRadius),
    backgroundColor: Color = CardSurface,
    contentColor: Color = TextPrimary,
    borderColor: Color? = CardBorder,
    borderWidth: Dp = 1.dp,
    contentPadding: PaddingValues = PaddingValues(OneUiTokens.PaddingMedium),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = OneUiTokens.MinTouchTarget),
        enabled = enabled,
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        border = borderColor?.let { BorderStroke(borderWidth, it) },
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
