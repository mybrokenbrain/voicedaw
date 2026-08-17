/**
 * AudioEngineJni.cpp — JNI bridge between Kotlin and the C++ AudioEngine.
 *
 * DESIGN: The JNI layer is kept intentionally thin.
 * - All logic lives in AudioEngine.cpp / MixerGraph.cpp
 * - JNI methods just forward to AudioEngine methods and marshal types
 * - No audio processing happens in this file
 *
 * Kotlin package: com.voicedaw.audioengine.AudioEngineJni
 */

#include <jni.h>
#include <cstdint>
#include <vector>
#include <algorithm>
#include <string>
#include "engine/AudioEngine.h"
#include "dsp/WavWriter.h"
#include <android/log.h>

#define LOG_TAG "VoiceDaw/JNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using voicedaw::AudioEngine;

// ─── Helper: convert native pointer stored as jlong ───────────────────────────

static AudioEngine* nativeHandle(jlong handle) {
    return reinterpret_cast<AudioEngine*>(handle);
}

extern "C" {

// ── com.voicedaw.audioengine.AudioEngineJni native methods ────────────────────

JNIEXPORT jlong JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeCreate(JNIEnv* /*env*/, jclass /*clazz*/) {
    auto* engine = new AudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeDestroy(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    delete nativeHandle(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeStart(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return static_cast<jboolean>(nativeHandle(handle)->start());
}

JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeStop(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    nativeHandle(handle)->stop();
}

JNIEXPORT jfloat JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetLatencyMs(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return nativeHandle(handle)->getLatencyMs();
}

JNIEXPORT jint JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetTier(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return static_cast<jint>(nativeHandle(handle)->getTier());
}

JNIEXPORT jlong JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetXrunCount(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return static_cast<jlong>(nativeHandle(handle)->getXrunCount());
}

JNIEXPORT jint JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetSampleRate(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return nativeHandle(handle)->getSampleRate();
}

JNIEXPORT jfloat JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetPitchHz(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return nativeHandle(handle)->getPitchHz();
}

JNIEXPORT jfloat JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetPitchAmp(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return nativeHandle(handle)->getPitchAmp();
}



JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetAnalysisMode(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle, jint mode)
{
    nativeHandle(handle)->setAnalysisMode(mode);
}

JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetBeatboxTrainingPad(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle, jint padIndex)
{
    nativeHandle(handle)->setBeatboxTrainingPad(padIndex);
}

JNIEXPORT jint JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetLastDetectedPad(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    return nativeHandle(handle)->getLastDetectedPad();
}

// ── Harmony & Tempo (M4) ──────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetHarmonyAssistEnabled(JNIEnv *env, jclass clazz, jlong handle, jboolean enabled) {
    auto* engine = reinterpret_cast<voicedaw::AudioEngine*>(handle);
    if (engine) engine->setHarmonyAssistEnabled(enabled);
}
JNIEXPORT jfloat JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetEstimatedBpm(JNIEnv *env, jclass clazz, jlong handle) {
    auto* engine = reinterpret_cast<voicedaw::AudioEngine*>(handle);
    return engine ? engine->getEstimatedBpm() : 0.0f;
}
JNIEXPORT jint JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetEstimatedKeyRoot(JNIEnv *env, jclass clazz, jlong handle) {
    auto* engine = reinterpret_cast<voicedaw::AudioEngine*>(handle);
    return engine ? engine->getEstimatedKeyRoot() : 0;
}
JNIEXPORT jboolean JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetEstimatedKeyIsMajor(JNIEnv *env, jclass clazz, jlong handle) {
    auto* engine = reinterpret_cast<voicedaw::AudioEngine*>(handle);
    return engine ? engine->getEstimatedKeyIsMajor() : true;
}

// ── Optimization & Stabilization (M6) ─────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetPerformanceTier(JNIEnv *env, jclass clazz, jlong handle, jint tier) {
    auto* engine = reinterpret_cast<voicedaw::AudioEngine*>(handle);
    if (handle) nativeHandle(handle)->setPerformanceTier(tier);
}

JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetOutputDeviceId(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle, jint deviceId) {
    if (handle) nativeHandle(handle)->setOutputDeviceId(deviceId);
}

// ── Note Events ──────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeNoteOn(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle, jint midiNote, jint velocity)
{
    nativeHandle(handle)->getMixerGraph().postNoteEvent(midiNote, velocity);
}

JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeNoteOff(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle, jint midiNote)
{
    nativeHandle(handle)->getMixerGraph().postNoteEvent(midiNote, 0);
}

JNIEXPORT void JNICALL
Java_com_voicedaw_audioengine_AudioEngineJni_nativeAllNotesOff(
    JNIEnv* /*env*/, jclass /*clazz*/, jlong handle)
{
    if (handle) nativeHandle(handle)->getMixerGraph().allNotesOff();
}

// ── Mixer / Track (M5) ────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTransportState(JNIEnv*, jclass, jlong handle, jint state) {
    nativeHandle(handle)->getMixerGraph().setTransportState(static_cast<voicedaw::TransportClock::State>(state));
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeLoadTrackClip(JNIEnv* env, jclass, jlong handle, jint trackIndex, jstring pathStr) {
    const char* path = env->GetStringUTFChars(pathStr, nullptr);
    nativeHandle(handle)->getMixerGraph().loadTrackClip(trackIndex, std::string(path));
    env->ReleaseStringUTFChars(pathStr, path);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTrackGain(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat gain) {
    nativeHandle(handle)->getMixerGraph().setTrackGain(trackIndex, gain);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTrackPan(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat pan) {
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).setPanNorm(pan);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTrackMute(JNIEnv*, jclass, jlong handle, jint trackIndex, jboolean mute) {
    nativeHandle(handle)->getMixerGraph().muteTrack(trackIndex, mute);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTrackSolo(JNIEnv*, jclass, jlong handle, jint trackIndex, jboolean solo) {
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).setSoloed(solo);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTrackReverbSend(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat send) {
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).setReverbSend(send);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTrackDelaySend(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat send) {
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).setDelaySend(send);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetTrackSubmixBus(JNIEnv*, jclass, jlong handle, jint trackIndex, jint busId) {
    nativeHandle(handle)->getMixerGraph().setTrackSubmixBus(trackIndex, busId);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetSidechainEnabled(JNIEnv*, jclass, jlong handle, jint trackIndex, jboolean enabled, jint sourceTrackIndex) {
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).setSidechainEnabled(enabled);
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).setSidechainSource(sourceTrackIndex);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetPerformanceFx(JNIEnv*, jclass, jlong handle, jint fxType, jboolean hold) {
    nativeHandle(handle)->getMixerGraph().setPerformanceFx(fxType, hold);
}

// ── EQ (M5) ───────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetEqEnabled(JNIEnv*, jclass, jlong handle, jint trackIndex, jboolean enabled) {
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).eq().setEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetEqLowShelf(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat freq, jfloat gain) {
    auto& eq = nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).eq();
    eq.setLowShelfFreq(freq); eq.setLowShelfGain(gain);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetEqMidBand(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat freq, jfloat gain, jfloat q) {
    auto& eq = nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).eq();
    eq.setMidFreq(freq); eq.setMidGain(gain); eq.setMidQ(q);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetEqHighShelf(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat freq, jfloat gain) {
    auto& eq = nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).eq();
    eq.setHighShelfFreq(freq); eq.setHighShelfGain(gain);
}

// ── Compressor (M5) ───────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetCompEnabled(JNIEnv*, jclass, jlong handle, jint trackIndex, jboolean enabled) {
    nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).comp().setEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetCompParams(JNIEnv*, jclass, jlong handle, jint trackIndex, jfloat thresh, jfloat ratio, jfloat attack, jfloat release) {
    auto& comp = nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).comp();
    comp.setThresholdDb(thresh); comp.setRatio(ratio); comp.setAttackMs(attack); comp.setReleaseMs(release);
}
JNIEXPORT jfloat JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetCompGainReduction(JNIEnv*, jclass, jlong handle, jint trackIndex) {
    return nativeHandle(handle)->getMixerGraph().getTrack(trackIndex).getGainReductionDb();
}

// ── Master Reverb (M5) ────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetReverbEnabled(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    nativeHandle(handle)->getMixerGraph().getReverb().setEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetReverbParams(JNIEnv*, jclass, jlong handle, jfloat room, jfloat damp, jfloat wetDry, jfloat width) {
    auto& rev = nativeHandle(handle)->getMixerGraph().getReverb();
    rev.setRoomSize(room); rev.setDamping(damp); rev.setWetDry(wetDry); rev.setWidth(width);
}

