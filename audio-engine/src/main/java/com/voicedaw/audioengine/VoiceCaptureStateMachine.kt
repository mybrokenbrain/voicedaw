package com.voicedaw.audioengine

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

class VoiceCaptureStateMachine(
    private val pollIntervalMs: Long = 16L,
    private val stabilityFrames: Int = 5,
    private val semitoneTolerance: Float = 0.7f,
    ampFloor: Float = 0.02f,
    var settings: VoiceCaptureSettings = VoiceCaptureSettings()
) {
    private var ampFloor: Float = ampFloor

    fun setAmpFloor(newFloor: Float) {
        ampFloor = newFloor.coerceAtLeast(0.001f)
    }

    val currentAmpFloor: Float get() = ampFloor

    companion object {
        private const val MIN_PITCH_HZ = 60f

        fun frequencyToMidiNote(hz: Float): Int = PitchMath.frequencyToMidiNote(hz)
    }

    data class CapturedNote(
        val midiNote: Int,
        val velocity: Int = 80,
        val startMs: Float,
        val durationMs: Float,
    )

    private var noteOn: Boolean = false
    internal var currentMidiNote: Int = -1
    private var stableFrameCount: Int = 0
    private var noteStartTimeMs: Float = 0f
    private var wallClockMs: Float = 0f
    private var lastAmpSeen: Float = 0.05f
    private val _capturedNotes: MutableList<CapturedNote> = mutableListOf()

    val hasActiveNote: Boolean get() = noteOn
    val capturedNotes: List<CapturedNote> get() = _capturedNotes.toList()

    fun processFrame(pitchHz: Float, amp: Float): List<CapturedNote>? {
        wallClockMs += pollIntervalMs

        // FIXED 2026-07-28: Reject invalid pitch markers from native detector.
        // YinPitchTracker now returns -1.0f for unvoiced/silent/out-of-range frames
        // instead of garbage values that would map to wrong MIDI notes. Check this
        // FIRST before attempting frequency-to-MIDI conversion.
        if (pitchHz < 0.0f) {
            return handleSilence()
        }

        var effectiveAmp = amp
        if (settings.noiseGateEnabled) {
            val ampDb = if (effectiveAmp <= 0.00001f) -100f else 20f * kotlin.math.log10(effectiveAmp)
            if (ampDb < settings.noiseGateThresholdDb) {
                effectiveAmp = 0f
            }
        }

        if (settings.deverbEnabled && effectiveAmp > 0f) {
            val deverbFactor = (1f - (settings.deverbAmount * 0.3f)).coerceIn(0.5f, 1f)
            effectiveAmp *= deverbFactor
        }

        val silent = effectiveAmp < ampFloor || pitchHz < MIN_PITCH_HZ
        if (silent) return handleSilence()

        val rawMidi = PitchMath.frequencyToMidiNote(pitchHz)
        val snappedMidi = PitchMath.snapToNearestActiveNote(rawMidi, settings.activePitchClasses)
        lastAmpSeen = effectiveAmp
        return handlePitch(snappedMidi)
    }

    fun flush(): List<CapturedNote> {
        if (noteOn) closeCurrentNote()
        return capturedNotes
    }

    fun reset() {
        noteOn = false
        currentMidiNote = -1
        stableFrameCount = 0
        noteStartTimeMs = 0f
        wallClockMs = 0f
        _capturedNotes.clear()
    }

    private fun handleSilence(): List<CapturedNote>? {
        stableFrameCount = 0
        if (!noteOn) return null
        closeCurrentNote()
        currentMidiNote = -1
        return capturedNotes
    }

    private fun handlePitch(midiNote: Int): List<CapturedNote>? {
        if (noteOn) {
            val semitoneShift = abs(midiNote - currentMidiNote).toFloat()
            if (semitoneShift > semitoneTolerance) {
                closeCurrentNote()
                noteOn = false
                currentMidiNote = midiNote
                stableFrameCount = 1
                noteStartTimeMs = wallClockMs
                return capturedNotes
            }
            return null
        }

        if (currentMidiNote == midiNote || currentMidiNote == -1) {
            stableFrameCount++
            if (stableFrameCount == 1) noteStartTimeMs = wallClockMs - pollIntervalMs
            if (stableFrameCount >= stabilityFrames) {
                currentMidiNote = midiNote
                noteOn = true
            } else {
                currentMidiNote = midiNote
            }
        } else {
            currentMidiNote = midiNote
            stableFrameCount = 1
            noteStartTimeMs = wallClockMs - pollIntervalMs
        }
        return null
    }

    private fun calculateVelocity(): Int {
        return when (settings.hardnessMode) {
            VelocityHardnessMode.FIXED -> settings.fixedVelocity.coerceIn(1, 127)
            VelocityHardnessMode.DYNAMIC -> {
                val scaled = (lastAmpSeen * 250f).roundToInt()
                scaled.coerceIn(settings.minVelocity, settings.maxVelocity)
            }
            VelocityHardnessMode.HYBRID -> {
                val scaled = (lastAmpSeen * 200f + 40).roundToInt()
                scaled.coerceIn(settings.minVelocity, settings.maxVelocity)
            }
        }
    }

    private fun closeCurrentNote() {
        val durationMs = (wallClockMs - noteStartTimeMs).coerceAtLeast(60f)
        _capturedNotes.add(
            CapturedNote(
                midiNote = currentMidiNote,
                velocity = calculateVelocity(),
                startMs = noteStartTimeMs,
                durationMs = durationMs,
            )
        )
        noteOn = false
    }
}
