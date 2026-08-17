package com.voicedaw.audioengine

import com.voicedaw.audioengine.mixer.CompressorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidechainCompressorTest {

    @Test
    fun testSidechainConfiguration() {
        val comp = CompressorState(
            enabled = true,
            sidechainEnabled = true,
            sidechainSourceTrackIndex = 0
        )
        assertTrue(comp.sidechainEnabled)
        assertEquals(0, comp.sidechainSourceTrackIndex)
    }
}
