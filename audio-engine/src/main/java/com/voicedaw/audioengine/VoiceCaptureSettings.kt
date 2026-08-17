package com.voicedaw.audioengine

enum class VelocityHardnessMode { DYNAMIC, FIXED, HYBRID }

enum class ScalePreset { CHROMATIC, MAJOR, MINOR, PENTATONIC_MAJOR, PENTATONIC_MINOR, CUSTOM }

data class VoiceCaptureSettings(
    val hardnessMode: VelocityHardnessMode = VelocityHardnessMode.DYNAMIC,
    val fixedVelocity: Int = 96,
    val minVelocity: Int = 30,
    val maxVelocity: Int = 127,

    val pitchBendEnabled: Boolean = false,
    val bendDeadbandCents: Float = 20f,
    val bendRangeSemitones: Int = 2,

    val activePitchClasses: BooleanArray = BooleanArray(12) { true },
    val scalePreset: ScalePreset = ScalePreset.CHROMATIC,

    val noiseGateEnabled: Boolean = true,
    val noiseGateThresholdDb: Float = -45f,
    val deverbEnabled: Boolean = true,
    val deverbAmount: Float = 0.5f,
    val spectralDenoiseDb: Float = 12f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceCaptureSettings
        return hardnessMode == other.hardnessMode &&
                fixedVelocity == other.fixedVelocity &&
                minVelocity == other.minVelocity &&
                maxVelocity == other.maxVelocity &&
                pitchBendEnabled == other.pitchBendEnabled &&
                bendDeadbandCents == other.bendDeadbandCents &&
                bendRangeSemitones == other.bendRangeSemitones &&
                activePitchClasses.contentEquals(other.activePitchClasses) &&
                scalePreset == other.scalePreset
    }

    override fun hashCode(): Int {
        var result = hardnessMode.hashCode()
        result = 31 * result + fixedVelocity
        result = 31 * result + minVelocity
        result = 31 * result + maxVelocity
        result = 31 * result + pitchBendEnabled.hashCode()
        result = 31 * result + bendDeadbandCents.hashCode()
        result = 31 * result + bendRangeSemitones
        result = 31 * result + activePitchClasses.contentHashCode()
        result = 31 * result + scalePreset.hashCode()
        return result
    }
}
