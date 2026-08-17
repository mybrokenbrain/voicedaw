#pragma once

#include <atomic>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string>
#include <vector>
#include <algorithm>
#include <android/log.h>

namespace voicedaw {

class PadVoice {
public:
    enum class PlaybackMode : int32_t {
        ONE_SHOT = 0,
        LOOP = 1,
        HOLD_TO_PLAY = 2,
        PING_PONG = 3,
    };

    void configureEngine(int32_t engineSampleRate) {
        mEngineSampleRate = engineSampleRate;
    }

    bool loadWav(const std::string& path) {
        FILE* f = fopen(path.c_str(), "rb");
        if (!f) {
            __android_log_print(ANDROID_LOG_ERROR, "VoiceDaw/PadVoice", "Cannot open %s", path.c_str());
            return false;
        }

        char riff[4]{};
        if (fread(riff, 1, 4, f) != 4 || memcmp(riff, "RIFF", 4) != 0) { fclose(f); return false; }
        fseek(f, 4, SEEK_CUR);
        char wave[4]{};
        if (fread(wave, 1, 4, f) != 4 || memcmp(wave, "WAVE", 4) != 0) { fclose(f); return false; }

        int32_t fileSampleRate = 48000;
        int16_t numChannels = 1;
        int16_t bitsPerSample = 16;
        int16_t audioFormat = 1;
        bool haveFmt = false;
        long dataOffset = -1;
        uint32_t dataSize = 0;

        while (true) {
            char chunkId[4]{};
            if (fread(chunkId, 1, 4, f) != 4) break;
            uint32_t chunkSize = 0;
            if (fread(&chunkSize, 4, 1, f) != 1) break;

            if (memcmp(chunkId, "fmt ", 4) == 0) {
                fread(&audioFormat, 2, 1, f);
                fread(&numChannels, 2, 1, f);
                fread(&fileSampleRate, 4, 1, f);
                fseek(f, 6, SEEK_CUR);
                fread(&bitsPerSample, 2, 1, f);
                long remaining = static_cast<long>(chunkSize) - 16;
                if (remaining > 0) fseek(f, remaining, SEEK_CUR);
                haveFmt = true;
            } else if (memcmp(chunkId, "data", 4) == 0) {
                dataOffset = ftell(f);
                dataSize = chunkSize;
                break;
            } else {
                fseek(f, static_cast<long>(chunkSize), SEEK_CUR);
            }
        }

        if (!haveFmt || dataOffset < 0 || audioFormat != 1 || bitsPerSample != 16 ||
            (numChannels != 1 && numChannels != 2) || dataSize == 0) {
            __android_log_print(ANDROID_LOG_ERROR, "VoiceDaw/PadVoice",
                "Unsupported/invalid WAV (%s) — need 16-bit PCM mono or stereo", path.c_str());
            fclose(f);
            return false;
        }

        fseek(f, dataOffset, SEEK_SET);
        int32_t numFrames = static_cast<int32_t>(dataSize / 2 / numChannels);
        std::vector<int16_t> raw(static_cast<size_t>(numFrames) * numChannels);
        size_t readCount = fread(raw.data(), sizeof(int16_t), raw.size(), f);
        fclose(f);
        if (readCount != raw.size()) {
            numFrames = static_cast<int32_t>(readCount / numChannels);
        }
        if (numFrames <= 0) return false;

        auto buf = std::make_shared<std::vector<float>>(static_cast<size_t>(numFrames) * 2);
        for (int32_t i = 0; i < numFrames; ++i) {
            if (numChannels == 1) {
                float s = raw[i] / 32768.0f;
                (*buf)[i * 2 + 0] = s;
                (*buf)[i * 2 + 1] = s;
            } else {
                (*buf)[i * 2 + 0] = raw[i * 2 + 0] / 32768.0f;
                (*buf)[i * 2 + 1] = raw[i * 2 + 1] / 32768.0f;
            }
        }

        mFileSampleRate = fileSampleRate;
        mTotalFrames = numFrames;
        mBufferHolder = buf;
        mBuffer.store(buf->data(), std::memory_order_release);
        mLoaded.store(true, std::memory_order_release);
        return true;
    }

