package com.voicedaw.midi

import org.junit.Assert.*
import org.junit.Test

class MidiCleanupTest {

    private fun note(midiNote: Int, vel: Int, startBeat: Float, durBeats: Float) =
        PianoNote(midiNote = midiNote, velocity = vel, startBeat = startBeat, durationBeats = durBeats)

    // Ghost note removal

    @Test fun `ghost note short AND quiet is removed`() {
        val notes = listOf(
            note(60, 10, 0f, 0.05f),
            note(64, 80, 1f, 0.5f),
        )
        val result = MidiCleanup.cleanupVocalTake(notes)
        assertEquals(1, result.size)
        assertEquals(64, result[0].midiNote)
    }

    @Test fun `note is kept if velocity is above threshold even if short`() {
        val notes = listOf(note(60, 80, 0f, 0.05f))
        val result = MidiCleanup.cleanupVocalTake(notes)
        assertEquals(1, result.size)
    }

    @Test fun `note is kept if duration is above threshold even if quiet`() {
        val notes = listOf(note(60, 10, 0f, 0.5f))
        val result = MidiCleanup.cleanupVocalTake(notes)
        assertEquals(1, result.size)
    }

    @Test fun `empty input returns empty output`() {
        assertTrue(MidiCleanup.cleanupVocalTake(emptyList()).isEmpty())
    }

    // Latency compensation

    @Test fun `latency compensation shifts note start earlier`() {
        val notes = listOf(note(60, 80, 1f, 0.5f))
        val config = MidiCleanup.Config(
            latencyCompensationBeats = 0.08f,
            quantize = false,
            applyLegato = false,
        )
        val result = MidiCleanup.cleanupVocalTake(notes, config)
        assertEquals(1f - 0.08f, result[0].startBeat, 0.001f)
    }

    @Test fun `latency compensation never produces negative start beat`() {
        val notes = listOf(note(60, 80, 0.05f, 0.5f))
        val config = MidiCleanup.Config(
            latencyCompensationBeats = 0.08f,
            quantize = false,
            applyLegato = false,
        )
        val result = MidiCleanup.cleanupVocalTake(notes, config)
        assertTrue(result[0].startBeat >= 0f)
    }

    // Quantization

    @Test fun `quantize snaps to nearest grid position`() {
        val notes = listOf(note(60, 80, 0.38f, 0.5f))
        val config = MidiCleanup.Config(
            latencyCompensationBeats = 0f,
            quantize = true,
            quantizeBeats = 0.25f,
            applyLegato = false,
        )
        val result = MidiCleanup.cleanupVocalTake(notes, config)
        assertEquals(0.50f, result[0].startBeat, 0.001f)
    }

    @Test fun `quantize disabled leaves start beat unchanged (after latency comp)`() {
        val notes = listOf(note(60, 80, 0.38f, 0.5f))
        val config = MidiCleanup.Config(
            latencyCompensationBeats = 0f,
            quantize = false,
            applyLegato = false,
        )
        val result = MidiCleanup.cleanupVocalTake(notes, config)
        assertEquals(0.38f, result[0].startBeat, 0.001f)
    }

    // Legato

    @Test fun `legato fills small gap between consecutive notes`() {
        val notes = listOf(
            note(60, 80, 0f, 0.5f),
            note(64, 80, 0.7f, 0.5f),
        )
        val config = MidiCleanup.Config(
            latencyCompensationBeats = 0f,
            quantize = false,
            applyLegato = true,
        )
        val result = MidiCleanup.cleanupVocalTake(notes, config)
        assertEquals(0.7f, result[0].startBeat + result[0].durationBeats, 0.001f)
    }

    @Test fun `legato does not extend note when gap is larger than threshold`() {
        val notes = listOf(
            note(60, 80, 0f, 0.5f),
            note(64, 80, 2.0f, 0.5f),
        )
        val config = MidiCleanup.Config(
            latencyCompensationBeats = 0f,
            quantize = false,
            applyLegato = true,
        )
        val result = MidiCleanup.cleanupVocalTake(notes, config)
        assertEquals(0.5f, result[0].durationBeats, 0.001f)
    }

    // Output ordering

    @Test fun `output is sorted by startBeat`() {
        val notes = listOf(
            note(64, 80, 2f, 0.5f),
            note(60, 80, 0f, 0.5f),
            note(67, 80, 1f, 0.5f),
        )
        val result = MidiCleanup.cleanupVocalTake(
            notes,
            MidiCleanup.Config(latencyCompensationBeats = 0f, quantize = false, applyLegato = false)
        )
        assertEquals(3, result.size)
        assertTrue(result[0].startBeat <= result[1].startBeat)
        assertTrue(result[1].startBeat <= result[2].startBeat)
    }
}
