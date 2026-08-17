package com.voicedaw.audioengine.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TrackRecorder(
    private val context: Context,
    val trackIndex: Int,
    private val projectId: String,
    private val inputSource: AudioTrack.InputSource = AudioTrack.InputSource.INTERNAL_MIC
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitudeSamples = MutableStateFlow<List<Float>>(emptyList())
    val amplitudeSamples: StateFlow<List<Float>> = _amplitudeSamples.asStateFlow()

    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var rawPcmFile: File? = null
    @Volatile private var totalBytesWritten: Long = 0
    @Volatile private var punchOutSampleTarget: Long = -1L
    private var recordingJob: Job? = null

    companion object {
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BYTES_PER_SAMPLE = 2 // 16-bit mono
    }

    suspend fun startRecording(punchInMs: Long = 0L, punchOutMs: Long = -1L): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    return@withContext Result.failure(
                        SecurityException("RECORD_AUDIO permission not granted; cannot start track $trackIndex")
                    )
                }

                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                if (minBufSize == AudioRecord.ERROR || minBufSize == AudioRecord.ERROR_BAD_VALUE) {
                    return@withContext Result.failure(
                        IOException("Unable to determine AudioRecord buffer size for track $trackIndex")
                    )
                }

                val audioSource = when (inputSource) {
                    AudioTrack.InputSource.USB_MIC -> MediaRecorder.AudioSource.UNPROCESSED
                    AudioTrack.InputSource.INTERNAL_MIC -> MediaRecorder.AudioSource.MIC
                }

                val record = AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    minBufSize * 4
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    return@withContext Result.failure(
                        IOException("AudioRecord failed to initialize for track $trackIndex")
                    )
                }

                val rawFile = File(context.cacheDir, "track_${trackIndex}_${System.currentTimeMillis()}.pcm")
                rawPcmFile = rawFile
                totalBytesWritten = 0
                punchOutSampleTarget = if (punchOutMs >= 0) bytesForMs(punchOutMs) else -1L
                val punchInBytes = bytesForMs(punchInMs)

                audioRecord = record
                _amplitudeSamples.value = emptyList()
                _isRecording.value = true
                record.startRecording()

                recordingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    runCaptureLoop(record, rawFile, punchInBytes)
                }

                Result.success(rawFile.absolutePath)
            } catch (e: Exception) {
                _isRecording.value = false
                Result.failure(e)
            }
        }
    }

    private suspend fun runCaptureLoop(record: AudioRecord, rawFile: File, punchInBytes: Long) {
        var bytesSeen = 0L
        val shortBuffer = ShortArray(record.bufferSizeInFrames.coerceAtLeast(1024))
        val byteBuffer = ByteArray(shortBuffer.size * 2)

        try {
            FileOutputStream(rawFile).use { fos ->
                while (kotlinx.coroutines.currentCoroutineContext().isActive && _isRecording.value) {
                    val read = record.read(shortBuffer, 0, shortBuffer.size)
                    if (read <= 0) continue

                    for (i in 0 until read) {
                        val s = shortBuffer[i].toInt()
                        byteBuffer[i * 2] = (s and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    val readBytes = read * 2

                    bytesSeen += readBytes
                    if (bytesSeen > punchInBytes) {
                        val skipInThisChunk = (readBytes - (bytesSeen - punchInBytes)).toInt().coerceIn(0, readBytes)
                        val writeLen = readBytes - skipInThisChunk
                        if (writeLen > 0) {
                            fos.write(byteBuffer, skipInThisChunk, writeLen)
                            totalBytesWritten += writeLen
                        }
                    }

                    var peak = 0
                    for (i in 0 until read) {
                        val a = abs(shortBuffer[i].toInt())
                        if (a > peak) peak = a
                    }
                    val normalizedPeak = (peak / 32767f).coerceIn(0f, 1f)
                    _amplitudeSamples.value = _amplitudeSamples.value + normalizedPeak

                    val target = punchOutSampleTarget
                    if (target >= 0 && totalBytesWritten >= target) {
                        _isRecording.value = false
                        break
                    }
                }
            }
        } catch (_: IOException) {
        }
    }

    suspend fun stopRecording(isFirstTake: Boolean = false): Result<AudioClip> {
        return withContext(Dispatchers.IO) {
            try {
                _isRecording.value = false
                recordingJob?.join()
                recordingJob = null

                val record = audioRecord
                audioRecord = null
                record?.let {
                    try {
                        it.stop()
                    } catch (_: IllegalStateException) {
                    }
                    it.release()
                }

                val rawFile = rawPcmFile
                rawPcmFile = null

                if (rawFile == null || !rawFile.exists() || totalBytesWritten <= 0) {
                    rawFile?.delete()
                    return@withContext Result.failure(
                        IOException("No audio captured for track $trackIndex")
                    )
                }

                val wavFile = File(
                    context.filesDir,
                    "project_${projectId}_track_${trackIndex}_${System.currentTimeMillis()}.wav"
                )
                writeWavFile(rawFile, wavFile, totalBytesWritten)
                rawFile.delete()

                val clip = AudioClip(
                    clipId = System.currentTimeMillis(),
                    trackIndex = trackIndex,
                    filePath = wavFile.absolutePath
                )
                Result.success(clip)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun cancel() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        audioRecord = null
        rawPcmFile?.delete()
        rawPcmFile = null
        totalBytesWritten = 0
        _amplitudeSamples.value = emptyList()
    }

    private fun bytesForMs(ms: Long): Long {
        if (ms <= 0L) return 0L
        return (ms * SAMPLE_RATE / 1000L) * BYTES_PER_SAMPLE
    }

    private fun writeWavFile(rawPcm: File, wavOut: File, dataBytes: Long) {

        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = SAMPLE_RATE * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        DataOutputStream(FileOutputStream(wavOut)).use { dos ->
            dos.writeBytes("RIFF")
            writeLe32(dos, (36 + dataBytes).toInt())
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            writeLe32(dos, 16)
            writeLe16(dos, 1)
            writeLe16(dos, numChannels)
            writeLe32(dos, SAMPLE_RATE)
            writeLe32(dos, byteRate)
            writeLe16(dos, blockAlign)
            writeLe16(dos, bitsPerSample)
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

    private fun writeLe32(dos: DataOutputStream, v: Int) {
        dos.write(v and 255)
        dos.write((v shr 8) and 255)
        dos.write((v shr 16) and 255)
        dos.write((v shr 24) and 255)
    }

    private fun writeLe16(dos: DataOutputStream, v: Int) {
        dos.write(v and 255)
        dos.write((v shr 8) and 255)
    }
}
