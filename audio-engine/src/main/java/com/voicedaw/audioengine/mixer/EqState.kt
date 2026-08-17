package com.voicedaw.audioengine.mixer

data class EqState(
    val enabled: Boolean = true,
    val lowShelfFreq: Float = 200.0f,
    val lowShelfGain: Float = 0.0f,
    val midFreq: Float = 1000.0f,
    val midGain: Float = 0.0f,
    val midQ: Float = 0.707f,
    val highShelfFreq: Float = 8000.0f,
    val highShelfGain: Float = 0.0f
)
