package com.voicedaw.audioengine.export

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AudioExporter {
    private const val TAG = "AudioExporter"
    private const val TIMEOUT_US = 10000L

    suspend fun convertWavToAac(wavFile: File, m4aFile: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            Result.success(Unit)
        }
    }
}
