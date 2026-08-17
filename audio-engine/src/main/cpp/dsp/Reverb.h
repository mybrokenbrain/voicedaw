#pragma once

#include <atomic>
#include <cstring>
#include <cstdint>
#include <cmath>
#include <vector>

namespace voicedaw {

class CombFilter {
public:
    void resize(int size) {
        mSize = size;
        mBuf  = new float[size]();
        mPos  = 0;
    }
    ~CombFilter() { delete[] mBuf; }

    void setFeedback(float fb) { mFeedback = fb; }
    void setDamping(float d)   { mDamping  = d; mDampComp = 1.0f - d; }

    inline float process(float x) {
        float out = mBuf[mPos];
        mFilter   = out * mDampComp + mFilter * mDamping;
        mBuf[mPos] = x + mFilter * mFeedback;
        if (++mPos >= mSize) mPos = 0;
        return out;
    }
    void reset() { if (mBuf) std::memset(mBuf, 0, sizeof(float) * mSize); mFilter = 0.0f; mPos = 0; }

private:
    float* mBuf{nullptr};
    int    mSize{0}, mPos{0};
    float  mFeedback{0.5f}, mDamping{0.5f}, mDampComp{0.5f}, mFilter{0.0f};
};

class AllPassFilter {
public:
    void resize(int size) {
        mSize = size;
        mBuf  = new float[size]();
        mPos  = 0;
    }
    ~AllPassFilter() { delete[] mBuf; }

    void setFeedback(float fb) { mFeedback = fb; }

    inline float process(float x) {
        float buf = mBuf[mPos];
        mBuf[mPos] = x + buf * mFeedback;
        if (++mPos >= mSize) mPos = 0;
        return buf - x;
    }
    void reset() { if (mBuf) std::memset(mBuf, 0, sizeof(float) * mSize); mPos = 0; }

private:
    float* mBuf{nullptr};
    int    mSize{0}, mPos{0};
    float  mFeedback{0.5f};
};

class Reverb {
public:
    Reverb() = default;
    ~Reverb() = default;

    void configure(int32_t sampleRate) {
        float scale = static_cast<float>(sampleRate) / 44100.0f;

        static const int kCombSizes[4] = {1116, 1188, 1277, 1356};
        for (int i = 0; i < 4; ++i) {
            mCombL[i].resize(static_cast<int>(kCombSizes[i] * scale));
            mCombR[i].resize(static_cast<int>((kCombSizes[i] + 23) * scale));
        }
        static const int kApSizes[2] = {556, 441};
        for (int i = 0; i < 2; ++i) {
            mApL[i].resize(static_cast<int>(kApSizes[i] * scale));
            mApR[i].resize(static_cast<int>((kApSizes[i] + 13) * scale));
            mApL[i].setFeedback(0.5f);
            mApR[i].setFeedback(0.5f);
        }
        mDirty.store(true, std::memory_order_release);
    }

    void setRoomSize(float v) { mRoomSize.store(v, std::memory_order_relaxed); mDirty = true; }
    void setDamping(float v)  { mDamping.store(v,  std::memory_order_relaxed); mDirty = true; }
    void setWetDry(float v)   { mWetDry.store(v,   std::memory_order_relaxed); }
    void setWidth(float v)    { mWidth.store(v,     std::memory_order_relaxed); }
    void setEnabled(bool en)  { mEnabled.store(en,  std::memory_order_relaxed); }

    void process(float* buffer, int32_t numFrames, int32_t channelCount) {
        if (!mEnabled.load(std::memory_order_relaxed)) return;
        if (channelCount != 2) return;

        if (mDirty.exchange(false, std::memory_order_acq_rel)) {
            rebuildParams();
        }

        float wet   = mWetDry.load(std::memory_order_relaxed);
        float dry   = 1.0f - wet;
        float width = mWidth.load(std::memory_order_relaxed);
        float wetL  = wet * (0.5f + 0.5f * width);
        float wetR  = wet * (0.5f - 0.5f * width);

        for (int32_t f = 0; f < numFrames; ++f) {
            float inL = buffer[f * 2 + 0];
            float inR = buffer[f * 2 + 1];
            float mono = (inL + inR) * 0.5f;

            float outL = 0.0f, outR = 0.0f;
            for (int i = 0; i < 4; ++i) {
                outL += mCombL[i].process(mono);
                outR += mCombR[i].process(mono);
            }
            for (int i = 0; i < 2; ++i) {
                outL = mApL[i].process(outL);
                outR = mApR[i].process(outR);
            }

            buffer[f * 2 + 0] = inL * dry + outL * wetL + outR * wetR;
            buffer[f * 2 + 1] = inR * dry + outR * wetL + outL * wetR;
        }
    }

    void reset() {
        for (int i = 0; i < 4; ++i) { mCombL[i].reset(); mCombR[i].reset(); }
        for (int i = 0; i < 2; ++i) { mApL[i].reset();   mApR[i].reset(); }
    }

private:
    void rebuildParams() {
        float room = mRoomSize.load(std::memory_order_relaxed);
        float damp = mDamping.load(std::memory_order_relaxed);
        float fb = 0.7f + room * 0.28f;
        for (int i = 0; i < 4; ++i) {
            mCombL[i].setFeedback(fb);
            mCombR[i].setFeedback(fb);
            mCombL[i].setDamping(damp);
            mCombR[i].setDamping(damp);
        }
    }

    CombFilter    mCombL[4], mCombR[4];
    AllPassFilter mApL[2],   mApR[2];

    std::atomic<float> mRoomSize{0.5f};
    std::atomic<float> mDamping{0.5f};
    std::atomic<float> mWetDry{0.3f};
    std::atomic<float> mWidth{1.0f};
    std::atomic<bool>  mEnabled{false};
    std::atomic<bool>  mDirty{true};
};

} // namespace voicedaw
