#pragma once
#include <cmath>

namespace voicedaw {
namespace fx {

class Saturation {
public:
    Saturation() = default;

    void setDrive(float drive) {
        mDrive = std::fmax(1.0f, drive);
    }

    void process(float* inOutBuffer, int numFrames) {
        if (mDrive <= 1.0f) return;
        
        float gain = mDrive;
        float invGain = 1.0f / gain;
        
        for (int i = 0; i < numFrames; ++i) {
            float in = inOutBuffer[i] * gain;
            
            
            float out = std::tanh(in);
            
            inOutBuffer[i] = out * invGain;
        }
    }

private:
    float mDrive = 1.0f;
};

} // namespace fx
} // namespace voicedaw
