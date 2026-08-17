package com.voicedaw.midi

import kotlin.math.roundToInt

object MidiCleanup {

    data class Config(
        val latencyCompensationBeats: Float = 0.08f,
        val minDurationBeats: Float = 0.125f,
        val minVelocity: Int = 30,
        val applyLegato: Boolean = true,
        val quantize: Boolean = true,
        val quantizeBeats: Float = 0.25f,
    )

    fun cleanupVocalTake(
        rawNotes: List<PianoNote>,
        config: Config = Config()
    ): List<PianoNote> {
        if (rawNotes.isEmpty()) return emptyList()

        // Filter, Latency Compensation, and Quantize
        val filteredAndQuantized = rawNotes.mapNotNull { note ->
            if (note.durationBeats < config.minDurationBeats && note.velocity < config.minVelocity) {
                return@mapNotNull null
            }

            var newStart = note.startBeat - config.latencyCompensationBeats
            if (newStart < 0f) newStart = 0f

            if (config.quantize && config.quantizeBeats > 0f) {
                newStart = (newStart / config.quantizeBeats).roundToInt() * config.quantizeBeats
            }

            note.copy(startBeat = newStart)
        }.sortedBy { it.startBeat }.toMutableList()

        // Legato correction
        if (config.applyLegato && filteredAndQuantized.isNotEmpty()) {
            for (i in 0 until filteredAndQuantized.size - 1) {
                val current = filteredAndQuantized[i]
                val next = filteredAndQuantized[i + 1]
                
                val endBeat = current.startBeat + current.durationBeats
                val gapBeats = next.startBeat - endBeat
                
                if (gapBeats > 0f && gapBeats < 0.5f) {
                    val newDuration = next.startBeat - current.startBeat
                    filteredAndQuantized[i] = current.copy(durationBeats = newDuration)
                }
            }
        }

        return filteredAndQuantized
    }
}
