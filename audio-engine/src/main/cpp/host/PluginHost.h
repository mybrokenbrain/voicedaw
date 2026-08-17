#pragma once
#include <string>

namespace voicedaw {
namespace host {

class PluginHost {
public:
    PluginHost() = default;
    ~PluginHost() = default;

    bool loadPlugin(const std::string& pluginId) {
        mCurrentPluginId = pluginId;
        mIsLoaded = true;
        return true;
    }

    void process(float* buffer, int numFrames) {
        if (!mIsLoaded) return;
    }

    void unloadPlugin() {
        mIsLoaded = false;
        mCurrentPluginId = "";
    }

private:
    bool mIsLoaded = false;
    std::string mCurrentPluginId;
};

} // namespace host
} // namespace voicedaw
