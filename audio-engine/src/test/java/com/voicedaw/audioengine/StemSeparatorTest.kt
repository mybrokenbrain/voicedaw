package com.voicedaw.audioengine

import com.voicedaw.audioengine.sampling.StemSeparator
import com.voicedaw.audioengine.sampling.StemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StemSeparatorTest {

    @Test
    fun testStemSeparationTargetPaths() {
        val tempInput = File.createTempFile("test_sample", ".wav")
        tempInput.writeText("RIFF_DUMMY_AUDIO_DATA")
        
        val separator = StemSeparator(null)
        val stems = separator.separateStems(tempInput.absolutePath)
        
        assertTrue(stems.vocalsPath.contains("_vocals.wav"))
        assertTrue(stems.drumsPath.contains("_drums.wav"))
        assertTrue(stems.bassPath.contains("_bass.wav"))
        assertTrue(stems.otherPath.contains("_other.wav"))
        assertEquals("Vocals", StemType.VOCALS.displayName)
        
        tempInput.delete()
    }
}
