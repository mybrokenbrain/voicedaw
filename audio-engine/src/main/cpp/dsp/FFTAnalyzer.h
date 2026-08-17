#pragma once
#include <vector>
#include <complex>

namespace voicedaw {
namespace dsp {

class FFTAnalyzer {
public:
    inline FFTAnalyzer(int fftSize) : mFftSize(fftSize) {
        mBuffer.resize(mFftSize);
        mTwiddles.resize(mFftSize / 2);
        mBitReversedIndices.resize(mFftSize);

        const float PI = std::acos(-1.0f);
        for (int i = 0; i < mFftSize / 2; ++i) {
            float angle = -2.0f * PI * i / mFftSize;
            mTwiddles[i] = std::complex<float>(std::cos(angle), std::sin(angle));
        }

        int bits = 0;
        int temp = mFftSize;
        while (temp > 1) {
            bits++;
            temp >>= 1;
        }

        for (int i = 0; i < mFftSize; ++i) {
            int reversed = 0;
            for (int j = 0; j < bits; ++j) {
                if ((i >> j) & 1) {
                    reversed |= (1 << (bits - 1 - j));
                }
            }
            mBitReversedIndices[i] = reversed;
        }
    }

    ~FFTAnalyzer() = default;

    inline void process(const float* input, float* outMagnitudes) {
        for (int i = 0; i < mFftSize; ++i) {
            mBuffer[i] = std::complex<float>(input[i], 0.0f);
        }

        performFFT(mBuffer);

        for (int i = 0; i < mFftSize / 2; ++i) {
            outMagnitudes[i] = std::abs(mBuffer[i]);
        }
    }

private:
    int mFftSize;
    std::vector<std::complex<float>> mBuffer;
    std::vector<std::complex<float>> mTwiddles;
    std::vector<int> mBitReversedIndices;

    inline void performFFT(std::vector<std::complex<float>>& x) {
        for (int i = 0; i < mFftSize; ++i) {
            int rev = mBitReversedIndices[i];
            if (i < rev) {
                std::swap(x[i], x[rev]);
            }
        }

        for (int len = 2; len <= mFftSize; len <<= 1) {
            int halfLen = len >> 1;
            int twiddleStep = mFftSize / len;
            for (int i = 0; i < mFftSize; i += len) {
                for (int j = 0; j < halfLen; ++j) {
                    std::complex<float> t = mTwiddles[j * twiddleStep] * x[i + j + halfLen];
                    std::complex<float> u = x[i + j];
                    x[i + j] = u + t;
                    x[i + j + halfLen] = u - t;
                }
            }
        }
    }
};

} // namespace dsp
} // namespace voicedaw
