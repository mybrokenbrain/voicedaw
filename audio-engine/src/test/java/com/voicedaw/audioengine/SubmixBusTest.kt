package com.voicedaw.audioengine

import com.voicedaw.audioengine.mixer.SubmixBus
import com.voicedaw.audioengine.mixer.TrackMixerState
import org.junit.Assert.assertEquals
import org.junit.Test

class SubmixBusTest {

    @Test
    fun testTrackSubmixBusRouting() {
        val trackState = TrackMixerState(trackIndex = 1, targetBus = SubmixBus.DRUM_BUS)
        assertEquals(SubmixBus.DRUM_BUS, trackState.targetBus)
        assertEquals("Drum Bus", trackState.targetBus.displayName)
    }
}
