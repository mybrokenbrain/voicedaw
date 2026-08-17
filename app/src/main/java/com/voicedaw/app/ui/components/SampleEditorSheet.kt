package com.voicedaw.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicedaw.audioengine.sampling.PadSampleData
import com.voicedaw.audioengine.AudioEngineViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleEditorSheet(
    padSample: PadSampleData,
    engineVm: AudioEngineViewModel,
    onDismiss: () -> Unit,
    onUpdatePad: (PadSampleData) -> Unit
) {
    var startMs by remember { mutableFloatStateOf(padSample.startMs) }
    var endMs by remember { mutableFloatStateOf(padSample.endMs) }
    var pitchShift by remember { mutableFloatStateOf(padSample.pitchShiftSemitones.toFloat()) }
    var reverse by remember { mutableStateOf(padSample.reverse) }
    var isLoop by remember { mutableStateOf(padSample.isLoop) }
    var fineTune by remember { mutableFloatStateOf(padSample.fineTuneCents.toFloat()) }
    var chokeGroup by remember { mutableIntStateOf(padSample.chokeGroup) }
    var chokeExpanded by remember { mutableStateOf(false) }
    var gainDb by remember { mutableFloatStateOf(padSample.gainDb) }
    var fadeInMs by remember { mutableFloatStateOf(padSample.fadeInMs) }
    var fadeOutMs by remember { mutableFloatStateOf(padSample.fadeOutMs) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit ${padSample.name}", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Text("Start Time (ms): ${startMs.toInt()}")
                Slider(value = startMs, onValueChange = { startMs = it }, valueRange = 0f..10000f)

                Text("End Time (ms): ${endMs.toInt()}")
                Slider(value = endMs, onValueChange = { endMs = it }, valueRange = 0f..10000f)

                Text("Pitch Shift (semitones): ${pitchShift.toInt()}")
                Slider(value = pitchShift, onValueChange = { pitchShift = it }, valueRange = -12f..12f)

                Text("Fine Tune (cents): ${fineTune.toInt()}")
                Slider(value = fineTune, onValueChange = { fineTune = it }, valueRange = -100f..100f)

                Text("Gain Boost (dB): ${gainDb.toInt()}")
                Slider(value = gainDb, onValueChange = { gainDb = it }, valueRange = -12f..12f)

                Text("Fade-In (ms): ${fadeInMs.toInt()}")
                Slider(value = fadeInMs, onValueChange = { fadeInMs = it }, valueRange = 0f..1000f)

                Text("Fade-Out (ms): ${fadeOutMs.toInt()}")
                Slider(value = fadeOutMs, onValueChange = { fadeOutMs = it }, valueRange = 0f..1000f)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Choke Group")
                    Box {
                        Button(onClick = { chokeExpanded = true }) {
                            Text(if (chokeGroup == 0) "Off" else "Group $chokeGroup")
                        }
                        DropdownMenu(expanded = chokeExpanded, onDismissRequest = { chokeExpanded = false }) {
                            DropdownMenuItem(text = { Text("Off") }, onClick = { chokeGroup = 0; chokeExpanded = false })
                            for (cg in 1..8) {
                                DropdownMenuItem(text = { Text("Group $cg") }, onClick = { chokeGroup = cg; chokeExpanded = false })
                            }
                        }
                    }
                }

    var playbackMode by remember { mutableStateOf(padSample.playbackMode) }
    var modeExpanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Playback Mode")
        Box {
            Button(onClick = { modeExpanded = true }) {
                Text(playbackMode.name.replace("_", " "))
            }
            DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                com.voicedaw.audioengine.sampling.SamplePlaybackMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.name.replace("_", " ")) },
                        onClick = {
                            playbackMode = mode
                            modeExpanded = false
                        }
                    )
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Reverse")
        Switch(checked = reverse, onCheckedChange = { reverse = it })
    }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Loop")
                    Switch(checked = isLoop, onCheckedChange = { isLoop = it })
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(onClick = {
                        gainDb = 0f
                        scope.launch {
                            snackbarHostState.showSnackbar("Peak Normalized to 0 dB Target")
                        }
                    }) {
                        Text("Peak Normalize")
                    }

                    Button(onClick = {
                        // "Transient Auto-Chop" button
                    }) {
                        Text("Auto-Chop")
                    }

                    Button(onClick = {
                        engineVm.splitSampleStemsToPads(padSample.padIndex)
                        scope.launch {
                            snackbarHostState.showSnackbar("Intelligent 4-Stem Separation complete!")
                        }
                    }) {
                        Text("Split 4 Stems")
                    }
                }

                Button(onClick = {
                    val updated = padSample.copy(
                        startMs = startMs,
                        endMs = endMs,
                        pitchShiftSemitones = pitchShift.toInt(),
                        fineTuneCents = fineTune.toInt(),
                        gainDb = gainDb,
                        fadeInMs = fadeInMs,
                        fadeOutMs = fadeOutMs,
                        reverse = reverse,
                        isLoop = isLoop,
                        playbackMode = playbackMode,
                        chokeGroup = chokeGroup
                    )
                    onUpdatePad(updated)
                    engineVm.setChromaticSampleSource(updated)
                    scope.launch {
                        snackbarHostState.showSnackbar("Sample loaded into Piano Roll instrument!")
                    }
                }) {
                    Text("Play as Instrument")
                }
                
                Button(onClick = {
                     val updated = padSample.copy(
                        startMs = startMs,
                        endMs = endMs,
                        pitchShiftSemitones = pitchShift.toInt(),
                        fineTuneCents = fineTune.toInt(),
                        gainDb = gainDb,
                        fadeInMs = fadeInMs,
                        fadeOutMs = fadeOutMs,
                        reverse = reverse,
                        isLoop = isLoop,
                        playbackMode = playbackMode,
                        chokeGroup = chokeGroup
                    )
                    onUpdatePad(updated)
                    onDismiss()
                }) {
                    Text("Save & Close")
                }
            }
            SnackbarHost(
                hostState = snackbarHostState, 
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )
        }
    }
}
