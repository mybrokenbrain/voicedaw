#pragma once

namespace voicedaw {

class TransportClock {
public:
    enum class State { Stopped, Playing, Paused, Recording };
    TransportClock() = default;
    ~TransportClock() = default;
    State getState() const { return mState; }
    void setState(State state) { mState = state; }
    int64_t getPositionFrames() const { return mFrames; }
    void resetPosition() { mFrames = 0; }
    void advanceFrames(int frames) { mFrames += frames; }
private:
    State mState{State::Stopped};
    int64_t mFrames{0};
};

} // namespace voicedaw
