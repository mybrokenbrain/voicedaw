package com.voicedaw.audioengine

import android.app.Application
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voicedaw.audioengine.export.AudioExporter
import com.voicedaw.audioengine.mixer.*
import com.voicedaw.audioengine.recording.*
import com.voicedaw.midi.MidiCleanup
import com.voicedaw.midi.PianoNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

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
        val listenToReference: Boolean = false,
        val masterEq: EqState = EqState(),
        val masterComp: CompressorState = CompressorState(),
        val currentMasteringPreset: String? = null,
        val integratedLUFS: Float = 0.0f,
        val availableOutputDevices: List<AudioDeviceState> = emptyList(),
        val selectedOutputDeviceId: Int = 0,
        val voiceInputMode: Int = 0, // 0=Off, 1=Hum, 2=Beatbox
        val playbackPositionMs: Float = 0.0f
    )

    val nativeHandle: Long = AudioEngineJni.nativeCreate()
    val recordingManager = MultitrackRecordingManager(application, viewModelScope)
    private val thermalMonitor = ThermalMonitor(application)
    
    private val _state = MutableStateFlow(AudioState(
        mixerTracks = List(MultitrackRecordingManager.MAX_TRACKS) { TrackMixerState(it) }
    ))
    val state: StateFlow<AudioState> = _state.asStateFlow()
    
    private var pollJob: Job? = null

    // Voice capture
    private val voiceStateMachine = VoiceCaptureStateMachine(pollIntervalMs = 16L)
    private var voiceCaptureJob: Job? = null

    private val _voicePreviewNotes = MutableStateFlow<List<VoiceCaptureStateMachine.CapturedNote>>(emptyList())
    val voicePreviewNotes: StateFlow<List<VoiceCaptureStateMachine.CapturedNote>> = _voicePreviewNotes.asStateFlow()

    companion object {
        private const val TAG = "AudioEngineViewModel"
    }

    init {
        
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
            recordingManager.stopRecording()
            if (nativeHandle != 0L) AudioEngineJni.nativeSetTransportState(nativeHandle, 0)
        } else {
            recordingManager.startRecording()
            if (nativeHandle != 0L) AudioEngineJni.nativeSetTransportState(nativeHandle, 2)
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


    fun startVoiceCapture() {
        voiceStateMachine.reset()
        _voicePreviewNotes.value = emptyList()
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
            }
        }
    }


    fun stopVoiceCapture(): List<VoiceCaptureStateMachine.CapturedNote> {
        voiceCaptureJob?.cancel()
        voiceCaptureJob = null
        setAnalysisMode(0)
        val raw = voiceStateMachine.flush()
        _voicePreviewNotes.value = raw
        return raw
    }


    fun commitVoiceCapture(bpm: Float): List<PianoNote> {
        val rawMs = _voicePreviewNotes.value
        _voicePreviewNotes.value = emptyList()
        voiceStateMachine.reset()
        if (rawMs.isEmpty()) return emptyList()

        val msPerBeat = if (bpm > 0f) 60000f / bpm else 500f // default 120bpm


        val asBeats = rawMs.map { note ->
            PianoNote(
                midiNote      = note.midiNote,
                startBeat     = note.startMs / msPerBeat,
                durationBeats = (note.durationMs / msPerBeat).coerceAtLeast(0.0625f),
                velocity      = note.velocity,
            )
        }

        return MidiCleanup.cleanupVocalTake(asBeats)
    }


    fun discardVoiceCapture() {
        voiceCaptureJob?.cancel()
        voiceCaptureJob = null
        setAnalysisMode(0)
        _voicePreviewNotes.value = emptyList()
        voiceStateMachine.reset()
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

    fun noteOn(midiNote: Int, velocity: Float) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeNoteOn(nativeHandle, midiNote, (velocity * 127f).toInt().coerceIn(0, 127))
        }
    }

    fun noteOff(midiNote: Int) {
        if (nativeHandle != 0L) {
            AudioEngineJni.nativeNoteOff(nativeHandle, midiNote)
        }
    }

    fun setBpm(bpm: Float) {
        _state.value = _state.value.copy(bpm = bpm)

    }

    suspend fun bounceToWav(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (nativeHandle != 0L) {
            val success = AudioEngineJni.nativeBounceToWav(nativeHandle, path)
            if (success) Result.success(Unit) else Result.failure(Exception("Native bounce failed"))
        } else {
            Result.failure(Exception("Native engine not initialized"))
        }
    }

    fun startEngine() {
        if (nativeHandle == 0L) return
        AudioEngineJni.nativeStart(nativeHandle)
        pollJob = viewModelScope.launch {
            while (true) {
                delay(100)
                pollFastStatus()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(500)
                pollSlowStatus()
            }
        }
    }


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
