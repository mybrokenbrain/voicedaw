

#include "AudioEngine.h"
#include "MixerGraph.h"
#include <android/log.h>
#include <unistd.h>
#include "../dsp/WavWriter.h"

#define LOG_TAG "VoiceDaw/AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AudioEngine", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace voicedaw {

// Construction

AudioEngine::AudioEngine()
    : mMixerGraph(std::make_unique<MixerGraph>()) {
    LOGI("AudioEngine created");
}



void AudioEngine::setPerformanceTier(int tier) {
    bool isEco = (tier == 1);
    mEcoModeEnabled.store(isEco, std::memory_order_relaxed);
}

void AudioEngine::setOutputDeviceId(int32_t deviceId) {
    if (mOutputDeviceId.load(std::memory_order_relaxed) != deviceId) {
        mOutputDeviceId.store(deviceId, std::memory_order_relaxed);
        restart();
    }
}

// Oboe callback

AudioEngine::~AudioEngine() {
    stop();
    LOGI("AudioEngine destroyed");
}

// Lifecycle

bool AudioEngine::bounceToWav(const std::string& path) {
    if (!mMixerGraph) return false;
    
    int numFramesTotal = mSampleRate.load() * 30; // 30 seconds max for now
    std::vector<float> interleavedOut;
    interleavedOut.reserve(numFramesTotal * 2);
    
    float tempBuf[256 * 2];
    int framesLeft = numFramesTotal;
    

    mMixerGraph->setTransportState(TransportClock::State::Playing); // Playing
    
    while (framesLeft > 0) {
        int chunk = std::min(framesLeft, 256);
        mMixerGraph->render(tempBuf, chunk, 2);
        for (int i = 0; i < chunk * 2; i++) {
            interleavedOut.push_back(tempBuf[i]);
        }
        framesLeft -= chunk;
    }
    
    mMixerGraph->setTransportState(TransportClock::State::Stopped); // Stopped
    
    return WavWriter::writeWav16(path, interleavedOut, mSampleRate.load(), 2);
}

void AudioEngine::saveBeatboxModel(const std::string& path) {
    mBeatboxClassifier.saveToFile(path);
}

void AudioEngine::loadBeatboxModel(const std::string& path) {
    mBeatboxClassifier.loadFromFile(path);
}

bool AudioEngine::start() {
    if (!openStream()) {
        return false;
    }
    auto result = mOutputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start output stream: %s", oboe::convertToText(result));
        closeStream();
        return false;
    }
    
    if (mInputStream) {
        mInputStream->requestStart();
    }
    
    LOGI("Stream started. SampleRate=%d, FramesPerBurst=%d, Tier=%d",
         mSampleRate.load(), mBufferSizeFrames.load(), (int)mTier.load());
    return true;
}

void AudioEngine::stop() {
    closeStream();
}

bool AudioEngine::restart() {
    LOGI("Restarting audio stream after routing change");
    stop();
    return start();
}

// Stream open/close

