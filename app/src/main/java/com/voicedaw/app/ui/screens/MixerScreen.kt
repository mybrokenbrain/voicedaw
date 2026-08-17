package com.voicedaw.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voicedaw.audioengine.AudioEngineViewModel
import com.voicedaw.audioengine.DelayState
import com.voicedaw.audioengine.GateState
import com.voicedaw.audioengine.SaturationState
import com.voicedaw.audioengine.mixer.CompressorState
import com.voicedaw.audioengine.mixer.EqState
import com.voicedaw.audioengine.mixer.ReverbState
import com.voicedaw.audioengine.mixer.TrackMixerState
import kotlinx.coroutines.delay

@Composable
fun MixerScreen(
    audioViewModel: AudioEngineViewModel = viewModel()
) {
    val state by audioViewModel.state.collectAsState()
    var selectedTrackForEq by remember { mutableStateOf<Int?>(null) }
    var selectedTrackForComp by remember { mutableStateOf<Int?>(null) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            audioViewModel.loadReferenceTrack(uri.toString())
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tracks
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.mixerTracks) { track ->
                ChannelStrip(
                    track = track,
                    onVolumeChange = { audioViewModel.setTrackVolume(track.trackIndex, it) },
                    onPanChange = { audioViewModel.setTrackPan(track.trackIndex, it) },
                    onMuteToggle = { audioViewModel.setTrackMute(track.trackIndex, !track.muted) },
                    onSoloToggle = { audioViewModel.setTrackSolo(track.trackIndex, !track.soloed) },
                    onReverbSendChange = { audioViewModel.setTrackReverbSend(track.trackIndex, it) },
                    onDelaySendChange = { audioViewModel.setTrackDelaySend(track.trackIndex, it) },
                    onSubmixBusChange = { audioViewModel.setTrackSubmixBus(track.trackIndex, it) },
                    onOpenEq = { selectedTrackForEq = track.trackIndex },
                    onOpenComp = { selectedTrackForComp = track.trackIndex },
                    getGainReduction = { audioViewModel.getCompGainReduction(track.trackIndex) }
                )
            }
        }

        // Master Reverb Strip
        Divider(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
        MasterStrip(
            reverb = state.masterReverb,
            onReverbChange = { audioViewModel.setReverbState(it) },
            saturation = state.masterSaturation,
            onSaturationChange = { audioViewModel.setMasterSaturation(it) },
            delay = state.masterDelay,
            onDelayChange = { audioViewModel.setMasterDelay(it) },
            gate = state.masterGate,
            onGateChange = { audioViewModel.setMasterGate(it) },
            onLoadReference = { launcher.launch("audio/*") },
            listenToReference = state.listenToReference,
            onToggleListenToReference = { audioViewModel.setListenToReference(it) },
            currentLufs = state.integratedLUFS,
            onApplyPreset = { audioViewModel.applyMasteringPreset(it) }
        )
    }

    // Dialogs
    selectedTrackForEq?.let { trackIndex ->
        EqDialog(
            eq = state.mixerTracks[trackIndex].eq,
            onDismiss = { selectedTrackForEq = null },
            onChange = { audioViewModel.setEqState(trackIndex, it) }
        )
    }

    selectedTrackForComp?.let { trackIndex ->
        CompDialog(
            comp = state.mixerTracks[trackIndex].comp,
            onDismiss = { selectedTrackForComp = null },
            onChange = { audioViewModel.setCompState(trackIndex, it) }
        )
    }
}

