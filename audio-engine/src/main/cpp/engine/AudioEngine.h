#pragma once

#include <oboe/Oboe.h>
#include <memory>
#include <atomic>
#include <functional>
#include <vector>
#include <array>

#include "MixerGraph.h"
#include "../analysis/VoiceToMidiAnalyzer.h"
#include "../analysis/HarmonyEngine.h"
#include "../analysis/KnnClassifier.h"
#include "../analysis/TempoTracker.h"

namespace voicedaw {

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    enum class AudioTier {
        PRO = 0,
        LOW = 1,
        UNSUPPORTED = 2,
        UNKNOWN = 3
    };

    AudioEngine();
    virtual ~AudioEngine();

    bool start();
    void stop();
    bool restart();
    
    void setPerformanceTier(int tier);
    void setOutputDeviceId(int32_t deviceId);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;
    void onErrorBeforeClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;
    

    float getIntegratedLUFS() { return 0.0f; }
    float getTruePeak() { return 0.0f; }
    long getXrunCount() { return mXrunCount.load(std::memory_order_relaxed); }
    float getLatencyMs() { return mLatencyMs.load(std::memory_order_relaxed); }
    float getPlaybackPositionMs() { return (mMixerGraph->getTransportPosition() / (float)mSampleRate) * 1000.0f; }
    float getPitchHz() { return mPitchHz.load(); }
    float getPitchAmp() { return mPitchAmp.load(); }
    int getEstimatedKeyRoot() { return mEstimatedKeyRoot.load(); }
    bool getEstimatedKeyIsMajor() { return mEstimatedKeyIsMajor.load(); }
    float getEstimatedBpm() { return mEstimatedBpm.load(); }
    int getLastDetectedPad() { return mLastDetectedPad.load(); }
    int getSampleRate() { return mSampleRate.load(); }
    int getTier() { return (int)mTier.load(); }

    void setTransportState(int state) {}
    void loadTrackClip(int32_t trackIndex, const std::string& path);
    void loadReferenceTrack(const std::string& path);
    bool bounceToWav(const std::string& path);

    void saveBeatboxModel(const std::string& path);
    void loadBeatboxModel(const std::string& path);

    void setListenToReference(bool listen) {}

    void setTrackMute(int trackIndex, bool mute) {}
    void setTrackSolo(int trackIndex, bool solo) {}
    void setTrackPan(int trackIndex, float pan) {}
    void setTrackReverbSend(int trackIndex, float send) {}
    void setTrackGain(int trackIndex, float gain) {}
    
    void setReverbEnabled(bool enabled) {}
    void setReverbParams(float room, float damp, float wetDry, float width) {}
    
    void setEqEnabled(int trackIndex, bool enabled) {}
    void setEqHighShelf(int trackIndex, float freq, float gain) {}
    void setEqLowShelf(int trackIndex, float freq, float gain) {}
    void setEqMidBand(int trackIndex, float freq, float gain, float q) {}
    
    void setCompEnabled(int trackIndex, bool enabled) {}
    void setCompParams(int trackIndex, float thresh, float ratio, float attack, float release) {}
    float getCompGainReduction(int trackIndex) { return 0.0f; }

    void setMasterEqEnabled(bool enabled) {}
    void setMasterEqHighShelf(float freq, float gain) {}
    void setMasterEqLowShelf(float freq, float gain) {}
    void setMasterEqMidBand(float freq, float gain, float q) {}
    
    void setMasterCompEnabled(bool enabled) {}
    void setMasterCompParams(float thresh, float ratio, float attack, float release) {}
    float getMasterCompGainReduction() { return 0.0f; }

    void noteOn(int midiNote, int velocity) {}
    void noteOff(int midiNote) {}
    
    void setAnalysisMode(int mode) {
        mAnalysisMode.store(mode, std::memory_order_relaxed);
        if (mAnalyzer) mAnalyzer->setAnalyzing(mode != 0);
    }
    void setHarmonyAssistEnabled(bool enabled) { mHarmonyAssistEnabled.store(enabled); }
    void setBeatboxTrainingPad(int padIndex) { mTrainingPadIndex.store(padIndex); }

    MixerGraph& getMixerGraph() { return *mMixerGraph; }

private:
    bool openStream();
    void closeStream();
    AudioTier detectTier(oboe::AudioStream* stream) const;

    std::unique_ptr<MixerGraph> mMixerGraph;
    std::atomic<bool> mEcoModeEnabled{false};
    std::atomic<int32_t> mOutputDeviceId{0};
    
    std::shared_ptr<oboe::AudioStream> mOutputStream;
    std::shared_ptr<oboe::AudioStream> mInputStream;
    
    std::atomic<AudioTier> mTier{AudioTier::UNKNOWN};
    std::atomic<int> mSampleRate{48000};
    std::atomic<int> mBufferSizeFrames{0};
    
    std::unique_ptr<VoiceToMidiAnalyzer> mAnalyzer;
    
    std::atomic<float> mPitchHz{0.0f};
    std::atomic<float> mPitchAmp{0.0f};
    std::atomic<int> mEstimatedKeyRoot{0};
    std::atomic<bool> mEstimatedKeyIsMajor{true};
    std::atomic<float> mEstimatedBpm{0.0f};
    std::atomic<bool> mHarmonyAssistEnabled{false};
    std::atomic<int32_t> mTrainingPadIndex{-1};
    std::atomic<int> mLastDetectedPad{-1};
    std::atomic<int> mAnalysisMode{0};
    std::atomic<long> mXrunCount{0};
    std::atomic<float> mLatencyMs{0.0f};
    
    HarmonyEngine mHarmonyEngine;
    TempoTracker mTempoTracker;
    KnnClassifier mBeatboxClassifier;
    
    bool mHumIsActive = false;
    int32_t mCurrentHumNote = -1;
    int mHumDebounceCounter = 0;
    std::array<int, 3> mCurrentHumChord = {-1, -1, -1};
    static const int kHumDebounceFrames = 3;
    
    int64_t mFramesRendered = 0;
};

} // namespace voicedaw
