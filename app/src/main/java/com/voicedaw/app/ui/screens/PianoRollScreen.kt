package com.voicedaw.app.ui.screens

import com.voicedaw.app.ui.components.NoteMonitorOverlay
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voicedaw.audioengine.AudioEngineViewModel
import com.voicedaw.audioengine.VoiceCaptureStateMachine
import com.voicedaw.audioengine.VoiceCaptureSettings
import com.voicedaw.audioengine.VelocityHardnessMode
import com.voicedaw.audioengine.ScalePreset
import com.voicedaw.audioengine.PitchMath
import com.voicedaw.midi.MidiFile
import com.voicedaw.midi.MidiViewModel
import com.voicedaw.midi.PianoNote
import kotlin.math.roundToInt

// Constants

private const val TOTAL_NOTES    = 88
private const val MIDI_NOTE_MIN  = 21
private const val MIDI_NOTE_MAX  = 108
private const val TOTAL_BEATS    = 32

private val WHITE_NOTES = setOf(0, 2, 4, 5, 7, 9, 11)

private fun isWhiteKey(midiNote: Int) = (midiNote % 12) in WHITE_NOTES
private val NOTE_NAMES = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")
private fun noteName(midiNote: Int) = NOTE_NAMES[midiNote % 12] + (midiNote / 12 - 1)

// Quantize Grid Values
private val QUANTIZE_VALUES = listOf(0.25f, 0.5f, 1f, 2f)
private val QUANTIZE_LABELS = listOf("1/16", "1/8", "1/4", "1/2")

// Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PianoRollScreen(
    engineVm: AudioEngineViewModel = viewModel(),
    midiVm:   MidiViewModel        = viewModel(),
) {
    var notes by remember { mutableStateOf(listOf<PianoNote>()) }
    var quantizeBeats by remember { mutableFloatStateOf(0.25f) }   // 1/16 default
    var showDeviceSheet by remember { mutableStateOf(false) }
    var alignCaptureToStart by remember { mutableStateOf(true) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val midiState by midiVm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val engineState by engineVm.state.collectAsState()

    // Voice Capture State
    var isVoiceCapturing by remember { mutableStateOf(false) }
    var showVoiceSettingsSheet by remember { mutableStateOf(false) }
    val voicePreviewNotes by engineVm.voicePreviewNotes.collectAsState()
    val liveBend by engineVm.livePitchBendSemitones.collectAsState()
    val voiceCaptureActive by engineVm.voiceCaptureActive.collectAsState()
    val calibratedAmpFloor by engineVm.calibratedAmpFloor.collectAsState()
    val voiceSettings by engineVm.voiceSettings.collectAsState()
    val isPreviewPlaying by engineVm.isPreviewPlaying.collectAsState()

    LaunchedEffect(Unit) {
        midiVm.onNoteEvent = { ev ->
            if (ev.isNoteOn) engineVm.noteOn(ev.note, ev.velocity.toFloat() / 127f)
            else             engineVm.noteOff(ev.note)
        }
        midiVm.onCcEvent = { cc, value ->
            val mappings = midiVm.state.value.learnedMappings
            if (mappings[cc] == "BPM") {
                val bpm = 60f + value.toFloat()
                engineVm.setBpm(bpm)
            }
        }
    }

    LaunchedEffect(Unit) {
        engineVm.pendingMidiNotes.collect { incoming ->
            if (incoming.isNotEmpty()) {
                val shifted = if (alignCaptureToStart) {
                    val minBeat = incoming.minOf { it.startBeat }
                    incoming.map { it.copy(startBeat = it.startBeat - minBeat) }
                } else {
                    incoming
                }
                notes = (notes + shifted).sortedBy { it.startBeat }
                snackbar.showSnackbar(
                    message = "${incoming.size} notes added from recording",
                    duration = SnackbarDuration.Short,
                )
            }
        }
    }

    // File Pickers

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/midi")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                MidiFile.exportMultiTrack(notes, os, engineState.bpm)
            }
        } catch (e: Exception) {
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { ins ->
                val imported = MidiFile.readFromStream(ins, engineState.bpm)
                notes = imported
            }
        } catch (e: Exception) {
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            // Header Toolbar
            Surface(shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "PIANO ROLL",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(Modifier.weight(1f))

                    val noteRepeatRate by engineVm.noteRepeatRate.collectAsState()

                    // Quantize Selector
                    QuantizeButton(
                        currentBeats = quantizeBeats,
                        onSelect = { quantizeBeats = it },
                    )

                    // Note Repeat Selector
                    NoteRepeatButton(
                        currentRate = noteRepeatRate,
                        onSelect = { engineVm.setNoteRepeatRate(it) },
                    )
                    
                    // Voice-to-MIDI Cleanup
                    IconButton(
                        onClick = {
                            notes = com.voicedaw.midi.MidiCleanup.cleanupVocalTake(
                                rawNotes = notes,
                                config = com.voicedaw.midi.MidiCleanup.Config(quantizeBeats = quantizeBeats)
                            )
                        },
                        enabled = notes.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.AutoFixHigh, "Cleanup Vocal MIDI", modifier = Modifier.size(20.dp))
                    }

                    // MIDI import
                    IconButton(onClick = { importLauncher.launch(arrayOf("audio/midi", "audio/x-midi", "*/*")) }) {
                        Icon(Icons.Default.FileOpen, "Import MIDI", modifier = Modifier.size(20.dp))
                    }

                    // MIDI export
                    IconButton(
                        onClick = { exportLauncher.launch("clip.mid") },
                        enabled = notes.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Save, "Export MIDI", modifier = Modifier.size(20.dp))
                    }

                    // MIDI device panel
                    val deviceCount = midiState.connectedDevices.size + midiState.bleScanResults.size
                    BadgedBox(
                        badge = {
                            if (deviceCount > 0) Badge { Text("$deviceCount") }
                        }
                    ) {
                        IconButton(onClick = { showDeviceSheet = true }) {
                            Icon(Icons.Default.Usb, "MIDI Devices", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Note count
                    Text(
                        "${notes.size} notes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, "More options", modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = alignCaptureToStart,
                                            onCheckedChange = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Align capture to beat 1")
                                    }
                                },
                                onClick = {
                                    alignCaptureToStart = !alignCaptureToStart
                                    showOverflowMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // MIDI Learn Banner
            if (midiState.midiLearnActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Move a knob/fader on your controller to map it to '${midiState.learnTargetName}'",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { midiVm.cancelMidiLearn() }) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Voice Input Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = {
                        if (isVoiceCapturing) {
                            engineVm.stopVoiceCapture()
                            isVoiceCapturing = false
                        } else {
                            engineVm.startVoiceCapture()
                            isVoiceCapturing = true
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isVoiceCapturing)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        imageVector = if (isVoiceCapturing) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isVoiceCapturing) "Stop voice capture" else "Start voice input",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isVoiceCapturing) "Stop" else "Voice Input",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                IconButton(
                    onClick = { showVoiceSettingsSheet = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Voice Recognition Settings",
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (!isVoiceCapturing && voicePreviewNotes.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = {
                            if (isPreviewPlaying) engineVm.stopVoicePreviewPlayback()
                            else engineVm.startVoicePreviewPlayback()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewPlaying) "Stop Preview" else "Play Preview",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Button(
                        onClick = {
                            engineVm.stopVoicePreviewPlayback()
                            val committed = engineVm.commitVoiceCapture(engineState.bpm)
                            if (committed.isNotEmpty()) {
                                notes = (notes + committed).sortedBy { it.startBeat }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Commit", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            engineVm.stopVoicePreviewPlayback()
                            engineVm.discardVoiceCapture()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text("Discard", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        "${voicePreviewNotes.size} notes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (isVoiceCapturing && engineState.pitchHz > 0f) {
                    val midiNum = VoiceCaptureStateMachine.frequencyToMidiNote(engineState.pitchHz)
                    Text(
                        "♪ $midiNum (${engineState.pitchHz.toInt()} Hz)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Piano Roll Body
            PianoRollBody(
                notes         = notes,
                voicePreview  = voicePreviewNotes,
                ghostNotes    = emptyList(),
                activePitchClasses = voiceSettings.activePitchClasses,
                liveBend      = liveBend,
                quantizeBeats = quantizeBeats,
                onAddNote     = { note ->
                    notes = notes + note
                    engineVm.noteOn(note.midiNote, note.velocity.toFloat())
                },
                onRemoveNote  = { note ->
                    notes = notes - note
                    engineVm.noteOff(note.midiNote)
                },
                onUpdateNote  = { old, new ->
                    notes = notes.map { if (it == old) new else it }
                },
                onKeyPress    = { midiNote -> engineVm.noteOn(midiNote, 0.8f) },
                onKeyRelease  = { midiNote -> engineVm.noteOff(midiNote) },
                modifier      = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

    NoteMonitorOverlay(
        visible        = voiceCaptureActive && engineState.voiceInputMode == 1,
        pitchHz        = engineState.pitchHz,
        pitchAmp       = engineState.pitchAmp,
        ampFloor       = calibratedAmpFloor,
        isRecording    = engineState.isRecording,
        onToggleRecord = { engineVm.toggleRecord() },
        onStop         = { engineVm.stopVoiceCapture() },
    )
}

    // MIDI Device Sheet
    if (showDeviceSheet) {
        MidiDeviceSheet(
            midiVm    = midiVm,
            midiState = midiState,
            onDismiss = { showDeviceSheet = false },
        )
    }

    if (showVoiceSettingsSheet) {
        VoiceSettingsSheet(
            engineVm = engineVm,
            voiceSettings = voiceSettings,
            onDismiss = { showVoiceSettingsSheet = false }
        )
    }
}

// Piano Roll Body

@Composable
private fun PianoRollBody(
    notes:         List<PianoNote>,
    voicePreview:  List<VoiceCaptureStateMachine.CapturedNote> = emptyList(),
    ghostNotes:    List<PianoNote> = emptyList(),
    activePitchClasses: BooleanArray = BooleanArray(12) { false },
    liveBend:      Float = 0f,
    quantizeBeats: Float,
    onAddNote:     (PianoNote) -> Unit,
    onRemoveNote:  (PianoNote) -> Unit,
    onUpdateNote:  (PianoNote, PianoNote) -> Unit,
    onKeyPress:    (Int) -> Unit,
    onKeyRelease:  (Int) -> Unit,
    modifier:      Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    var scrollNoteOffset by remember { mutableFloatStateOf(0f) }
    var scrollBeatOffset by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var draggingNote by remember { mutableStateOf<PianoNote?>(null) }

    val keyStripWidthDp = 52.dp
    val noteHeightDp    = 14.dp
    val beatWidthDp     = 40.dp

    val keyStripWidthPx: Float
    val noteHeightPx: Float
    val beatWidthPx: Float
    with(density) {
        keyStripWidthPx = keyStripWidthDp.toPx()
        noteHeightPx    = noteHeightDp.toPx() * zoom
        beatWidthPx     = beatWidthDp.toPx() * zoom
    }

    val gridColor   = MaterialTheme.colorScheme.outlineVariant
    val noteColor   = MaterialTheme.colorScheme.primary
    val noteShade   = MaterialTheme.colorScheme.primaryContainer
    val blackKey    = MaterialTheme.colorScheme.onSurface
    val whiteKey    = MaterialTheme.colorScheme.surface
    val keyBorder   = MaterialTheme.colorScheme.outline
    val dragColor   = MaterialTheme.colorScheme.tertiary

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.3f, 5f)
        scrollBeatOffset = (scrollBeatOffset - panChange.x).coerceAtLeast(0f)
        scrollNoteOffset = (scrollNoteOffset - panChange.y).coerceAtLeast(0f)
    }

    var activePressedNote by remember { mutableStateOf<Int?>(null) }

    Box(modifier = modifier.transformable(transformState)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Live Key Strip Press & Release Tracking
                .pointerInput(keyStripWidthPx, noteHeightPx, scrollNoteOffset) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val pos = change.position
                            if (pos.x < keyStripWidthPx) {
                                val gridY = pos.y + scrollNoteOffset
                                val noteIdx = (gridY / noteHeightPx).toInt().coerceIn(0, TOTAL_NOTES - 1)
                                val midiNote = MIDI_NOTE_MAX - noteIdx

                                if (change.pressed && activePressedNote != midiNote) {
                                    activePressedNote?.let { onKeyRelease(it) }
                                    activePressedNote = midiNote
                                    onKeyPress(midiNote)
                                } else if (!change.pressed && activePressedNote != null) {
                                    onKeyRelease(activePressedNote!!)
                                    activePressedNote = null
                                }
                            } else if (activePressedNote != null) {
                                onKeyRelease(activePressedNote!!)
                                activePressedNote = null
                            }
                        }
                    }
                }
                // Tap: Add/Remove Notes
                .pointerInput(notes, quantizeBeats) {
                    detectTapGestures { tapOffset ->
                        val gridX = tapOffset.x - keyStripWidthPx + scrollBeatOffset
                        val gridY = tapOffset.y + scrollNoteOffset

                        if (tapOffset.x < keyStripWidthPx) {
                            return@detectTapGestures
                        }

                        val noteIdx  = (gridY / noteHeightPx).toInt().coerceIn(0, TOTAL_NOTES - 1)
                        val midiNote = MIDI_NOTE_MAX - noteIdx
                        val beat     = (gridX / beatWidthPx).coerceAtLeast(0f)
                        val beatSnap = (beat / quantizeBeats).roundToInt() * quantizeBeats

                        val existing = notes.firstOrNull {
                            it.midiNote == midiNote &&
                            kotlin.math.abs(it.startBeat - beatSnap) < quantizeBeats
                        }
                        if (existing != null) onRemoveNote(existing)
                        else                  onAddNote(PianoNote(midiNote, beatSnap, quantizeBeats))
                    }
                }
                // Long-Press + Drag: Extend/Shorten Note Duration
                .pointerInput(notes) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val gridX = offset.x - keyStripWidthPx + scrollBeatOffset
                            val gridY = offset.y + scrollNoteOffset
                            val noteIdx = (gridY / noteHeightPx).toInt().coerceIn(0, TOTAL_NOTES - 1)
                            val midiNote = MIDI_NOTE_MAX - noteIdx
                            val beat = (gridX / beatWidthPx).coerceAtLeast(0f)
                            draggingNote = notes.minByOrNull { n ->
                                if (n.midiNote != midiNote) Float.MAX_VALUE
                                else kotlin.math.abs(n.startBeat - beat)
                            }.takeIf {
                                it != null && kotlin.math.abs(it!!.startBeat - beat) < 1.5f
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val dragging = draggingNote ?: return@detectDragGesturesAfterLongPress
                            val beatDelta = dragAmount.x / beatWidthPx
                            val newDuration = (dragging.durationBeats + beatDelta)
                                .coerceAtLeast(quantizeBeats)
                            val updated = dragging.copy(durationBeats = newDuration)
                            onUpdateNote(dragging, updated)
                            draggingNote = updated
                        },
                        onDragEnd = { draggingNote = null },
                        onDragCancel = { draggingNote = null },
                    )
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height

            // Key Strip
            for (i in 0 until TOTAL_NOTES) {
                val midiNote = MIDI_NOTE_MAX - i
                val y = i * noteHeightPx - scrollNoteOffset
                if (y > canvasH || y + noteHeightPx < 0) continue

                drawRect(
                    color   = if (isWhiteKey(midiNote)) whiteKey else blackKey,
                    topLeft = Offset(0f, y),
                    size    = Size(keyStripWidthPx, noteHeightPx - 1f),
                )
                if (PitchMath.isNoteInActiveScale(midiNote, activePitchClasses)) {
                    drawRect(
                        color   = noteShade.copy(alpha = 0.3f),
                        topLeft = Offset(0f, y),
                        size    = Size(keyStripWidthPx, noteHeightPx - 1f),
                    )
                }
                drawRect(
                    color   = keyBorder,
                    topLeft = Offset(0f, y),
                    size    = Size(keyStripWidthPx, noteHeightPx - 1f),
                    style   = androidx.compose.ui.graphics.drawscope.Stroke(0.5f),
                )
                if (midiNote % 12 == 0) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text  = noteName(midiNote),
                        topLeft = Offset(2f, y + 1f),
                        style = TextStyle(
                            color      = blackKey,
                            fontSize   = 8.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }

            // Grid
            for (i in 0..TOTAL_NOTES) {
                val y = i * noteHeightPx - scrollNoteOffset
                if (y < 0 || y > canvasH) continue
                drawLine(
                    color       = gridColor,
                    start       = Offset(keyStripWidthPx, y),
                    end         = Offset(canvasW, y),
                    strokeWidth = if (!isWhiteKey(MIDI_NOTE_MAX - i + 1)) 0.5f else 1f,
                )
            }
            val firstBeat = (scrollBeatOffset / beatWidthPx).toInt()
            val lastBeat  = ((scrollBeatOffset + canvasW) / beatWidthPx).toInt() + 1
            for (b in firstBeat..lastBeat.coerceAtMost(TOTAL_BEATS)) {
                val x = b * beatWidthPx - scrollBeatOffset + keyStripWidthPx
                drawLine(
                    color       = if (b % 4 == 0) gridColor.copy(alpha = 0.8f) else gridColor.copy(alpha = 0.3f),
                    start       = Offset(x, 0f),
                    end         = Offset(x, canvasH),
                    strokeWidth = if (b % 4 == 0) 1.5f else 0.5f,
                )
            }

            // Ghost Notes
            for (note in ghostNotes) {
                val noteIdx = MIDI_NOTE_MAX - note.midiNote
                val y = noteIdx * noteHeightPx - scrollNoteOffset
                if (y > canvasH || y + noteHeightPx < 0) continue
                val x = note.startBeat * beatWidthPx - scrollBeatOffset + keyStripWidthPx
                val w = note.durationBeats * beatWidthPx - 2f
                if (x + w < keyStripWidthPx || x > canvasW) continue

                drawRect(
                    color   = Color(0x40888888),
                    topLeft = Offset(x.coerceAtLeast(keyStripWidthPx), y + 1f),
                    size    = Size(w, noteHeightPx - 2f),
                )
            }

            // Notes
            for (note in notes) {
                val noteIdx = MIDI_NOTE_MAX - note.midiNote
                val y = noteIdx * noteHeightPx - scrollNoteOffset
                if (y > canvasH || y + noteHeightPx < 0) continue
                val x = note.startBeat * beatWidthPx - scrollBeatOffset + keyStripWidthPx
                val w = note.durationBeats * beatWidthPx - 2f
                if (x + w < keyStripWidthPx || x > canvasW) continue

                val velAlpha = 0.4f + (note.velocity / 127f) * 0.6f
                val isDragging = note == draggingNote
                drawRect(
                    color   = if (isDragging) dragColor.copy(alpha = velAlpha) else noteColor.copy(alpha = velAlpha),
                    topLeft = Offset(x.coerceAtLeast(keyStripWidthPx), y + 1f),
                    size    = Size(w, noteHeightPx - 2f),
                )
                drawRect(
                    color   = if (isDragging) dragColor else noteColor,
                    topLeft = Offset(x.coerceAtLeast(keyStripWidthPx), y + noteHeightPx - 3f),
                    size    = Size(w * (note.velocity / 127f), 2f),
                )
            }

            // Voice Preview Ghost Notes
            if (voicePreview.isNotEmpty()) {
                val firstMs = voicePreview.minOf { it.startMs }
                val msPerBeat = 500f
                for (previewNote in voicePreview) {
                    val noteIdx = MIDI_NOTE_MAX - previewNote.midiNote
                    val y = noteIdx * noteHeightPx - scrollNoteOffset
                    if (y > canvasH || y + noteHeightPx < 0) continue
                    val startBeat = (previewNote.startMs - firstMs) / msPerBeat
                    val durBeats  = (previewNote.durationMs / msPerBeat).coerceAtLeast(0.0625f)
                    val x = startBeat * beatWidthPx - scrollBeatOffset + keyStripWidthPx
                    val w = (durBeats * beatWidthPx - 2f).coerceAtLeast(4f)
                    if (x + w < keyStripWidthPx || x > canvasW) continue
                    drawRect(
                        color   = Color(0x55A855F7),
                        topLeft = Offset(x.coerceAtLeast(keyStripWidthPx), y + 1f),
                        size    = Size(w, noteHeightPx - 2f),
                    )
                    drawRect(
                        color   = Color(0xCCA855F7),
                        topLeft = Offset(x.coerceAtLeast(keyStripWidthPx), y + 1f),
                        size    = Size(w, noteHeightPx - 2f),
                        style   = androidx.compose.ui.graphics.drawscope.Stroke(1f),
                    )
                }
            }

            // Live Pitch Bend Ribbon
            if (voicePreview.isNotEmpty() && liveBend != 0f) {
                val lastNote = voicePreview.maxByOrNull { it.startMs }
                if (lastNote != null) {
                    val noteIdx = MIDI_NOTE_MAX - lastNote.midiNote
                    val y = noteIdx * noteHeightPx - scrollNoteOffset
                    val firstMs = voicePreview.minOf { it.startMs }
                    val msPerBeat = 500f
                    val startBeat = (lastNote.startMs - firstMs) / msPerBeat
                    val durBeats  = (lastNote.durationMs / msPerBeat).coerceAtLeast(0.0625f)
                    val x = (startBeat + durBeats) * beatWidthPx - scrollBeatOffset + keyStripWidthPx
                    val ribbonH = kotlin.math.abs(liveBend / 2f) * noteHeightPx
                    val ribbonColor = if (liveBend > 0) Color(0xFF4CAF50) else Color(0xFFFF7043)
                    val ribbonY = if (liveBend > 0) y - ribbonH else y + noteHeightPx
                    drawRect(
                        color   = ribbonColor.copy(alpha = 0.80f),
                        topLeft = Offset(x, ribbonY),
                        size    = Size(6f, ribbonH.coerceAtLeast(2f)),
                    )
                }
            }
        }
    }
}

// Quantize Button

@Composable
private fun QuantizeButton(
    currentBeats: Float,
    onSelect: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = QUANTIZE_LABELS.getOrElse(QUANTIZE_VALUES.indexOf(currentBeats)) { "Q" }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Text("Q:$label", style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            QUANTIZE_VALUES.zip(QUANTIZE_LABELS).forEach { (beats, lbl) ->
                DropdownMenuItem(
                    text = { Text(lbl) },
                    onClick = { onSelect(beats); expanded = false },
                    trailingIcon = {
                        if (beats == currentBeats) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        }
                    }
                )
            }
        }
    }
}

// Note Repeat Button

@Composable
private fun NoteRepeatButton(
    currentRate: com.voicedaw.audioengine.NoteRepeatRate,
    onSelect: (com.voicedaw.audioengine.NoteRepeatRate) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Text("Repeat:${currentRate.label}", style = MaterialTheme.typography.labelSmall)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.voicedaw.audioengine.NoteRepeatRate.values().forEach { rate ->
                DropdownMenuItem(
                    text = { Text(rate.label) },
                    onClick = { onSelect(rate); expanded = false },
                    trailingIcon = {
                        if (rate == currentRate) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        }
                    }
                )
            }
        }
    }
}

