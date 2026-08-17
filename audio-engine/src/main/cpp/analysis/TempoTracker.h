#pragma once
#include <cstdint>
#include <vector>

namespace voicedaw {

class TempoTracker {
public:
    TempoTracker() = default;
    ~TempoTracker() = default;

    float registerOnset(int64_t nowMs) {
        mOnsets.push_back(nowMs);
        if (mOnsets.size() > 10) {
            mOnsets.erase(mOnsets.begin());
        }
        
        if (mOnsets.size() < 2) return 0.0f;
        
        float avgInterval = 0.0f;
        for (size_t i = 1; i < mOnsets.size(); ++i) {
            avgInterval += (mOnsets[i] - mOnsets[i - 1]);
        }
        avgInterval /= (mOnsets.size() - 1);
        
        if (avgInterval > 0) {
            return 60000.0f / avgInterval;
        }
        return 0.0f;
    }

private:
    std::vector<int64_t> mOnsets;
};

} // namespace voicedaw
