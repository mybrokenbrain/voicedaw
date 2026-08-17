package com.voicedaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voicedaw.audioengine.AudioEngineViewModel
import com.voicedaw.midi.PianoNote
import kotlinx.coroutines.delay

// Constants

private const val STEPS = 16


private val DRUM_ROWS = listOf(
    "Kick"    to 36,
    "Snare"   to 38,
    "Hi-Hat"  to 42,
    "Open HH" to 46,
    "Crash"   to 49,
    "Clap"    to 39,
    "Tom Lo"  to 45,
    "Tom Hi"  to 50,
)


private fun levelToVelocity(level: Int) = when (level) {
    1 -> 60
    2 -> 90
    3 -> 120
    else -> 0
}

// Screen

@Composable
fun DrumSequencerScreen(
    engineVm: AudioEngineViewModel = viewModel(),
) {
    val state by engineVm.state.collectAsState()


    val grid = remember { Array(DRUM_ROWS.size) { IntArray(STEPS) { 0 } } }

    var gridVersion by remember { mutableIntStateOf(0) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(-1) }

    val bpm = state.bpm.coerceAtLeast(40f)

    val stepMs = ((60_000f / bpm) / 4f).toLong().coerceAtLeast(50L)

    // Sequencer playback loop
    LaunchedEffect(isPlaying, bpm) {
        if (!isPlaying) { currentStep = -1; return@LaunchedEffect }
        var step = 0
        while (isPlaying) {
            currentStep = step
            DRUM_ROWS.forEachIndexed { rowIdx, (_, midiNote) ->
                val vel = levelToVelocity(grid[rowIdx][step])
                if (vel > 0) engineVm.noteOn(midiNote, vel / 127f)
            }
            delay(40L)
            DRUM_ROWS.forEach { (_, midiNote) -> engineVm.noteOff(midiNote) }
            delay((stepMs - 40L).coerceAtLeast(10L))
            step = (step + 1) % STEPS
        }
    }

    val noteRepeatRate by engineVm.noteRepeatRate.collectAsState()
    var repeatExpanded by remember { mutableStateOf(false) }

    // Export helper
    fun exportToPianoRoll() {
        val pianoNotes = mutableListOf<PianoNote>()
        val repeatRate = engineVm.noteRepeatRate.value
        DRUM_ROWS.forEachIndexed { rowIdx, (_, midiNote) ->
            for (step in 0 until STEPS) {
                val vel = levelToVelocity(grid[rowIdx][step])
                if (vel > 0) {
                    val stepStartBeat = step * 0.25f
                    val stepDur = 0.25f
                    if (repeatRate != com.voicedaw.audioengine.NoteRepeatRate.OFF && repeatRate.beats > 0f) {
                        val subs = repeatRate.getSubdivisions(stepDur)
                        val subDur = stepDur / subs
                        for (i in 0 until subs) {
                            pianoNotes.add(
                                PianoNote(
                                    midiNote = midiNote,
                                    startBeat = stepStartBeat + (i * subDur),
                                    durationBeats = subDur,
                                    velocity = vel,
                                    midiChannel = 10,
                                )
                            )
                        }
                    } else {
                        pianoNotes.add(
                            PianoNote(
                                midiNote = midiNote,
                                startBeat = stepStartBeat,
                                durationBeats = stepDur,
                                velocity = vel,
                                midiChannel = 10,
                            )
                        )
                    }
                }
            }
        }
        engineVm.emitDrumSequencerNotes(pianoNotes)
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "DRUM SEQUENCER",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "BPM ${bpm.toInt()} · 16 steps · Roll: ${noteRepeatRate.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    OutlinedButton(
                        onClick = { repeatExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Roll: ${noteRepeatRate.label}", style = MaterialTheme.typography.labelSmall)
                    }
                    DropdownMenu(expanded = repeatExpanded, onDismissRequest = { repeatExpanded = false }) {
                        com.voicedaw.audioengine.NoteRepeatRate.values().forEach { rate ->
                            DropdownMenuItem(
                                text = { Text(rate.label) },
                                onClick = {
                                    engineVm.setNoteRepeatRate(rate)
                                    repeatExpanded = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = if (isPlaying) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { exportToPianoRoll() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Export to Piano Roll",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Step numbers header row
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 68.dp),
        ) {
            for (step in 0 until STEPS) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val isBeat = step % 4 == 0
                    Text(
                        if (isBeat) "${step / 4 + 1}" else "·",
                        fontSize = 9.sp,
                        color = if (isBeat) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(4.dp))

        // Grid rows — use gridVersion as key so Compose re-reads grid on mutation
        key(gridVersion) {
            DRUM_ROWS.forEachIndexed { rowIdx, (name, _) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Row label
                    Text(
                        name,
                        modifier = Modifier.width(68.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    // Steps
                    for (step in 0 until STEPS) {
                        val velLevel = grid[rowIdx][step]
                        val isActive = step == currentStep && isPlaying

                        val cellColor = when {
                            isActive && velLevel > 0 -> MaterialTheme.colorScheme.primary
                            isActive                 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            velLevel == 3            -> MaterialTheme.colorScheme.primary
                            velLevel == 2            -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            velLevel == 1            -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else                     -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val beatBorderWidth = if (step % 4 == 0) 1.5.dp else 0.5.dp

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .padding(horizontal = 1.dp)
                                .background(cellColor, RoundedCornerShape(3.dp))
                                .border(
                                    beatBorderWidth,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                    RoundedCornerShape(3.dp),
                                )
                                .clickable {
                                    grid[rowIdx][step] = (velLevel + 1) % 4
                                    gridVersion++   // trigger recompose
                                }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(Modifier.height(6.dp))

        // Velocity legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Velocity:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            listOf(
                "Low (60)"   to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                "Med (90)"   to MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                "High (120)" to MaterialTheme.colorScheme.primary,
            ).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(12.dp).background(color, RoundedCornerShape(2.dp)))
                    Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
        }
    }
}
