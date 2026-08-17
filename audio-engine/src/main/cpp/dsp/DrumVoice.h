#pragma once
#include <cstdint>
#include <cmath>
#include <cstdlib>

namespace voicedaw {

class DrumVoice {
public:
    DrumVoice() = default;
    ~DrumVoice() = default;

    void setSampleRate(int sampleRate) {
        mSampleRate = sampleRate;
    }

    void noteOn(int note, int velocity) {
        mMidiNote = note;
        mGain = static_cast<float>(velocity) / 127.0f * 0.8f;
        mPhase = 0.0f;
        mEnvLevel = 1.0f;
        mActive = true;

        if (note == 36) {
            mType = DrumType::Kick;
            mFrequency = 150.0f;
        } else if (note == 37) {
            mType = DrumType::Snare;
            mFrequency = 200.0f;
        } else {
            mType = DrumType::Hat;
            mFrequency = 8000.0f;
        }
    }

    void noteOff(int note) {
        if (mMidiNote == note) {
            mActive = false;
        }
    }

    void render(float* outBuffer, int numFrames, int channels) {
        if (!mActive) return;

        float invSampleRate = 1.0f / static_cast<float>(mSampleRate);

        for (int i = 0; i < numFrames; ++i) {
            float sample = 0.0f;

            if (mType == DrumType::Kick) {
                mFrequency *= 0.998f;
                if (mFrequency < 40.0f) mFrequency = 40.0f;

                float phaseInc = 6.2831853072f * mFrequency * invSampleRate;
                sample = std::sin(mPhase);
                mPhase += phaseInc;
                if (mPhase >= 6.2831853072f) mPhase -= 6.2831853072f;

                mEnvLevel *= 0.999f;
            } else if (mType == DrumType::Snare) {
                        float phaseInc = 6.2831853072f * mFrequency * invSampleRate;
                float tone = std::sin(mPhase);
                mPhase += phaseInc;
                if (mPhase >= 6.2831853072f) mPhase -= 6.2831853072f;

                        float noise = ((std::rand() % 2000) / 1000.0f) - 1.0f;

                sample = (tone * 0.4f + noise * 0.6f);
                mEnvLevel *= 0.998f;
            } else if (mType == DrumType::Hat) {
                        float noise = ((std::rand() % 2000) / 1000.0f) - 1.0f;
                        mFilterState = noise - mFilterState * 0.5f;
                sample = mFilterState;
                mEnvLevel *= 0.995f;
            }

            sample *= mEnvLevel * mGain;

                outBuffer[i * channels] += sample;
            if (channels == 2) outBuffer[i * channels + 1] += sample;

            if (mEnvLevel < 0.001f) {
                mActive = false;
                break;
            }
        }
    }

    bool isActive() const { return mActive; }
    int midiNote() const { return mMidiNote; }

private:
    enum class DrumType { Kick, Snare, Hat };

    int mSampleRate{48000};
    int mMidiNote{0};
    float mGain{0.0f};
    float mPhase{0.0f};
    float mFrequency{0.0f};
    float mEnvLevel{0.0f};
    float mFilterState{0.0f};
    bool mActive{false};
    DrumType mType{DrumType::Kick};
};

} // namespace voicedaw
