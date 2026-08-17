package com.voicedaw.audioengine.sampling

enum class SamplePlaybackMode {
    ONE_SHOT,
    LOOP,
    HOLD_TO_PLAY,
    PING_PONG
}

data class PadSampleData(
    val padIndex: Int,
    val samplePath: String = "",
    val name: String = "Pad ${padIndex + 1}",
    val startMs: Float = 0f,
    val endMs: Float = 0f,
    val pitchShiftSemitones: Int = 0,
    val fineTuneCents: Int = 0,
    val gainDb: Float = 0f,
    val fadeInMs: Float = 0f,
    val fadeOutMs: Float = 0f,
    val reverse: Boolean = false,
    val isLoop: Boolean = false,
    val playbackMode: SamplePlaybackMode = SamplePlaybackMode.ONE_SHOT,
    val chokeGroup: Int = 0,
    val midiNoteTrigger: Int = 36 + padIndex
)
