package com.voicedaw.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TransportBar(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    isRecording: Boolean = false,
    isLooping: Boolean = false,
    bpm: Float = 120.0f,
    currentPositionBars: Int = 1,
    currentPositionBeats: Int = 1,
    onPlayPause: () -> Unit = {},
    onStop: () -> Unit = {},
    onRecord: () -> Unit = {},
    onLoopToggle: () -> Unit = {},
    onBpmChange: (Float) -> Unit = {}
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val positionText = String.format("%d.%d", currentPositionBars, currentPositionBeats)
        Text(
            text = positionText,
            modifier = Modifier.width(52.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        IconButton(onClick = onStop) {
            Icon(Icons.Default.Stop, contentDescription = "Stop")
        }
        
        IconButton(onClick = onPlayPause) {
            if (isPlaying) {
                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
            }
        }
        
        IconButton(onClick = onRecord) {
            val recordTint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
            Icon(Icons.Default.FiberManualRecord, contentDescription = "Record", tint = recordTint)
        }
        
        IconButton(onClick = onLoopToggle) {
            val loopTint = if (isLooping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Icon(Icons.Default.Loop, contentDescription = "Loop", tint = loopTint)
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        val bpmText = String.format("%.1f BPM", bpm)
        Text(
            text = bpmText,
            modifier = Modifier.width(76.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
