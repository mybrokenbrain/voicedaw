package com.voicedaw.audioengine.sampling

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class VocalPadManager(private val context: Context?) {

    private val _pads = MutableStateFlow<List<PadSampleData>>(
        List(16) { index -> PadSampleData(padIndex = index) }
    )
    val pads: StateFlow<List<PadSampleData>> = _pads.asStateFlow()

    fun loadCustomSample(context: Context?, padIndex: Int, uri: Uri) {
        if (padIndex !in 0..15 || context == null) return
        
        try {
            val cacheDir = context.cacheDir
            val destFile = File(cacheDir, "pad_sample_$padIndex.wav")
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            val currentPads = _pads.value.toMutableList()
            currentPads[padIndex] = currentPads[padIndex].copy(samplePath = destFile.absolutePath)
            _pads.value = currentPads
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun updatePad(padData: PadSampleData) {
        val currentPads = _pads.value.toMutableList()
        if (padData.padIndex in currentPads.indices) {
            currentPads[padData.padIndex] = padData
            _pads.value = currentPads
        }
    }

    fun normalizePad(padIndex: Int) {
        if (padIndex !in 0..15) return
        val currentPads = _pads.value.toMutableList()
        val pad = currentPads.getOrNull(padIndex) ?: return
        if (pad.samplePath.isEmpty()) return

        val peak = readWavPeakAbs(pad.samplePath) ?: return
        if (peak <= 0.0001f) return

        val targetPeak = 0.944f // -0.5 dBFS
        val gainLinear = targetPeak / peak
        val gainDb = (20.0 * kotlin.math.log10(gainLinear.toDouble())).toFloat()
            .coerceIn(-12f, 12f)

        currentPads[padIndex] = pad.copy(gainDb = gainDb)
        _pads.value = currentPads
    }

    private fun readWavPeakAbs(path: String): Float? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(44)
                if (input.read(header) != 44) return null
                if (String(header, 0, 4) != "RIFF" || String(header, 8, 4) != "WAVE") return null

                var peak = 0
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    var i = 0
                    while (i + 1 < n) {
                        val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                        val abs = kotlin.math.abs(sample)
                        if (abs > peak) peak = abs
                        i += 2
                    }
                }
                peak / 32768f
            }
        } catch (e: Exception) {
            null
        }
    }

    fun resampleToPad(padIndex: Int, samplePath: String, name: String = "Resample $padIndex") {
        if (padIndex !in 0..15) return
        val currentPads = _pads.value.toMutableList()
        currentPads[padIndex] = currentPads[padIndex].copy(
            samplePath = samplePath,
            name = name
        )
        _pads.value = currentPads
    }

    private val activePlayingPads = mutableSetOf<Int>()

    fun triggerPad(padIndex: Int, velocity: Float) {
        if (padIndex !in 0..15) return
        val pad = _pads.value.getOrNull(padIndex) ?: return
        
        if (pad.chokeGroup > 0) {
            val toChoke = _pads.value.filter { it.padIndex != padIndex && it.chokeGroup == pad.chokeGroup }
            for (chokedPad in toChoke) {
                activePlayingPads.remove(chokedPad.padIndex)
            }
        }
        
        if (velocity > 0f) {
            activePlayingPads.add(padIndex)
        } else {
            activePlayingPads.remove(padIndex)
        }
    }

    fun isPadPlaying(padIndex: Int): Boolean {
        return activePlayingPads.contains(padIndex)
    }

    fun handleMidiNote(midiNote: Int, velocity: Float): Boolean {
        if (midiNote in 36..51) {
            val padIndex = midiNote - 36
            triggerPad(padIndex, velocity)
            return true
        }
        return false
    }
}