// ── Master FX Chain (M7) ───────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterSaturationEnabled(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    nativeHandle(handle)->getMixerGraph().setMasterSaturationEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterSaturationDrive(JNIEnv*, jclass, jlong handle, jfloat drive) {
    nativeHandle(handle)->getMixerGraph().setMasterSaturationDrive(drive);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterDelayEnabled(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    nativeHandle(handle)->getMixerGraph().setMasterDelayEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterDelayParams(JNIEnv*, jclass, jlong handle, jfloat timeMs, jfloat feedback, jfloat mix) {
    nativeHandle(handle)->getMixerGraph().setMasterDelayParams(timeMs, feedback, mix);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterGateEnabled(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    nativeHandle(handle)->getMixerGraph().setMasterGateEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterGateParams(JNIEnv*, jclass, jlong handle, jfloat thresholdDb, jfloat attackMs, jfloat releaseMs) {
    nativeHandle(handle)->getMixerGraph().setMasterGateParams(thresholdDb, attackMs, releaseMs);
}

// ── Export (M6) ───────────────────────────────────────────────────────────
JNIEXPORT jboolean JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeBounceToWav(JNIEnv* env, jclass, jlong handle, jstring pathStr) {
    auto* engine = nativeHandle(handle);
    const char* path = env->GetStringUTFChars(pathStr, nullptr);
    
    auto& mixer = engine->getMixerGraph();
    int64_t totalFrames = mixer.getLongestClipFrames();
    
    int32_t sampleRate = engine->getSampleRate();
    if (sampleRate == 0) sampleRate = 48000;
    
    totalFrames += sampleRate; // 1s tail
    
    // Stop engine for offline render
    engine->stop();
    mixer.resetForOfflineRender();

    int32_t channels = 2;
    std::vector<float> interleavedOutput;
    interleavedOutput.reserve(totalFrames * channels);

    int32_t maxChunk = 2048;
    std::vector<float> chunkBuffer(maxChunk * channels);

    int64_t framesRendered = 0;
    while (framesRendered < totalFrames) {
        int32_t framesThisChunk = std::min(static_cast<int64_t>(maxChunk), totalFrames - framesRendered);
        mixer.render(chunkBuffer.data(), framesThisChunk, channels);
        interleavedOutput.insert(interleavedOutput.end(), chunkBuffer.begin(), chunkBuffer.begin() + framesThisChunk * channels);
        framesRendered += framesThisChunk;
    }

    bool success = voicedaw::WavWriter::writeWav16(std::string(path), interleavedOutput, sampleRate, channels);
    
    env->ReleaseStringUTFChars(pathStr, path);
    return success;
}

// ── Phase 3 Mastering ───────────────────────────────────────────────────────
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeLoadReferenceTrack(JNIEnv* env, jclass, jlong handle, jstring pathStr) {
    const char* path = env->GetStringUTFChars(pathStr, nullptr);
    nativeHandle(handle)->getMixerGraph().loadReferenceTrack(std::string(path));
    env->ReleaseStringUTFChars(pathStr, path);
}

JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetListenToReference(JNIEnv*, jclass, jlong handle, jboolean listen) {
    nativeHandle(handle)->getMixerGraph().setListenToReference(listen);
}

JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterEqEnabled(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    if (m) m->eq().setEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterEqLowShelf(JNIEnv*, jclass, jlong handle, jfloat freq, jfloat gain) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    if (m) { m->eq().setLowShelfFreq(freq); m->eq().setLowShelfGain(gain); }
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterEqMidBand(JNIEnv*, jclass, jlong handle, jfloat freq, jfloat gain, jfloat q) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    if (m) { m->eq().setMidFreq(freq); m->eq().setMidGain(gain); m->eq().setMidQ(q); }
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterEqHighShelf(JNIEnv*, jclass, jlong handle, jfloat freq, jfloat gain) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    if (m) { m->eq().setHighShelfFreq(freq); m->eq().setHighShelfGain(gain); }
}

JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterCompEnabled(JNIEnv*, jclass, jlong handle, jboolean enabled) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    if (m) m->comp().setEnabled(enabled);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSetMasterCompParams(JNIEnv*, jclass, jlong handle, jfloat thresh, jfloat ratio, jfloat attack, jfloat release) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    if (m) { m->comp().setThresholdDb(thresh); m->comp().setRatio(ratio); m->comp().setAttackMs(attack); m->comp().setReleaseMs(release); }
}
JNIEXPORT jfloat JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetMasterCompGainReduction(JNIEnv*, jclass, jlong handle) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    return m ? m->comp().getGainReductionDb() : 0.0f;
}

JNIEXPORT jfloat JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetIntegratedLUFS(JNIEnv*, jclass, jlong handle) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    return m ? m->getIntegratedLUFS() : -70.0f;
}

