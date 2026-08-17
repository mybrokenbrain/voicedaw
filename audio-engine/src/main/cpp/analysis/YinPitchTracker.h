#pragma once
#include <vector>
#include <cmath>

namespace voicedaw {

class YinPitchTracker {
public:
    YinPitchTracker(int sampleRate, int bufferSize) 
        : mSampleRate(sampleRate) {
        mHalfBufferSize = bufferSize / 2;
        mYinBuffer.assign(mHalfBufferSize, 0.0f);
    }

    /**
     * @param buffer Audio samples (mono)
     * @param threshold Yin algorithm threshold (0.1–0.15 typical)
     * @param minAmplitude RMS floor below which to reject as silence (default 0.005)
     * @return Pitch in Hz, or -1.0f if unvoiced/silent/out-of-range
     */
    float getPitch(const float* buffer, float threshold = 0.15f, float minAmplitude = 0.005f) {
        float rms = 0.0f;
        for (int i = 0; i < mHalfBufferSize; i++) {
            rms += buffer[i] * buffer[i];
        }
        rms = std::sqrt(rms / mHalfBufferSize);
        if (rms < minAmplitude) return -1.0f;

        int tauEstimate = -1;
        float pitchInHertz = -1.0f;

        mYinBuffer[0] = 1.0f;
        float runningSum = 0.0f;
        bool foundTau = false;

        for (int tau = 1; tau < mHalfBufferSize; tau++) {
            float difference = 0.0f;
            for (int i = 0; i < mHalfBufferSize; i++) {
                float delta = buffer[i] - buffer[i + tau];
                difference += delta * delta;
            }

            runningSum += difference;
            mYinBuffer[tau] = difference * tau / runningSum;

                if (mYinBuffer[tau] < threshold) {
                while (tau + 1 < mHalfBufferSize && mYinBuffer[tau + 1] < mYinBuffer[tau]) {
                    tau++;
                }
                tauEstimate = tau;
                foundTau = true;
                break;
            }
        }

        if (!foundTau) {
                float minVal = 1.0f;
            for (int tau = 1; tau < mHalfBufferSize; tau++) {
                if (mYinBuffer[tau] < minVal) {
                    minVal = mYinBuffer[tau];
                    tauEstimate = tau;
                }
            }
        }

        if (tauEstimate > 0) {
                float betterTau = static_cast<float>(tauEstimate);
            if (tauEstimate > 0 && tauEstimate < mHalfBufferSize - 1) {
                float s0 = mYinBuffer[tauEstimate - 1];
                float s1 = mYinBuffer[tauEstimate];
                float s2 = mYinBuffer[tauEstimate + 1];
                betterTau = tauEstimate + (s2 - s0) / (2 * (2 * s1 - s2 - s0));
            }

            if (betterTau < 10.0f) betterTau = 10.0f;

            pitchInHertz = static_cast<float>(mSampleRate) / betterTau;

            if (pitchInHertz < 60.0f || pitchInHertz > 2000.0f) {
                return -1.0f;
            }
        }

        return pitchInHertz;
    }

private:
    int mSampleRate;
    int mHalfBufferSize;
    std::vector<float> mYinBuffer;
};

} // namespace voicedaw