@Composable
fun ChannelStrip(
    track: TrackMixerState,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onReverbSendChange: (Float) -> Unit,
    onDelaySendChange: (Float) -> Unit,
    onSubmixBusChange: (com.voicedaw.audioengine.mixer.SubmixBus) -> Unit,
    onOpenEq: () -> Unit,
    onOpenComp: () -> Unit,
    getGainReduction: () -> Float
) {
    var gr by remember { mutableFloatStateOf(0f) }
    var busExpanded by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        while(true) {
            gr = getGainReduction()
            delay(50)
        }
    }

    Column(
        modifier = Modifier
            .width(100.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val trackName = if (track.trackIndex == 0) "Synth" else "Track ${track.trackIndex}"
        Text(trackName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(4.dp))

        Box {
            OutlinedButton(
                onClick = { busExpanded = true },
                modifier = Modifier.fillMaxWidth().height(26.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(track.targetBus.displayName, fontSize = 9.sp)
            }
            DropdownMenu(expanded = busExpanded, onDismissRequest = { busExpanded = false }) {
                com.voicedaw.audioengine.mixer.SubmixBus.values().forEach { bus ->
                    DropdownMenuItem(
                        text = { Text(bus.displayName, fontSize = 11.sp) },
                        onClick = {
                            onSubmixBusChange(bus)
                            busExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // FX Buttons
        Button(
            onClick = onOpenEq,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (track.eq.enabled) MaterialTheme.colorScheme.primary else Color.Gray)
        ) {
            Text("EQ", fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onOpenComp,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (track.comp.enabled) MaterialTheme.colorScheme.primary else Color.Gray)
        ) {
            Text("COMP ${if (gr < -0.1f) String.format("%.1f", gr) else ""}", fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reverb Send
        Text("Rev Send", fontSize = 10.sp)
        Slider(
            value = track.reverbSend,
            onValueChange = onReverbSendChange,
            valueRange = 0f..1f,
            modifier = Modifier.height(24.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Delay Send
        Text("Dly Send", fontSize = 10.sp)
        Slider(
            value = track.delaySend,
            onValueChange = onDelaySendChange,
            valueRange = 0f..1f,
            modifier = Modifier.height(24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Pan
        Text("Pan", fontSize = 10.sp)
        Slider(
            value = track.pan,
            onValueChange = onPanChange,
            valueRange = -1f..1f,
            modifier = Modifier.height(24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Mute / Solo
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilterChip(
                selected = track.muted,
                onClick = onMuteToggle,
                label = { Text("M", fontSize = 10.sp) },
                modifier = Modifier.size(width = 36.dp, height = 32.dp)
            )
            FilterChip(
                selected = track.soloed,
                onClick = onSoloToggle,
                label = { Text("S", fontSize = 10.sp) },
                modifier = Modifier.size(width = 36.dp, height = 32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Volume Fader
        Text("${track.volumeDb.toInt()} dB", fontSize = 10.sp)
        Box(modifier = Modifier.weight(1f)) {
        }
        Slider(
            value = track.volumeDb,
            onValueChange = onVolumeChange,
            valueRange = -60f..6f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MasterStrip(
    reverb: ReverbState,
    onReverbChange: (ReverbState) -> Unit,
    saturation: SaturationState,
    onSaturationChange: (SaturationState) -> Unit,
    delay: DelayState,
    onDelayChange: (DelayState) -> Unit,
    gate: GateState,
    onGateChange: (GateState) -> Unit,
    onLoadReference: () -> Unit,
    listenToReference: Boolean,
    onToggleListenToReference: (Boolean) -> Unit,
    currentLufs: Float,
    onApplyPreset: (String) -> Unit
) {
    var showPresetMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(120.dp)
            .fillMaxHeight()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MASTER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Reverb", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("On", fontSize = 10.sp)
            Switch(
                checked = reverb.enabled,
                onCheckedChange = { onReverbChange(reverb.copy(enabled = it)) },
                modifier = Modifier.scale(0.7f)
            )
        }

        Text("Room", fontSize = 10.sp)
        Slider(value = reverb.roomSize, onValueChange = { onReverbChange(reverb.copy(roomSize = it)) })
        
        Text("Damp", fontSize = 10.sp)
        Slider(value = reverb.damping, onValueChange = { onReverbChange(reverb.copy(damping = it)) })
        
        Text("Mix", fontSize = 10.sp)
        Slider(value = reverb.wetDry, onValueChange = { onReverbChange(reverb.copy(wetDry = it)) })

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(8.dp))

        // Master FX Chain
        Text("Saturation", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("On", fontSize = 10.sp)
            Switch(
                checked = saturation.enabled,
                onCheckedChange = { onSaturationChange(saturation.copy(enabled = it)) },
                modifier = Modifier.scale(0.7f)
            )
        }
        Text("Drive", fontSize = 10.sp)
        Slider(
            value = saturation.drive,
            onValueChange = { onSaturationChange(saturation.copy(drive = it)) },
            valueRange = 1f..10f
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Delay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("On", fontSize = 10.sp)
            Switch(
                checked = delay.enabled,
                onCheckedChange = { onDelayChange(delay.copy(enabled = it)) },
                modifier = Modifier.scale(0.7f)
            )
        }
        Text("Time", fontSize = 10.sp)
        Slider(
            value = delay.timeMs,
            onValueChange = { onDelayChange(delay.copy(timeMs = it)) },
            valueRange = 10f..1000f
        )
        Text("Feedback", fontSize = 10.sp)
        Slider(
            value = delay.feedback,
            onValueChange = { onDelayChange(delay.copy(feedback = it)) },
            valueRange = 0f..0.95f
        )
        Text("Mix", fontSize = 10.sp)
        Slider(
            value = delay.mix,
            onValueChange = { onDelayChange(delay.copy(mix = it)) },
            valueRange = 0f..1f
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Gate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("On", fontSize = 10.sp)
            Switch(
                checked = gate.enabled,
                onCheckedChange = { onGateChange(gate.copy(enabled = it)) },
                modifier = Modifier.scale(0.7f)
            )
        }
        Text("Threshold", fontSize = 10.sp)
        Slider(
            value = gate.thresholdDb,
            onValueChange = { onGateChange(gate.copy(thresholdDb = it)) },
            valueRange = -80f..0f
        )
        Text("Attack", fontSize = 10.sp)
        Slider(
            value = gate.attackMs,
            onValueChange = { onGateChange(gate.copy(attackMs = it)) },
            valueRange = 0.1f..50f
        )
        Text("Release", fontSize = 10.sp)
        Slider(
            value = gate.releaseMs,
            onValueChange = { onGateChange(gate.copy(releaseMs = it)) },
            valueRange = 10f..1000f
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(8.dp))

        Text("MASTERING", fontSize = 12.sp, fontWeight = FontWeight.Bold)

        Text("LUFS: ${if (currentLufs > -69f) String.format("%.1f", currentLufs) else "--"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)

        Spacer(modifier = Modifier.height(4.dp))
        
        Box {
            Button(
                onClick = { showPresetMenu = true },
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Intelligent Master", fontSize = 9.sp)
            }
            DropdownMenu(
                expanded = showPresetMenu,
                onDismissRequest = { showPresetMenu = false }
            ) {
                com.voicedaw.audioengine.mixer.MasteringPresets.presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.name, fontSize = 12.sp) },
                        onClick = {
                            onApplyPreset(preset.name)
                            showPresetMenu = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onLoadReference,
            modifier = Modifier.fillMaxWidth().height(32.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("Load Ref", fontSize = 10.sp)
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("A/B Ref", fontSize = 10.sp)
            Switch(
                checked = listenToReference,
                onCheckedChange = onToggleListenToReference,
                modifier = Modifier.scale(0.7f)
            )
        }
    }
}

@Composable
fun EqDialog(eq: EqState, onDismiss: () -> Unit, onChange: (EqState) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parametric EQ") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable")
                    Switch(checked = eq.enabled, onCheckedChange = { onChange(eq.copy(enabled = it)) })
                }
                Text("Low Shelf: ${eq.lowShelfFreq.toInt()} Hz, ${eq.lowShelfGain.toInt()} dB")
                Slider(value = eq.lowShelfGain, onValueChange = { onChange(eq.copy(lowShelfGain = it)) }, valueRange = -24f..24f)
                
                Text("Mid Band: ${eq.midFreq.toInt()} Hz, ${eq.midGain.toInt()} dB (Q=${String.format("%.2f", eq.midQ)})")
                Slider(value = eq.midFreq, onValueChange = { onChange(eq.copy(midFreq = it)) }, valueRange = 200f..5000f)
                Slider(value = eq.midGain, onValueChange = { onChange(eq.copy(midGain = it)) }, valueRange = -24f..24f)

                Text("High Shelf: ${eq.highShelfFreq.toInt()} Hz, ${eq.highShelfGain.toInt()} dB")
                Slider(value = eq.highShelfGain, onValueChange = { onChange(eq.copy(highShelfGain = it)) }, valueRange = -24f..24f)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun CompDialog(comp: CompressorState, onDismiss: () -> Unit, onChange: (CompressorState) -> Unit) {
    var sidechainExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compressor & Sidechain Ducking") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable")
                    Switch(checked = comp.enabled, onCheckedChange = { onChange(comp.copy(enabled = it)) })
                }
                Text("Threshold: ${comp.thresholdDb.toInt()} dB")
                Slider(value = comp.thresholdDb, onValueChange = { onChange(comp.copy(thresholdDb = it)) }, valueRange = -60f..0f)
                
                Text("Ratio: ${comp.ratio.toInt()}:1")
                Slider(value = comp.ratio, onValueChange = { onChange(comp.copy(ratio = it)) }, valueRange = 1f..20f)

                Text("Attack: ${comp.attackMs.toInt()} ms")
                Slider(value = comp.attackMs, onValueChange = { onChange(comp.copy(attackMs = it)) }, valueRange = 1f..100f)

                Text("Release: ${comp.releaseMs.toInt()} ms")
                Slider(value = comp.releaseMs, onValueChange = { onChange(comp.copy(releaseMs = it)) }, valueRange = 10f..1000f)

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sidechain Ducking")
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = comp.sidechainEnabled,
                        onCheckedChange = { onChange(comp.copy(sidechainEnabled = it)) }
                    )
                }

                if (comp.sidechainEnabled) {
                    Text("Sidechain Key Source Track:", fontSize = 12.sp)
                    Box {
                        OutlinedButton(
                            onClick = { sidechainExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            val srcLabel = if (comp.sidechainSourceTrackIndex < 0) "None (Self Key)" else if (comp.sidechainSourceTrackIndex == 0) "Track 0 (Synth/Kick)" else "Track ${comp.sidechainSourceTrackIndex}"
                            Text(srcLabel, fontSize = 11.sp)
                        }
                        DropdownMenu(expanded = sidechainExpanded, onDismissRequest = { sidechainExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Track 0 (Synth/Kick)", fontSize = 11.sp) },
                                onClick = {
                                    onChange(comp.copy(sidechainSourceTrackIndex = 0))
                                    sidechainExpanded = false
                                }
                            )
                            (1..7).forEach { trkIdx ->
                                DropdownMenuItem(
                                    text = { Text("Track $trkIdx", fontSize = 11.sp) },
                                    onClick = {
                                        onChange(comp.copy(sidechainSourceTrackIndex = trkIdx))
                                        sidechainExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