bool AudioEngine::openStream() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Stereo);
    builder.setSampleRate(48000);
    builder.setDeviceId(mOutputDeviceId.load(std::memory_order_relaxed));
    builder.setDataCallback(this);
    builder.setErrorCallback(this);


    auto result = builder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open LowLatency Exclusive stream: %s. Trying shared fallback.",
             oboe::convertToText(result));


        builder.setSharingMode(oboe::SharingMode::Shared);
        result = builder.openStream(mOutputStream);
    }

    if (result != oboe::Result::OK) {
        LOGE("Failed to open any output stream: %s", oboe::convertToText(result));
        mTier.store(AudioTier::UNSUPPORTED, std::memory_order_relaxed);
        return false;
    }


    AudioTier tier = detectTier(mOutputStream.get());
    mTier.store(tier, std::memory_order_relaxed);
    mSampleRate.store(mOutputStream->getSampleRate(), std::memory_order_relaxed);
    mBufferSizeFrames.store(mOutputStream->getFramesPerBurst(), std::memory_order_relaxed);


    mOutputStream->setBufferSizeInFrames(mOutputStream->getFramesPerBurst() * 2);


    mMixerGraph->configure(mOutputStream->getSampleRate(), mOutputStream->getChannelCount());


    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input);
    inBuilder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    inBuilder.setFormat(oboe::AudioFormat::Float);
    inBuilder.setChannelCount(oboe::ChannelCount::Mono);
    inBuilder.setSampleRate(mOutputStream->getSampleRate());
    inBuilder.setDataCallback(this);
    
    auto inResult = inBuilder.openStream(mInputStream);
    if (inResult == oboe::Result::OK) {
        LOGI("Input stream opened successfully");
        mAnalyzer = std::make_unique<VoiceToMidiAnalyzer>(
            mInputStream->getSampleRate(),
            [this](float pitch, float amp) {
                mPitchHz.store(pitch, std::memory_order_relaxed);
                mPitchAmp.store(amp, std::memory_order_relaxed);
                

                if (amp > 0.05f && pitch > 50.0f && pitch < 2000.0f) {
                    float floatNote = 69.0f + 12.0f * std::log2(pitch / 440.0f);
                    int32_t rawNote = static_cast<int32_t>(std::round(floatNote));
                    
                    if (!mHumIsActive) {
                        if (rawNote == mCurrentHumNote) {
                            mHumDebounceCounter++;
                            if (mHumDebounceCounter >= kHumDebounceFrames) {
        
                                int32_t velocity = std::min(127, static_cast<int>(amp * 127.0f * 2.0f));
                                
                                mHarmonyEngine.registerNote(rawNote, 1.0f);
                                auto key = mHarmonyEngine.getEstimatedKey();
                                mEstimatedKeyRoot.store(key.rootPitchClass, std::memory_order_relaxed);
                                mEstimatedKeyIsMajor.store(key.isMajor, std::memory_order_relaxed);

                                if (mHarmonyAssistEnabled.load(std::memory_order_relaxed)) {
                                    mCurrentHumChord = mHarmonyEngine.suggestChord(rawNote);
                                    for (int i = 0; i < 3; ++i) {
                                        mMixerGraph->postNoteEvent(mCurrentHumChord[i], std::max(60, velocity));
                                    }
                                } else {
                                    mCurrentHumChord[0] = rawNote;
                                    mCurrentHumChord[1] = -1;
                                    mCurrentHumChord[2] = -1;
                                    mMixerGraph->postNoteEvent(rawNote, std::max(60, velocity));
                                }
                                mHumIsActive = true;
                            }
                        } else {
                            mCurrentHumNote = rawNote;
                            mHumDebounceCounter = 1;
                        }
                    } else {
                        float bend = floatNote - mCurrentHumNote;
                        if (std::abs(bend) <= 2.5f && !mEcoModeEnabled.load(std::memory_order_relaxed)) {
                            for (int i = 0; i < 3; ++i) {
                                if (mCurrentHumChord[i] != -1) {
                                    mMixerGraph->postPitchBend(mCurrentHumChord[i], bend);
                                }
                            }
                        } else {

                            for (int i = 0; i < 3; ++i) {
                                if (mCurrentHumChord[i] != -1) {
                                    mMixerGraph->postNoteEvent(mCurrentHumChord[i], 0);
                                    mCurrentHumChord[i] = -1;
                                }
                            }
                            mCurrentHumNote = rawNote;
                            mHumDebounceCounter = 1;
                            mHumIsActive = false;
                        }
                    }
                } else if (amp < 0.02f) {

                    if (mHumIsActive) {
                        for (int i = 0; i < 3; ++i) {
                            if (mCurrentHumChord[i] != -1) {
                                mMixerGraph->postNoteEvent(mCurrentHumChord[i], 0);
                                mCurrentHumChord[i] = -1;
                            }
                        }
                        mHumIsActive = false;
                    }
                    mCurrentHumNote = -1;
                    mHumDebounceCounter = 0;
                }
            },
            [this](const std::vector<float>& mfccs, float amp) {

                int64_t nowMs = (mFramesRendered * 1000) / mSampleRate.load(std::memory_order_relaxed);
                float bpm = mTempoTracker.registerOnset(nowMs);
                if (bpm > 0.0f) {
                    mEstimatedBpm.store(bpm, std::memory_order_relaxed);
                }

                int32_t pad = mTrainingPadIndex.load(std::memory_order_relaxed);
                if (pad >= 0) {

                    mBeatboxClassifier.addTrainingData(mfccs, pad);
                    mLastDetectedPad.store(pad, std::memory_order_relaxed);
                    LOGI("Trained pad %d with %d features. Total samples: %d", pad, (int)mfccs.size(), mBeatboxClassifier.getNumTrainingSamples());
                } else {

                    int32_t predictedPad = mBeatboxClassifier.predict(mfccs);
                    if (predictedPad >= 0) {
                        mLastDetectedPad.store(predictedPad, std::memory_order_relaxed);
                        int32_t drumNote = 36 + predictedPad;
                        int32_t velocity = std::min(127, static_cast<int>(amp * 127.0f * 5.0f));
                        mMixerGraph->postNoteEvent(drumNote, std::max(80, velocity));
                    }
                }
            }
        );
        mAnalyzer->setAnalyzing(mAnalysisMode.load(std::memory_order_relaxed) != 0);
    } else {
        LOGE("Failed to open input stream: %s. Voice-to-MIDI will be unavailable.", oboe::convertToText(inResult));
    }

    return true;
}

