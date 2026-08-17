package com.voicedaw.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.voicedaw.app.ui.screens.ArrangeScreen
import com.voicedaw.app.ui.screens.DrumSequencerScreen
import com.voicedaw.app.ui.screens.MixerScreen
import com.voicedaw.app.ui.screens.PianoRollScreen
import com.voicedaw.app.ui.screens.RecordScreen
import com.voicedaw.app.ui.screens.VocalPadScreen
import com.voicedaw.audioengine.AudioEngineViewModel
import com.voicedaw.audioengine.recording.effectiveDurationFrames
import com.voicedaw.midi.MidiViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import com.voicedaw.app.ui.screens.ProjectsScreen
import com.voicedaw.app.ui.components.FloatingWindow

private val topLevelDestinations = listOf(
    DawDestination.Record,
    DawDestination.Arrange,
    DawDestination.Mixer,
    DawDestination.PianoRoll,
    DawDestination.DrumSequencer,
    DawDestination.VocalPads
)

// App Root

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceDawApp(windowSizeClass: WindowSizeClass? = null) {
    var currentProjectId by remember { mutableStateOf<String?>(null) }
    val engineVm: AudioEngineViewModel = viewModel()
    val midiVm:   MidiViewModel        = viewModel()

    if (currentProjectId == null) {
        ProjectsScreen(onProjectSelected = { projectId ->
            engineVm.loadProject(projectId)
            currentProjectId = projectId
        })
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    var showMixerWindow by remember { mutableStateOf(false) }
    var showPianoRollWindow by remember { mutableStateOf(false) }
    var mixerZIndex by remember { mutableFloatStateOf(1f) }
    var pianoRollZIndex by remember { mutableFloatStateOf(1f) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(currentProjectId) {
        if (currentProjectId != null && currentProjectId != "default") {
            val repo = com.voicedaw.projectformat.ProjectRepository(context)
            while (true) {
                kotlinx.coroutines.delay(30_000L)
                val tracks = engineVm.recordingManager.tracks.value
                val manifest = com.voicedaw.projectformat.ProjectManifest(
                    projectId = currentProjectId!!,
                    name = currentProjectId!!,
                    tracks = tracks.map { t ->
                        com.voicedaw.projectformat.TrackManifest(
                            trackId = "track_${t.trackIndex}",
                            name = t.name,
                            type = com.voicedaw.projectformat.TrackType.AUDIO,
                            index = t.trackIndex,
                            gain = t.volume,
                            pan = t.pan,
                            muted = t.isMuted,
                            soloed = t.isSolo,
                            clips = t.takes.map { clip ->
                                com.voicedaw.projectformat.AudioClipManifest(
                                    clipId = "clip_${clip.clipId}",
                                    name = "Clip ${clip.clipId}",
                                    audioFilePath = clip.filePath,
                                    startFrameInProject = clip.startSample,
                                    durationFrames = clip.effectiveDurationFrames(com.voicedaw.audioengine.recording.AudioClip.SAMPLE_RATE)
                                )
                            }
                        )
                    }
                )
                repo.saveProject(manifest)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Expanded || maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("Voice DAW") },
                    actions = {
                        val state by engineVm.state.collectAsState()
                        var deviceMenuExpanded by remember { mutableStateOf(false) }
                        val currentDevice = state.availableOutputDevices.find { it.id == state.selectedOutputDeviceId }
                        val deviceLabel = currentDevice?.name ?: "Default Audio"

                        Box {
                            androidx.compose.material3.TextButton(onClick = { deviceMenuExpanded = true }) {
                                Text(deviceLabel, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = deviceMenuExpanded,
                                onDismissRequest = { deviceMenuExpanded = false }
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text("Default Audio") },
                                    onClick = {
                                        engineVm.setOutputDevice(0)
                                        deviceMenuExpanded = false
                                    }
                                )
                                state.availableOutputDevices.forEach { device ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(device.name) },
                                        onClick = {
                                            engineVm.setOutputDevice(device.id)
                                            deviceMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            scope.launch {
                                val wavFile = File(context.getExternalFilesDir(null), "export.wav")
                                val m4aFile = File(context.getExternalFilesDir(null), "export.m4a")
                                val successWav = engineVm.bounceToWav(wavFile.absolutePath)
                                if (successWav.isSuccess) {
                                    val aacResult = com.voicedaw.audioengine.export.AudioExporter.convertWavToAac(wavFile, m4aFile)
                                    if (aacResult.isSuccess) {
                                        android.widget.Toast.makeText(context, "Exported to WAV and M4A", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "WAV Exported, but AAC failed: ${aacResult.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.SaveAlt, contentDescription = "Export WAV")
                        }
                    }
                )
            },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar {
                        topLevelDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (isExpanded) {
                    NavigationRail {
                        topLevelDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true,
                                onClick = {
                                    if (dest == DawDestination.Mixer) {
                                        showMixerWindow = !showMixerWindow
                                        mixerZIndex = maxOf(mixerZIndex, pianoRollZIndex) + 1f
                                    } else if (dest == DawDestination.PianoRoll) {
                                        showPianoRollWindow = !showPianoRollWindow
                                        pianoRollZIndex = maxOf(mixerZIndex, pianoRollZIndex) + 1f
                                    } else {
                                        navController.navigate(dest.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = DawDestination.Record.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(DawDestination.Record.route) {
                    RecordScreen(engineVm = engineVm)
                }
                composable(DawDestination.Arrange.route) {
                    ArrangeScreen(engineVm = engineVm)
                }
                composable(DawDestination.Mixer.route) {
                    MixerScreen(audioViewModel = engineVm)
                }
                composable(DawDestination.PianoRoll.route) {
                    PianoRollScreen(engineVm = engineVm, midiVm = midiVm)
                }
                composable(DawDestination.DrumSequencer.route) {
                    DrumSequencerScreen(engineVm = engineVm)
                }
                composable(DawDestination.VocalPads.route) {
                    VocalPadScreen(engineVm = engineVm)
                }
            }

            // Floating Windows
            if (isExpanded) {
                if (showMixerWindow) {
                    FloatingWindow(
                        title = "Mixer",
                        onClose = { showMixerWindow = false },
                        initialX = 100f,
                        initialY = 50f,
                        zIndex = mixerZIndex,
                        onFocus = { mixerZIndex = maxOf(mixerZIndex, pianoRollZIndex) + 1f }
                    ) {
                        MixerScreen(audioViewModel = engineVm)
                    }
                }
                if (showPianoRollWindow) {
                    FloatingWindow(
                        title = "Piano Roll",
                        onClose = { showPianoRollWindow = false },
                        initialX = 400f,
                        initialY = 100f,
                        zIndex = pianoRollZIndex,
                        onFocus = { pianoRollZIndex = maxOf(mixerZIndex, pianoRollZIndex) + 1f }
                    ) {
                        PianoRollScreen(engineVm = engineVm, midiVm = midiVm)
                    }
                }
            }
        } // end Box
            } // end Row
        } // end Scaffold
    } // end BoxWithConstraints
} // end VoiceDawApp
