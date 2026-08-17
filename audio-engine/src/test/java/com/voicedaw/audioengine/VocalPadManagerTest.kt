package com.voicedaw.audioengine

import android.content.Context
import com.voicedaw.audioengine.sampling.VocalPadManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VocalPadManagerTest {

    private lateinit var manager: VocalPadManager

    @Before
    fun setup() {
        manager = VocalPadManager(null)
    }

    @Test
    fun testHandleMidiNote() {
        assertTrue(manager.handleMidiNote(36, 1.0f))
        
        assertTrue(manager.handleMidiNote(51, 1.0f))
        
        assertFalse(manager.handleMidiNote(60, 1.0f))
    }

    @Test
    fun testChokeGroupMuting() {
        val pad0 = manager.pads.value[0].copy(chokeGroup = 1)
        val pad1 = manager.pads.value[1].copy(chokeGroup = 1)
        val pad2 = manager.pads.value[2].copy(chokeGroup = 2)

        manager.updatePad(pad0)
        manager.updatePad(pad1)
        manager.updatePad(pad2)

        manager.triggerPad(0, 1.0f)
        assertTrue(manager.isPadPlaying(0))

        manager.triggerPad(1, 1.0f)
        assertFalse(manager.isPadPlaying(0))
        assertTrue(manager.isPadPlaying(1))

        manager.triggerPad(2, 1.0f)
        assertTrue(manager.isPadPlaying(1))
        assertTrue(manager.isPadPlaying(2))
    }

    @Test
    fun testPlaybackModes() {
        val pad0 = manager.pads.value[0].copy(playbackMode = com.voicedaw.audioengine.sampling.SamplePlaybackMode.HOLD_TO_PLAY)
        manager.updatePad(pad0)
        assertEquals(com.voicedaw.audioengine.sampling.SamplePlaybackMode.HOLD_TO_PLAY, manager.pads.value[0].playbackMode)

        manager.triggerPad(0, 1.0f)
        assertTrue(manager.isPadPlaying(0))

        manager.triggerPad(0, 0.0f)
        assertFalse(manager.isPadPlaying(0))
    }

    @Test
    fun testSampleProcessing() {
        val pad0 = manager.pads.value[0].copy(
            gainDb = 3.0f,
            fadeInMs = 50f,
            fadeOutMs = 100f
        )
        manager.updatePad(pad0)
        assertEquals(3.0f, manager.pads.value[0].gainDb, 0.01f)
        assertEquals(50f, manager.pads.value[0].fadeInMs, 0.01f)
        assertEquals(100f, manager.pads.value[0].fadeOutMs, 0.01f)
    }

    @Test
    fun testNormalizePad() {
        val pad0 = manager.pads.value[0].copy(gainDb = -6.0f)
        manager.updatePad(pad0)
        assertEquals(-6.0f, manager.pads.value[0].gainDb, 0.01f)

        manager.normalizePad(0)
        assertEquals(0.0f, manager.pads.value[0].gainDb, 0.01f)
    }
}