void AudioEngine::closeStream() {
    if (mOutputStream) {
        mOutputStream->stop();
        mOutputStream->close();
        mOutputStream.reset();
        LOGI("Output stream closed");
    }
    if (mInputStream) {
        mInputStream->stop();
        mInputStream->close();
        mInputStream.reset();
        LOGI("Input stream closed");
    }
}

AudioEngine::AudioTier AudioEngine::detectTier(oboe::AudioStream* stream) const {
    bool isLowLatency = stream->getPerformanceMode() == oboe::PerformanceMode::LowLatency;
    bool isExclusive  = stream->getSharingMode() == oboe::SharingMode::Exclusive;
    if (isLowLatency && isExclusive) return AudioTier::PRO;
    if (isLowLatency)               return AudioTier::LOW;
    return AudioTier::LOW;  // Any open stream is at least LOW
}

// Audio callback

oboe::DataCallbackResult AudioEngine::onAudioReady(
    oboe::AudioStream* stream,
    void* audioData,
    int32_t numFrames)
{
    if (stream->getDirection() == oboe::Direction::Input) {
        if (mAnalyzer) {
            mAnalyzer->processAudio(static_cast<const float*>(audioData), numFrames);
        }
        return oboe::DataCallbackResult::Continue;
    }


    auto* output = static_cast<float*>(audioData);
    mMixerGraph->render(output, numFrames, stream->getChannelCount());


    int32_t currentXruns = mOutputStream ? mOutputStream->getXRunCount().value() : 0;
    mXrunCount.store(currentXruns, std::memory_order_relaxed);

    mFramesRendered += numFrames;

    if (mFramesRendered % (stream->getSampleRate()) < numFrames) {
        float latencyMs = static_cast<float>(stream->getBufferSizeInFrames())
                        / static_cast<float>(stream->getSampleRate()) * 1000.0f;
        mLatencyMs.store(latencyMs, std::memory_order_relaxed);
    }

    return oboe::DataCallbackResult::Continue;
}

// Error callbacks

void AudioEngine::onErrorBeforeClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("Audio stream error before close: %s", oboe::convertToText(error));
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("Audio stream error after close: %s — attempting restart", oboe::convertToText(error));
    restart();
}

} // namespace voicedaw
