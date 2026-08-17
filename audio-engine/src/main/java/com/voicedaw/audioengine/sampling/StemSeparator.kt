package com.voicedaw.audioengine.sampling

import android.content.Context
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10

enum class StemType(val displayName: String) {
    VOCALS("Vocals"),
    DRUMS("Drums"),
    BASS("Bass"),
    OTHER("Other (Inst)")
}

data class SeparatedStems(
    val vocalsPath: String,
    val drumsPath: String,
    val bassPath: String,
    val otherPath: String
)

class StemSeparator(private val context: Context?) {

    fun separateStems(inputAudioPath: String): SeparatedStems? {
        val outputDir = context?.cacheDir ?: File(inputAudioPath).parentFile ?: File("/tmp")
        val baseName = File(inputAudioPath).nameWithoutExtension

        val vocalsFile = File(outputDir, "${baseName}_vocals.wav")
        val drumsFile = File(outputDir, "${baseName}_drums.wav")
        val bassFile = File(outputDir, "${baseName}_bass.wav")
        val otherFile = File(outputDir, "${baseName}_other.wav")

        val wav = readWavStereo(inputAudioPath) ?: return null
        val n = wav.left.size

        val mid = FloatArray(n)
        val side = FloatArray(n)
        for (i in 0 until n) {
            mid[i] = (wav.left[i] + wav.right[i]) * 0.5f
            side[i] = (wav.left[i] - wav.right[i]) * 0.5f
        }

        val sr = wav.sampleRate.toFloat()
        val bassMono = lowPass(mid, sr, cutoffHz = 150f)
        val drumsMono = highPass(mid, sr, cutoffHz = 150f)
        val vocalsMono = highPass(lowPass(mid, sr, cutoffHz = 3400f), sr, cutoffHz = 300f)

        writeWavStereo(vocalsFile, vocalsMono, vocalsMono, wav.sampleRate)
        writeWavStereo(drumsFile, drumsMono, drumsMono, wav.sampleRate)
        writeWavStereo(bassFile, bassMono, bassMono, wav.sampleRate)
        writeWavStereo(otherFile, side, side.map { -it }.toFloatArray(), wav.sampleRate)

        return SeparatedStems(
            vocalsPath = vocalsFile.absolutePath,
            drumsPath = drumsFile.absolutePath,
            bassPath = bassFile.absolutePath,
            otherPath = otherFile.absolutePath
        )
    }

    // Simple one-pole filters

    private fun lowPass(input: FloatArray, sampleRate: Float, cutoffHz: Float): FloatArray {
        val alpha = computeAlpha(sampleRate, cutoffHz)
        val out = FloatArray(input.size)
        var prev = 0f
        for (i in input.indices) {
            prev += alpha * (input[i] - prev)
            out[i] = prev
        }
        return out
    }

    private fun highPass(input: FloatArray, sampleRate: Float, cutoffHz: Float): FloatArray {
        val lp = lowPass(input, sampleRate, cutoffHz)
        val out = FloatArray(input.size)
        for (i in input.indices) out[i] = input[i] - lp[i]
        return out
    }

    private fun computeAlpha(sampleRate: Float, cutoffHz: Float): Float {
        val rc = 1.0 / (2.0 * Math.PI * cutoffHz)
        val dt = 1.0 / sampleRate
        return (dt / (rc + dt)).toFloat()
    }

    // WAV read/write

    private data class StereoWav(val left: FloatArray, val right: FloatArray, val sampleRate: Int)

    private fun readWavStereo(path: String): StereoWav? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            file.inputStream().use { input ->
                val riff = ByteArray(12)
                if (input.read(riff) != 12) return null
                if (String(riff, 0, 4) != "RIFF" || String(riff, 8, 4) != "WAVE") return null

                var sampleRate = 48000
                var numChannels = 1
                var bitsPerSample = 16
                var dataBytes: ByteArray? = null

                while (true) {
                    val chunkHeader = ByteArray(8)
                    if (input.read(chunkHeader) != 8) break
                    val chunkId = String(chunkHeader, 0, 4)
                    val chunkSize = (chunkHeader[4].toInt() and 0xFF) or
                        ((chunkHeader[5].toInt() and 0xFF) shl 8) or
                        ((chunkHeader[6].toInt() and 0xFF) shl 16) or
                        ((chunkHeader[7].toInt() and 0xFF) shl 24)

                    if (chunkId == "fmt ") {
                        val fmt = ByteArray(chunkSize)
                        input.read(fmt)
                        numChannels = (fmt[2].toInt() and 0xFF) or ((fmt[3].toInt() and 0xFF) shl 8)
                        sampleRate = (fmt[4].toInt() and 0xFF) or ((fmt[5].toInt() and 0xFF) shl 8) or
                            ((fmt[6].toInt() and 0xFF) shl 16) or ((fmt[7].toInt() and 0xFF) shl 24)
                        bitsPerSample = (fmt[14].toInt() and 0xFF) or ((fmt[15].toInt() and 0xFF) shl 8)
                    } else if (chunkId == "data") {
                        dataBytes = ByteArray(chunkSize)
                        input.read(dataBytes)
                        break
                    } else {
                        input.skip(chunkSize.toLong())
                    }
                }

                if (dataBytes == null || bitsPerSample != 16 || (numChannels != 1 && numChannels != 2)) return null
                val data = dataBytes
                val frameCount = data.size / 2 / numChannels
                val left = FloatArray(frameCount)
                val right = FloatArray(frameCount)
                for (i in 0 until frameCount) {
                    if (numChannels == 1) {
                        val s = sample16(data, i * 2)
                        left[i] = s; right[i] = s
                    } else {
                        left[i] = sample16(data, i * 4)
                        right[i] = sample16(data, i * 4 + 2)
                    }
                }
                StereoWav(left, right, sampleRate)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sample16(data: ByteArray, byteOffset: Int): Float {
        val s = ((data[byteOffset + 1].toInt() shl 8) or (data[byteOffset].toInt() and 0xFF)).toShort()
        return s / 32768f
    }

    private fun writeWavStereo(outFile: File, left: FloatArray, right: FloatArray, sampleRate: Int) {
        val numChannels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataBytes = left.size.toLong() * blockAlign

        DataOutputStream(FileOutputStream(outFile)).use { dos ->
            dos.writeBytes("RIFF")
            writeLe32(dos, (36 + dataBytes).toInt())
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            writeLe32(dos, 16)
            writeLe16(dos, 1)
            writeLe16(dos, numChannels)
            writeLe32(dos, sampleRate)
            writeLe32(dos, byteRate)
            writeLe16(dos, blockAlign)
            writeLe16(dos, bitsPerSample)
            dos.writeBytes("data")
            writeLe32(dos, dataBytes.toInt())

            for (i in left.indices) {
                writeLe16(dos, (left[i].coerceIn(-1f, 1f) * 32767f).toInt())
                writeLe16(dos, (right[i].coerceIn(-1f, 1f) * 32767f).toInt())
            }
        }
    }

    private fun writeLe32(dos: DataOutputStream, v: Int) {
        dos.write(v and 0xFF)
        dos.write((v shr 8) and 0xFF)
        dos.write((v shr 16) and 0xFF)
        dos.write((v shr 24) and 0xFF)
    }

    private fun writeLe16(dos: DataOutputStream, v: Int) {
        dos.write(v and 0xFF)
        dos.write((v shr 8) and 0xFF)
    }
}
