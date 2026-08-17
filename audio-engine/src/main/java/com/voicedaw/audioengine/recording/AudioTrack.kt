package com.voicedaw.audioengine.recording

data class AudioTrack(
    val trackIndex: Int,
    val name: String = "Track ${trackIndex + 1}",
    val inputSource: InputSource = InputSource.INTERNAL_MIC,
    val takes: List<AudioClip> = emptyList(),
                      val activeTakeIndex: Int = -1,
                      val isArmed: Boolean = false,
                      val isMuted: Boolean = false,
                      val isSolo: Boolean = false,
                      val volume: Float = 1.0f,
                      val pan: Float = 0.0f
) {
    val activeTake: AudioClip?
    get() = takes.getOrNull(activeTakeIndex)

    val hasAudio: Boolean
    get() = takes.isNotEmpty()

    enum class InputSource(val label: String) {
        INTERNAL_MIC("Built-in mic"),
        USB_MIC("USB mic")
    }
}
