package com.voicedaw.audioengine.mixer

data class CompressorState(
    val enabled: Boolean = true,
    val thresholdDb: Float = -18.0f,
    val ratio: Float = 4.0f,
    val attackMs: Float = 10.0f,
    val releaseMs: Float = 100.0f,
    val sidechainEnabled: Boolean = false,
    val sidechainSourceTrackIndex: Int = -1
)
