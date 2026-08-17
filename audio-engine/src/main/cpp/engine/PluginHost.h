#pragma once

#include <vector>
#include <string>
#include <memory>
#include <cstdint>

// Forward declarations for AAP types
namespace aap {
    class PluginHostManager;
    class PluginInstance;
}

namespace voicedaw {

class PluginHost {
public:
    PluginHost();
    ~PluginHost();

    void initialize(int32_t sampleRate, int32_t blockFrames, int32_t channelCount);

    void scanPlugins();

    bool loadPlugin(const std::string& pluginId);

    void process(float* audioBuffer, int32_t numFrames);

    void setParameter(int32_t index, float value);

private:
    int32_t mSampleRate{48000};
    int32_t mBlockFrames{256};
    int32_t mChannelCount{2};
    bool mInitialized{false};

    // std::unique_ptr<aap::PluginHostManager> mHostManager;
    // std::unique_ptr<aap::PluginInstance> mActivePlugin;
};

} // namespace voicedaw
