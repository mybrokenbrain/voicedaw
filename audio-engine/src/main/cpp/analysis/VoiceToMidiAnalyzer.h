#pragma once
#include <functional>
#include <vector>
#include <atomic>
#include <cmath>
#include "YinPitchTracker.h"
#include "MfccExtractor.h"
#include "OnsetDetector.h"

namespace voicedaw {

class VoiceToMidiAnalyzer {
public:
    VoiceToMidiAnalyzer(int sampleRate, 
                        std::function<void(float, float)> onPitchAndAmp, 
                        std::function<void(const std::vector<float>&, float)> onMfccs)
        : mSampleRate(sampleRate), 
          mOnPitchAndAmp(onPitchAndAmp), 
          mOnMfccs(onMfccs),
          mPitchTracker(1024),
          mMfccExtractor(512),
          mOnsetDetector(512),
          mAnalyzing(false),
          mHighPassPrevIn(0.0f),
          mHighPassPrevOut(0.0f)
    {
        mAudioBuffer.reserve(1024);
    }

    void setAnalyzing(bool analyzing) {
        mAnalyzing.store(analyzing, std::memory_order_relaxed);
    }

    void processAudio(const float* buffer, int numFrames) {
        if (!mAnalyzing.load(std::memory_order_relaxed)) return;

        for (int i = 0; i < numFrames; ++i) {
            mAudioBuffer.push_back(buffer[i]);
            
            if (mAudioBuffer.size() >= 1024) {
                analyzeBuffer(mAudioBuffer.data(), mAudioBuffer.size());
                
                    std::copy(mAudioBuffer.begin() + 512, mAudioBuffer.end(), mAudioBuffer.begin());
                mAudioBuffer.resize(512);
            }
        }
    }

private:
    float applyHighPassFilter(const float* input, size_t size, std::vector<float>& output) {
        output.resize(size);
        const float alpha = 0.9857f;

        for (size_t i = 0; i < size; i++) {
            output[i] = alpha * (mHighPassPrevOut + input[i] - mHighPassPrevIn);
            mHighPassPrevIn = input[i];
            mHighPassPrevOut = output[i];
        }

        return mHighPassPrevOut;
    }

    void analyzeBuffer(const float* data, size_t size) {
        float maxAmp = 0.0f;
        for (size_t i = 0; i < size; ++i) {
            maxAmp = std::max(maxAmp, std::abs(data[i]));
        }

        std::vector<float> filtered;
        applyHighPassFilter(data, size, filtered);

        float pitch = mPitchTracker.getPitch(filtered.data(), 0.15f, maxAmp * 0.05f);
        if (mOnPitchAndAmp) {
            mOnPitchAndAmp(pitch, maxAmp);
        }

        bool onset = mOnsetDetector.process(filtered.data());
        if (onset) {
            std::vector<float> mfccs = mMfccExtractor.compute(filtered.data());
            if (mOnMfccs) {
                mOnMfccs(mfccs, maxAmp);
            }
        }
    }

    int mSampleRate;
    std::function<void(float, float)> mOnPitchAndAmp;
    std::function<void(const std::vector<float>&, float)> mOnMfccs;
    
    YinPitchTracker mPitchTracker;
    MfccExtractor mMfccExtractor;
    OnsetDetector mOnsetDetector;
    
    std::atomic<bool> mAnalyzing;
    std::vector<float> mAudioBuffer;
    float mHpStateY = 0.0f;
    float mHighPassPrevIn;
    float mHighPassPrevOut;
};

}
