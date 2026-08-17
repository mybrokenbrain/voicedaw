#pragma once
/**
 * MixerGraph.h — N-track to stereo summing bus.
 *
 * Topology:
 *  - Polyphonic SynthVoices and DrumVoices
 *  - PadPlayerPool for 16-pad vocal grid / sampler
 *  - Per-track ChannelStrip (gain/pan, EQ, compressor, sidechain routing, sends)
 *  - Submix buses (Master, Drum, Vocal, Instrument)
 *  - Master FX insert chain (Saturation -> Master Delay -> NoiseGate) -> Reverb -> Mastering
 *  - Performance FX rack (touch-hold stutters, tape stop, filters, distortion, bitcrush)
 */

#include <cstdint>
#include <vector>
#include <memory>
#include "dsp/SynthVoice.h"
#include "dsp/DrumVoice.h"
#include "dsp/RingBuffer.h"
#include "dsp/ChannelStrip.h"
#include "dsp/Reverb.h"
#include "dsp/AudioClipPlayer.h"
#include "TransportClock.h"
#include "dsp/MasteringModule.h"
#include "fx/Saturation.h"
#include "fx/Delay.h"
#include "fx/NoiseGate.h"
#include "fx/PerformanceFxRack.h"
#include "dsp/PadPlayerPool.h"

namespace voicedaw {

class MixerGraph {
public:
    MixerGraph();
    ~MixerGraph() = default;

    // Called from main thread before stream starts — safe to allocate here
    void configure(int32_t sampleRate, int32_t channelCount);

    // Called from audio thread — MUST be allocation-free
    void render(float* outputBuffer, int32_t numFrames, int32_t channelCount);

    // ── MIDI instrument (M4) ─────────────────────────────────────────────────
    void postNoteEvent(int32_t midiNote, int32_t velocity);
    void postPitchBend(int32_t midiNote, float bendSemis);
    void allNotesOff();

    void setTrackGain(int32_t trackIndex, float gain);
    void muteTrack(int32_t trackIndex, bool mute);
    void setPan(int32_t trackIndex, float pan);
    void setMute(int32_t trackIndex, bool mute);
    void setSolo(int32_t trackIndex, bool solo);

    int64_t getTransportPosition() const { return mTransport.getPositionFrames(); }
    int64_t getLongestClipFrames() const;
    void resetForOfflineRender();

    ChannelStrip& getTrack(int32_t trackIndex) { return mTracks[trackIndex]; }
    Reverb& getReverb() { return mReverb; }

    void setTransportState(TransportClock::State state);
    void loadTrackClip(int32_t trackIndex, const std::string& path);

    // M5/Phase 3 Mastering
    void setListenToReference(bool listen) { mListenToReference = listen; }
    void loadReferenceTrack(const std::string& path);
    MasteringModule* getMastering() const { return mMastering.get(); }

    // ── M7: Master bus FX chain ──────────────────────────────────────────────
    void setMasterSaturationEnabled(bool enabled) { mMasterSaturationEnabled = enabled; }
    void setMasterSaturationDrive(float drive) { mMasterSaturation.setDrive(drive); }

    void setMasterDelayEnabled(bool enabled) { mMasterDelayEnabled = enabled; }
    void setMasterDelayParams(float timeMs, float feedback, float mix) {
        if (mMasterDelay) mMasterDelay->setParams(timeMs, feedback, mix);
    }

    void setMasterGateEnabled(bool enabled) { mMasterGateEnabled = enabled; }
    void setMasterGateParams(float thresholdDb, float attackMs, float releaseMs) {
        if (mMasterGate) mMasterGate->setParams(thresholdDb, attackMs, releaseMs);
    }

    void setPerformanceFx(int32_t fxType, bool hold) {
        mPerformanceFx.setFx(fxType, hold);
    }

    // busId: 0=Master, 1=Drum, 2=Vocal, 3=Instrument (see SubmixBus.kt)
    void setTrackSubmixBus(int32_t trackIndex, int32_t busId) {
        if (trackIndex >= 0 && trackIndex < kMaxTracks && busId >= 0 && busId < kNumBuses) {
            mTracks[trackIndex].setTargetBus(busId);
        }
    }

