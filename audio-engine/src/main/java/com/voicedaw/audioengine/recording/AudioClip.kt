package com.voicedaw.audioengine.recording

data class AudioClip(
    val clipId: Long,
    val trackIndex: Int,
    val filePath: String,
    val startSample: Long = 0L,
    val endSample: Long = -1L,
    val fadeInSamples: Int = 0,
    val fadeOutSamples: Int = 0,
    val isBestTake: Boolean = false,
    val recordedAtMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNELS = 2
        const val BYTES_PER_SAMPLE = 2
        const val BYTES_PER_FRAME = 4

        fun msToSamples(ms: Long): Long {
            return (SAMPLE_RATE * ms) / 1000
        }

        fun samplesToMs(samples: Long): Long {
            return (1000 * samples) / SAMPLE_RATE
        }
    }
}

fun AudioClip.effectiveDurationFrames(sampleRate: Int): Long {
    return if (endSample > startSample) {
        endSample - startSample
    } else {
        sampleRate.toLong() * 10L
    }
}