// MIDI Device Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MidiDeviceSheet(
    midiVm:    MidiViewModel,
    midiState: MidiViewModel.MidiState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "MIDI Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // USB MIDI Devices
            Text(
                "USB / Connected",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (midiState.connectedDevices.isEmpty()) {
                Text(
                    "No USB MIDI devices detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                midiState.connectedDevices.forEach { info ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Usb, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            info.properties.getString("name") ?: "USB MIDI Device",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("Connected", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // BLE MIDI
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Bluetooth MIDI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (midiState.isBleScanActive) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { midiVm.stopBleScan() }) { Text("Stop", style = MaterialTheme.typography.labelSmall) }
                } else {
                    TextButton(onClick = { midiVm.startBleScan() }) { Text("Scan", style = MaterialTheme.typography.labelSmall) }
                }
            }

            if (midiState.bleScanResults.isEmpty() && !midiState.isBleScanActive) {
                Text(
                    "Tap Scan to discover BLE MIDI controllers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(midiState.bleScanResults) { result ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Bluetooth, null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(result.name, style = MaterialTheme.typography.bodySmall)
                            Text("${result.rssi} dBm", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                        OutlinedButton(
                            onClick = { midiVm.connectBleDevice(result) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Connect", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // MIDI Learn
            Text("MIDI Learn", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))

            val learnTargets = listOf("BPM")
            learnTargets.forEach { target ->
                val mappedCc = midiState.learnedMappings.entries.firstOrNull { it.value == target }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        target,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (mappedCc != null) {
                        Text(
                            "CC ${mappedCc.key}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        IconButton(
                            onClick = { midiVm.clearMidiMapping(target) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Close, "Clear mapping", modifier = Modifier.size(14.dp))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { midiVm.startMidiLearn(target); onDismiss() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Learn", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// Voice Recognition Settings Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSettingsSheet(
    engineVm: AudioEngineViewModel,
    voiceSettings: VoiceCaptureSettings,
    onDismiss: () -> Unit,
) {
    val noteLabels = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Text(
                "Voice Recognition Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 1. Scale Lock / Auto-Tune Mask
            Text("Scale Lock / Auto-Tune Mask", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))

            var expandedPreset by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expandedPreset = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Preset: ${voiceSettings.scalePreset.name.replace("_", " ")}")
                }
                DropdownMenu(expanded = expandedPreset, onDismissRequest = { expandedPreset = false }) {
                    ScalePreset.values().forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.name.replace("_", " ")) },
                            onClick = {
                                expandedPreset = false
                                val newMask = PitchMath.getScalePresetMask(0, preset)
                                engineVm.updateVoiceSettings(
                                    voiceSettings.copy(
                                        scalePreset = preset,
                                        activePitchClasses = newMask
                                    )
                                )
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                noteLabels.forEachIndexed { idx, label ->
                    val isActive = voiceSettings.activePitchClasses.getOrElse(idx) { true }
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            val updatedMask = voiceSettings.activePitchClasses.copyOf()
                            updatedMask[idx] = !isActive
                            engineVm.updateVoiceSettings(
                                voiceSettings.copy(
                                    scalePreset = ScalePreset.CUSTOM,
                                    activePitchClasses = updatedMask
                                )
                            )
                        },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 2. Velocity Hardness
            Text("Velocity Hardness", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VelocityHardnessMode.values().forEach { mode ->
                    FilterChip(
                        selected = voiceSettings.hardnessMode == mode,
                        onClick = {
                            engineVm.updateVoiceSettings(voiceSettings.copy(hardnessMode = mode))
                        },
                        label = { Text(mode.name) }
                    )
                }
            }

            if (voiceSettings.hardnessMode == VelocityHardnessMode.FIXED) {
                Text("Fixed Velocity: ${voiceSettings.fixedVelocity}", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = voiceSettings.fixedVelocity.toFloat(),
                    onValueChange = { engineVm.updateVoiceSettings(voiceSettings.copy(fixedVelocity = it.toInt())) },
                    valueRange = 1f..127f
                )
            } else {
                Text("Min Velocity: ${voiceSettings.minVelocity} | Max Velocity: ${voiceSettings.maxVelocity}", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = voiceSettings.minVelocity.toFloat(),
                    onValueChange = { engineVm.updateVoiceSettings(voiceSettings.copy(minVelocity = it.toInt())) },
                    valueRange = 1f..127f
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 3. Pitch Bend Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pitch Bend Tracking", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(
                    checked = voiceSettings.pitchBendEnabled,
                    onCheckedChange = { engineVm.updateVoiceSettings(voiceSettings.copy(pitchBendEnabled = it)) }
                )
            }

            if (voiceSettings.pitchBendEnabled) {
                Text("Deadband Threshold: ${voiceSettings.bendDeadbandCents.toInt()} cents", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = voiceSettings.bendDeadbandCents,
                    onValueChange = { engineVm.updateVoiceSettings(voiceSettings.copy(bendDeadbandCents = it)) },
                    valueRange = 5f..50f
                )

                Text("Max Bend Range: ±${voiceSettings.bendRangeSemitones} semitones", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = voiceSettings.bendRangeSemitones.toFloat(),
                    onValueChange = { engineVm.updateVoiceSettings(voiceSettings.copy(bendRangeSemitones = it.toInt())) },
                    valueRange = 1f..12f,
                    steps = 11
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


