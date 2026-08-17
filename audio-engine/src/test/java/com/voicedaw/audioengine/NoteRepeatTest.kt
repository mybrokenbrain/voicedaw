package com.voicedaw.audioengine

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRepeatTest {

    @Test
    fun testNoteRepeatRateSubdivisions() {
        assertEquals(1, NoteRepeatRate.OFF.getSubdivisions(1.0f))
        assertEquals(2, NoteRepeatRate.RATE_1_8.getSubdivisions(1.0f))
        assertEquals(4, NoteRepeatRate.RATE_1_16.getSubdivisions(1.0f))
        assertEquals(8, NoteRepeatRate.RATE_1_32.getSubdivisions(1.0f))
        assertEquals(16, NoteRepeatRate.RATE_1_64.getSubdivisions(1.0f))
    }
}
