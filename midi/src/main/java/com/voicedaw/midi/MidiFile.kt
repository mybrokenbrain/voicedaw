package com.voicedaw.midi

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object MidiFile {

    private const val TAG = "MidiFile"

    const val TICKS_PER_BEAT = 480
    private const val DEFAULT_BPM = 120
    private const val MICROSECONDS_PER_MINUTE = 60_000_000L
    private const val NOTE_ON  = 0x90
    private const val NOTE_OFF = 0x80

    // Export

    fun export(notes: List<PianoNote>, outputFile: File, bpm: Float = DEFAULT_BPM.toFloat()) {
        FileOutputStream(outputFile).use { fos ->
            writeToStream(notes, fos, bpm)
        }
        Log.i(TAG, "Exported ${notes.size} notes → ${outputFile.path}")
    }

    fun writeToStream(notes: List<PianoNote>, out: OutputStream, bpm: Float = DEFAULT_BPM.toFloat()) {
        val usPerBeat = (MICROSECONDS_PER_MINUTE / bpm).toLong()

        val trackBytes = mutableListOf<Byte>()

        trackBytes.addAll(varLen(0))
        trackBytes.add(0xFF.toByte())
        trackBytes.add(0x51.toByte())
        trackBytes.add(0x03.toByte())
        trackBytes.add(((usPerBeat shr 16) and 0xFF).toByte())
        trackBytes.add(((usPerBeat shr 8)  and 0xFF).toByte())
        trackBytes.add((usPerBeat          and 0xFF).toByte())

        data class RawEvent(val tick: Long, val status: Int, val note: Int, val velocity: Int)
        val events = mutableListOf<RawEvent>()
        for (note in notes) {
            val onTick  = (note.startBeat * TICKS_PER_BEAT).toLong()
            val offTick = ((note.startBeat + note.durationBeats) * TICKS_PER_BEAT).toLong()
            events.add(RawEvent(onTick,  NOTE_ON,  note.midiNote, 100))
            events.add(RawEvent(offTick, NOTE_OFF, note.midiNote, 0))
        }
        events.sortWith(compareBy({ it.tick }, { it.status }))

        var currentTick = 0L
        for (ev in events) {
            val delta = ev.tick - currentTick
            currentTick = ev.tick
            trackBytes.addAll(varLen(delta))
            trackBytes.add(ev.status.toByte())
            trackBytes.add(ev.note.toByte())
            trackBytes.add(ev.velocity.toByte())
        }

        trackBytes.addAll(varLen(0))
        trackBytes.add(0xFF.toByte())
        trackBytes.add(0x2F.toByte())
        trackBytes.add(0x00.toByte())

        val trackLen = trackBytes.size

        // Header chunk
        out.write("MThd".toByteArray())
        out.writeInt32(6)
        out.writeInt16(0)
        out.writeInt16(1)
        out.writeInt16(TICKS_PER_BEAT)

        // Track chunk
        out.write("MTrk".toByteArray())
        out.writeInt32(trackLen)
        out.write(trackBytes.toByteArray())
    }

    fun exportMultiTrack(notes: List<PianoNote>, out: OutputStream, bpm: Float = DEFAULT_BPM.toFloat()) {
        val usPerBeat = (MICROSECONDS_PER_MINUTE / bpm).toLong()
        val channels = notes.groupBy { it.midiChannel }.toSortedMap()
        val numTracks = channels.size

        val trackChunks = channels.entries.mapIndexed { i, (channel, channelNotes) ->
            buildTrackBytes(channelNotes, channel, usPerBeat, includeTempoEvent = (i == 0))
        }

        // Header: Type 1
        out.write("MThd".toByteArray())
        out.writeInt32(6)
        out.writeInt16(1)
        out.writeInt16(numTracks)
        out.writeInt16(TICKS_PER_BEAT)

        for (chunk in trackChunks) {
            out.write("MTrk".toByteArray())
            out.writeInt32(chunk.size)
            out.write(chunk)
        }
        Log.i(TAG, "Exported ${notes.size} notes across ${numTracks} tracks (Type 1)")
    }

    fun exportMultiTrack(notes: List<PianoNote>, outputFile: File, bpm: Float = DEFAULT_BPM.toFloat()) {
        FileOutputStream(outputFile).use { exportMultiTrack(notes, it, bpm) }
    }

    private fun buildTrackBytes(
        notes: List<PianoNote>,
        channel: Int,
        usPerBeat: Long,
        includeTempoEvent: Boolean,
    ): ByteArray {
        val ch = (channel - 1) and 0x0F
        val trackBytes = mutableListOf<Byte>()

        if (includeTempoEvent) {
            trackBytes.addAll(varLen(0))
            trackBytes.add(0xFF.toByte())
            trackBytes.add(0x51.toByte())
            trackBytes.add(0x03.toByte())
            trackBytes.add(((usPerBeat shr 16) and 0xFF).toByte())
            trackBytes.add(((usPerBeat shr 8)  and 0xFF).toByte())
            trackBytes.add((usPerBeat          and 0xFF).toByte())
        }

        data class RawEvent(val tick: Long, val isOn: Boolean, val note: Int, val velocity: Int)
        val events = mutableListOf<RawEvent>()
        for (note in notes) {
            val onTick  = (note.startBeat * TICKS_PER_BEAT).toLong()
            val offTick = ((note.startBeat + note.durationBeats) * TICKS_PER_BEAT).toLong()
            events.add(RawEvent(onTick,  true,  note.midiNote, note.velocity.coerceIn(1, 127)))
            events.add(RawEvent(offTick, false, note.midiNote, 0))
        }
        events.sortWith(compareBy({ it.tick }, { if (it.isOn) 1 else 0 }))

        var currentTick = 0L
        for (ev in events) {
            val delta = ev.tick - currentTick
            currentTick = ev.tick
            trackBytes.addAll(varLen(delta))
            val status = if (ev.isOn) (0x90 or ch) else (0x80 or ch)
            trackBytes.add(status.toByte())
            trackBytes.add(ev.note.toByte())
            trackBytes.add(ev.velocity.toByte())
        }

        trackBytes.addAll(varLen(0))
        trackBytes.add(0xFF.toByte())
        trackBytes.add(0x2F.toByte())
        trackBytes.add(0x00.toByte())

        return trackBytes.toByteArray()
    }

    // Import

    fun import(file: File, bpm: Float = DEFAULT_BPM.toFloat()): List<PianoNote> {
        return FileInputStream(file).use { fis ->
            readFromStream(fis, bpm)
        }
    }

    fun readFromStream(input: InputStream, bpm: Float = DEFAULT_BPM.toFloat()): List<PianoNote> {
        val bytes = input.readBytes()
        var pos = 0

        fun readInt32(): Int {
            val v = ((bytes[pos].toInt() and 0xFF) shl 24) or
                    ((bytes[pos+1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos+2].toInt() and 0xFF) shl 8)  or
                    (bytes[pos+3].toInt() and 0xFF)
            pos += 4; return v
        }
        fun readInt16(): Int {
            val v = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos+1].toInt() and 0xFF)
            pos += 2; return v
        }
        fun readVarLen(): Long {
            var value = 0L
            var b: Int
            do {
                b = bytes[pos++].toInt() and 0xFF
                value = (value shl 7) or (b and 0x7F).toLong()
            } while (b and 0x80 != 0)
            return value
        }

        val header = String(bytes, 0, 4)
        if (header != "MThd") {
            Log.e(TAG, "Not a MIDI file (got '$header')")
            return emptyList()
        }
        pos = 4
        readInt32()
        val format = readInt16()
        val numTracks = readInt16()
        val ticksPerBeat = readInt16()
        Log.d(TAG, "MIDI: format=$format, tracks=$numTracks, tpb=$ticksPerBeat")

        val noteOnTicks = mutableMapOf<Pair<Int, Int>, Long>()
        val notes = mutableListOf<PianoNote>()
        var detectedUsPerBeat = (MICROSECONDS_PER_MINUTE / bpm).toLong()

        for (trackIdx in 0 until numTracks) {
            if (pos + 8 > bytes.size) break
            val chunkId = String(bytes, pos, 4); pos += 4
            val chunkLen = readInt32()
            if (chunkId != "MTrk") { pos += chunkLen; continue }

            val trackEnd = pos + chunkLen
            var absoluteTick = 0L
            var runningStatus = 0

            while (pos < trackEnd) {
                val delta = readVarLen()
                absoluteTick += delta

                var statusByte = bytes[pos].toInt() and 0xFF
                if (statusByte and 0x80 == 0) {
                    statusByte = runningStatus
                } else {
                    runningStatus = statusByte
                    pos++
                }

                val type = statusByte and 0xF0

                when {
                    statusByte == 0xFF -> {
                        val metaType = bytes[pos++].toInt() and 0xFF
                        val metaLen  = readVarLen().toInt()
                        if (metaType == 0x51 && metaLen == 3) {
                            detectedUsPerBeat =
                                ((bytes[pos].toLong() and 0xFF) shl 16) or
                                ((bytes[pos+1].toLong() and 0xFF) shl 8)  or
                                (bytes[pos+2].toLong() and 0xFF)
                        }
                        pos += metaLen
                    }
                    statusByte == 0xF0 || statusByte == 0xF7 -> {
                        val sysexLen = readVarLen().toInt()
                        pos += sysexLen
                    }
                    type == NOTE_ON || type == NOTE_OFF -> {
                        val note        = bytes[pos++].toInt() and 0x7F
                        val velocity    = bytes[pos++].toInt() and 0x7F
                        val midiChannel = (statusByte and 0x0F) + 1
                        val key         = Pair(midiChannel, note)
                        if (type == NOTE_ON && velocity > 0) {
                            noteOnTicks[key] = absoluteTick
                        } else {
                            val onTick = noteOnTicks.remove(key) ?: continue
                            val startBeats    = onTick.toFloat() / ticksPerBeat
                            val durationBeats = (absoluteTick - onTick).toFloat() / ticksPerBeat
                            notes.add(PianoNote(
                                midiNote      = note,
                                startBeat     = startBeats,
                                durationBeats = durationBeats.coerceAtLeast(0.25f),
                                velocity      = velocity.coerceAtLeast(1),
                                midiChannel   = midiChannel,
                            ))
                        }
                    }
                    type == 0xA0 -> pos += 2
                    type == 0xB0 -> pos += 2
                    type == 0xC0 -> pos += 1
                    type == 0xD0 -> pos += 1
                    type == 0xE0 -> pos += 2
                    else -> break
                }
            }
            pos = trackEnd
        }

        Log.i(TAG, "Imported ${notes.size} notes from MIDI file")
        return notes.sortedBy { it.startBeat }
    }

    // Helpers

    private fun varLen(value: Long): List<Byte> {
        val bytes = mutableListOf<Byte>()
        var v = value
        bytes.add(0, (v and 0x7F).toByte())
        v = v shr 7
        while (v > 0) {
            bytes.add(0, ((v and 0x7F) or 0x80).toByte())
            v = v shr 7
        }
        return bytes
    }

    private fun OutputStream.writeInt32(v: Int) {
        write((v shr 24) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 8)  and 0xFF)
        write(v          and 0xFF)
    }

    private fun OutputStream.writeInt16(v: Int) {
        write((v shr 8) and 0xFF)
        write(v         and 0xFF)
    }
}
