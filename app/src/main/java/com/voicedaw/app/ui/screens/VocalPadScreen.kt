package com.voicedaw.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.voicedaw.audioengine.AudioEngineViewModel
import com.voicedaw.audioengine.sampling.PadSampleData
import com.voicedaw.app.ui.components.SampleEditorSheet

@Composable
fun VocalPadScreen(engineVm: AudioEngineViewModel) {
    val pads by engineVm.pads.collectAsState()
    val perfState by engineVm.performanceFxState.collectAsState()
    val context = LocalContext.current
    var editingPad by remember { mutableStateOf<PadSampleData?>(null) }
    var activeTab by remember { mutableIntStateOf(0) }
    
    var selectedPadForLoad by remember { mutableStateOf<Int?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && selectedPadForLoad != null) {
            engineVm.loadPadSample(selectedPadForLoad!!, uri)
        }
        selectedPadForLoad = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vocal Pads & FX", style = MaterialTheme.typography.headlineMedium)
            
            TabRow(selectedTabIndex = activeTab, modifier = Modifier.width(240.dp)) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                    Text("Pads", modifier = Modifier.padding(8.dp))
                }
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                    Text("Perf FX", modifier = Modifier.padding(8.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        if (activeTab == 0) {
            Column(modifier = Modifier.weight(1f)) {
                for (row in 0..3) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0..3) {
                            val index = row * 4 + col
                            val pad = pads.getOrNull(index) ?: PadSampleData(padIndex = index)
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp)
                                    .fillMaxHeight()
                                    .background(if (pad.samplePath.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .pointerInput(pad.playbackMode) {
                                        detectTapGestures(
                                            onPress = {
                                                engineVm.triggerPad(index)
                                                val released = tryAwaitRelease()
                                                if (released && pad.playbackMode == com.voicedaw.audioengine.sampling.SamplePlaybackMode.HOLD_TO_PLAY) {
                                                    engineVm.releasePad(index)
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Text(pad.name, modifier = Modifier.align(Alignment.Center))
                                if (pad.samplePath.isNotEmpty()) {
                                    IconButton(
                                        onClick = { editingPad = pad },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Pad")
                                    }
                                }
                                Row(
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = { 
                                            selectedPadForLoad = index
                                            launcher.launch("audio/*")
                                        },
                                        contentPadding = PaddingValues(4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Load", style = MaterialTheme.typography.bodySmall)
                                    }

                                    OutlinedButton(
                                        onClick = { 
                                            engineVm.resampleMasterToPad(index)
                                        },
                                        contentPadding = PaddingValues(4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Rec Mix", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Performance FX Rack Matrix
            Column(modifier = Modifier.weight(1f)) {
                val fxTypes = com.voicedaw.audioengine.performance.PerformanceFxType.values()
                for (row in 0..3) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0..3) {
                            val index = row * 4 + col
                            val fx = fxTypes.getOrNull(index) ?: break
                            val isActive = perfState.activeFxType == fx

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp)
                                    .fillMaxHeight()
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiaryContainer,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .pointerInput(fx) {
                                        detectTapGestures(
                                            onPress = {
                                                engineVm.triggerPerformanceFx(fx, hold = true)
                                                tryAwaitRelease()
                                                engineVm.triggerPerformanceFx(null, hold = false)
                                            }
                                        )
                                    }
                            ) {
                                Text(
                                    fx.displayName,
                                    modifier = Modifier.align(Alignment.Center),
                                    color = if (isActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onTertiaryContainer,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    editingPad?.let { pad ->
        SampleEditorSheet(
            padSample = pad,
            engineVm = engineVm,
            onDismiss = { editingPad = null },
            onUpdatePad = { updatedPad ->
                engineVm.updatePadAndSync(updatedPad)
                editingPad = null
            }
        )
    }
}
