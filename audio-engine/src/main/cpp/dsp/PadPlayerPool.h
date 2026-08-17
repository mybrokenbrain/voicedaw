#pragma once

#include <cstdint>
#include <string>
#include "PadVoice.h"
#include "../dsp/RingBuffer.h"

namespace voicedaw {

struct PadEvent {
    int32_t padIndex;
    int32_t type; 
    float velocity;
    int32_t extraSemitones;
};

class PadPlayerPool {
public:
    static constexpr int32_t kMaxPads = 16;

    void configure(int32_t engineSampleRate) {
        for (auto& v : mVoices) v.configureEngine(engineSampleRate);
    }

    bool loadSample(int32_t padIndex, const std::string& path) {
        if (padIndex < 0 || padIndex >= kMaxPads) return false;
        return mVoices[padIndex].loadWav(path);
    }

    void configurePad(int32_t padIndex, float startMs, float endMs, int32_t pitchSemitones,
                       int32_t fineTuneCents, float gainDb, float fadeInMs, float fadeOutMs,
                       bool reverse, int32_t playbackMode, int32_t chokeGroup) {
        if (padIndex < 0 || padIndex >= kMaxPads) return;
        mVoices[padIndex].setParams(startMs, endMs, pitchSemitones, fineTuneCents, gainDb,
                                     fadeInMs, fadeOutMs, reverse, playbackMode, chokeGroup);
    }

    void postTrigger(int32_t padIndex, float velocity, int32_t extraSemitones) {
        PadEvent ev{padIndex, 0, velocity, extraSemitones};
        mEventQueue.push(ev);
    }

    void postRelease(int32_t padIndex) {
        PadEvent ev{padIndex, 1, 0.0f, 0};
        mEventQueue.push(ev);
    }

    void drainEvents() {
        PadEvent ev;
        while (mEventQueue.pop(ev)) {
            if (ev.padIndex < 0 || ev.padIndex >= kMaxPads) continue;
            if (ev.type == 0) {
                int32_t group = mVoices[ev.padIndex].getChokeGroup();
                if (group > 0) {
                    for (int32_t i = 0; i < kMaxPads; ++i) {
                        if (i != ev.padIndex && mVoices[i].isActive() &&
                            mVoices[i].getChokeGroup() == group) {
                            mVoices[i].choke();
                        }
                    }
                }
                mVoices[ev.padIndex].trigger(ev.velocity, ev.extraSemitones);
            } else {
                mVoices[ev.padIndex].release();
            }
        }
    }

    void render(float* outBuffer, int32_t numFrames, int32_t channelCount) {
        for (auto& v : mVoices) {
            v.render(outBuffer, numFrames, channelCount);
        }
    }

private:
    PadVoice mVoices[kMaxPads];
    RingBuffer<PadEvent, 128> mEventQueue;
};

} // namespace voicedaw
