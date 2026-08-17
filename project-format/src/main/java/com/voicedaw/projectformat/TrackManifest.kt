package com.voicedaw.projectformat

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class TrackManifest(
    val trackId: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val index: Int,
    val gain: Float = 1.0f,
    val pan: Float = 0.0f,
    val muted: Boolean = false,
    val soloed: Boolean = false,
    val clips: List<AudioClipManifest> = emptyList(),
    val midiClips: List<MidiClipManifest> = emptyList()
)
