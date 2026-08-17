package com.voicedaw.midi

data class MidiNoteEvent(
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean
)
