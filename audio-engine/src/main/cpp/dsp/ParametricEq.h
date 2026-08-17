#pragma once

namespace voicedaw {

class ParametricEq {
public:
    ParametricEq() = default;
    ~ParametricEq() = default;

    void setEnabled(bool enabled) {}

    void configure(int sampleRate) {}
    void setSampleRate(int sampleRate) {}
    void setHighShelfFreq(float freq) {}
    void setHighShelfGain(float gain) {}
    void setLowShelfFreq(float freq) {}
    void setLowShelfGain(float gain) {}
    void setMidFreq(float freq) {}
    void setMidGain(float gain) {}
    void setMidQ(float q) {}
    void process(float* buffer, int numFrames, int channels) {}
};

} // namespace voicedaw
