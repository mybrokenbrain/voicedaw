package com.voicedaw.audioengine.mixer

data class MasteringPreset(
    val name: String,
    val targetLufs: Float,
    val masterEq: EqState,
    val masterComp: CompressorState
)
