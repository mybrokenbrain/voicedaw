cmake_minimum_required(VERSION 3.22.1)
project("voicedaw-engine")

find_package(oboe REQUIRED CONFIG)

add_library(
    voicedaw-engine
    SHARED
    jni/AudioEngineJni.cpp
    engine/AudioEngine.cpp
    engine/MixerGraph.cpp
)

target_include_directories(voicedaw-engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/engine
    ${CMAKE_CURRENT_SOURCE_DIR}/dsp
    ${CMAKE_CURRENT_SOURCE_DIR}/analysis
    ${CMAKE_CURRENT_SOURCE_DIR}/fx
    ${CMAKE_CURRENT_SOURCE_DIR}/host
)

target_link_libraries(
    voicedaw-engine
    oboe::oboe
    log
)
