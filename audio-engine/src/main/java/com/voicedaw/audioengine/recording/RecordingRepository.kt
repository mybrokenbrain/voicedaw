package com.voicedaw.audioengine.recording

import android.content.Context
import android.media.AudioRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

class RecordingRepository(private val context: Context) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedFilePath = MutableStateFlow<String?>(null)
    val recordedFilePath: StateFlow<String?> = _recordedFilePath.asStateFlow()

    private val _amplitudeSamples = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeSamples: StateFlow<List<Float>> = _amplitudeSamples.asStateFlow()

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var rawPcmFile: File? = null
    @Volatile private var totalBytesWritten: Long = 0

    suspend fun startRecording(projectId: String, trackIndex: Int): Result<String> {
        return withContext(Dispatchers.IO) {
            Result.success("")
        }
    }

    suspend fun stopRecording(): Result<String> {
        return withContext(Dispatchers.IO) {
            Result.success("")
        }
    }

    fun cancelRecording() {
        _isRecording.value = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        rawPcmFile?.delete()
        rawPcmFile = null
        _amplitudeSamples.value = emptyList()
    }

    private fun writeWavFile(rawPcm: File, wavOut: File, dataBytes: Long) {
        val byteRate = 2 * 48000 * 16 / 8
        val blockAlign = 2 * 16 / 8
        DataOutputStream(FileOutputStream(wavOut)).use { dos ->
            dos.writeBytes("RIFF")
            writeLe32(dos, (36 + dataBytes).toInt())
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            writeLe32(dos, 16)
            writeLe16(dos, 1)
            writeLe16(dos, 2)
            writeLe32(dos, 48000)
            writeLe32(dos, byteRate)
            writeLe16(dos, blockAlign)
            writeLe16(dos, 16)
            dos.writeBytes("data")
            writeLe32(dos, dataBytes.toInt())
        }

        RandomAccessFile(wavOut, "rw").use { raf ->
            raf.seek(wavOut.length())
            FileInputStream(rawPcm).use { input ->
                FileOutputStream(raf.fd).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun writeLe32(dos: DataOutputStream, value: Int) {
        dos.write(value and 255)
        dos.write((value shr 8) and 255)
        dos.write((value shr 16) and 255)
        dos.write((value shr 24) and 255)
    }

    private fun writeLe16(dos: DataOutputStream, value: Int) {
        dos.write(value and 255)
        dos.write((value shr 8) and 255)
    }
}
