package com.voicedaw.audioengine

import com.voicedaw.audioengine.performance.PerformanceFxState
import com.voicedaw.audioengine.performance.PerformanceFxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceFxTest {

    @Test
    fun testPerformanceFxState() {
        val state = PerformanceFxState(
            activeFxType = PerformanceFxType.TAPE_STOP,
            isHoldEnabled = true
        )
        assertEquals(PerformanceFxType.TAPE_STOP, state.activeFxType)
        assertTrue(state.isHoldEnabled)
    }
}
