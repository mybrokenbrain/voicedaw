package com.voicedaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voicedaw.app.ui.RecordPermissionEffect
import com.voicedaw.app.ui.components.*
import com.voicedaw.app.ui.components.NoteMonitorOverlay
import com.voicedaw.audioengine.AudioEngineViewModel
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.CircularProgressIndicator

@Composable
fun RecordScreen(
    engineVm: AudioEngineViewModel = viewModel(),
) {
    val state by engineVm.state.collectAsState()
    val voiceCaptureActive by engineVm.voiceCaptureActive.collectAsState()
    val isCalibrating      by engineVm.isCalibrating.collectAsState()
    val calibratedAmpFloor by engineVm.calibratedAmpFloor.collectAsState()
    val autoCalibrate      by engineVm.autoCalibrate.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Track index whose TakeSelectorSheet is open (null = closed)
    var takesSheetTrackIndex by remember { mutableStateOf<Int?>(null) }

    RecordPermissionEffect(snackbarHostState = snackbarHostState)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Transport
            TransportBar(
                modifier    = Modifier.fillMaxWidth(),
                isPlaying   = state.isPlaying,
                isRecording = state.isRecording,
                isLooping   = state.isLooping,
                bpm         = state.bpm,
                onPlayPause = { if (state.isPlaying) engineVm.pause() else engineVm.play() },
                onStop      = { engineVm.stop() },
                onRecord    = { engineVm.toggleRecord() },
                onLoopToggle = { engineVm.toggleLoop() },
            )

            // Punch controls
            PunchControls(
                enabled      = state.punchEnabled,
                punchInMs    = state.punchInMs,
                punchOutMs   = state.punchOutMs,
                onToggle     = { engineVm.setPunchEnabled(!state.punchEnabled) },
                onPunchInMs  = { engineVm.setPunchInMs(it) },
                onPunchOutMs = { engineVm.setPunchOutMs(it) },
                modifier     = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // System Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LatencyIndicator(
                        latencyMs = state.latencyMs,
                        tier      = state.tier,
                    )
                    if (state.isEcoMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("ECO MODE", color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text  = "Noise floor: ${"%.4f".format(calibratedAmpFloor)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isCalibrating) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text("Calibrating...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(
                                onClick = { engineVm.calibrateMic() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            ) { Text("Calibrate Mic", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Auto-calibrate on rec",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(
                            checked = autoCalibrate,
                            onCheckedChange = { engineVm.setAutoCalibrate(it) },
                        )
                    }
                    
                    val keyRoots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                    val keyStr = if (state.estimatedKeyRoot in 0..11) {
                        "${keyRoots[state.estimatedKeyRoot]} ${if(state.estimatedKeyIsMajor) "Maj" else "Min"}"
                    } else "---"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Key: $keyStr | BPM: ${if(state.estimatedBpm > 0) state.estimatedBpm.toInt() else "---"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = { engineVm.onTapTempo() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp),
                            ) {
                                Text("TAP", style = MaterialTheme.typography.labelSmall)
                            }
                            if (state.estimatedBpm > 0) {
                                IconButton(
                                    onClick = { engineVm.lockToVoiceBpm() },
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "Lock to voice BPM (${state.estimatedBpm.toInt()})",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Master waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                WaveformView(
                    amplitudeSamples = state.amplitudeSamples,
                    isRecording      = state.isRecording,
                    modifier         = Modifier.fillMaxSize(),
                )

                if (state.isRecording) {
                    Text(
                        text = "● REC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                }

                val armedCount = state.tracks.count { it.isArmed }
                if (armedCount > 0) {
                    Text(
                        text = if (state.isRecording) "$armedCount track${if (armedCount > 1) "s" else ""} recording"
                               else "$armedCount track${if (armedCount > 1) "s" else ""} armed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isRecording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Pending MIDI banner
            if (state.pendingMidiCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "♪ ${state.pendingMidiCount} MIDI notes captured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = { engineVm.consumePendingMidiNotes() }) {
                        Text(
                            "Send to Piano Roll",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Voice Input", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.voiceInputMode == 0,
                        onClick = { engineVm.setAnalysisMode(0) },
                        label = { Text("Off") }
                    )
                    FilterChip(
                        selected = state.voiceInputMode == 1,
                        onClick = { engineVm.setAnalysisMode(1) },
                        label = { Text("Melody") }
                    )
                    FilterChip(
                        selected = state.voiceInputMode == 2,
                        onClick = { engineVm.setAnalysisMode(2) },
                        label = { Text("Beatbox") }
                    )
                }
            }

            if (state.voiceInputMode == 1) { // Hum-to-Melody
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Harmony Assist (Auto Chords)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.harmonyAssistEnabled,
                        onCheckedChange = { engineVm.setHarmonyAssistEnabled(it) }
                    )
                }
            } else if (state.voiceInputMode == 2) { // Beatbox-to-Drums
                var beatboxMode by remember { mutableIntStateOf(0) }
                BeatboxTrainingPanel(
                    currentMode = beatboxMode,
                    lastDetectedPad = state.lastDetectedPad,
                    onModeChange = { beatboxMode = it },
                    onTrainPad = { padIndex -> 
                        engineVm.setBeatboxTrainingPad(padIndex)
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

            // Track list
            TrackListPanel(
                tracks            = state.tracks,
                isRecordingActive = state.isRecording,
                amplitudeSamples  = state.amplitudeSamples,
                onArmToggle       = { i -> engineVm.armTrack(i, !state.tracks[i].isArmed) },
                onMuteToggle      = { i -> engineVm.toggleMute(i) },
                onSoloToggle      = { i -> engineVm.toggleSolo(i) },
                onTakesClick      = { i -> takesSheetTrackIndex = i },
                modifier          = Modifier
                    .fillMaxSize()
                    .weight(1f),
            )
        }
    }

    NoteMonitorOverlay(
        visible        = voiceCaptureActive && state.voiceInputMode == 1,
        pitchHz        = state.pitchHz,
        pitchAmp       = state.pitchAmp,
        ampFloor       = calibratedAmpFloor,
        isRecording    = state.isRecording,
        onToggleRecord = { engineVm.toggleRecord() },
        onStop         = { engineVm.stopVoiceCapture() },
    )
}

    // Takes selector sheet
    val sheetIndex = takesSheetTrackIndex
    if (sheetIndex != null) {
        val track = state.tracks.getOrNull(sheetIndex)
        if (track != null) {
            TakeSelectorSheet(
                track        = track,
                onSelectTake = { takeIdx -> engineVm.selectTake(sheetIndex, takeIdx) },
                onDismiss    = { takesSheetTrackIndex = null },
            )
        }
    }
}
