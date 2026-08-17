package com.voicedaw.audioengine

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicedaw.audioengine.export.AudioExporter
import com.voicedaw.audioengine.mixer.*
import com.voicedaw.audioengine.recording.*
import com.voicedaw.audioengine.sampling.*
import com.voicedaw.midi.MidiCleanup
import com.voicedaw.midi.PianoNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

// ── Master FX chain (M7) state ──────────────────────────────────────────────
// Same shape/placement convention as ReverbState/EqState/CompressorState
// (com.voicedaw.audioengine.mixer) — kept here since those files aren't in
// this module's visible set; move alongside them if that package is local.
data class SaturationState(val enabled: Boolean = false, val drive: Float = 1f)
data class DelayState(val enabled: Boolean = false, val timeMs: Float = 250f, val feedback: Float = 0.3f, val mix: Float = 0.2f)
data class GateState(val enabled: Boolean = false, val thresholdDb: Float = -40f, val attackMs: Float = 5f, val releaseMs: Float = 100f)

/** A single beatbox hit detected during a drum capture session. */
data class DrumEvent(
    val padIndex: Int,      // 1–5 (matches nativeGetLastDetectedPad values)
    val timestampMs: Long,  // wall-clock ms since capture started
    val velocity: Int = 100,
)

/**
 * Sub-semitone pitch deviation captured during a sustained melody note.
 * Used to render a live bend ribbon in the piano roll and (future) write MIDI pitch bend events.
 */
data class PitchBendEvent(val semitones: Float, val timestampMs: Long)

/** Maps beatbox pad index (1–5) to GM standard MIDI drum note number. */
private val GM_DRUM_MAP = mapOf(
    1 to 36,  // Kick / Bass Drum 1
    2 to 38,  // Snare / Acoustic Snare
    3 to 42,  // Hi-hat closed
    4 to 46,  // Hi-hat open
    5 to 49,  // Crash Cymbal 1
)

class AudioEngineViewModel(application: Application) : AndroidViewModel(application) {

    data class AudioDeviceState(
        val id: Int,
        val name: String,
        val type: Int,
        val isSink: Boolean
    )

    data class AudioState(
        val isPlaying: Boolean = false,
        val isRecording: Boolean = false,
        val isLooping: Boolean = false,
        val latencyMs: Float = 0.0f,
        val tier: AudioTier = AudioTier.UNKNOWN,
        val xrunCount: Long = 0L,
        val sampleRate: Int = 48000,
        val bpm: Float = 120.0f,
        val pitchHz: Float = 0.0f,
        val pitchAmp: Float = 0.0f,
        val lastDetectedPad: Int = 0,
        val estimatedBpm: Float = 0.0f,
        val estimatedKeyRoot: Int = 0,
        val estimatedKeyIsMajor: Boolean = false,
        val harmonyAssistEnabled: Boolean = false,
        val isEcoMode: Boolean = false,
        val tracks: List<AudioTrack> = emptyList(),
        val amplitudeSamples: List<Float> = emptyList(),
        val lastRecordedFilePath: String? = null,
        val punchEnabled: Boolean = false,
        val punchInMs: Long = 0L,
        val punchOutMs: Long = 0L,
        val mixerTracks: List<TrackMixerState> = emptyList(),
        val masterReverb: ReverbState = ReverbState(),
        val masterSaturation: SaturationState = SaturationState(),
        val masterDelay: DelayState = DelayState(),
        val masterGate: GateState = GateState(),
        val listenToReference: Boolean = false,
        val masterEq: EqState = EqState(),
        val masterComp: CompressorState = CompressorState(),
        val currentMasteringPreset: String? = null,
        val integratedLUFS: Float = 0.0f,
        val availableOutputDevices: List<AudioDeviceState> = emptyList(),
        val selectedOutputDeviceId: Int = 0,
        val voiceInputMode: Int = 0, // 0=Off, 1=Hum, 2=Beatbox
        val playbackPositionMs: Float = 0.0f,
        val pendingMidiCount: Int = 0,
    )

    val nativeHandle: Long = AudioEngineJni.nativeCreate()
    val recordingManager = MultitrackRecordingManager(application, viewModelScope)
    val vocalPadManager = VocalPadManager(application)
    val pads: StateFlow<List<PadSampleData>> = vocalPadManager.pads
    
    private val _chromaticSampleSource = MutableStateFlow<PadSampleData?>(null)
    val chromaticSampleSource: StateFlow<PadSampleData?> = _chromaticSampleSource.asStateFlow()
    
    fun setChromaticSampleSource(padSample: PadSampleData?) { 
        _chromaticSampleSource.value = padSample 
    }
    
    private val _noteRepeatRate = MutableStateFlow(NoteRepeatRate.OFF)
    val noteRepeatRate: StateFlow<NoteRepeatRate> = _noteRepeatRate.asStateFlow()

    private val _performanceFxState = MutableStateFlow(com.voicedaw.audioengine.performance.PerformanceFxState())
    val performanceFxState: StateFlow<com.voicedaw.audioengine.performance.PerformanceFxState> = _performanceFxState.asStateFlow()

    fun setNoteRepeatRate(rate: NoteRepeatRate) {
        _noteRepeatRate.value = rate
    }

