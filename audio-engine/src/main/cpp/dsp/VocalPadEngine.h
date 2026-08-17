#ifndef VOCALPADENGINE_H
#define VOCALPADENGINE_H

#include <vector>
#include <string>

class VocalPadEngine {
public:
    VocalPadEngine() {}
    ~VocalPadEngine() {}

    void loadSample(int padIndex, const std::string& path) {
    }

    void triggerPad(int padIndex, float velocity) {
    }

private:
};

#endif // VOCALPADENGINE_H
