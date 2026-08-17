package com.voicedaw.audioengine.mixer

enum class SubmixBus(val busId: Int, val displayName: String) {
    MASTER(0, "Master"),
    DRUM_BUS(1, "Drum Bus"),
    VOCAL_BUS(2, "Vocal Bus"),
    INSTRUMENT_BUS(3, "Inst Bus")
}
