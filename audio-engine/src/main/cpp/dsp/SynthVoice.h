#pragma once

#include <cstdint>
#include <cmath>
#include <atomic>

namespace voicedaw {

class SynthVoice {
public:
    SynthVoice() = default;

    void configure(int32_t sampleRate) {
        mSampleRate = sampleRate;
    }

    void noteOn(int32_t midiNote, int32_t velocity) {
        mMidiNote   = midiNote;
        mFrequency  = midiNoteToHz(midiNote);
        mPhaseInc   = kTwoPi * mFrequency / static_cast<float>(mSampleRate);
        mPhase      = 0.0f;
        mGain       = static_cast<float>(velocity) / 127.0f * 0.25f;
        mEnvStage   = EnvStage::Attack;
        mEnvLevel   = 0.0f;
        mActive     = true;
    }

    void noteOff() {
        if (mActive) mEnvStage = EnvStage::Release;
    }

    bool isActive()   const { return mActive; }
    int32_t midiNote() const { return mMidiNote; }

    void applyPitchBend(float bendSemis) {
        mFrequency = midiNoteToHz(mMidiNote) * std::pow(2.0f, bendSemis / 12.0f);
        mPhaseInc = kTwoPi * mFrequency / static_cast<float>(mSampleRate);
    }

    void render(float* out, int32_t numFrames) {
        if (!mActive) return;

        for (int32_t i = 0; i < numFrames; ++i) {
            switch (mEnvStage) {
                case EnvStage::Attack:
                    mEnvLevel += kAttackCoeff;
                    if (mEnvLevel >= 1.0f) { mEnvLevel = 1.0f; mEnvStage = EnvStage::Decay; }
                    break;
                case EnvStage::Decay:
                    mEnvLevel += (kSustainLevel - mEnvLevel) * kDecayCoeff;
                    if (mEnvLevel <= kSustainLevel + 0.001f) { mEnvLevel = kSustainLevel; mEnvStage = EnvStage::Sustain; }
                    break;
                case EnvStage::Sustain:
                    mEnvLevel = kSustainLevel;
                    break;
                case EnvStage::Release:
                    mEnvLevel *= kReleaseCoeff;
                    if (mEnvLevel < 0.0001f) { mEnvLevel = 0.0f; mActive = false; return; }
                    break;
            }

            float osc = (mPhase / static_cast<float>(M_PI)) - 1.0f;

            float cutoff = 200.0f + mEnvLevel * 4000.0f;
            mFilterState += cutoff * (osc - mFilterState);

            out[i] += mFilterState * mEnvLevel * mGain;
            
            mPhase += mPhaseInc;
            if (mPhase >= kTwoPi) mPhase -= kTwoPi;
        }
    }

private:
    static float midiNoteToHz(int32_t note) {
        return 440.0f * std::pow(2.0f, (note - 69) / 12.0f);
    }

    enum class EnvStage : uint8_t { Attack, Decay, Sustain, Release };

    static constexpr float kTwoPi        = 6.2831853072f;
    static constexpr float kAttackCoeff  = 0.002f;
    static constexpr float kDecayCoeff   = 0.005f;
    static constexpr float kSustainLevel = 0.7f;
    static constexpr float kReleaseCoeff = 0.9985f;

    int32_t  mSampleRate{48000};
    int32_t  mMidiNote{60};
    float    mFrequency{261.63f};
    float    mPhase{0.0f};
    float    mPhaseInc{0.0f};
    float    mGain{0.25f};
    float    mEnvLevel{0.0f};
    float    mFilterState{0.0f};
    EnvStage mEnvStage{EnvStage::Release};
    bool     mActive{false};
};

struct NoteEvent {
    int32_t midiNote{0};
    int32_t velocity;
    bool isPitchBend{false};
    float bendSemis{0.0f};
};

} // namespace voicedaw
