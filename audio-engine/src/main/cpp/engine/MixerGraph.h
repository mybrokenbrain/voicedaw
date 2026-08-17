#pragma once

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

    void configure(int32_t sampleRate, int32_t channelCount);

    void render(float* outputBuffer, int32_t numFrames, int32_t channelCount);

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

    void setListenToReference(bool listen) { mListenToReference = listen; }
    void loadReferenceTrack(const std::string& path);
    MasteringModule* getMastering() const { return mMastering.get(); }

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

    void setTrackSubmixBus(int32_t trackIndex, int32_t busId) {
        if (trackIndex >= 0 && trackIndex < kMaxTracks && busId >= 0 && busId < kNumBuses) {
            mTracks[trackIndex].setTargetBus(busId);
        }
    }

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
    static constexpr int32_t kMaxTracks = 5;
    static constexpr int32_t kMaxFramesPerCallback = 2048;
    static constexpr int32_t kNumBuses           = 4;

    int32_t mSampleRate{48000};
    int32_t mChannelCount{2};
    bool    mConfigured{false};

    // Voice Pool
    SynthVoice mVoices[kMaxVoices];
    DrumVoice mDrumVoices[kMaxDrumVoices];

    RingBuffer<NoteEvent, 256> mNoteQueue;

    // Scratch Buffer
    float mScratchBuffer[kMaxFramesPerCallback]{};
    float mReverbSendBuffer[kMaxFramesPerCallback * 2]{};
    float mReverbBusBuffer[kMaxFramesPerCallback * 2]{};
    float mDelaySendBuffer[kMaxFramesPerCallback * 2]{};
    float mDelayBusBuffer[kMaxFramesPerCallback * 2]{};

    float mPreCompBuffers[kMaxTracks][kMaxFramesPerCallback * 2]{};

    float mBusBuffers[kNumBuses][kMaxFramesPerCallback * 2]{};

    // M5 Mixer Components
    ChannelStrip mTracks[kMaxTracks];
    Reverb mReverb;
    TransportClock mTransport;
    AudioClipPlayer mClipPlayers[kMaxTracks];

    // Phase 3 Mastering
    std::unique_ptr<MasteringModule> mMastering;
    AudioClipPlayer mReferencePlayer;
    bool mListenToReference{false};

    // Master Bus FX
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
