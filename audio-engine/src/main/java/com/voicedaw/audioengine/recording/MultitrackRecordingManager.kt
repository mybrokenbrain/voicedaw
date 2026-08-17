package com.voicedaw.audioengine.recording

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MultitrackRecordingManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val MAX_TRACKS = 4
        private const val TAG = "MultitrackRecMgr"
        private const val DEFAULT_PROJECT = "default"
    }

    private val _tracks = MutableStateFlow<List<AudioTrack>>(
        List(MAX_TRACKS) { AudioTrack(it) }
    )
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _amplitudeSamples = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeSamples: StateFlow<List<Float>> = _amplitudeSamples.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _punchEnabled = MutableStateFlow(false)
    val punchEnabled: StateFlow<Boolean> = _punchEnabled.asStateFlow()

    private val _punchInMs = MutableStateFlow(0L)
    val punchInMs: StateFlow<Long> = _punchInMs.asStateFlow()

    private val _punchOutMs = MutableStateFlow(0L)
    val punchOutMs: StateFlow<Long> = _punchOutMs.asStateFlow()

    var projectId: String = DEFAULT_PROJECT
    
    private val activeRecorders = mutableMapOf<Int, TrackRecorder>()
    private val recorderJobs = mutableMapOf<Int, Job>()
    private var ampCollectJob: Job? = null

    fun setArmed(trackIndex: Int, armed: Boolean) {
        _tracks.value = _tracks.value.mapIndexed { index, track ->
            if (index == trackIndex) track.copy(isArmed = armed) else track
        }
    }

    fun toggleMute(trackIndex: Int) {
        _tracks.value = _tracks.value.mapIndexed { index, track ->
            if (index == trackIndex) track.copy(isMuted = !track.isMuted) else track
        }
    }

    fun toggleSolo(trackIndex: Int) {
        _tracks.value = _tracks.value.mapIndexed { index, track ->
            if (index == trackIndex) track.copy(isSolo = !track.isSolo) else track
        }
    }

    fun selectTake(trackIndex: Int, takeIndex: Int) {
        _tracks.value = _tracks.value.mapIndexed { index, track ->
            if (index == trackIndex) {
                val updatedTakes = track.takes.mapIndexed { ti, clip ->
                    clip.copy(isBestTake = ti == takeIndex)
                }
                track.copy(takes = updatedTakes, activeTakeIndex = takeIndex)
            } else track
        }
    }

    fun updateClipTrim(trackIndex: Int, takeIndex: Int, startSample: Long, endSample: Long, fadeInSamples: Int, fadeOutSamples: Int) {
        _tracks.value = _tracks.value.mapIndexed { index, track ->
            if (index == trackIndex) {
                val updatedTakes = track.takes.mapIndexed { ti, clip ->
                    if (ti == takeIndex) {
                        clip.copy(
                            startSample = startSample,
                            endSample = endSample,
                            fadeInSamples = fadeInSamples,
                            fadeOutSamples = fadeOutSamples
                        )
                    } else clip
                }
                track.copy(takes = updatedTakes)
            } else track
        }
    }

    fun setInputSource(trackIndex: Int, source: AudioTrack.InputSource) {
        _tracks.value = _tracks.value.mapIndexed { index, track ->
            if (index == trackIndex) track.copy(inputSource = source) else track
        }
    }

    fun setPunchEnabled(enabled: Boolean) {
        _punchEnabled.value = enabled
    }

    fun setPunchIn(ms: Long) {
        _punchInMs.value = ms
    }

    fun setPunchOut(ms: Long) {
        _punchOutMs.value = ms
    }

    fun startRecording() {
        if (_isRecording.value) return
        
        var armedTracks = _tracks.value.filter { it.isArmed }
        if (armedTracks.isEmpty()) {
            setArmed(0, true)
            armedTracks = _tracks.value.filter { it.isArmed }
        }
        
        val effectivePunchOutMs = if (_punchEnabled.value && _punchOutMs.value > _punchInMs.value) _punchOutMs.value else -1L
        val effectivePunchInMs = if (_punchEnabled.value && _punchOutMs.value > _punchInMs.value) _punchInMs.value else 0L
        
        armedTracks.forEach { track ->
            val recorder = TrackRecorder(context, track.trackIndex, projectId, track.inputSource)
            activeRecorders[track.trackIndex] = recorder
            val job = scope.launch(Dispatchers.IO) {
                recorder.startRecording(effectivePunchInMs, effectivePunchOutMs)
            }
            recorderJobs[track.trackIndex] = job
        }
        
        _isRecording.value = true
    }

    fun stopRecording() {
        if (_isRecording.value) {
            _isRecording.value = false
            ampCollectJob?.cancel()
            ampCollectJob = null
            
            val snapRecorders = activeRecorders.toMap()
            activeRecorders.clear()
            
            scope.launch(Dispatchers.IO) {
                snapRecorders.forEach { (trackIndex, recorder) ->
                    val result = recorder.stopRecording()
                    result.onSuccess { newClip ->

                        _tracks.value = _tracks.value.mapIndexed { index, track ->
                            if (index == trackIndex) {
                                val updatedTakes = track.takes + newClip
                                track.copy(
                                    takes = updatedTakes,
                                    activeTakeIndex = updatedTakes.size - 1
                                )
                            } else track
                        }
                    }.onFailure { e ->
                        Log.e(TAG, "Recording failed for track $trackIndex", e)
                    }
                }
            }
        }
    }

    fun cancelRecording() {
        _isRecording.value = false
        ampCollectJob?.cancel()
        ampCollectJob = null
        
        activeRecorders.values.forEach { it.cancel() }
        activeRecorders.clear()
        
        recorderJobs.values.forEach { it.cancel() }
        recorderJobs.clear()
        
        _amplitudeSamples.value = emptyList()
    }

    fun isAnyTrackRecording(): Boolean {
        return activeRecorders.values.any { it.isRecording.value }
    }
}
