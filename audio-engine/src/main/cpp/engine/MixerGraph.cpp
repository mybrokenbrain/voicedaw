#include "MixerGraph.h"
#include <cstring>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "VoiceDaw/MixerGraph"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)

namespace voicedaw {

MixerGraph::MixerGraph() {
}

void MixerGraph::configure(int32_t sampleRate, int32_t channelCount) {
    mSampleRate   = sampleRate;
    mChannelCount = channelCount;
    mMastering = std::make_unique<MasteringModule>();
    for (auto& v : mVoices) {
        v.configure(sampleRate);
    }
    for (auto& t : mTracks) {
        t.configure(sampleRate, channelCount);
        t.eq().setEnabled(true);
        t.comp().setEnabled(true);
    }
    mReverb.configure(sampleRate);
    mReverb.setEnabled(true);

    mMasterDelay = std::make_unique<fx::Delay>(sampleRate);
    mMasterGate = std::make_unique<fx::NoiseGate>(sampleRate);

    mPerformanceFx.configure(sampleRate);
    mPadPlayers.configure(sampleRate);

    mConfigured = true;
    LOGI("MixerGraph configured: sampleRate=%d, channels=%d, voices=%d",
         sampleRate, channelCount, kMaxVoices);
}

void MixerGraph::postNoteEvent(int32_t midiNote, int32_t velocity) {
    NoteEvent ev{midiNote, velocity, false, 0.0f};
    if (!mNoteQueue.push(ev)) {
        LOGW("NoteEventQueue full — event dropped (note=%d vel=%d)", midiNote, velocity);
    }
}

void MixerGraph::postPitchBend(int32_t midiNote, float bendSemis) {
    NoteEvent ev{midiNote, 0, true, bendSemis};
    if (!mNoteQueue.push(ev)) {
        LOGW("NoteEventQueue full — pitch bend dropped (note=%d bend=%f)", midiNote, bendSemis);
    }
}

void MixerGraph::allNotesOff() {
    for (int32_t i = 0; i < 128; ++i) {
        postNoteEvent(i, 0);
    }
}

void MixerGraph::setTrackGain(int32_t trackIndex, float gain) {
    if (trackIndex >= 0 && trackIndex < kMaxTracks) {
        float db = (gain <= 0.001f) ? -60.0f : (20.0f * std::log10(gain));
        mTracks[trackIndex].setVolumeDb(db);
    }
}

void MixerGraph::muteTrack(int32_t trackIndex, bool mute) {
    if (trackIndex >= 0 && trackIndex < kMaxTracks) {
        mTracks[trackIndex].setMuted(mute);
    }
}

void MixerGraph::setTransportState(TransportClock::State state) {
    mTransport.setState(state);
    if (state == TransportClock::State::Stopped) {
        mTransport.resetPosition();
    }
}

void MixerGraph::loadTrackClip(int32_t trackIndex, const std::string& path) {
    if (trackIndex >= 0 && trackIndex < kMaxTracks) {
        mClipPlayers[trackIndex].loadWavFile(path);
    }
}

void MixerGraph::loadReferenceTrack(const std::string& path) {
    mReferencePlayer.loadWavFile(path);
}

void MixerGraph::render(float* outputBuffer, int32_t numFrames, int32_t channelCount) {
    if (!mConfigured) {
        std::memset(outputBuffer, 0, sizeof(float) * numFrames * channelCount);
        return;
    }

    drainNoteQueue();
    mPadPlayers.drainEvents();

    std::memset(outputBuffer, 0, sizeof(float) * numFrames * channelCount);
    std::memset(mReverbBusBuffer, 0, sizeof(float) * numFrames * channelCount);
    std::memset(mDelayBusBuffer, 0, sizeof(float) * numFrames * channelCount);
    for (int32_t b = 1; b < kNumBuses; ++b) {
        std::memset(mBusBuffers[b], 0, sizeof(float) * numFrames * channelCount);
    }

    bool anySoloed = false;
    for (int i = 0; i < kMaxTracks; ++i) {
        if (mTracks[i].isSoloed()) { anySoloed = true; break; }
    }

    for (int t = 0; t < kMaxTracks; ++t) {
        float* trackBuf = mPreCompBuffers[t];
        std::memset(trackBuf, 0, sizeof(float) * numFrames * channelCount);
        std::memset(mScratchBuffer, 0, sizeof(float) * numFrames);

        if (t == 0) {
            for (auto& voice : mVoices) {
                if (!voice.isActive()) continue;
                voice.render(mScratchBuffer, numFrames);
            }
            for (auto& drum : mDrumVoices) {
                if (!drum.isActive()) continue;
                drum.render(mScratchBuffer, numFrames, 1); 
            }
        }

        mClipPlayers[t].process(mScratchBuffer, numFrames, mTransport);

        for (int32_t f = 0; f < numFrames; ++f) {
            trackBuf[f * channelCount + 0] = mScratchBuffer[f];
            trackBuf[f * channelCount + 1] = mScratchBuffer[f];
        }

        mTracks[t].renderPreComp(trackBuf, numFrames, anySoloed);
    }

    for (int t = 0; t < kMaxTracks; ++t) {
        float* trackBuf = mPreCompBuffers[t];

        const float* sidechainBuffer = nullptr;
        if (mTracks[t].isSidechainEnabled()) {
            int32_t scSource = mTracks[t].getSidechainSource();
            if (scSource >= 0 && scSource < kMaxTracks && scSource != t) {
                sidechainBuffer = mPreCompBuffers[scSource];
            }
        }

        mTracks[t].applyPostComp(trackBuf, mReverbSendBuffer, mDelaySendBuffer,
                                  numFrames, anySoloed, sidechainBuffer);

        int32_t bus = mTracks[t].getTargetBus();
        float* dest = (bus > 0 && bus < kNumBuses) ? mBusBuffers[bus] : outputBuffer;
        for (int32_t i = 0; i < numFrames * channelCount; ++i) {
            dest[i] += trackBuf[i];
            mReverbBusBuffer[i] += mReverbSendBuffer[i];
            mDelayBusBuffer[i] += mDelaySendBuffer[i];
        }
    }

    // Sum submix buses into master. No per-bus FX inserts yet — this is
    // grouping only (matches what "Track-to-Bus & Submix Routing" actually
    // shipped as; per-bus processing is a follow-up feature, not this fix).
    for (int32_t b = 1; b < kNumBuses; ++b) {
        for (int32_t i = 0; i < numFrames * channelCount; ++i) {
            outputBuffer[i] += mBusBuffers[b][i];
        }
    }

    // Vocal Pad Grid — sums directly into master (bypasses per-track
    // EQ/compressor/submix-bus/sends for now; that's a reasonable v1 scope
    // boundary, not an oversight — pads still go through the master FX
    // chain, reverb, and mastering below since they're in outputBuffer now).
    mPadPlayers.render(outputBuffer, numFrames, channelCount);

    mReverb.process(mReverbBusBuffer, numFrames, channelCount);
    for (int32_t i = 0; i < numFrames * channelCount; ++i) {
        outputBuffer[i] += mReverbBusBuffer[i];
    }

    // NOTE: mMasterDelay is shared by both the master-insert-delay role and
    // the per-track delay-send-bus role (see header comment). Gating on
    // mMasterDelayEnabled restores the toggle's previous meaning but also
    // means aux delay sends go silent when master delay is off.
    if (mMasterDelayEnabled && mMasterDelay) {
        mMasterDelay->process(mDelayBusBuffer, numFrames * channelCount);
        for (int32_t i = 0; i < numFrames * channelCount; ++i) {
            outputBuffer[i] += mDelayBusBuffer[i];
        }
    }

    mPerformanceFx.process(outputBuffer, numFrames, channelCount);

    // Master Bus FX
    if (mMasterSaturationEnabled) {
        mMasterSaturation.process(outputBuffer, numFrames * channelCount);
    }
    if (mMasterGateEnabled && mMasterGate) {
        mMasterGate->process(outputBuffer, numFrames * channelCount);
    }

    // Phase 3
    if (mMastering) {
        float stereoRef[kMaxFramesPerCallback * 2] = {0};
        if (mReferencePlayer.isLoaded()) {
            std::memset(mScratchBuffer, 0, sizeof(float) * numFrames);
            mReferencePlayer.process(mScratchBuffer, numFrames, mTransport);

            for (int32_t f = 0; f < numFrames; ++f) {
                stereoRef[f * 2]     = mScratchBuffer[f];
                stereoRef[f * 2 + 1] = mScratchBuffer[f];
            }
            mMastering->processReference(stereoRef, numFrames);
        }

        if (mListenToReference) {
            std::copy(stereoRef, stereoRef + numFrames * 2, outputBuffer);
        } else {
            mMastering->process(outputBuffer, numFrames);
        }
    }

    if (mTransport.getState() == TransportClock::State::Playing || mTransport.getState() == TransportClock::State::Recording) {
        mTransport.advanceFrames(numFrames);
    }
}

// Offline Export

int64_t MixerGraph::getLongestClipFrames() const {
    int64_t maxFrames = 0;
    for (int i = 0; i < kMaxTracks; ++i) {
        int64_t f = static_cast<int64_t>(mClipPlayers[i].getTotalFrames());
        if (f > maxFrames) maxFrames = f;
    }
    return maxFrames;
}

void MixerGraph::resetForOfflineRender() {
    mTransport.resetPosition();
    mTransport.setState(TransportClock::State::Playing);
}

// Private

void MixerGraph::drainNoteQueue() {
    NoteEvent ev;
    while (mNoteQueue.pop(ev)) {
        if (ev.isPitchBend) {
            for (auto& voice : mVoices) {
                if (voice.isActive() && voice.midiNote() == ev.midiNote) {
                    voice.applyPitchBend(ev.bendSemis);
                }
            }
        } else if (ev.velocity > 0) {
            if (ev.midiNote >= 36 && ev.midiNote <= 38) {
                        DrumVoice* d = allocDrumVoice(ev.midiNote);
                if (d) d->noteOn(ev.midiNote, ev.velocity);
            } else {
                SynthVoice* v = allocVoice(ev.midiNote);
                v->noteOn(ev.midiNote, ev.velocity);
            }
        } else {
            for (auto& voice : mVoices) {
                if (voice.isActive() && voice.midiNote() == ev.midiNote) {
                    voice.noteOff();
                    break;
                }
            }
            for (auto& drum : mDrumVoices) {
                if (drum.isActive() && drum.midiNote() == ev.midiNote) {
                    drum.noteOff(ev.midiNote);
                    break;
                }
            }
        }
    }
}

SynthVoice* MixerGraph::allocVoice(int32_t midiNote) {
    for (auto& v : mVoices) {
        if (v.isActive() && v.midiNote() == midiNote) return &v;
    }
    for (auto& v : mVoices) {
        if (!v.isActive()) return &v;
    }
    LOGW("Voice pool full — stealing voice 0");
    return &mVoices[0];
}

DrumVoice* MixerGraph::allocDrumVoice(int32_t midiNote) {
    for (auto& d : mDrumVoices) {
        if (!d.isActive()) return &d;
    }
    LOGW("Drum voice pool full — stealing voice 0");
    return &mDrumVoices[0];
}

} // namespace voicedaw
