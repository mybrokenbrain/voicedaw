package com.voicedaw.midi

data class PianoNote(
    val midiNote: Int,
    val startBeat: Float,
    val durationBeats: Float = 0.5f,
    val velocity: Int = 100,
    val midiChannel: Int = 1,
)
