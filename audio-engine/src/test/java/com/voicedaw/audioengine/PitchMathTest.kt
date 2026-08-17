package com.voicedaw.audioengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchMathTest {

    @Test fun `frequencyToMidiNote maps 440Hz to 69`() {
        assertEquals(69, PitchMath.frequencyToMidiNote(440f))
    }

    @Test fun `middle C 261_63 Hz maps to MIDI 60`() {
        assertEquals(60, PitchMath.frequencyToMidiNote(261.63f))
    }

    @Test fun `B4 493_88 Hz maps to MIDI 71`() {
        assertEquals(71, PitchMath.frequencyToMidiNote(493.88f))
    }

    @Test fun `20 Hz maps to MIDI 15 (within valid range)`() {
        assertEquals(15, PitchMath.frequencyToMidiNote(20f))
    }

    @Test fun `sub 8 Hz clamps to MIDI 0`() {
        assertEquals(0, PitchMath.frequencyToMidiNote(1f))
    }

    @Test fun `very high freq clamps to MIDI 127`() {
        assertEquals(127, PitchMath.frequencyToMidiNote(15000f))
    }

    @Test fun `snapToNearestActiveNote snaps C# 61 to C 60 when C# disabled`() {
        val mask = BooleanArray(12) { true }
        mask[1] = false
        val snapped = PitchMath.snapToNearestActiveNote(61, mask)
        assertTrue(snapped == 60 || snapped == 62)
    }

    @Test fun `calculatePitchBendSemitones respects deadband`() {
        assertEquals(0f, PitchMath.calculatePitchBendSemitones(440f, 69, 20f), 0.01f)
    }

    @Test fun `applyScalePreset MAJOR activates major scale notes`() {
        val mask = PitchMath.getScalePresetMask(0, ScalePreset.MAJOR)
        assertTrue(mask[0])
        assertTrue(!mask[1])
        assertTrue(mask[2])
    }

    @Test fun `isNoteInActiveScale checks if note is in scale`() {
        val mask = BooleanArray(12) { false }
        mask[0] = true
        mask[2] = true
        
        assertTrue(PitchMath.isNoteInActiveScale(60, mask))
        assertTrue(PitchMath.isNoteInActiveScale(72, mask))
        assertTrue(!PitchMath.isNoteInActiveScale(61, mask))
    }
}
