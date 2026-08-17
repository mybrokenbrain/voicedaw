package com.voicedaw.audioengine

object AudioEngineJni {
    enum class AudioTier(val ordinal_: Int) {
        PRO(0),
        LOW(1),
        UNSUPPORTED(2),
        UNKNOWN(3);

        companion object {
            fun fromInt(v: Int): AudioTier {
                return entries.firstOrNull { it.ordinal_ == v } ?: UNKNOWN
            }
        }
    }

    init {
        System.loadLibrary("voicedaw-engine")
    }

    @JvmStatic external fun nativeBounceToWav(handle: Long, path: String): Boolean
    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeGetCompGainReduction(handle: Long, trackIndex: Int): Float
    @JvmStatic external fun nativeGetEstimatedBpm(handle: Long): Float
    @JvmStatic external fun nativeGetEstimatedKeyIsMajor(handle: Long): Boolean
    @JvmStatic external fun nativeGetEstimatedKeyRoot(handle: Long): Int
    @JvmStatic external fun nativeGetIntegratedLUFS(handle: Long): Float
    @JvmStatic external fun nativeGetLastDetectedPad(handle: Long): Int
    @JvmStatic external fun nativeGetLatencyMs(handle: Long): Float
    @JvmStatic external fun nativeGetMasterCompGainReduction(handle: Long): Float
    @JvmStatic external fun nativeGetPitchAmp(handle: Long): Float
    @JvmStatic external fun nativeGetPitchHz(handle: Long): Float
    @JvmStatic external fun nativeGetSampleRate(handle: Long): Int
    @JvmStatic external fun nativeGetTier(handle: Long): Int
    @JvmStatic external fun nativeGetTruePeak(handle: Long): Float
    @JvmStatic external fun nativeGetXrunCount(handle: Long): Long
    @JvmStatic external fun nativeGetPlaybackPosition(handle: Long): Float
    @JvmStatic external fun nativeLoadReferenceTrack(handle: Long, path: String)
    @JvmStatic external fun nativeLoadTrackClip(handle: Long, trackIndex: Int, path: String)
    @JvmStatic external fun nativeNoteOff(handle: Long, midiNote: Int)
    @JvmStatic external fun nativeNoteOn(handle: Long, midiNote: Int, velocity: Int)
    @JvmStatic external fun nativeAllNotesOff(handle: Long)
    @JvmStatic external fun nativeSaveBeatboxModel(handle: Long, path: String)
    @JvmStatic external fun nativeLoadBeatboxModel(handle: Long, path: String)
    @JvmStatic external fun nativeSetAnalysisMode(handle: Long, mode: Int)
    @JvmStatic external fun nativeSetBeatboxTrainingPad(handle: Long, padIndex: Int)
    @JvmStatic external fun nativeSetCompEnabled(handle: Long, trackIndex: Int, enabled: Boolean)
    @JvmStatic external fun nativeSetCompParams(handle: Long, trackIndex: Int, thresh: Float, ratio: Float, attack: Float, release: Float)
    @JvmStatic external fun nativeSetEqEnabled(handle: Long, trackIndex: Int, enabled: Boolean)
    @JvmStatic external fun nativeSetEqHighShelf(handle: Long, trackIndex: Int, freq: Float, gain: Float)
    @JvmStatic external fun nativeSetEqLowShelf(handle: Long, trackIndex: Int, freq: Float, gain: Float)
    @JvmStatic external fun nativeSetEqMidBand(handle: Long, trackIndex: Int, freq: Float, gain: Float, q: Float)
    @JvmStatic external fun nativeSetHarmonyAssistEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetListenToReference(handle: Long, listen: Boolean)
    @JvmStatic external fun nativeSetMasterCompEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetMasterCompParams(handle: Long, thresh: Float, ratio: Float, attack: Float, release: Float)
    @JvmStatic external fun nativeSetMasterEqEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetMasterEqHighShelf(handle: Long, freq: Float, gain: Float)
    @JvmStatic external fun nativeSetMasterEqLowShelf(handle: Long, freq: Float, gain: Float)
    @JvmStatic external fun nativeSetMasterEqMidBand(handle: Long, freq: Float, gain: Float, q: Float)
    @JvmStatic external fun nativeSetMasterSaturationEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetMasterSaturationDrive(handle: Long, drive: Float)
    @JvmStatic external fun nativeSetMasterDelayEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetMasterDelayParams(handle: Long, timeMs: Float, feedback: Float, mix: Float)
    @JvmStatic external fun nativeSetMasterGateEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetMasterGateParams(handle: Long, thresholdDb: Float, attackMs: Float, releaseMs: Float)
    @JvmStatic external fun nativeSetOutputDeviceId(handle: Long, deviceId: Int)
    @JvmStatic external fun nativeSetPerformanceTier(handle: Long, tier: Int)
    @JvmStatic external fun nativeSetReverbEnabled(handle: Long, enabled: Boolean)
    @JvmStatic external fun nativeSetReverbParams(handle: Long, room: Float, damp: Float, wetDry: Float, width: Float)
    @JvmStatic external fun nativeSetTrackGain(handle: Long, trackIndex: Int, gain: Float)
    @JvmStatic external fun nativeSetTrackPan(handle: Long, trackIndex: Int, pan: Float)
    @JvmStatic external fun nativeSetTrackMute(handle: Long, trackIndex: Int, muted: Boolean)
    @JvmStatic external fun nativeSetTrackSolo(handle: Long, trackIndex: Int, solo: Boolean)
    @JvmStatic external fun nativeSetTrackReverbSend(handle: Long, trackIndex: Int, send: Float)
    @JvmStatic external fun nativeSetTrackDelaySend(handle: Long, trackIndex: Int, send: Float)
    @JvmStatic external fun nativeSetTrackSubmixBus(handle: Long, trackIndex: Int, busId: Int)
    @JvmStatic external fun nativeSetSidechainEnabled(handle: Long, trackIndex: Int, enabled: Boolean, sourceTrackIndex: Int)
    @JvmStatic external fun nativeSetPerformanceFx(handle: Long, fxType: Int, hold: Boolean)

    @JvmStatic external fun nativeSetTransportState(handle: Long, state: Int)
    @JvmStatic external fun nativeStart(handle: Long): Boolean
    @JvmStatic external fun nativeStop(handle: Long)

    // ── Vocal Pad Grid ───────────────────────────────────────────────────────
    @JvmStatic external fun nativeLoadPadSample(handle: Long, padIndex: Int, path: String): Boolean
    @JvmStatic external fun nativeConfigurePad(handle: Long, padIndex: Int, startMs: Float, endMs: Float, pitchSemitones: Int, fineTuneCents: Int, gainDb: Float, fadeInMs: Float, fadeOutMs: Float, reverse: Boolean, playbackMode: Int, chokeGroup: Int)
    @JvmStatic external fun nativeTriggerPad(handle: Long, padIndex: Int, velocity: Float, extraSemitones: Int)
    @JvmStatic external fun nativeReleasePad(handle: Long, padIndex: Int)
}
