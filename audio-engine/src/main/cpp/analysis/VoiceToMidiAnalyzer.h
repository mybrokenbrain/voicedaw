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
          mPitchTracker(sampleRate, 1024), // standard buffer size for Yin
          mMfccExtractor(sampleRate, 512), // standard fft size for MFCC
          mOnsetDetector(512), // matching fft size
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

        // In a real implementation we would buffer up to 1024 frames and then process.
        // For simplicity, assuming we get chunked calls.
        for (int i = 0; i < numFrames; ++i) {
            mAudioBuffer.push_back(buffer[i]);
            
            if (mAudioBuffer.size() >= 1024) {
                analyzeBuffer(mAudioBuffer.data(), mAudioBuffer.size());
                
                // Overlap by 512 samples
                std::copy(mAudioBuffer.begin() + 512, mAudioBuffer.end(), mAudioBuffer.begin());
                mAudioBuffer.resize(512);
            }
        }
    }

private:
    /**
     * Apply a one-pole high-pass filter to attenuate DC offset, mic handling rumble,
     * and low-frequency ambient noise before pitch tracking.
     * Cutoff frequency is ~80 Hz at 48 kHz sample rate.
     */
    float applyHighPassFilter(const float* input, size_t size, std::vector<float>& output) {
        output.resize(size);
        // One-pole high-pass with cutoff ~80 Hz at 48 kHz
        // alpha = 1 - exp(-2 * pi * fc / fs) simplified to 0.9857 for fc=80Hz, fs=48kHz
        const float alpha = 0.9857f;

        for (size_t i = 0; i < size; i++) {
            // High-pass: output = alpha * (prev_output + input - prev_input)
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

        // Apply high-pass filter to remove DC and rumble before pitch detection
        std::vector<float> filtered;
        applyHighPassFilter(data, size, filtered);

        // Pitch Tracking (Hum-to-Melody)
        float pitch = mPitchTracker.getPitch(filtered.data(), 0.15f, maxAmp * 0.05f);
        if (mOnPitchAndAmp) {
            mOnPitchAndAmp(pitch, maxAmp);
        }

        // Beatbox-to-Drums (Onset + MFCC)
        // Use filtered audio for onset detection too, to avoid DC/rumble triggering false onsets
        if (mOnsetDetector.processFrame(filtered.data())) { // assuming OnsetDetector takes raw frames or processes them
            // Onset detected! Extract MFCCs from first 512 samples of filtered data
            auto mfccs = mMfccExtractor.compute(filtered.data()); // uses first 512 samples
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

    // High-pass filter state (stateful to maintain continuity across buffer boundaries)
    float mHighPassPrevIn;
    float mHighPassPrevOut;
};

} // namespace voicedaw
