package com.voicedaw.audioengine.performance

enum class PerformanceFxType(val id: Int, val displayName: String) {
    STUTTER_8TH(0, "Stutter 1/8"),
    STUTTER_16TH(1, "Stutter 1/16"),
    STUTTER_32ND(2, "Stutter 1/32"),
    TAPE_STOP(3, "Tape Stop"),
    FILTER_LP_SWEEP(4, "LP Filter"),
    FILTER_HP_SWEEP(5, "HP Filter"),
    BITCRUSH(6, "Bitcrush"),
    REVERB_BURST(7, "Reverb Burst"),
    PITCH_DROP(8, "Pitch Drop"),
    PITCH_RISE(9, "Pitch Rise"),
    CHORUS_FLANGE(10, "Flanger"),
    VINYL_CRACKLE(11, "Vinyl Scratch"),
    DELAY_FREEZE(12, "Delay Freeze"),
    GATED_REVERB(13, "Gated Rev"),
    DISTORTION_BOOST(14, "Distortion"),
    SILENCE_CUT(15, "Mute Cut")
}

data class PerformanceFxState(
    val activeFxType: PerformanceFxType? = null,
    val isHoldEnabled: Boolean = false
)
