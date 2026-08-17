#pragma once
#include <cmath>

namespace voicedaw {
namespace fx {

class NoiseGate {
public:
    NoiseGate(int sampleRate) : mSampleRate(sampleRate) {}

    void setParams(float thresholdDb, float attackMs, float releaseMs) {
        mThreshold = std::pow(10.0f, thresholdDb / 20.0f);
        mAttackCoef = std::exp(-1.0f / (attackMs * 0.001f * mSampleRate));
        mReleaseCoef = std::exp(-1.0f / (releaseMs * 0.001f * mSampleRate));
    }

    void process(float* inOutBuffer, int numFrames) {
        for (int i = 0; i < numFrames; ++i) {
            float in = inOutBuffer[i];
            float absIn = std::abs(in);
            
            if (absIn > mThreshold) {
                mEnvelope = mAttackCoef * mEnvelope + (1.0f - mAttackCoef) * 1.0f;
            } else {
                mEnvelope = mReleaseCoef * mEnvelope;
            }
            
            inOutBuffer[i] = in * mEnvelope;
        }
    }

private:
    int mSampleRate;
    float mThreshold = 0.0f;
    float mAttackCoef = 0.0f;
    float mReleaseCoef = 0.0f;
    float mEnvelope = 0.0f;
};

} // namespace fx
} // namespace voicedaw