JNIEXPORT jfloat JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetTruePeak(JNIEnv*, jclass, jlong handle) {
    auto* m = nativeHandle(handle)->getMixerGraph().getMastering();
    return m ? m->getTruePeak() : -100.0f;
}

JNIEXPORT jfloat JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeGetPlaybackPosition(JNIEnv*, jclass, jlong handle) {
    return nativeHandle(handle)->getPlaybackPositionMs();
}

JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeSaveBeatboxModel(JNIEnv* env, jclass, jlong handle, jstring path) {
    const char* cPath = env->GetStringUTFChars(path, nullptr);
    nativeHandle(handle)->saveBeatboxModel(cPath);
    env->ReleaseStringUTFChars(path, cPath);
}

JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeLoadBeatboxModel(JNIEnv* env, jclass, jlong handle, jstring path) {
    const char* cPath = env->GetStringUTFChars(path, nullptr);
    nativeHandle(handle)->loadBeatboxModel(cPath);
    env->ReleaseStringUTFChars(path, cPath);
}

// ── Vocal Pad Grid Native Bindings ──────────────────────────────────────────
JNIEXPORT jboolean JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeLoadPadSample(JNIEnv* env, jclass, jlong handle, jint padIndex, jstring pathStr) {
    const char* path = env->GetStringUTFChars(pathStr, nullptr);
    bool ok = nativeHandle(handle)->getMixerGraph().loadPadSample(padIndex, std::string(path));
    env->ReleaseStringUTFChars(pathStr, path);
    return static_cast<jboolean>(ok);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeConfigurePad(JNIEnv*, jclass, jlong handle, jint padIndex, jfloat startMs, jfloat endMs, jint pitchSemitones, jint fineTuneCents, jfloat gainDb, jfloat fadeInMs, jfloat fadeOutMs, jboolean reverse, jint playbackMode, jint chokeGroup) {
    nativeHandle(handle)->getMixerGraph().configurePad(padIndex, startMs, endMs, pitchSemitones, fineTuneCents, gainDb, fadeInMs, fadeOutMs, reverse, playbackMode, chokeGroup);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeTriggerPad(JNIEnv*, jclass, jlong handle, jint padIndex, jfloat velocity, jint extraSemitones) {
    nativeHandle(handle)->getMixerGraph().triggerPad(padIndex, velocity, extraSemitones);
}
JNIEXPORT void JNICALL Java_com_voicedaw_audioengine_AudioEngineJni_nativeReleasePad(JNIEnv*, jclass, jlong handle, jint padIndex) {
    nativeHandle(handle)->getMixerGraph().releasePad(padIndex);
}

} // extern "C"

