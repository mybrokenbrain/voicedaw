package com.voicedaw.audioengine.mixer

data class TrackMixerState(
    val trackIndex: Int,
    val volumeDb: Float = 0.0f,
    val pan: Float = 0.0f,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val reverbSend: Float = 0.0f,
    val delaySend: Float = 0.0f,
    val targetBus: SubmixBus = SubmixBus.MASTER,
    val eq: EqState = EqState(),
    val comp: CompressorState = CompressorState()
)
