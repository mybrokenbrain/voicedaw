#pragma once

#include <atomic>
#include <cstdint>
#include <cassert>
#include <type_traits>

namespace voicedaw {

template<typename T, int32_t Size>
class RingBuffer {
    static_assert(std::is_trivially_copyable_v<T>,
        "RingBuffer<T>: T must be trivially copyable (no heap allocation)");
    static_assert((Size & (Size - 1)) == 0,
        "RingBuffer: Size must be a power of two");

public:
    bool push(const T& item) {
        int32_t head = mHead.load(std::memory_order_relaxed);
        int32_t next = (head + 1) & kMask;
        if (next == mTail.load(std::memory_order_acquire)) {
            return false;
        }
        mBuffer[head] = item;
        mHead.store(next, std::memory_order_release);
        return true;
    }

    bool pop(T& out) {
        int32_t tail = mTail.load(std::memory_order_relaxed);
        if (tail == mHead.load(std::memory_order_acquire)) {
            return false;
        }
        out = mBuffer[tail];
        mTail.store((tail + 1) & kMask, std::memory_order_release);
        return true;
    }

    bool isEmpty() const {
        return mHead.load(std::memory_order_relaxed) ==
               mTail.load(std::memory_order_relaxed);
    }

private:
    static constexpr int32_t kMask = Size - 1;
    T mBuffer[Size];
    std::atomic<int32_t> mHead{0};
    std::atomic<int32_t> mTail{0};
};

enum class AudioCommandType : uint8_t {
    Play,
    Pause,
    Stop,
    SetTrackGain,
    SetTrackMute,
    SetBpm,
};

struct AudioCommand {
    AudioCommandType type;
    int32_t trackIndex{-1};
    float   floatValue{0.0f};
    bool    boolValue{false};
};

enum class AudioEventType : uint8_t {
    PositionUpdate,
    LevelMeter,
    XrunDetected,
    LatencyUpdate,
    PitchUpdate,
};

struct AudioEvent {
    AudioEventType type;
    int64_t framesRendered;
    float peakLevel;
    float    latencyMs{0.0f};
    float    pitchHz{0.0f};
};

using CommandQueue = RingBuffer<AudioCommand, 256>;
using EventQueue   = RingBuffer<AudioEvent, 512>;

}
