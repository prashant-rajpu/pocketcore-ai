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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.pocketcore.data.api.model.HfModelSummary
import com.ai.pocketcore.ui.components.OneUiCard
import com.ai.pocketcore.ui.hub.CuratedModel
import com.ai.pocketcore.ui.theme.CardBorder
import com.ai.pocketcore.ui.theme.CardSurface
import com.ai.pocketcore.ui.theme.CyberCyan
import com.ai.pocketcore.ui.theme.ElectricCobalt
import com.ai.pocketcore.ui.theme.OneUiTokens
import com.ai.pocketcore.ui.theme.TextMuted
import com.ai.pocketcore.ui.theme.TextPrimary
import com.ai.pocketcore.ui.theme.TextSecondary
import com.ai.pocketcore.ui.theme.WarningOrange
import java.util.Locale

/**
 * One UI Pro Model Card component for displaying Hugging Face models.
 * Styled with 24dp squircle corners, AMOLED contrast, and S25 badge indicator.
 */
@Composable
fun ModelCard(
    model: HfModelSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRecommended: Boolean = false,
    subtitle: String? = null
) {
    OneUiCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiTokens.CardCornerRadius),
        backgroundColor = CardSurface,
        borderColor = if (isRecommended) CyberCyan.copy(alpha = 0.4f) else CardBorder
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row: Author + Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.author.ifBlank { "Community" },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (model.isGated) {
                        GatedBadge()
                    }
                    if (isRecommended) {
                        RecommendedForS25Chip()
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Model Title
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Optional Subtitle / Description
            val desc = subtitle ?: model.description
            if (!desc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Metrics: Downloads, Likes, and Pipeline / Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Downloads
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Downloads",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formatMetricCount(model.downloads),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    // Likes
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Likes",
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = formatMetricCount(model.likes.toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // GGUF Tag Pill
                Box(
                    modifier = Modifier
                        .background(
                            color = Color(0xFF2C2C2E),
                            shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "GGUF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberCyan
                    )
                }
            }
        }
    }
}

/**
 * Dedicated visual card for Curated Flagship picks in horizontal viewing carousel.
 */
@Composable
fun CuratedModelCard(
    curated: CuratedModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OneUiCard(
        onClick = onClick,
        modifier = modifier.width(280.dp),
        shape = RoundedCornerShape(OneUiTokens.CardCornerRadius),
        backgroundColor = CardSurface,
        borderColor = CyberCyan.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = curated.author,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    RecommendedForS25Chip()
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = curated.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = curated.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${curated.parameterSize} • ~${curated.estimatedRamGb}GB RAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .background(
                            color = ElectricCobalt.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                        )
                        .border(
                            width = 1.dp,
                            color = ElectricCobalt.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = curated.recommendedQuantization,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendedForS25Chip(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = ElectricCobalt.copy(alpha = 0.2f),
                shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
            )
            .border(
                width = 1.dp,
                color = CyberCyan.copy(alpha = 0.4f),
                shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = "Recommended for S25",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
        }
    }
}

@Composable
private fun GatedBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = WarningOrange.copy(alpha = 0.2f),
                shape = RoundedCornerShape(OneUiTokens.PillCornerRadius)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Gated",
                tint = WarningOrange,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = "Gated",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = WarningOrange
            )
        }
    }
}

fun formatMetricCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000.0)
        else -> count.toString()
    }
}