    bool isLoaded() const { return mLoaded.load(std::memory_order_relaxed); }
    int32_t getChokeGroup() const { return mChokeGroup.load(std::memory_order_relaxed); }
    bool isActive() const { return mActive.load(std::memory_order_relaxed); }

    void setParams(float startMs, float endMs, int32_t pitchSemitones, int32_t fineTuneCents,
                    float gainDb, float fadeInMs, float fadeOutMs, bool reverse,
                    int32_t playbackMode, int32_t chokeGroup) {
        mStartMs.store(startMs, std::memory_order_relaxed);
        mEndMs.store(endMs, std::memory_order_relaxed);
        mPitchSemitones.store(pitchSemitones, std::memory_order_relaxed);
        mFineTuneCents.store(fineTuneCents, std::memory_order_relaxed);
        mGainDb.store(gainDb, std::memory_order_relaxed);
        mFadeInMs.store(fadeInMs, std::memory_order_relaxed);
        mFadeOutMs.store(fadeOutMs, std::memory_order_relaxed);
        mReverse.store(reverse, std::memory_order_relaxed);
        mPlaybackMode.store(playbackMode, std::memory_order_relaxed);
        mChokeGroup.store(chokeGroup, std::memory_order_relaxed);
    }

    void trigger(float velocity, int32_t extraSemitones) {
        if (!mLoaded.load(std::memory_order_relaxed)) return;

        float startMs = mStartMs.load(std::memory_order_relaxed);
        float endMs = mEndMs.load(std::memory_order_relaxed);
        int32_t startFrame = static_cast<int32_t>(startMs * mFileSampleRate / 1000.0f);
        int32_t endFrame = (endMs > startMs)
            ? static_cast<int32_t>(endMs * mFileSampleRate / 1000.0f)
            : mTotalFrames;
        mRegionStart = std::max(0, std::min(startFrame, mTotalFrames - 1));
        mRegionEnd = std::max(mRegionStart + 1, std::min(endFrame, mTotalFrames));

        bool reverse = mReverse.load(std::memory_order_relaxed);
        mDirection = reverse ? -1.0 : 1.0;
        mPlayPosFrames = reverse ? static_cast<double>(mRegionEnd - 1) : static_cast<double>(mRegionStart);

        int32_t semis = mPitchSemitones.load(std::memory_order_relaxed) + extraSemitones;
        float cents = static_cast<float>(mFineTuneCents.load(std::memory_order_relaxed));
        float pitchRatio = std::pow(2.0f, (static_cast<float>(semis) + cents / 100.0f) / 12.0f);
        mPlaybackRate = (static_cast<double>(mFileSampleRate) / mEngineSampleRate) * pitchRatio;

        mVelocity = velocity;
        mHeld = true;
        mReleasing = false;
        mReleaseGain = 1.0f;
        mFadeInFramesTotal = std::max(1, static_cast<int32_t>(
            mFadeInMs.load(std::memory_order_relaxed) * mEngineSampleRate / 1000.0f));
        mFadeInFramesRemaining = mFadeInFramesTotal;
        mActive.store(true, std::memory_order_relaxed);
    }

    void release() {
        mHeld = false;
        if (mPlaybackMode.load(std::memory_order_relaxed) == static_cast<int32_t>(PlaybackMode::HOLD_TO_PLAY)) {
            mReleasing = true;
        }
    }

    void choke() {
        if (mActive.load(std::memory_order_relaxed)) {
            mReleasing = true;
            mHeld = false;
        }
    }

