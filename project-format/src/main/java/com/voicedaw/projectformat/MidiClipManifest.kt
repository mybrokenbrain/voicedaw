package com.voicedaw.projectformat

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class MidiClipManifest(
    val clipId: String = UUID.randomUUID().toString(),
    val name: String,
    val midiFilePath: String,
    val startFrameInProject: Long,
    val durationFrames: Long
)
