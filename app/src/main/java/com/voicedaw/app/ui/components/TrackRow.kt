package com.voicedaw.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicedaw.audioengine.recording.AudioTrack

@Composable
fun TrackRow(
    track: AudioTrack,
    isRecordingActive: Boolean,
    amplitudeSamples: List<Float>,       // live samples from this track's recorder
    onArmToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onTakesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val armColor by animateColorAsState(
        targetValue = if (track.isArmed && isRecordingActive)
            MaterialTheme.colorScheme.error
        else if (track.isArmed)
            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        else
            MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "arm_color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (track.isArmed) 1.dp else 0.dp,
                color = if (track.isArmed) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        else Color.Transparent,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Arm button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(armColor)
                .clickable(onClick = onArmToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (track.isArmed) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (track.isArmed) "Disarm track" else "Arm track",
                tint = if (track.isArmed) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }

        // Track label
        Column(
            modifier = Modifier.width(64.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (track.hasAudio) {
                Text(
                    text = "${track.takes.size} take${if (track.takes.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.sp,
                )
            }
        }

        // Mini waveform
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            if (track.isArmed && amplitudeSamples.isNotEmpty()) {
                WaveformView(
                    amplitudeSamples = amplitudeSamples,
                    isRecording = isRecordingActive && track.isArmed,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (track.hasAudio) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "♪",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Mute button
        SmallTrackButton(
            label = "M",
            active = track.isMuted,
            activeColor = MaterialTheme.colorScheme.tertiary,
            onClick = onMuteToggle,
        )

        // Solo button
        SmallTrackButton(
            label = "S",
            active = track.isSolo,
            activeColor = MaterialTheme.colorScheme.secondary,
            onClick = onSoloToggle,
        )

        // Takes chip
        if (track.hasAudio) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .clickable(onClick = onTakesClick)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Takes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallTrackButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) activeColor else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}
