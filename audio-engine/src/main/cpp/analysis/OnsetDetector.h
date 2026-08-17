#pragma once
#include <vector>
#include <cmath>
#include <algorithm>
#include "../dsp/FFTAnalyzer.h"

namespace voicedaw {

class OnsetDetector {
public:
    OnsetDetector(int fftSize, float threshold = 2.5f) 
        : mThreshold(threshold), mFft(fftSize) {
        mCurrentMagnitudes.resize(fftSize / 2, 0.0f);
        mPreviousMagnitudes.resize(fftSize / 2, 0.0f);
    }

    bool processFrame(const float* audioData) {
        bool isOnset = false;
        
        mFft.process(audioData, mCurrentMagnitudes.data());

        float flux = 0.0f;
        for (size_t i = 0; i < mCurrentMagnitudes.size(); ++i) {
            float diff = mCurrentMagnitudes[i] - mPreviousMagnitudes[i];
            if (diff > 0.0f) {
                flux += diff;
            }
            mPreviousMagnitudes[i] = mCurrentMagnitudes[i];
        }
        
        if (flux > 0.05f && flux > (mSmoothedFlux * mThreshold) && !mInCooldown) {
            isOnset = true;
            mInCooldown = true;
            mCooldownCounter = 5;
        }

        if (mInCooldown) {
            mCooldownCounter--;
            if (mCooldownCounter <= 0) mInCooldown = false;
        }

        if (flux > mSmoothedFlux) {
            mSmoothedFlux = mSmoothedFlux * 0.5f + flux * 0.5f;
        } else {
            mSmoothedFlux = mSmoothedFlux * 0.95f + flux * 0.05f;
        }

        return isOnset;
    }

private:
    float mSmoothedFlux{0.0f};
    float mThreshold{2.5f};
    bool mInCooldown{false};
    int mCooldownCounter{0};
    
    dsp::FFTAnalyzer mFft;
    std::vector<float> mCurrentMagnitudes;
    std::vector<float> mPreviousMagnitudes;
};

} // namespace voicedaw
