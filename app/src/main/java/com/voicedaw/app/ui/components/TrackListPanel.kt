package com.voicedaw.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.voicedaw.audioengine.recording.AudioTrack

/**
 * TrackListPanel — vertical scrollable list of [TrackRow] components.
 *
 * Per ARCHITECTURE.md decision, this is Compose (not a custom Android View)
 * because it's a list layout, not a real-time-redraw surface. The per-track
 * waveform mini-views inside each row use the existing [WaveformView] Canvas.
 *
 * @param tracks              Current list of tracks (from ViewModel state).
 * @param isRecordingActive   Whether a recording pass is currently in progress.
 * @param amplitudeSamples    Live amplitude samples for the primary armed track.
 * @param onArmToggle         Called with track index when arm button tapped.
 * @param onMuteToggle        Called with track index when mute tapped.
 * @param onSoloToggle        Called with track index when solo tapped.
 * @param onTakesClick        Called with track index when takes chip tapped.
 */
@Composable
fun TrackListPanel(
    tracks: List<AudioTrack>,
    isRecordingActive: Boolean,
    amplitudeSamples: List<Float>,
    onArmToggle: (Int) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onSoloToggle: (Int) -> Unit,
    onTakesClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(tracks) { index, track ->
            TrackRow(
                track              = track,
                isRecordingActive  = isRecordingActive,
                amplitudeSamples   = if (track.isArmed) amplitudeSamples else emptyList(),
                onArmToggle        = { onArmToggle(index) },
                onMuteToggle       = { onMuteToggle(index) },
                onSoloToggle       = { onSoloToggle(index) },
                onTakesClick       = { onTakesClick(index) },
            )
            if (index < tracks.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}
