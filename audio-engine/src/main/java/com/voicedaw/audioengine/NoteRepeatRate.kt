package com.voicedaw.audioengine

enum class NoteRepeatRate(val beats: Float, val label: String) {
    OFF(0f, "Off"),
    RATE_1_8(0.5f, "1/8"),
    RATE_1_16(0.25f, "1/16"),
    RATE_1_32(0.125f, "1/32"),
    RATE_1_64(0.0625f, "1/64");

    fun getSubdivisions(totalBeats: Float): Int {
        if (this == OFF || beats <= 0f) return 1
        return (totalBeats / beats).toInt().coerceAtLeast(1)
    }
}
