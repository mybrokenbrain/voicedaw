#pragma once

#include <atomic>
#include <cmath>
#include <cstdint>

namespace voicedaw {

class Compressor {
public:
    Compressor() = default;

    void configure(int32_t sampleRate) {
        mSampleRate = static_cast<float>(sampleRate);
        mDirty.store(true, std::memory_order_release);
    }

    void setThresholdDb(float db)  { mThreshDb.store(db,  std::memory_order_relaxed); mDirty = true; }
    void setRatio(float ratio)     { mRatio.store(ratio,  std::memory_order_relaxed); mDirty = true; }
    void setAttackMs(float ms)     { mAttackMs.store(ms,  std::memory_order_relaxed); mDirty = true; }
    void setReleaseMs(float ms)    { mReleaseMs.store(ms, std::memory_order_relaxed); mDirty = true; }
    void setKneeDb(float db)       { mKneeDb.store(db,    std::memory_order_relaxed); mDirty = true; }
    void setMakeupGainDb(float db) { mMakeupDb.store(db,  std::memory_order_relaxed); mDirty = true; }
    void setEnabled(bool enabled)  { mEnabled.store(enabled, std::memory_order_relaxed); }

    float getGainReductionDb() const { return mGainReductionDb.load(std::memory_order_relaxed); }

    void process(float* buffer, int32_t numFrames, int32_t channelCount, const float* sidechainBuffer = nullptr) {
        if (!mEnabled.load(std::memory_order_relaxed)) { mGainReductionDb.store(0.0f); return; }

        if (mDirty.exchange(false, std::memory_order_acq_rel)) {
            rebuildCoeffs();
        }

        for (int32_t f = 0; f < numFrames; ++f) {
            float peak = 0.0f;
            if (sidechainBuffer != nullptr) {
                for (int c = 0; c < channelCount; ++c) {
                    float s = sidechainBuffer[f * channelCount + c];
                    float a = s < 0.0f ? -s : s;
                    if (a > peak) peak = a;
                }
            } else {
                for (int c = 0; c < channelCount; ++c) {
                    float s = buffer[f * channelCount + c];
                    float a = s < 0.0f ? -s : s;
                    if (a > peak) peak = a;
                }
            }

            float inputDb = (peak > 1e-9f) ? 20.0f * std::log10(peak) : -120.0f;

            float targetGainDb = computeGain(inputDb);

            if (targetGainDb < mCurrentGainDb) {
                mCurrentGainDb += mAttackCoeff * (targetGainDb - mCurrentGainDb);
            } else {
                mCurrentGainDb += mReleaseCoeff * (targetGainDb - mCurrentGainDb);
            }

            float gr = mCurrentGainDb;
            mGainReductionDb.store(gr, std::memory_order_relaxed);

            float gain = std::pow(10.0f, (gr + mMakeupDb.load(std::memory_order_relaxed)) / 20.0f);
            for (int c = 0; c < channelCount; ++c) {
                buffer[f * channelCount + c] *= gain;
            }
        }
    }

private:
    float computeGain(float inputDb) const {
        float T = mThreshDb.load(std::memory_order_relaxed);
        float R = mRatio.load(std::memory_order_relaxed);
        float W = mKneeDb.load(std::memory_order_relaxed);

        float diff = inputDb - T;
        if (diff < -W * 0.5f) return 0.0f;   
        if (diff >  W * 0.5f) {               
            return (1.0f / R - 1.0f) * diff;
        }
        float kn = diff + W * 0.5f;
        return (1.0f / R - 1.0f) * kn * kn / (2.0f * W);
    }

    void rebuildCoeffs() {
        float at = mAttackMs.load(std::memory_order_relaxed);
        float rt = mReleaseMs.load(std::memory_order_relaxed);
        mAttackCoeff  = 1.0f - std::exp(-1.0f / (mSampleRate * at  * 0.001f));
        mReleaseCoeff = 1.0f - std::exp(-1.0f / (mSampleRate * rt  * 0.001f));
    }

    float mSampleRate{48000.0f};
    float mCurrentGainDb{0.0f};
    float mAttackCoeff{0.01f};
    float mReleaseCoeff{0.001f};

    std::atomic<float> mThreshDb{-18.0f};
    std::atomic<float> mRatio{4.0f};
    std::atomic<float> mAttackMs{10.0f};
    std::atomic<float> mReleaseMs{100.0f};
    std::atomic<float> mKneeDb{6.0f};
    std::atomic<float> mMakeupDb{0.0f};
    std::atomic<float> mGainReductionDb{0.0f};
    std::atomic<bool>  mEnabled{true};
    std::atomic<bool>  mDirty{true};
};

} // namespace voicedaw
