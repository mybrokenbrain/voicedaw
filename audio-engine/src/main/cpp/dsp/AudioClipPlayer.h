#pragma once

#include <vector>
#include <string>
#include <atomic>
#include <memory>
#include <cstdio>
#include <android/log.h>
#include "TransportClock.h"

namespace voicedaw {

class AudioClipPlayer {
public:
    AudioClipPlayer() {}

    void loadWavFile(const std::string& path) {
        FILE* file = fopen(path.c_str(), "rb");
        if (!file) {
            __android_log_print(ANDROID_LOG_ERROR, "VoiceDaw/AudioClip", "Failed to open WAV: %s", path.c_str());
            return;
        }

        fseek(file, 44, SEEK_SET);

        std::vector<int16_t> pcm16;
        int16_t sample;
        while (fread(&sample, sizeof(int16_t), 1, file) == 1) {
            pcm16.push_back(sample);
        }
        fclose(file);

        auto floatData = std::make_shared<std::vector<float>>(pcm16.size());
        for (size_t i = 0; i < pcm16.size(); ++i) {
            (*floatData)[i] = pcm16[i] / 32768.0f;
        }

        float* oldPtr = mAudioData.exchange(floatData->data(), std::memory_order_release);
        (void)oldPtr;
        mFrameCount.store(floatData->size(), std::memory_order_release);
        
        mHolder = floatData; 
    }

    bool isLoaded() const { return mHolder != nullptr; }

    void process(float* outBuffer, int numFrames, const TransportClock& clock) {
        if (clock.getState() != TransportClock::State::Playing) return;

        float* data = mAudioData.load(std::memory_order_acquire);
        size_t totalFrames = mFrameCount.load(std::memory_order_acquire);
        int64_t currentFrame = clock.getPositionFrames();

        if (!data || totalFrames == 0 || currentFrame >= (int64_t)totalFrames) return;

        for (int i = 0; i < numFrames; ++i) {
            int64_t frame = currentFrame + i;
            if (frame < (int64_t)totalFrames) {
                outBuffer[i] += data[frame];
            }
        }
    }

    void clear() {
        mAudioData.store(nullptr, std::memory_order_release);
        mFrameCount.store(0, std::memory_order_release);
        mHolder.reset();
    }

    size_t getTotalFrames() const {
        return mFrameCount.load(std::memory_order_relaxed);
    }

private:
    std::shared_ptr<std::vector<float>> mHolder;
    std::atomic<float*> mAudioData{nullptr};
    std::atomic<size_t> mFrameCount{0};
};

} // namespace voicedaw
