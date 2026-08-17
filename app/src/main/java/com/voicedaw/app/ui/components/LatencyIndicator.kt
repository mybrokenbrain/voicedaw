package com.voicedaw.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicedaw.audioengine.AudioTier

@Composable
fun LatencyIndicator(
    modifier: Modifier = Modifier,
    latencyMs: Float = 0f,
    tier: AudioTier = AudioTier.UNKNOWN
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chipColor = when (tier) {
            AudioTier.PRO -> MaterialTheme.colorScheme.primaryContainer
            AudioTier.LOW -> MaterialTheme.colorScheme.tertiaryContainer
            AudioTier.UNSUPPORTED -> MaterialTheme.colorScheme.errorContainer
            AudioTier.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
        }
        
        SuggestionChip(
            onClick = {},
            label = { Text(tier.label, style = MaterialTheme.typography.labelSmall) },
            colors = SuggestionChipDefaults.suggestionChipColors(
                containerColor = chipColor
            )
        )
        
        if (latencyMs > 0f) {
            val text = String.format("%.1f ms", latencyMs)
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
        } else {
            Text(
                text = "Measuring…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
