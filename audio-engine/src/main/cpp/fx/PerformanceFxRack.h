#pragma once


#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <algorithm>

namespace voicedaw {
namespace fx {

class PerformanceFxRack {
public:
    void configure(int32_t sampleRate) {
        mSampleRate = static_cast<float>(sampleRate);
    }

    void setFx(int32_t fxType, bool hold) {
        if (!hold) {
            mActiveFx.store(-1, std::memory_order_relaxed);
            return;
        }

        mActiveFx.store(fxType, std::memory_order_relaxed);
        mPhase = 0.0f;

        if (fxType == 0 || fxType == 1 || fxType == 2) {
            float durSec = (fxType == 0) ? 0.25f : (fxType == 1) ? 0.125f : 0.0625f;
            int32_t loopLen = static_cast<int32_t>(mSampleRate * durSec);
            mStutterLoopLenFrames = std::min(loopLen, kMaxStutterFrames);
            mStutterWritePos = 0;
            mStutterReadPos = 0;
            mStutterCaptured = false;
        }

        if (fxType == 4 || fxType == 5) {
            mFilterState[0] = 0.0f;
            mFilterState[1] = 0.0f;
            mSweepElapsedFrames = 0;
        }
    }

    void process(float* buffer, int32_t numFrames, int32_t channelCount) {
        int32_t fx = mActiveFx.load(std::memory_order_relaxed);
        if (fx == -1) return;

        for (int i = 0; i < numFrames; ++i) {
            switch (fx) {
                case 0: case 1: case 2: // STUTTER_8TH / 16TH / 32ND
                {
                    if (!mStutterCaptured) {
                        for (int c = 0; c < channelCount && c < 2; ++c) {
                            mStutterBuffer[mStutterWritePos * 2 + c] = buffer[i * channelCount + c];
                        }
                        mStutterWritePos++;
                        if (mStutterWritePos >= mStutterLoopLenFrames) {
                            mStutterCaptured = true;
                            mStutterReadPos = 0;
                        }
                    } else {
                        for (int c = 0; c < channelCount && c < 2; ++c) {
                            buffer[i * channelCount + c] = mStutterBuffer[mStutterReadPos * 2 + c];
                        }
                        mStutterReadPos = (mStutterReadPos + 1) % std::max(1, mStutterLoopLenFrames);
                    }
                    break;
                }
                case 3: // TAPE_STOP
                {
                    float gain = std::max(0.0f, 1.0f - (mPhase / (mSampleRate * 1.5f)));
                    mPhase += 1.0f;
                    for (int c = 0; c < channelCount; ++c) {
                        buffer[i * channelCount + c] *= gain;
                    }
                    break;
                }
                case 4: case 5: // FILTER_LP_SWEEP / FILTER_HP_SWEEP
                {
                    float sweepT = std::min(1.0f, mSweepElapsedFrames / (mSampleRate * 1.0f));
                    float cutoffHz = (fx == 4)
                        ? (8000.0f - sweepT * 7800.0f)
                        : (200.0f + sweepT * 7800.0f);
                    cutoffHz = std::max(20.0f, std::min(cutoffHz, mSampleRate * 0.45f));
                    float alpha = std::exp(-2.0f * static_cast<float>(M_PI) * cutoffHz / mSampleRate);

                    for (int c = 0; c < channelCount && c < 2; ++c) {
                        float x = buffer[i * channelCount + c];
                        mFilterState[c] = alpha * mFilterState[c] + (1.0f - alpha) * x;
                        float lp = mFilterState[c];
                        buffer[i * channelCount + c] = (fx == 4) ? lp : (x - lp);
                    }
                    mSweepElapsedFrames++;
                    break;
                }
                case 6: // BITCRUSH
                {
                    float crush = 16.0f;
                    for (int c = 0; c < channelCount; ++c) {
                        float s = buffer[i * channelCount + c];
                        buffer[i * channelCount + c] = std::round(s * crush) / crush;
                    }
                    break;
                }
                case 14: // DISTORTION_BOOST
                {
                    for (int c = 0; c < channelCount; ++c) {
                        float s = buffer[i * channelCount + c];
                        buffer[i * channelCount + c] = std::tanh(s * 5.0f) * 0.5f;
                    }
                    break;
                }
                case 15: // SILENCE_CUT
                {
                    for (int c = 0; c < channelCount; ++c) {
                        buffer[i * channelCount + c] = 0.0f;
                    }
                    break;
                }
                default:
                    // TODO: Not implemented
                    break;
            }
        }
    }

private:
    static constexpr int32_t kMaxStutterFrames = 16384; // ~0.34s at 48kHz, covers all 3 rates

    float mSampleRate{48000.0f};
    std::atomic<int32_t> mActiveFx{-1};
    float mPhase{0.0f};

    // Stutter
    float mStutterBuffer[kMaxStutterFrames * 2]{};
    int32_t mStutterLoopLenFrames{0};
    int32_t mStutterWritePos{0};
    int32_t mStutterReadPos{0};
    bool mStutterCaptured{false};

    // Filter sweep
    float mFilterState[2]{0.0f, 0.0f};
    int64_t mSweepElapsedFrames{0};
};

} // namespace fx
} // namespace voicedaw
