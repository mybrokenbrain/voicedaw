package com.voicedaw.audioengine

import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt

object PitchMath {

    fun frequencyToMidiNote(hz: Float): Int =
        if (hz <= 0f) 0
        else (12.0 * log2(hz.toDouble() / 440.0) + 69.0).roundToInt().coerceIn(0, 127)

    fun frequencyToMidiFractional(hz: Float): Float =
        if (hz <= 0f) 0f
        else (12.0 * log2(hz.toDouble() / 440.0) + 69.0).toFloat()

    fun snapToNearestActiveNote(rawMidiNote: Int, activePitchClasses: BooleanArray): Int {
        if (activePitchClasses.all { !it }) return rawMidiNote
        val pitchClass = (rawMidiNote % 12 + 12) % 12
        if (activePitchClasses[pitchClass]) return rawMidiNote

        for (offset in 1..6) {
            val lowerClass = (pitchClass - offset + 12) % 12
            if (activePitchClasses[lowerClass]) return (rawMidiNote - offset).coerceIn(0, 127)

            val upperClass = (pitchClass + offset) % 12
            if (activePitchClasses[upperClass]) return (rawMidiNote + offset).coerceIn(0, 127)
        }
        return rawMidiNote
    }

    fun calculatePitchBendSemitones(pitchHz: Float, baseMidiNote: Int, deadbandCents: Float): Float {
        val rawFractional = frequencyToMidiFractional(pitchHz)
        val devSemitones = rawFractional - baseMidiNote
        val devCents = devSemitones * 100f
        return if (abs(devCents) <= deadbandCents) 0f else devSemitones
    }

    fun getScalePresetMask(rootNoteClass: Int, preset: ScalePreset): BooleanArray {
        val mask = BooleanArray(12) { false }
        val intervals = when (preset) {
            ScalePreset.CHROMATIC -> intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
            ScalePreset.MAJOR -> intArrayOf(0, 2, 4, 5, 7, 9, 11)
            ScalePreset.MINOR -> intArrayOf(0, 2, 3, 5, 7, 8, 10)
            ScalePreset.PENTATONIC_MAJOR -> intArrayOf(0, 2, 4, 7, 9)
            ScalePreset.PENTATONIC_MINOR -> intArrayOf(0, 3, 5, 7, 10)
            ScalePreset.CUSTOM -> intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        }
        for (interval in intervals) {
            val noteClass = (rootNoteClass + interval) % 12
            mask[noteClass] = true
        }
        return mask
    }

    fun isNoteInActiveScale(midiNote: Int, activePitchClasses: BooleanArray): Boolean {
        if (midiNote !in 0..127) return false
        if (activePitchClasses.isEmpty()) return false
        val pitchClass = (midiNote % 12 + 12) % 12
        return activePitchClasses[pitchClass]
    }
}
