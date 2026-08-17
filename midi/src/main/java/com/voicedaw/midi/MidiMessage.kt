package com.voicedaw.midi

data class MidiMessage(
    val status: Int,
    val data1: Int = 0,
    val data2: Int = 0,
    val timestampNs: Long = 0
) {
    val channel: Int get() = status and 0x0F
    val messageType: Int get() = status and 0xF0

    companion object {
        const val NOTE_OFF      = 0x80
        const val NOTE_ON       = 0x90
        const val POLY_PRESSURE = 0xA0
        const val CONTROL_CHANGE = 0xB0
        const val PROGRAM_CHANGE = 0xC0
        const val PITCH_BEND    = 0xE0
    }
}
