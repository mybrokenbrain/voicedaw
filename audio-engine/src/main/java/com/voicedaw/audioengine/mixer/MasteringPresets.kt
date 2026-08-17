package com.voicedaw.audioengine.mixer

object MasteringPresets {
    val presets: List<MasteringPreset> = listOf(
        MasteringPreset(
            name = "Warm Acoustic",
            targetLufs = -14.0f,
            masterEq = EqState(enabled = true, lowShelfFreq = 100.0f, lowShelfGain = 2.0f, midFreq = 2500.0f, midGain = 1.5f, midQ = 1.0f, highShelfFreq = 10000.0f, highShelfGain = 1.0f),
            masterComp = CompressorState(enabled = true, thresholdDb = -15.0f, ratio = 2.0f, attackMs = 30.0f, releaseMs = 250.0f)
        ),
        MasteringPreset(
            name = "Modern Pop",
            targetLufs = -11.0f,
            masterEq = EqState(enabled = true, lowShelfFreq = 60.0f, lowShelfGain = 3.0f, midFreq = 3000.0f, midGain = 2.0f, midQ = 1.5f, highShelfFreq = 12000.0f, highShelfGain = 3.5f),
            masterComp = CompressorState(enabled = true, thresholdDb = -18.0f, ratio = 4.0f, attackMs = 10.0f, releaseMs = 100.0f)
        ),
        MasteringPreset(
            name = "Podcast / Dialogue",
            targetLufs = -16.0f,
            masterEq = EqState(enabled = true, lowShelfFreq = 80.0f, lowShelfGain = -2.0f, midFreq = 3000.0f, midGain = 4.0f, midQ = 0.8f, highShelfFreq = 10000.0f, highShelfGain = 0.0f),
            masterComp = CompressorState(enabled = true, thresholdDb = -20.0f, ratio = 3.0f, attackMs = 5.0f, releaseMs = 150.0f)
        ),
        MasteringPreset(
            name = "Lofi Hip-Hop",
            targetLufs = -14.0f,
            masterEq = EqState(enabled = true, lowShelfFreq = 120.0f, lowShelfGain = 4.0f, midFreq = 1000.0f, midGain = -1.0f, midQ = 1.0f, highShelfFreq = 8000.0f, highShelfGain = -4.0f),
            masterComp = CompressorState(enabled = true, thresholdDb = -12.0f, ratio = 4.0f, attackMs = 50.0f, releaseMs = 300.0f)
        )
    )
}
