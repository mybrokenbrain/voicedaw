package com.voicedaw.audioengine.sampling

import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

class StemSeparatorTest {

    /**
     * Writes a minimal valid 16-bit PCM stereo WAV file so separateStems()
     * has real audio to process. A few hundred samples of a simple sine-ish
     * pattern is enough to exercise the mid/side + filter logic without
     * needing a real recording.
     */
    private fun writeTestWav(file: File, frameCount: Int = 4800, sampleRate: Int = 48000) {
        val numChannels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataBytes = frameCount.toLong() * blockAlign

        DataOutputStream(FileOutputStream(file)).use { dos ->
            dos.writeBytes("RIFF")
            writeLe32(dos, (36 + dataBytes).toInt())
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            writeLe32(dos, 16)
            writeLe16(dos, 1) // PCM
            writeLe16(dos, numChannels)
            writeLe32(dos, sampleRate)
            writeLe32(dos, byteRate)
            writeLe16(dos, blockAlign)
            writeLe16(dos, bitsPerSample)
            dos.writeBytes("data")
            writeLe32(dos, dataBytes.toInt())

            for (i in 0 until frameCount) {
                // Simple tone-ish waveform, different phase per channel so
                // mid/side actually differ (otherwise side would be all zero).
                val l = (Math.sin(i * 0.05) * 8000).toInt()
                val r = (Math.sin(i * 0.05 + 0.3) * 8000).toInt()
                writeLe16(dos, l)
                writeLe16(dos, r)
            }
        }
    }

    private fun writeLe32(dos: DataOutputStream, v: Int) {
        dos.write(v and 0xFF)
        dos.write((v shr 8) and 0xFF)
        dos.write((v shr 16) and 0xFF)
        dos.write((v shr 24) and 0xFF)
    }

    private fun writeLe16(dos: DataOutputStream, v: Int) {
        dos.write(v and 0xFF)
        dos.write((v shr 8) and 0xFF)
    }

    @Test
    fun `separateStems produces four distinct output files for a valid WAV`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "stemtest_${System.nanoTime()}")
        tmpDir.mkdirs()
        val inputFile = File(tmpDir, "input.wav")
        writeTestWav(inputFile)

        val separator = StemSeparator(context = null)
        val stems = separator.separateStems(inputFile.absolutePath)

        assertNotNull("separateStems should succeed on a valid WAV", stems)
        stems!!

        assertTrue("vocals file should exist", File(stems.vocalsPath).exists())
        assertTrue("drums file should exist", File(stems.drumsPath).exists())
        assertTrue("bass file should exist", File(stems.bassPath).exists())
        assertTrue("other file should exist", File(stems.otherPath).exists())

        // Ensure distinct stem audio channels were produced
        val vocalsBytes = File(stems.vocalsPath).readBytes()
        val drumsBytes = File(stems.drumsPath).readBytes()
        val bassBytes = File(stems.bassPath).readBytes()
        val otherBytes = File(stems.otherPath).readBytes()

        assertFalse("vocals and drums must not be identical", vocalsBytes.contentEquals(drumsBytes))
        assertFalse("vocals and bass must not be identical", vocalsBytes.contentEquals(bassBytes))
        assertFalse("vocals and other must not be identical", vocalsBytes.contentEquals(otherBytes))
        assertFalse("bass and other must not be identical", bassBytes.contentEquals(otherBytes))

        tmpDir.deleteRecursively()
    }

    @Test
    fun `separateStems returns null for a nonexistent input file`() {
        val separator = StemSeparator(context = null)
        val result = separator.separateStems("/nonexistent/path/does_not_exist.wav")
        assertNull("separateStems should fail cleanly on missing input, not throw", result)
    }

    @Test
    fun `separateStems returns null for an invalid non-WAV file`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"), "stemtest_invalid_${System.nanoTime()}")
        tmpDir.mkdirs()
        val badFile = File(tmpDir, "not_a_wav.txt")
        badFile.writeText("this is not a WAV file")

        val separator = StemSeparator(context = null)
        val result = separator.separateStems(badFile.absolutePath)
        assertNull("separateStems should fail cleanly on unsupported format", result)

        tmpDir.deleteRecursively()
    }
}
