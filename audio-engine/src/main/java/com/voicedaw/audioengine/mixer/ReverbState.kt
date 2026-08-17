package com.voicedaw.audioengine.mixer

data class ReverbState(
    val enabled: Boolean = true,
    val roomSize: Float = 0.5f,
    val damping: Float = 0.5f,
    val wetDry: Float = 0.3f,
    val width: Float = 1.0f
)
