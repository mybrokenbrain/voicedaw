package com.voicedaw.projectformat

import com.squareup.moshi.JsonClass
import java.util.UUID

@JsonClass(generateAdapter = true)
data class ProjectManifest(
    val version: Int = 1,
    val projectId: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val bpm: Float = 120.0f,
    val timeSignatureNumerator: Int = 4,
    val timeSignatureDenominator: Int = 4,
    val sampleRate: Int = 48000,
    val bitDepth: Int = 24,
    val tracks: List<TrackManifest> = emptyList()
) {
    companion object {
        const val MANIFEST_FILE = "manifest.json"
        const val MANIFEST_VERSION = 1
    }
}
