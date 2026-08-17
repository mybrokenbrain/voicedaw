package com.voicedaw.audioengine

import com.voicedaw.audioengine.sampling.VocalPadManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ResamplingTest {

    @Test
    fun testResampleToPad() {
        val manager = VocalPadManager(null)
        manager.resampleToPad(padIndex = 2, samplePath = "/tmp/resample_pad_2.wav", name = "Master Resample")
        
        val pad = manager.pads.value[2]
        assertEquals("/tmp/resample_pad_2.wav", pad.samplePath)
        assertEquals("Master Resample", pad.name)
    }
}
