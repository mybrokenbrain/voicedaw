#pragma once

#include <vector>
#include <map>
#include <algorithm>
#include <array>

class HarmonyEngine {
public:
    HarmonyEngine() {
        mMajorProfile = {6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f};
        mMinorProfile = {6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f};
        mPitchClassWeights.assign(12, 0.0f);
    }

    void registerNote(int midiNote, float durationSeconds) {
        int pitchClass = midiNote % 12;
        mPitchClassWeights[pitchClass] += durationSeconds;
        
        for (int i = 0; i < 12; ++i) {
            mPitchClassWeights[i] *= 0.95f; 
        }
        
        updateEstimatedKey();
    }

    struct Key {
        int rootPitchClass;
        bool isMajor;
    };

    Key getEstimatedKey() const { return mEstimatedKey; }

    std::array<int, 3> suggestChord(int rootMidiNote) {
        std::array<int, 3> chord;
        chord[0] = rootMidiNote;
        
        int rootPC = rootMidiNote % 12;
        int scaleIndex = getScaleIndex(rootPC, mEstimatedKey);
        
        if (scaleIndex >= 0) {
            int thirdOffset = getScalePitchClass(scaleIndex + 2, mEstimatedKey) - rootPC;
            if (thirdOffset < 0) thirdOffset += 12;
            
            int fifthOffset = getScalePitchClass(scaleIndex + 4, mEstimatedKey) - rootPC;
            if (fifthOffset < 0) fifthOffset += 12;

            chord[1] = rootMidiNote + thirdOffset;
            chord[2] = rootMidiNote + fifthOffset;
        } else {
            chord[1] = rootMidiNote + (mEstimatedKey.isMajor ? 4 : 3);
            chord[2] = rootMidiNote + 7;
        }
        
        return chord;
    }

    void reset() {
        mPitchClassWeights.assign(12, 0.0f);
        mEstimatedKey = {0, true};
    }

private:
    std::vector<float> mPitchClassWeights;
    std::vector<float> mMajorProfile;
    std::vector<float> mMinorProfile;
    Key mEstimatedKey{0, true};

    void updateEstimatedKey() {
        float maxCorrelation = -1.0f;
        Key bestKey{0, true};

        for (int root = 0; root < 12; ++root) {
            float majorCorr = calculateCorrelation(root, true);
            if (majorCorr > maxCorrelation) {
                maxCorrelation = majorCorr;
                bestKey = {root, true};
            }
            float minorCorr = calculateCorrelation(root, false);
            if (minorCorr > maxCorrelation) {
                maxCorrelation = minorCorr;
                bestKey = {root, false};
            }
        }
        
        mEstimatedKey = bestKey;
    }

    float calculateCorrelation(int rootPC, bool isMajor) {
        const auto& profile = isMajor ? mMajorProfile : mMinorProfile;
        float correlation = 0.0f;
        for (int i = 0; i < 12; ++i) {
            int profileIndex = (i - rootPC + 12) % 12;
            correlation += mPitchClassWeights[i] * profile[profileIndex];
        }
        return correlation;
    }

    int getScaleIndex(int pitchClass, Key key) {
        std::vector<int> majorIntervals = {0, 2, 4, 5, 7, 9, 11};
        std::vector<int> minorIntervals = {0, 2, 3, 5, 7, 8, 10};
        const auto& intervals = key.isMajor ? majorIntervals : minorIntervals;
        
        for (int i = 0; i < 7; ++i) {
            if ((key.rootPitchClass + intervals[i]) % 12 == pitchClass) {
                return i;
            }
        }
        return -1;
    }

    int getScalePitchClass(int scaleIndex, Key key) {
        std::vector<int> majorIntervals = {0, 2, 4, 5, 7, 9, 11};
        std::vector<int> minorIntervals = {0, 2, 3, 5, 7, 8, 10};
        const auto& intervals = key.isMajor ? majorIntervals : minorIntervals;
        
        int normalizedIndex = scaleIndex % 7;
        return (key.rootPitchClass + intervals[normalizedIndex]) % 12;
    }
};
