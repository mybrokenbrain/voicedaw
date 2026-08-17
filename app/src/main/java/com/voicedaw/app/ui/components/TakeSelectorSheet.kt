package com.voicedaw.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voicedaw.audioengine.recording.AudioClip
import com.voicedaw.audioengine.recording.AudioTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TakeSelectorSheet — Modal bottom sheet for choosing the active take on a track.
 *
 * Shows all recorded takes for [track] ordered newest-first with:
 *  - Timestamp label
 *  - Duration (from file size, approximate)
 *  - "Best take" star icon (marks [AudioClip.isBestTake])
 *  - Checkmark on the currently active take
 *  - Tapping a row selects that take
 *
 * This satisfies the M3 comping acceptance criterion:
 * "Comping: multiple takes per track, simple best-take selection UI"
 *
 * @param track         The track whose takes to show.
 * @param onSelectTake  Called with the selected take index when user taps a row.
 * @param onDismiss     Called when the sheet should close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeSelectorSheet(
    track: AudioTrack,
    onSelectTake: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${track.name} — Takes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${track.takes.size} take${if (track.takes.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            if (track.takes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No takes recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Takes listed newest first
                val takesNewestFirst = track.takes.indices.reversed().toList()

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    itemsIndexed(takesNewestFirst) { listPos, takeIndex ->
                        val clip = track.takes[takeIndex]
                        val isActive = takeIndex == track.activeTakeIndex
                        TakeRow(
                            clip        = clip,
                            takeNumber  = track.takes.size - listPos,
                            isActive    = isActive,
                            dateFormat  = dateFormat,
                            onClick     = {
                                onSelectTake(takeIndex)
                                onDismiss()
                            },
                        )
                        if (listPos < takesNewestFirst.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TakeRow(
    clip: AudioClip,
    takeNumber: Int,
    isActive: Boolean,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
) {
    val approximateDurationSec = run {
        val file = java.io.File(clip.filePath)
        if (file.exists()) {
            // WAV: subtract 44-byte header, divide by bytes-per-second
            val dataBytes = (file.length() - 44L).coerceAtLeast(0L)
            val bytesPerSec = AudioClip.SAMPLE_RATE * AudioClip.CHANNELS * 2L
            dataBytes / bytesPerSec
        } else 0L
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Take number badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$takeNumber",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Take $takeNumber",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = dateFormat.format(Date(clip.recordedAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
                if (approximateDurationSec > 0) {
                    Text(
                        text = formatDuration(approximateDurationSec),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        // Best-take star
        if (clip.isBestTake) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Best take",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        // Active checkmark
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Active take",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
