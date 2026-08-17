#pragma once
#include <vector>
#include <cmath>
#include <atomic>

namespace voicedaw {
namespace fx {

class Delay {
public:
    Delay(int sampleRate) : mSampleRate(sampleRate), mWriteIndex(0) {
        mBuffer.resize(sampleRate * 2, 0.0f);
    }

    void setParams(float timeMs, float feedback, float mix) {
        mDelaySamples = static_cast<int>((timeMs / 1000.0f) * mSampleRate);
        mFeedback = feedback;
        mMix = mix;
    }

    void process(float* inOutBuffer, int numFrames) {
        if (mDelaySamples <= 0 || mMix <= 0.0f) return;

        for (int i = 0; i < numFrames; ++i) {
            float in = inOutBuffer[i];
            
            int readIndex = mWriteIndex - mDelaySamples;
            if (readIndex < 0) readIndex += mBuffer.size();
            
            float delayed = mBuffer[readIndex];
            mBuffer[mWriteIndex] = in + delayed * mFeedback;
            
            inOutBuffer[i] = in * (1.0f - mMix) + delayed * mMix;
            
            if (++mWriteIndex >= mBuffer.size()) mWriteIndex = 0;
        }
    }

private:
    int mSampleRate;
    std::vector<float> mBuffer;
    int mWriteIndex;
    int mDelaySamples = 0;
    float mFeedback = 0.0f;
    float mMix = 0.0f;
};

} // namespace fx
} // namespace voicedaw