    void render(float* outBuffer, int32_t numFrames, int32_t channelCount) {
        if (!mActive.load(std::memory_order_relaxed)) return;
        const float* data = mBuffer.load(std::memory_order_acquire);
        if (!data) { mActive.store(false, std::memory_order_relaxed); return; }

        float gain = std::pow(10.0f, mGainDb.load(std::memory_order_relaxed) / 20.0f) * mVelocity;
        int32_t mode = mPlaybackMode.load(std::memory_order_relaxed);
        float fadeOutMs = mFadeOutMs.load(std::memory_order_relaxed);
        int32_t releaseFrames = std::max(1, static_cast<int32_t>(
            (fadeOutMs > 0.0f ? fadeOutMs : 15.0f) * mEngineSampleRate / 1000.0f));

        for (int32_t i = 0; i < numFrames; ++i) {
            if (!mActive.load(std::memory_order_relaxed)) break;

            int32_t frame = static_cast<int32_t>(mPlayPosFrames);
            if (frame < mRegionStart || frame >= mRegionEnd) {
                if (mode == static_cast<int32_t>(PlaybackMode::LOOP) ||
                    (mode == static_cast<int32_t>(PlaybackMode::HOLD_TO_PLAY) && mHeld)) {
                    mPlayPosFrames = (mDirection > 0) ? mRegionStart : (mRegionEnd - 1);
                    frame = static_cast<int32_t>(mPlayPosFrames);
                } else if (mode == static_cast<int32_t>(PlaybackMode::PING_PONG)) {
                    mDirection = -mDirection;
                    mPlayPosFrames = (mDirection > 0) ? mRegionStart : (mRegionEnd - 1);
                    frame = static_cast<int32_t>(mPlayPosFrames);
                } else {
                    mActive.store(false, std::memory_order_relaxed);
                    break;
                }
            }

            float sL = data[frame * 2 + 0];
            float sR = data[frame * 2 + 1];

            float env = 1.0f;
            if (mFadeInFramesRemaining > 0) {
                env *= 1.0f - (static_cast<float>(mFadeInFramesRemaining) / mFadeInFramesTotal);
                mFadeInFramesRemaining--;
            }
            if (mReleasing) {
                mReleaseGain -= 1.0f / releaseFrames;
                if (mReleaseGain <= 0.0f) {
                    mReleaseGain = 0.0f;
                    mActive.store(false, std::memory_order_relaxed);
                }
                env *= mReleaseGain;
            } else if (mode == static_cast<int32_t>(PlaybackMode::ONE_SHOT)) {
                int32_t framesFromEnd = mRegionEnd - frame;
                int32_t fadeOutFrames = static_cast<int32_t>(fadeOutMs * mEngineSampleRate / 1000.0f);
                if (fadeOutFrames > 0 && framesFromEnd <= fadeOutFrames) {
                    env *= static_cast<float>(framesFromEnd) / fadeOutFrames;
                }
            }

            outBuffer[i * channelCount + 0] += sL * gain * env;
            if (channelCount > 1) outBuffer[i * channelCount + 1] += sR * gain * env;

            mPlayPosFrames += mDirection * mPlaybackRate;
        }
    }

private:
    int32_t mEngineSampleRate{48000};
    int32_t mFileSampleRate{48000};
    int32_t mTotalFrames{0};

    std::shared_ptr<std::vector<float>> mBufferHolder;
    std::atomic<float*> mBuffer{nullptr};
    std::atomic<bool> mLoaded{false};

    std::atomic<float> mStartMs{0.0f};
    std::atomic<float> mEndMs{0.0f};
    std::atomic<int32_t> mPitchSemitones{0};
    std::atomic<int32_t> mFineTuneCents{0};
    std::atomic<float> mGainDb{0.0f};
    std::atomic<float> mFadeInMs{0.0f};
    std::atomic<float> mFadeOutMs{0.0f};
    std::atomic<bool> mReverse{false};
    std::atomic<int32_t> mPlaybackMode{0};
    std::atomic<int32_t> mChokeGroup{0};

    // Audio-thread Playback State
    std::atomic<bool> mActive{false};
    bool mHeld{false};
    bool mReleasing{false};
    float mReleaseGain{1.0f};
    double mPlayPosFrames{0.0};
    double mPlaybackRate{1.0};
    double mDirection{1.0};
    int32_t mRegionStart{0};
    int32_t mRegionEnd{0};
    float mVelocity{1.0f};
    int32_t mFadeInFramesRemaining{0};
    int32_t mFadeInFramesTotal{1};
};

} // namespace voicedaw
