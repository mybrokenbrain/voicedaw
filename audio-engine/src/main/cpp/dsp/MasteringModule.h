#pragma once

#include "ParametricEq.h"
#include "Compressor.h"

namespace voicedaw {

class MasteringModule {
public:
    MasteringModule() = default;
    ~MasteringModule() = default;

    void configure(int sampleRate) {}
    void process(float* buffer, int numFrames) {}
    void processReference(float* buffer, int numFrames) {}

    ParametricEq& eq() { return mEq; }
    Compressor& comp() { return mComp; }
    
    float getIntegratedLUFS() const { return -70.0f; }
    float getTruePeak() const { return -100.0f; }

private:
    ParametricEq mEq;
    Compressor mComp;
};

} // namespace voicedaw