    // ── Vocal Pad Grid / Sampler ───────────────────────────────────────────
    bool loadPadSample(int32_t padIndex, const std::string& path) {
        return mPadPlayers.loadSample(padIndex, path);
    }
    void configurePad(int32_t padIndex, float startMs, float endMs, int32_t pitchSemitones,
                       int32_t fineTuneCents, float gainDb, float fadeInMs, float fadeOutMs,
                       bool reverse, int32_t playbackMode, int32_t chokeGroup) {
        mPadPlayers.configurePad(padIndex, startMs, endMs, pitchSemitones, fineTuneCents,
                                  gainDb, fadeInMs, fadeOutMs, reverse, playbackMode, chokeGroup);
    }
    void triggerPad(int32_t padIndex, float velocity, int32_t extraSemitones = 0) {
        mPadPlayers.postTrigger(padIndex, velocity, extraSemitones);
    }
    void releasePad(int32_t padIndex) {
        mPadPlayers.postRelease(padIndex);
    }

private:
    static constexpr int32_t kMaxVoices          = 16;
    static constexpr int32_t kMaxDrumVoices      = 4;
    static constexpr int32_t kMaxTracks          = 5; // 4 audio tracks + 1 synth track (M5)
    static constexpr int32_t kMaxFramesPerCallback = 2048;
    static constexpr int32_t kNumBuses           = 4; // Master, Drum, Vocal, Instrument

    int32_t mSampleRate{48000};
    int32_t mChannelCount{2};
    bool    mConfigured{false};

    // ── Polyphonic voice pool (M4 built-in instrument) ───────────────────────
    SynthVoice mVoices[kMaxVoices];
    DrumVoice mDrumVoices[kMaxDrumVoices];

    RingBuffer<NoteEvent, 256> mNoteQueue;

    // ── Pre-allocated scratch buffer (zero alloc in render()) ────────────────
    float mScratchBuffer[kMaxFramesPerCallback]{};
    float mReverbSendBuffer[kMaxFramesPerCallback * 2]{};
    float mReverbBusBuffer[kMaxFramesPerCallback * 2]{};
    float mDelaySendBuffer[kMaxFramesPerCallback * 2]{};
    float mDelayBusBuffer[kMaxFramesPerCallback * 2]{};

    // Per-track pre-compressor buffers — rendered in render()'s phase 1 so
    // that phase 2 (compression, incl. sidechain) can read any track's
    // isolated signal regardless of processing order. Also holds each
    // track's final post-comp output, since applyPostComp() writes in place.
    float mPreCompBuffers[kMaxTracks][kMaxFramesPerCallback * 2]{};

    // Submix bus accumulation. Index 0 (Master) is unused — Master-bus
    // tracks sum straight into outputBuffer. Indices 1..3 correspond to
    // busId 1..3 (Drum/Vocal/Instrument).
    float mBusBuffers[kNumBuses][kMaxFramesPerCallback * 2]{};

    // ── M5 Mixer Components ──────────────────────────────────────────────────
    ChannelStrip mTracks[kMaxTracks];
    Reverb mReverb;
    TransportClock mTransport;
    AudioClipPlayer mClipPlayers[kMaxTracks];

    // ── Phase 3 Mastering ────────────────────────────────────────────────────
    std::unique_ptr<MasteringModule> mMastering;
    AudioClipPlayer mReferencePlayer;
    bool mListenToReference{false};

    // ── M7 Master bus FX chain (post-reverb, pre-mastering) ──────────────────
    // Saturation has no sample-rate dependency, so it's a direct member.
    // Delay/NoiseGate size internal buffers/coefficients from sampleRate at
    // construction time, so they must be (re)constructed in configure() once
    // the real device sample rate is known — NOT default-constructed here.
    fx::Saturation mMasterSaturation;
    std::unique_ptr<fx::Delay> mMasterDelay;
    std::unique_ptr<fx::NoiseGate> mMasterGate;
    fx::PerformanceFxRack mPerformanceFx;
    PadPlayerPool mPadPlayers;
    bool mMasterSaturationEnabled{false};
    bool mMasterDelayEnabled{false};
    bool mMasterGateEnabled{false};

    void drainNoteQueue();
    SynthVoice* allocVoice(int32_t midiNote);
    DrumVoice* allocDrumVoice(int32_t midiNote);
};

} // namespace voicedaw
