package com.voicedaw.audioengine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VoiceCaptureStateMachineTest {

    private lateinit var sm: VoiceCaptureStateMachine

    @Before fun setup() {
        sm = VoiceCaptureStateMachine(
            pollIntervalMs  = 16L,
            stabilityFrames = 5,
            ampFloor        = 0.02f,
            semitoneTolerance = 0.7f,
        )
    }

    // Silence

    @Test fun `silence produces no notes`() {
        repeat(20) { sm.processFrame(0f, 0f) }
        assertTrue(sm.capturedNotes.isEmpty())
    }

    @Test fun `below amp floor is treated as silence`() {
        repeat(20) { sm.processFrame(440f, 0.01f) }
        assertTrue(sm.capturedNotes.isEmpty())
    }

    @Test fun `below 60 Hz is treated as silence`() {
        repeat(20) { sm.processFrame(50f, 0.5f) }
        assertTrue(sm.capturedNotes.isEmpty())
    }

    // Stability threshold

    @Test fun `4 stable frames (below threshold) does not emit note`() {
        repeat(4) { sm.processFrame(440f, 0.5f) }
        sm.processFrame(0f, 0f)
        assertTrue(sm.capturedNotes.isEmpty())
    }

    @Test fun `5 stable frames emits note on flush`() {
        repeat(5) { sm.processFrame(440f, 0.5f) }
        sm.processFrame(0f, 0f)
        val notes = sm.capturedNotes
        assertEquals(1, notes.size)
        assertEquals(69, notes[0].midiNote)
    }

    @Test fun `note duration is at least 60ms`() {
        repeat(5) { sm.processFrame(440f, 0.5f) }
        sm.processFrame(0f, 0f)
        val notes = sm.capturedNotes
        assertTrue(notes[0].durationMs >= 60f)
    }

    // Pitch change

    @Test fun `pitch change larger than semitone tolerance closes current note`() {
        repeat(5) { sm.processFrame(440f, 0.5f) }
        repeat(5) { sm.processFrame(523f, 0.5f) }
        val flushed = sm.flush()
        assertTrue(flushed.isNotEmpty())
        assertEquals(69, flushed.first().midiNote)
    }

    @Test fun `minor pitch variation within tolerance does not close note`() {
        repeat(5) { sm.processFrame(440f, 0.5f) }
        repeat(5) { sm.processFrame(442f, 0.5f) }
        sm.processFrame(0f, 0f)
        assertEquals(1, sm.capturedNotes.size)
    }

    // Scale Snapping and Velocity Hardness

    @Test fun `scale snapping applies when pitch is detected`() {
        val mask = BooleanArray(12) { true }
        mask[1] = false
        val settings = VoiceCaptureSettings(activePitchClasses = mask)
        val testSm = VoiceCaptureStateMachine(settings = settings, stabilityFrames = 1)

        testSm.processFrame(277.18f, 0.5f)
        val notes = testSm.flush()
        assertEquals(1, notes.size)
        assertEquals(60, notes[0].midiNote)
    }

    @Test fun `velocity hardness FIXED mode applies configured velocity`() {
        val settings = VoiceCaptureSettings(
            hardnessMode = VelocityHardnessMode.FIXED,
            fixedVelocity = 110
        )
        val testSm = VoiceCaptureStateMachine(settings = settings, stabilityFrames = 1)
        testSm.processFrame(440f, 0.5f)
        val notes = testSm.flush()
        assertEquals(110, notes[0].velocity)
    }

    // Flush

    @Test fun `flush returns empty list when no notes captured`() {
        repeat(3) { sm.processFrame(440f, 0.5f) }
        val flushed = sm.flush()
        assertTrue(flushed.isEmpty())
    }

    @Test fun `flush closes any open note`() {
        repeat(5) { sm.processFrame(440f, 0.5f) }
        val flushed = sm.flush()
        assertEquals(1, flushed.size)
    }

    // Reset

    @Test fun `reset clears all captured notes and state`() {
        repeat(10) { sm.processFrame(440f, 0.5f) }
        sm.reset()
        assertTrue(sm.capturedNotes.isEmpty())
        repeat(5) { sm.processFrame(440f, 0.5f) }
        sm.processFrame(0f, 0f)
        assertEquals(1, sm.capturedNotes.size)
    }

    // Multiple notes

    @Test fun `multiple distinct pitches produce multiple notes`() {
        repeat(5) { sm.processFrame(440f, 0.5f) }
        sm.processFrame(0f, 0f)
        repeat(5) { sm.processFrame(523f, 0.5f) }
        sm.processFrame(0f, 0f)
        val notes = sm.flush()
        assertEquals(2, notes.size)
        assertEquals(69, notes[0].midiNote)
        assertEquals(72, notes[1].midiNote)
    }

    @Test fun `noise gate filters out low amplitude ambient room noise`() {
        val settings = VoiceCaptureSettings(
            noiseGateEnabled = true,
            noiseGateThresholdDb = -30f
        )
        val testSm = VoiceCaptureStateMachine(settings = settings, stabilityFrames = 1)

        testSm.processFrame(440f, 0.01f)
        val notes = testSm.flush()
        assertTrue(notes.isEmpty())
    }
}