    fun triggerPerformanceFx(fxType: com.voicedaw.audioengine.performance.PerformanceFxType?, hold: Boolean = false) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetPerformanceFx(
                nativeHandle, 
                fxType?.ordinal ?: -1, 
                hold
            )
        }
        _performanceFxState.value = com.voicedaw.audioengine.performance.PerformanceFxState(
            activeFxType = fxType,
            isHoldEnabled = hold
        )
    }

    private val thermalMonitor = ThermalMonitor(application)
    
    private val _state = MutableStateFlow(AudioState(
        mixerTracks = List(MultitrackRecordingManager.MAX_TRACKS) { TrackMixerState(it) }
    ))
    val state: StateFlow<AudioState> = _state.asStateFlow()
    
    private var pollJob: Job? = null

    // ── Voice capture ─────────────────────────────────────────────────────────
    private val voiceStateMachine = VoiceCaptureStateMachine(pollIntervalMs = 16L)
    private var voiceCaptureJob: Job? = null

    // ── Drum capture ──────────────────────────────────────────────────────────────
    private val _drumEvents = mutableListOf<DrumEvent>()
    private var drumCaptureJob: Job? = null
    private var drumCaptureStartMs: Long = 0L

    // ── Pending MIDI bus (cross-screen) ───────────────────────────────────────────
    private val _pendingMidiNotes = MutableSharedFlow<List<PianoNote>>(extraBufferCapacity = 1)
    /** Emits once when new MIDI notes are ready to be merged into the piano roll. */
    val pendingMidiNotes: SharedFlow<List<PianoNote>> = _pendingMidiNotes

    // ── Live pitch bend (for piano roll ribbon during capture) ────────────────────
    private val _livePitchBendSemitones = MutableStateFlow(0f)
    val livePitchBendSemitones: StateFlow<Float> = _livePitchBendSemitones.asStateFlow()

    private val _voicePreviewNotes = MutableStateFlow<List<VoiceCaptureStateMachine.CapturedNote>>(emptyList())
    /** Live list of notes captured during an active voice-input session (ms-based, pre-cleanup). */
    val voicePreviewNotes: StateFlow<List<VoiceCaptureStateMachine.CapturedNote>> = _voicePreviewNotes.asStateFlow()

    // ── Mic calibration ───────────────────────────────────────────────────────────
    private val _isCalibrating      = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _calibratedAmpFloor = MutableStateFlow(0.02f)
    val calibratedAmpFloor: StateFlow<Float> = _calibratedAmpFloor.asStateFlow()

    private val _autoCalibrate = MutableStateFlow(true)
    val autoCalibrate: StateFlow<Boolean> = _autoCalibrate.asStateFlow()

    private val _voiceCaptureActive = MutableStateFlow(false)
    val voiceCaptureActive: StateFlow<Boolean> = _voiceCaptureActive.asStateFlow()

    private val _voiceSettings = MutableStateFlow(VoiceCaptureSettings())
    val voiceSettings: StateFlow<VoiceCaptureSettings> = _voiceSettings.asStateFlow()

    private val _isPreviewPlaying = MutableStateFlow(false)
    val isPreviewPlaying: StateFlow<Boolean> = _isPreviewPlaying.asStateFlow()

    private var previewJob: Job? = null

    private var calibrationJob: Job? = null
    private val PREFS_NAME          = "voicedaw_calibration"
    private val PREF_AMP_FLOOR      = "amp_floor"
    private val PREF_AUTO_CALIBRATE = "auto_calibrate"

    companion object {
        private const val TAG = "AudioEngineViewModel"
    }

    init {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedFloor = prefs.getFloat(PREF_AMP_FLOOR, 0.02f)
        val savedAuto  = prefs.getBoolean(PREF_AUTO_CALIBRATE, true)
        _calibratedAmpFloor.value = savedFloor
        _autoCalibrate.value      = savedAuto
        voiceStateMachine.setAmpFloor(savedFloor)
        
        viewModelScope.launch {
            recordingManager.tracks.collect { tracks ->
                _state.value = _state.value.copy(tracks = tracks)
                tracks.forEach { track ->
                    val activeTake = track.activeTake
                    val path = activeTake?.filePath ?: ""
                    if (path.isNotEmpty() && nativeHandle != 0L) {
                        AudioEngineJni.nativeLoadTrackClip(nativeHandle, track.trackIndex, path)
                    }
                }
            }
        }
        viewModelScope.launch {
            recordingManager.isRecording.collect { rec ->
                _state.value = _state.value.copy(isRecording = rec)
            }
        }
        viewModelScope.launch {
            recordingManager.amplitudeSamples.collect { amps ->
                _state.value = _state.value.copy(amplitudeSamples = amps)
            }
        }
        thermalMonitor.setOnTierChangeListener { perfTier ->
            val audioTier = if (perfTier == ThermalMonitor.PerformanceTier.HIGH_PERFORMANCE)
                AudioTier.PRO else AudioTier.LOW
            _state.value = _state.value.copy(tier = audioTier, isEcoMode = perfTier == ThermalMonitor.PerformanceTier.ECO_MODE)
            if (nativeHandle != 0L) {
                AudioEngineJni.nativeSetPerformanceTier(nativeHandle, perfTier.level)
            }
        }
        
        refreshAudioDevices()
        startEngine()
        loadBeatboxModel()
    }

    fun refreshAudioDevices() {
        val audioManager = getApplication<Application>().getSystemService(Application.AUDIO_SERVICE) as AudioManager
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val deviceStates = devices.map { device ->
            AudioDeviceState(device.id, device.productName?.toString() ?: "Unknown Device", device.type, device.isSink)
        }
        _state.value = _state.value.copy(availableOutputDevices = deviceStates)
    }

    fun setOutputDevice(deviceId: Int) {
        _state.value = _state.value.copy(selectedOutputDeviceId = deviceId)
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetOutputDeviceId(nativeHandle, deviceId)
        }
    }

    fun loadProject(projectId: String) {
        recordingManager.projectId = projectId
    }

    fun play() {
        _state.value = _state.value.copy(isPlaying = true)
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTransportState(nativeHandle, 1)
    }

    fun pause() {
        _state.value = _state.value.copy(isPlaying = false)
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTransportState(nativeHandle, 0)
    }

    fun stop() {
        _state.value = _state.value.copy(isPlaying = false)
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTransportState(nativeHandle, 0)
        if (recordingManager.isRecording.value) {
            recordingManager.cancelRecording()
        }
    }

    fun toggleRecord() {
        if (_state.value.isRecording) {
            // ── Stop recording + capture ──────────────────────────────────────
            when (_state.value.voiceInputMode) {
                1 -> {
                    // Melody: flush voice capture → clean → emit
                    val rawMs = voiceStateMachine.flush()
                    voiceCaptureJob?.cancel()
                    voiceCaptureJob = null
                    setAnalysisMode(0)
                    _voicePreviewNotes.value = rawMs
                    if (rawMs.isNotEmpty()) {
                        val msPerBeat = if (_state.value.bpm > 0f) 60_000f / _state.value.bpm else 500f
                        val committed = MidiCleanup.cleanupVocalTake(
                            rawNotes = rawMs.map { cn ->
                                PianoNote(
                                    midiNote      = cn.midiNote,
                                    startBeat     = cn.startMs / msPerBeat,
                                    durationBeats = (cn.durationMs / msPerBeat).coerceAtLeast(0.0625f),
                                    velocity      = cn.velocity,
                                )
                            }
                        )
                        if (committed.isNotEmpty()) emitPendingMidi(committed)
                    }
                }
                2 -> {
                    // Beatbox: convert drum events → emit
                    val drumNotes = stopDrumCapture()
                    if (drumNotes.isNotEmpty()) emitPendingMidi(drumNotes)
                }
            }
            recordingManager.stopRecording()
            if (nativeHandle != 0L) AudioEngineJni.nativeSetTransportState(nativeHandle, 0)
        } else {
            // ── Start recording + capture ─────────────────────────────────────
            viewModelScope.launch {
                if (_autoCalibrate.value && !_isCalibrating.value) {
                    calibrateMic(5_000L)
                    _isCalibrating.first { !it }  // suspend until done
                }
                when (_state.value.voiceInputMode) {
                    1 -> startVoiceCapture()
                    2 -> startDrumCapture()
                }
                recordingManager.startRecording()
                if (nativeHandle != 0L) AudioEngineJni.nativeSetTransportState(nativeHandle, 2)
            }
        }
    }

    fun toggleLoop() {
        _state.value = _state.value.copy(isLooping = !_state.value.isLooping)
    }

    fun armTrack(trackIndex: Int, armed: Boolean) {
        recordingManager.setArmed(trackIndex, armed)
    }

    fun toggleMute(trackIndex: Int) {
        recordingManager.toggleMute(trackIndex)
    }

    fun toggleSolo(trackIndex: Int) {
        recordingManager.toggleSolo(trackIndex)
    }

    fun selectTake(trackIndex: Int, takeIndex: Int) {
        recordingManager.selectTake(trackIndex, takeIndex)
    }

    fun updateClipTrim(trackIndex: Int, takeIndex: Int, startSample: Long, endSample: Long, fadeInSamples: Int, fadeOutSamples: Int) {
        recordingManager.updateClipTrim(trackIndex, takeIndex, startSample, endSample, fadeInSamples, fadeOutSamples)
    }

    fun setInputSource(trackIndex: Int, source: AudioTrack.InputSource) {
        recordingManager.setInputSource(trackIndex, source)
    }

    fun setAnalysisMode(mode: Int) {
        _state.value = _state.value.copy(voiceInputMode = mode)
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetAnalysisMode(nativeHandle, mode)
        }
    }

    /**
     * Sample ambient amplitude for [durationMs] ms, compute P95 x 1.5 as the new ampFloor.
     * Switches analysis mode to 1 for sampling, restores previous mode when done.
     */
    fun calibrateMic(durationMs: Long = 5_000L) {
        if (_isCalibrating.value) return
        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch(Dispatchers.Default) {
            _isCalibrating.value = true
            val prevMode = _state.value.voiceInputMode
            withContext(Dispatchers.Main) { setAnalysisMode(1) }
            delay(200L) // let mic settle

            val samples = mutableListOf<Float>()
            val pollMs  = 50L
            val iterations = (durationMs / pollMs).toInt()
            repeat(iterations) {
                if (nativeHandle != 0L) {
                    samples.add(AudioEngineJni.nativeGetPitchAmp(nativeHandle))
                }
                delay(pollMs)
            }

            // P95 x 1.5 margin
            samples.sort()
            val p95Index = ((samples.size - 1) * 0.95f).toInt()
            val p95      = samples.getOrElse(p95Index) { 0.02f }
            val newFloor = (p95 * 1.5f).coerceIn(0.002f, 0.3f)

            voiceStateMachine.setAmpFloor(newFloor)
            _calibratedAmpFloor.value = newFloor

            val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putFloat(PREF_AMP_FLOOR, newFloor).apply()

            withContext(Dispatchers.Main) { setAnalysisMode(prevMode) }
            _isCalibrating.value = false
        }
    }

    fun setAutoCalibrate(enabled: Boolean) {
        _autoCalibrate.value = enabled
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_AUTO_CALIBRATE, enabled).apply()
    }

    /**
     * Begin a voice-to-MIDI melodic capture session.
     * Polls pitchHz/pitchAmp at 16ms and feeds them into [VoiceCaptureStateMachine].
     * Preview notes accumulate in [voicePreviewNotes].
     */
    fun startVoiceCapture() {
        _voiceCaptureActive.value = true
        voiceStateMachine.reset()
        _voicePreviewNotes.value = emptyList()
        _livePitchBendSemitones.value = 0f
        setAnalysisMode(1) // 1 = pitch/melody mode
        voiceCaptureJob?.cancel()
        voiceCaptureJob = viewModelScope.launch {
            while (true) {
                delay(16)
                if (nativeHandle == 0L) continue
                val hz  = AudioEngineJni.nativeGetPitchHz(nativeHandle)
                val amp = AudioEngineJni.nativeGetPitchAmp(nativeHandle)
                voiceStateMachine.processFrame(hz, amp)?.let { updated ->
                    _voicePreviewNotes.value = updated
                }
                // Track live pitch bend for the currently active note
                if (voiceStateMachine.hasActiveNote) {
                    val activeNote = voiceStateMachine.currentMidiNote
                    if (activeNote >= 0 && hz > 60f) {
                        val refHz = 440.0 * Math.pow(2.0, (activeNote - 69) / 12.0)
                        val bendSemitones = (12.0 * Math.log(hz / refHz) / Math.log(2.0)).toFloat()
                        _livePitchBendSemitones.value = bendSemitones.coerceIn(-2f, 2f)
                    }
                } else {
                    _livePitchBendSemitones.value = 0f
                }
            }
        }
    }

    /**
     * Stop an active voice capture session.
     * Flushes the state machine (closes any open note) and returns the raw note list.
     */
    fun stopVoiceCapture(): List<VoiceCaptureStateMachine.CapturedNote> {
        _voiceCaptureActive.value = false
        voiceCaptureJob?.cancel()
        voiceCaptureJob = null
        setAnalysisMode(0)
        _livePitchBendSemitones.value = 0f
        val raw = voiceStateMachine.flush()
        _voicePreviewNotes.value = raw
        return raw
    }

    /**
     * Run [MidiCleanup] on the preview notes and convert to [PianoNote]s (beat-based).
     * Clears [voicePreviewNotes] afterward.
     *
     * @param bpm  Project tempo used for quantization grid calculation.
     * @return Cleaned, beat-quantized [PianoNote]s ready for insertion into the piano roll.
     */
    fun commitVoiceCapture(bpm: Float): List<PianoNote> {
        val rawMs = _voicePreviewNotes.value
        _voicePreviewNotes.value = emptyList()
        voiceStateMachine.reset()
        if (rawMs.isEmpty()) return emptyList()

        val msPerBeat = if (bpm > 0f) 60000f / bpm else 500f // default 120bpm

        // Convert ms-based CapturedNotes to beat-based PianoNotes for MidiCleanup
        val asBeats = rawMs.map { note ->
            PianoNote(
                midiNote      = note.midiNote,
                startBeat     = note.startMs / msPerBeat,
                durationBeats = (note.durationMs / msPerBeat).coerceAtLeast(0.0625f), // min 1/16th
                velocity      = note.velocity,
            )
        }

        return MidiCleanup.cleanupVocalTake(asBeats)
    }

    /** Discard all captured preview notes and reset the state machine without committing. */
    fun discardVoiceCapture() {
        _voiceCaptureActive.value = false
        voiceCaptureJob?.cancel()
        voiceCaptureJob = null
        setAnalysisMode(0)
        _voicePreviewNotes.value = emptyList()
        voiceStateMachine.reset()
    }

    /**
     * Begin a beatbox-to-drums capture session.
     * Polls [AudioEngineJni.nativeGetLastDetectedPad] at 16ms.
     * Each pad hit (transition from 0 → non-zero) records a [DrumEvent].
     */
    fun startDrumCapture() {
        _voiceCaptureActive.value = true
        _drumEvents.clear()
        drumCaptureStartMs = System.currentTimeMillis()
        setAnalysisMode(2) // ensure beatbox mode is active
        drumCaptureJob?.cancel()
        drumCaptureJob = viewModelScope.launch {
            var lastPad = 0
            while (true) {
                delay(16)
                if (nativeHandle == 0L) continue
                val pad = AudioEngineJni.nativeGetLastDetectedPad(nativeHandle)
                if (pad != lastPad && pad != 0) {
                    val elapsed = System.currentTimeMillis() - drumCaptureStartMs
                    _drumEvents.add(DrumEvent(padIndex = pad, timestampMs = elapsed))
                }
                lastPad = pad
            }
        }
    }

    /**
     * Stop drum capture and convert collected [DrumEvent]s to beat-quantized
     * [PianoNote]s on MIDI channel 10 (GM drums).
     */
    fun stopDrumCapture(bpm: Float = _state.value.bpm): List<PianoNote> {
        _voiceCaptureActive.value = false
        drumCaptureJob?.cancel()
        drumCaptureJob = null
        setAnalysisMode(0)
        val msPerBeat = if (bpm > 0f) 60_000f / bpm else 500f
        val notes = _drumEvents.mapNotNull { event ->
            val midiNote = GM_DRUM_MAP[event.padIndex] ?: return@mapNotNull null
            PianoNote(
                midiNote      = midiNote,
                startBeat     = event.timestampMs / msPerBeat,
                durationBeats = 0.25f,
                velocity      = event.velocity,
                midiChannel   = 10,
            )
        }
        _drumEvents.clear()
        return notes
    }

    /** Emit [notes] to [pendingMidiNotes] and update the RecordScreen banner count. */
    private fun emitPendingMidi(notes: List<PianoNote>) {
        _state.value = _state.value.copy(pendingMidiCount = notes.size)
        viewModelScope.launch {
            _pendingMidiNotes.emit(notes)
        }
    }

    /** Clear the pending MIDI banner after the user has dispatched notes to the piano roll. */
    fun consumePendingMidiNotes() {
        _state.value = _state.value.copy(pendingMidiCount = 0)
    }

    /** Emit drum sequencer pattern notes to the piano roll via the pending MIDI bus. */
    fun emitDrumSequencerNotes(notes: List<PianoNote>) {
        if (notes.isNotEmpty()) emitPendingMidi(notes)
    }

    fun setBeatboxTrainingPad(padIndex: Int) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetBeatboxTrainingPad(nativeHandle, padIndex)
        }
    }

    fun saveBeatboxModel() {
        if (nativeHandle != 0L) {
            val path = File(getApplication<Application>().filesDir, "beatbox_model.bin").absolutePath
            AudioEngineJni.nativeSaveBeatboxModel(nativeHandle, path)
        }
    }

    fun loadBeatboxModel() {
        if (nativeHandle != 0L) {
            val path = File(getApplication<Application>().filesDir, "beatbox_model.bin").absolutePath
            if (File(path).exists()) {
                AudioEngineJni.nativeLoadBeatboxModel(nativeHandle, path)
            }
        }
    }

    fun setHarmonyAssistEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(harmonyAssistEnabled = enabled)
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetHarmonyAssistEnabled(nativeHandle, enabled)
        }
    }

    fun setPunchEnabled(enabled: Boolean) {
        recordingManager.setPunchEnabled(enabled)
        _state.value = _state.value.copy(punchEnabled = enabled)
    }

    fun setPunchInMs(ms: Long) {
        recordingManager.setPunchIn(ms)
        _state.value = _state.value.copy(punchInMs = ms)
    }

    fun setPunchOutMs(ms: Long) {
        recordingManager.setPunchOut(ms)
        _state.value = _state.value.copy(punchOutMs = ms)
    }

    fun setTrackVolume(trackIndex: Int, volumeDb: Float) {
        val linear = if (volumeDb <= -60.0f) 0.0f else Math.pow(10.0, volumeDb / 20.0).toFloat()
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTrackGain(nativeHandle, trackIndex, linear)
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(volumeDb = volumeDb)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setTrackPan(trackIndex: Int, pan: Float) {
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTrackPan(nativeHandle, trackIndex, pan)
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(pan = pan)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setTrackMute(trackIndex: Int, muted: Boolean) {
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTrackMute(nativeHandle, trackIndex, muted)
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(muted = muted)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setTrackSolo(trackIndex: Int, solo: Boolean) {
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTrackSolo(nativeHandle, trackIndex, solo)
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(soloed = solo)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setTrackReverbSend(trackIndex: Int, send: Float) {
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTrackReverbSend(nativeHandle, trackIndex, send)
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(reverbSend = send)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setTrackDelaySend(trackIndex: Int, send: Float) {
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTrackDelaySend(nativeHandle, trackIndex, send)
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(delaySend = send)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setTrackSubmixBus(trackIndex: Int, bus: SubmixBus) {
        if (nativeHandle != 0L) AudioEngineJni.nativeSetTrackSubmixBus(nativeHandle, trackIndex, bus.ordinal)
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(targetBus = bus)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setEqState(trackIndex: Int, eq: EqState) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetEqEnabled(nativeHandle, trackIndex, eq.enabled)
            AudioEngineJni.nativeSetEqLowShelf(nativeHandle, trackIndex, eq.lowShelfFreq, eq.lowShelfGain)
            AudioEngineJni.nativeSetEqMidBand(nativeHandle, trackIndex, eq.midFreq, eq.midGain, eq.midQ)
            AudioEngineJni.nativeSetEqHighShelf(nativeHandle, trackIndex, eq.highShelfFreq, eq.highShelfGain)
        }
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(eq = eq)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setCompState(trackIndex: Int, comp: CompressorState) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetCompEnabled(nativeHandle, trackIndex, comp.enabled)
            AudioEngineJni.nativeSetCompParams(nativeHandle, trackIndex, comp.thresholdDb, comp.ratio, comp.attackMs, comp.releaseMs)
            AudioEngineJni.nativeSetSidechainEnabled(nativeHandle, trackIndex, comp.sidechainEnabled, comp.sidechainSourceTrackIndex)
        }
        
        val newMixerTracks = _state.value.mixerTracks.toMutableList()
        if (trackIndex in newMixerTracks.indices) {
            newMixerTracks[trackIndex] = newMixerTracks[trackIndex].copy(comp = comp)
            _state.value = _state.value.copy(mixerTracks = newMixerTracks)
        }
    }

    fun setReverbState(reverb: ReverbState) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetReverbEnabled(nativeHandle, reverb.enabled)
            AudioEngineJni.nativeSetReverbParams(nativeHandle, reverb.roomSize, reverb.damping, reverb.wetDry, reverb.width)
        }
        _state.value = _state.value.copy(masterReverb = reverb)
    }

    fun setMasterSaturation(state: SaturationState) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetMasterSaturationEnabled(nativeHandle, state.enabled)
            AudioEngineJni.nativeSetMasterSaturationDrive(nativeHandle, state.drive)
        }
        _state.value = _state.value.copy(masterSaturation = state)
    }

    fun setMasterDelay(state: DelayState) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetMasterDelayEnabled(nativeHandle, state.enabled)
            AudioEngineJni.nativeSetMasterDelayParams(nativeHandle, state.timeMs, state.feedback, state.mix)
        }
        _state.value = _state.value.copy(masterDelay = state)
    }

    fun setMasterGate(state: GateState) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetMasterGateEnabled(nativeHandle, state.enabled)
            AudioEngineJni.nativeSetMasterGateParams(nativeHandle, state.thresholdDb, state.attackMs, state.releaseMs)
        }
        _state.value = _state.value.copy(masterGate = state)
    }

    fun getCompGainReduction(trackIndex: Int): Float {
        return if (nativeHandle != 0L) AudioEngineJni.nativeGetCompGainReduction(nativeHandle, trackIndex) else 0.0f
    }

    fun loadReferenceTrack(uriString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                val tempFile = File(getApplication<Application>().cacheDir, "reference_track.wav")
                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                if (nativeHandle != 0L) {
                    AudioEngineJni.nativeLoadReferenceTrack(nativeHandle, tempFile.absolutePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load reference track", e)
            }
        }
    }

    fun setListenToReference(listen: Boolean) {
        _state.value = _state.value.copy(listenToReference = listen)
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetListenToReference(nativeHandle, listen)
        }
    }

    fun applyMasteringPreset(presetName: String) {
        val preset = MasteringPresets.presets.find { it.name == presetName } ?: return
        
        val eq = preset.masterEq
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetMasterEqEnabled(nativeHandle, eq.enabled)
            AudioEngineJni.nativeSetMasterEqLowShelf(nativeHandle, eq.lowShelfFreq, eq.lowShelfGain)
            AudioEngineJni.nativeSetMasterEqMidBand(nativeHandle, eq.midFreq, eq.midGain, eq.midQ)
            AudioEngineJni.nativeSetMasterEqHighShelf(nativeHandle, eq.highShelfFreq, eq.highShelfGain)
        }
        
        val comp = preset.masterComp
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeSetMasterCompEnabled(nativeHandle, comp.enabled)
            AudioEngineJni.nativeSetMasterCompParams(nativeHandle, comp.thresholdDb, comp.ratio, comp.attackMs, comp.releaseMs)
        }
        
        _state.value = _state.value.copy(
            masterEq = eq,
            masterComp = comp,
            currentMasteringPreset = presetName
        )
    }

    fun updateVoiceSettings(settings: VoiceCaptureSettings) {
        _voiceSettings.value = settings
        voiceStateMachine.settings = settings
    }

    fun startVoicePreviewPlayback() {
        val notesToPlay = voicePreviewNotes.value
        if (notesToPlay.isEmpty()) return
        stopVoicePreviewPlayback()

        previewJob = viewModelScope.launch(Dispatchers.Default) {
            _isPreviewPlaying.value = true
            val firstMs = notesToPlay.minOf { it.startMs }
            val bpm = state.value.bpm.coerceAtLeast(40f)
            val msPerBeat = 60000f / bpm

            for (note in notesToPlay.sortedBy { it.startMs }) {
                if (!coroutineContext.isActive) break
                val noteDelayMs = ((note.startMs - firstMs) * (msPerBeat / 500f)).toLong()
                val noteDurMs = (note.durationMs * (msPerBeat / 500f)).toLong().coerceAtLeast(50L)

                delay(noteDelayMs)
                noteOn(note.midiNote, note.velocity / 127f)
                delay(noteDurMs)
                noteOff(note.midiNote)
            }
            _isPreviewPlaying.value = false
        }
    }

    fun stopVoicePreviewPlayback() {
        previewJob?.cancel()
        previewJob = null
        _isPreviewPlaying.value = false
    }

    fun noteOn(midiNote: Int, velocity: Float) {
        // Chromatic Instrument Mode: pitch-shift the selected sample relative to middle C (60)
        val chromaticPad = _chromaticSampleSource.value
        if (chromaticPad != null) {
            val extraSemitones = midiNote - 60
            if (nativeHandle != 0L) {
                AudioEngineJni.nativeTriggerPad(nativeHandle, chromaticPad.padIndex, velocity, extraSemitones)
            }
            return
        }

        // Vocal Pad Grid: MIDI notes 36-51 map to pads 0-15
        if (midiNote in 36..51) {
            val padIndex = midiNote - 36
            vocalPadManager.triggerPad(padIndex, velocity)
            if (nativeHandle != 0L) {
                AudioEngineJni.nativeTriggerPad(nativeHandle, padIndex, velocity, 0)
            }
            return
        }

        if (nativeHandle != 0L) {
            AudioEngineJni.nativeNoteOn(nativeHandle, midiNote, (velocity * 127f).toInt().coerceIn(0, 127))
        }
    }

    fun noteOff(midiNote: Int) {
        val chromaticPad = _chromaticSampleSource.value
        if (chromaticPad != null) {
            // NOTE: this releases the pad voice regardless of which key was
            // held, since PadPlayerPool is one voice per pad, not one voice
            // per (pad, key) combination. True polyphonic chromatic release
            // — holding multiple keys of the same pad independently — would
            // need per-note voice allocation, which is out of scope here.
            if (nativeHandle != 0L) {
                AudioEngineJni.nativeReleasePad(nativeHandle, chromaticPad.padIndex)
            }
            return
        }

        if (midiNote in 36..51) {
            val padIndex = midiNote - 36
            vocalPadManager.triggerPad(padIndex, 0f) // UI state
            if (nativeHandle != 0L) {
                AudioEngineJni.nativeReleasePad(nativeHandle, padIndex)
            }
            return
        }

        if (nativeHandle != 0L) {
            AudioEngineJni.nativeNoteOff(nativeHandle, midiNote)
        }
    }

    /** Called from a direct pad tap in VocalPadScreen (not via MIDI). */
    fun triggerPad(padIndex: Int) {
        vocalPadManager.triggerPad(padIndex, 1f) // UI state (isPadPlaying)
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeTriggerPad(nativeHandle, padIndex, 1f, 0)
        }
    }

    /** Called on tap release for HOLD_TO_PLAY pads. */
    fun releasePad(padIndex: Int) {
        vocalPadManager.triggerPad(padIndex, 0f)
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeReleasePad(nativeHandle, padIndex)
        }
    }

    /** Loads a user-picked audio file onto a pad and pushes it to the native engine. */
    fun loadPadSample(padIndex: Int, uri: Uri) {
        vocalPadManager.loadCustomSample(getApplication(), padIndex, uri)
        val path = vocalPadManager.pads.value.getOrNull(padIndex)?.samplePath ?: return
        if (nativeHandle != 0L && path.isNotEmpty()) {
            val loaded = AudioEngineJni.nativeLoadPadSample(nativeHandle, padIndex, path)
            if (!loaded) {
                Log.w(TAG, "Pad $padIndex: native load failed for $path — only 16-bit PCM WAV is supported today")
            }
            pushPadConfig(padIndex)
        }
    }

    /** Updates pad metadata (trim/pitch/fades/etc.) and pushes it to the native engine. */
    fun updatePadAndSync(padData: PadSampleData) {
        vocalPadManager.updatePad(padData)
        pushPadConfig(padData.padIndex)
    }

    private fun pushPadConfig(padIndex: Int) {
        val pad = vocalPadManager.pads.value.getOrNull(padIndex) ?: return
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeConfigurePad(
                nativeHandle, padIndex, pad.startMs, pad.endMs,
                pad.pitchShiftSemitones, pad.fineTuneCents, pad.gainDb,
                pad.fadeInMs, pad.fadeOutMs, pad.reverse,
                pad.playbackMode.ordinal, // relies on SamplePlaybackMode's
                                          // declaration order matching
                                          // PadVoice::PlaybackMode's — same
                                          // fragile-but-currently-correct
                                          // pattern as SubmixBus.ordinal
                pad.chokeGroup
            )
        }
    }

    fun setBpm(bpm: Float) {
        _state.value = _state.value.copy(bpm = bpm)
        // BPM is managed at the Kotlin layer; native engine reads it via polling
    }

    private val tapTimestamps = mutableListOf<Long>()
    private val TAP_TIMEOUT_MS = 2000L

    /**
     * Call each time the user taps the tempo button.
     * Averages up to the last 5 inter-tap intervals → sets project BPM.
     * Resets the sequence if more than 2 seconds have elapsed since the last tap.
     */
    fun onTapTempo() {
        val now = System.currentTimeMillis()
        if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > TAP_TIMEOUT_MS) {
            tapTimestamps.clear()
        }
        tapTimestamps.add(now)
        if (tapTimestamps.size < 2) return
        val recent = tapTimestamps.takeLast(5)
        val avgMs = recent.zipWithNext { a, b -> b - a }.average()
        setBpm((60_000.0 / avgMs).toFloat().coerceIn(40f, 240f))
    }

    /**
     * Lock the project BPM to whatever the native pitch/rhythm engine has detected.
     * No-op if no reliable estimate is available (returns 0).
     */
    fun lockToVoiceBpm() {
        if (nativeHandle == 0L) return
        val detected = AudioEngineJni.nativeGetEstimatedBpm(nativeHandle)
        if (detected > 0f) setBpm(detected.coerceIn(40f, 240f))
    }

    suspend fun bounceToWav(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (nativeHandle != 0L) {
            val success = AudioEngineJni.nativeBounceToWav(nativeHandle, path)
            if (success) Result.success(Unit) else Result.failure(Exception("Native bounce failed"))
        } else {
            Result.failure(Exception("Native engine not initialized"))
        }
    }

    fun resampleMasterToPad(targetPadIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                val destFile = File(cacheDir, "resample_pad_$targetPadIndex.wav")
                val result = bounceToWav(destFile.absolutePath)
                if (result.isSuccess) {
                    withContext(Dispatchers.Main) {
                        vocalPadManager.resampleToPad(targetPadIndex, destFile.absolutePath, "Resample ${targetPadIndex + 1}")
                        if (nativeHandle != 0L) {
                            AudioEngineJni.nativeLoadPadSample(nativeHandle, targetPadIndex, destFile.absolutePath)
                            pushPadConfig(targetPadIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resample master mix to pad $targetPadIndex", e)
            }
        }
    }

    fun splitSampleStemsToPads(sourcePadIndex: Int) {
        val pad = vocalPadManager.pads.value.getOrNull(sourcePadIndex) ?: return
        if (pad.samplePath.isEmpty()) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stemSeparator = com.voicedaw.audioengine.sampling.StemSeparator(getApplication())
                val stems = stemSeparator.separateStems(pad.samplePath)
                if (stems == null) {
                    Log.e(TAG, "Stem separation failed for pad $sourcePadIndex (unreadable/unsupported WAV)")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val baseIdx = (sourcePadIndex / 4) * 4
                    val targets = listOf(
                        baseIdx to (stems.vocalsPath to "Vocals"),
                        baseIdx + 1 to (stems.drumsPath to "Drums"),
                        baseIdx + 2 to (stems.bassPath to "Bass"),
                        baseIdx + 3 to (stems.otherPath to "Other"),
                    )
                    for ((idx, pathAndName) in targets) {
                        val (path, name) = pathAndName
                        vocalPadManager.resampleToPad(idx, path, name)
                        if (nativeHandle != 0L) {
                            AudioEngineJni.nativeLoadPadSample(nativeHandle, idx, path)
                            pushPadConfig(idx)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to separate stems for pad $sourcePadIndex", e)
            }
        }
    }

    fun startEngine() {
        if (nativeHandle == 0L) return
        AudioEngineJni.nativeStart(nativeHandle)
        // Fast poll: pitch + voice analysis (100ms = ~10fps, responsive for UI)
        pollJob = viewModelScope.launch {
            while (true) {
                delay(100)
                pollFastStatus()
            }
        }
        // Slow poll: LUFS, xruns, latency, sample rate (500ms is fine for meters)
        viewModelScope.launch {
            while (true) {
                delay(500)
                pollSlowStatus()
            }
        }
    }

    /**
     * Reopens the native output+input streams (AudioEngine::stop()+start()).
     *
     * Needed because the input stream (used for pitch tracking / voice-to-MIDI)
     * is opened once, at the first startEngine() call in init{} — which runs
     * before the user has necessarily answered the RECORD_AUDIO permission
     * dialog. If that first attempt failed due to missing permission, the
     * input stream stays closed forever unless something calls this after
     * the permission is actually granted (see MainActivity's permission
     * launcher callback).
     */
    fun restartEngine() {
        if (nativeHandle == 0L) return
        AudioEngineJni.nativeStop(nativeHandle)
        AudioEngineJni.nativeStart(nativeHandle)
    }

    private fun pollFastStatus() {
        if (nativeHandle == 0L) return
        _state.value = _state.value.copy(
            pitchHz             = AudioEngineJni.nativeGetPitchHz(nativeHandle),
            pitchAmp            = AudioEngineJni.nativeGetPitchAmp(nativeHandle),
            lastDetectedPad     = AudioEngineJni.nativeGetLastDetectedPad(nativeHandle),
            estimatedBpm        = AudioEngineJni.nativeGetEstimatedBpm(nativeHandle),
            estimatedKeyRoot    = AudioEngineJni.nativeGetEstimatedKeyRoot(nativeHandle),
            estimatedKeyIsMajor = AudioEngineJni.nativeGetEstimatedKeyIsMajor(nativeHandle),
            playbackPositionMs  = AudioEngineJni.nativeGetPlaybackPosition(nativeHandle)
        )
    }

    private fun pollSlowStatus() {
        if (nativeHandle == 0L) return
        _state.value = _state.value.copy(
            latencyMs      = AudioEngineJni.nativeGetLatencyMs(nativeHandle),
            xrunCount      = AudioEngineJni.nativeGetXrunCount(nativeHandle),
            integratedLUFS = AudioEngineJni.nativeGetIntegratedLUFS(nativeHandle),
            sampleRate     = AudioEngineJni.nativeGetSampleRate(nativeHandle),
        )
    }

    override fun onCleared() {
        super.onCleared()
        saveBeatboxModel()
        pollJob?.cancel()
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeStop(nativeHandle)
            AudioEngineJni.nativeDestroy(nativeHandle)
        }
    }
}
