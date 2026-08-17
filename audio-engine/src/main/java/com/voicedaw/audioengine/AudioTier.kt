package com.voicedaw.audioengine

enum class AudioTier(val label: String) {
    PRO("PRO — LowLatency"),
    LOW("LOW — StandardLatency"),
    UNSUPPORTED("⚠ Unsupported"),
    UNKNOWN("Detecting…")
}
