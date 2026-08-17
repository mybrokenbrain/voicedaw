#pragma once
#include <string>
#include <fstream>
#include <vector>
#include <cstdint>
#include <cmath>
#include <android/log.h>

namespace voicedaw {

class WavWriter {
public:
    static bool writeWav16(const std::string& path, const std::vector<float>& interleavedSamples, int32_t sampleRate, int32_t numChannels) {
        std::ofstream file(path, std::ios::binary);
        if (!file.is_open()) {
            __android_log_print(ANDROID_LOG_ERROR, "WavWriter", "Failed to open file for writing: %s", path.c_str());
            return false;
        }

        int32_t numSamples = interleavedSamples.size();
        int32_t byteRate = sampleRate * numChannels * 2;
        int32_t blockAlign = numChannels * 2;
        int32_t dataSize = numSamples * 2;
        int32_t chunkSize = 36 + dataSize;

        file.write("RIFF", 4);
        file.write(reinterpret_cast<const char*>(&chunkSize), 4);
        file.write("WAVE", 4);

        file.write("fmt ", 4);
        int32_t fmtSize = 16;
        int16_t audioFormat = 1;
        int16_t channels = numChannels;
        file.write(reinterpret_cast<const char*>(&fmtSize), 4);
        file.write(reinterpret_cast<const char*>(&audioFormat), 2);
        file.write(reinterpret_cast<const char*>(&channels), 2);
        file.write(reinterpret_cast<const char*>(&sampleRate), 4);
        file.write(reinterpret_cast<const char*>(&byteRate), 4);
        file.write(reinterpret_cast<const char*>(&blockAlign), 2);
        int16_t bitsPerSample = 16;
        file.write(reinterpret_cast<const char*>(&bitsPerSample), 2);

        file.write("data", 4);
        file.write(reinterpret_cast<const char*>(&dataSize), 4);

        for (float sample : interleavedSamples) {
            if (sample > 1.0f) sample = 1.0f;
            if (sample < -1.0f) sample = -1.0f;
            int16_t pcm = static_cast<int16_t>(sample * 32767.0f);
            file.write(reinterpret_cast<const char*>(&pcm), 2);
        }

        file.close();
        return true;
    }
};

} // namespace voicedaw
