#pragma once
/**
 * ChannelStrip.h — Per-track DSP chain: gain → EQ → compressor → send level.
 *
 * Signal path:
 *   Input -> Gain/Pan -> ParametricEQ -> Compressor -> MainOut
 *                                                 \-> ReverbSend -> shared Reverb bus
 *                                                 \-> DelaySend  -> shared Delay bus
 *
 * Processing occurs in two phases:
 *   1. renderPreComp(): applies mute/solo, gain/pan, and EQ.
 *   2. applyPostComp(): evaluates compression (supporting sidechain from any
 *      track buffer) and routes to sends.
 *
 * Automation: volume and EQ mid-gain can be automated via setVolumeDb()
 * and setEqMidGain() on the audio thread.
 *
 * RULES (A-09): No heap allocation after configure(). Lock-free.
 */

#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include "ParametricEq.h"
#include "Compressor.h"

namespace voicedaw {

class ChannelStrip {
public:
    ChannelStrip() = default;

    void configure(int32_t sampleRate, int32_t channelCount) {
        mSampleRate   = sampleRate;
        mChannelCount = channelCount;
        mEq.configure(sampleRate);
        mComp.configure(sampleRate);
    }

    // ── Main-thread controls ──────────────────────────────────────────────────
    void setVolumeDb(float db)  { mVolumeDb.store(db,  std::memory_order_relaxed); }
    void setPanNorm(float pan)  { mPan.store(pan,      std::memory_order_relaxed); }  // -1..+1
    void setMuted(bool m)       { mMuted.store(m,      std::memory_order_relaxed); }
    void setSoloed(bool s)      { mSoloed.store(s,     std::memory_order_relaxed); }
    void setReverbSend(float v) { mReverbSend.store(v, std::memory_order_relaxed); }  // 0..1
    void setDelaySend(float v)  { mDelaySend.store(v, std::memory_order_relaxed); }   // 0..1

    // Sidechain state
    void setSidechainEnabled(bool e) { mSidechainEnabled.store(e, std::memory_order_relaxed); }
    void setSidechainSource(int32_t s) { mSidechainSource.store(s, std::memory_order_relaxed); }
    bool isSidechainEnabled() const { return mSidechainEnabled.load(std::memory_order_relaxed); }
    int32_t getSidechainSource() const { return mSidechainSource.load(std::memory_order_relaxed); }

    // Submix bus routing — busId matches SubmixBus.kt: 0=Master, 1=Drum, 2=Vocal, 3=Instrument.
    void setTargetBus(int32_t busId) { mTargetBus.store(busId, std::memory_order_relaxed); }
    int32_t getTargetBus() const { return mTargetBus.load(std::memory_order_relaxed); }

    // EQ delegates
    ParametricEq& eq() { return mEq; }
    // Compressor delegates
    Compressor& comp() { return mComp; }

    float getVolumeDb()    const { return mVolumeDb.load(std::memory_order_relaxed); }
    float getPan()         const { return mPan.load(std::memory_order_relaxed); }
    bool  isMuted()        const { return mMuted.load(std::memory_order_relaxed); }
    bool  isSoloed()       const { return mSoloed.load(std::memory_order_relaxed); }
    float getReverbSend()  const { return mReverbSend.load(std::memory_order_relaxed); }
    float getGainReductionDb() const { return mComp.getGainReductionDb(); }

    // ── Audio-thread render, phase 1 ──────────────────────────────────────────
    /**
     * Applies mute/solo, gain/pan, EQ. Leaves the pre-compressor signal in
     * buffer[]. Call this for every track BEFORE calling applyPostComp() on
     * ANY track, so that any track's buffer is available as a sidechain
     * source regardless of processing order.
     */
    void renderPreComp(float* buffer, int32_t numFrames, bool anySoloed) {
        bool muted  = mMuted.load(std::memory_order_relaxed);
        bool soloed = mSoloed.load(std::memory_order_relaxed);
        if (anySoloed && !soloed) muted = true;

        if (muted) {
            std::memset(buffer, 0, sizeof(float) * numFrames * mChannelCount);
            return;
        }

        float vol  = std::pow(10.0f, mVolumeDb.load(std::memory_order_relaxed) / 20.0f);
        float pan  = mPan.load(std::memory_order_relaxed);
        float gainL = vol * std::sqrt(0.5f * (1.0f - pan));
        float gainR = vol * std::sqrt(0.5f * (1.0f + pan));

        for (int32_t f = 0; f < numFrames; ++f) {
            buffer[f * 2 + 0] *= gainL;
            buffer[f * 2 + 1] *= gainR;
        }

        mEq.process(buffer, numFrames, mChannelCount);
    }

    // ── Audio-thread render, phase 2 ──────────────────────────────────────────
    /**
     * Fills reverbSendOut[]/delaySendOut[] from the phase-1 buffer, then
     * compresses buffer[] in place. sidechainBuffer, if provided, must be
     * another track's phase-1 (pre-comp) buffer — never the partially-summed
     * master bus.
     */
    void applyPostComp(float* buffer, float* reverbSendOut, float* delaySendOut,
                        int32_t numFrames, bool anySoloed, const float* sidechainBuffer = nullptr) {
        bool muted  = mMuted.load(std::memory_order_relaxed);
        bool soloed = mSoloed.load(std::memory_order_relaxed);
        if (anySoloed && !soloed) muted = true;

        if (muted) {
            if (reverbSendOut) std::memset(reverbSendOut, 0, sizeof(float) * numFrames * mChannelCount);
            if (delaySendOut) std::memset(delaySendOut, 0, sizeof(float) * numFrames * mChannelCount);
            return; // buffer already zeroed by renderPreComp()
        }

        float rSend = mReverbSend.load(std::memory_order_relaxed);
        float dSend = mDelaySend.load(std::memory_order_relaxed);
        for (int32_t i = 0; i < numFrames * mChannelCount; ++i) {
            if (reverbSendOut) reverbSendOut[i] = buffer[i] * rSend;
            if (delaySendOut) delaySendOut[i] = buffer[i] * dSend;
        }

        mComp.process(buffer, numFrames, mChannelCount, sidechainBuffer);
    }

private:
    int32_t mSampleRate{48000};
    int32_t mChannelCount{2};

    ParametricEq mEq;
    Compressor   mComp;

    std::atomic<float> mVolumeDb{0.0f};
    std::atomic<float> mPan{0.0f};
    std::atomic<bool>  mMuted{false};
    std::atomic<bool>  mSoloed{false};
    std::atomic<float> mReverbSend{0.0f};
    std::atomic<float> mDelaySend{0.0f};

    std::atomic<bool> mSidechainEnabled{false};
    std::atomic<int32_t> mSidechainSource{-1};
    std::atomic<int32_t> mTargetBus{0}; // 0 = Master
};

} // namespace voicedaw
