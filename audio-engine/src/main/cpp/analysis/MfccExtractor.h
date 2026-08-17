#pragma once
#include <vector>
#include <cmath>
#include "../dsp/FFTAnalyzer.h"

namespace voicedaw {

class MfccExtractor {
public:
    MfccExtractor(int sampleRate, int fftSize, int numMelBans = 40, int numMfccs = 13)
        : mSampleRate(sampleRate), mFftSize(fftSize), 
          mNumMelBands(numMelBans), mNumMfccs(numMfccs),
          mFft(fftSize) {
        
        mHammingWindow.resize(fftSize);
        for (int i = 0; i < fftSize; ++i) {
            mHammingWindow[i] = 0.54f - 0.46f * std::cos(2.0f * M_PI * i / (fftSize - 1));
        }

        buildMelFilterbank();
    }

    std::vector<float> compute(const float* audioData) {
        std::vector<float> windowedData(mFftSize);
        
        for (int i = 0; i < mFftSize; ++i) {
            windowedData[i] = audioData[i] * mHammingWindow[i];
        }

        std::vector<float> magnitudes(mFftSize / 2);
        mFft.process(windowedData.data(), magnitudes.data());

        std::vector<float> powerSpec(mFftSize / 2);
        for (int i = 0; i < mFftSize / 2; ++i) {
            powerSpec[i] = (magnitudes[i] * magnitudes[i]) / mFftSize;
        }

        std::vector<float> melEnergies(mNumMelBands, 0.0f);
        for (int i = 0; i < mNumMelBands; ++i) {
            for (int j = 0; j < mFftSize / 2; ++j) {
                melEnergies[i] += powerSpec[j] * mFilterbank[i][j];
            }
            melEnergies[i] = std::log(std::max(melEnergies[i], 1e-10f));
        }

        std::vector<float> mfccs(mNumMfccs, 0.0f);
        for (int i = 0; i < mNumMfccs; ++i) {
            for (int j = 0; j < mNumMelBands; ++j) {
                mfccs[i] += melEnergies[j] * std::cos(M_PI * i * (j + 0.5f) / mNumMelBands);
            }
        }

        return mfccs;
    }

private:
    float hzToMel(float hz) { return 2595.0f * std::log10(1.0f + hz / 700.0f); }
    float melToHz(float mel) { return 700.0f * (std::pow(10.0f, mel / 2595.0f) - 1.0f); }

    void buildMelFilterbank() {
        float minMel = hzToMel(20.0f);
        float maxMel = hzToMel(mSampleRate / 2.0f);
        
        std::vector<float> melPoints(mNumMelBands + 2);
        for (int i = 0; i < mNumMelBands + 2; ++i) {
            melPoints[i] = minMel + i * (maxMel - minMel) / (mNumMelBands + 1);
        }

        std::vector<int> fftBins(mNumMelBands + 2);
        for (int i = 0; i < mNumMelBands + 2; ++i) {
            fftBins[i] = static_cast<int>(std::floor((mFftSize + 1) * melToHz(melPoints[i]) / mSampleRate));
        }

        mFilterbank.assign(mNumMelBands, std::vector<float>(mFftSize / 2, 0.0f));

        for (int i = 0; i < mNumMelBands; ++i) {
            for (int j = fftBins[i]; j < fftBins[i + 1]; ++j) {
                if (j < mFftSize / 2) {
                    mFilterbank[i][j] = (j - fftBins[i]) / static_cast<float>(fftBins[i + 1] - fftBins[i]);
                }
            }
            for (int j = fftBins[i + 1]; j < fftBins[i + 2]; ++j) {
                if (j < mFftSize / 2) {
                    mFilterbank[i][j] = (fftBins[i + 2] - j) / static_cast<float>(fftBins[i + 2] - fftBins[i + 1]);
                }
            }
        }
    }

    int mSampleRate;
    int mFftSize;
    int mNumMelBands;
    int mNumMfccs;
    std::vector<float> mHammingWindow;
    std::vector<std::vector<float>> mFilterbank;
    voicedaw::dsp::FFTAnalyzer mFft;
};

} // namespace voicedaw
