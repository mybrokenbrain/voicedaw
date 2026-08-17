package com.voicedaw.projectformat

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class AudioClipManifest(
    val clipId: String = UUID.randomUUID().toString(),
    val name: String,
    val audioFilePath: String,
    val startFrameInProject: Long,
    val durationFrames: Long,
    val offsetFramesInFile: Long = 0,
    val fadeInFrames: Long = 0,
    val fadeOutFrames: Long = 0
)
