#pragma once

#include <vector>
#include <algorithm>
#include <cmath>

namespace voicedaw {

struct MidiNote {
    int noteNumber;
    int velocity;
    float startMs;
    float durationMs;
};

class MidiCleanup {
public:
    struct Config {
        float latencyCompensationMs = 40.0f;
        float minDurationMs = 60.0f;
        int minVelocity = 20;
        bool applyLegato = true;
        bool quantize = true;
        float quantizationGridSize = 0.25f;
    };

    static std::vector<MidiNote> cleanupVocalTake(
        const std::vector<MidiNote>& rawNotes,
        float bpm,
        const Config& config = Config()
    ) {
        if (rawNotes.empty()) return {};

        std::vector<MidiNote> cleaned;
        cleaned.reserve(rawNotes.size());

        float msPerBeat = (bpm > 0) ? (60000.0f / bpm) : 500.0f;
        float gridMs = msPerBeat * config.quantizationGridSize;

        for (const auto& note : rawNotes) {
            if (note.durationMs < config.minDurationMs && note.velocity < config.minVelocity) {
                continue;
            }

            MidiNote n = note;
            n.startMs -= config.latencyCompensationMs;
            if (n.startMs < 0.0f) n.startMs = 0.0f;

            if (config.quantize && gridMs > 0.0f) {
                float gridTicks = std::round(n.startMs / gridMs);
                n.startMs = gridTicks * gridMs;
            }

            cleaned.push_back(n);
        }

        std::sort(cleaned.begin(), cleaned.end(), [](const MidiNote& a, const MidiNote& b) {
            return a.startMs < b.startMs;
        });

        if (config.applyLegato && !cleaned.empty()) {
            for (size_t i = 0; i < cleaned.size() - 1; ++i) {
                MidiNote& current = cleaned[i];
                MidiNote& next = cleaned[i + 1];
                
                float endMs = current.startMs + current.durationMs;
                float gapMs = next.startMs - endMs;
                
                if (gapMs > 0.0f && gapMs < 200.0f) { 
                    current.durationMs = next.startMs - current.startMs;
                }
            }
        }

        return cleaned;
    }
};

} // namespace voicedaw
