package com.voicedaw.midi

import android.content.Context
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MidiDeviceManager(context: Context) {

    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private val _deviceEvents = MutableSharedFlow<MidiDeviceEvent>(extraBufferCapacity = 32)
    val deviceEvents: SharedFlow<MidiDeviceEvent> = _deviceEvents.asSharedFlow()

    fun getConnectedDevices(): List<MidiDeviceInfo> {
        @Suppress("DEPRECATION")
        return midiManager?.getDevices()?.toList() ?: emptyList()
    }

    fun registerDeviceCallback(callback: MidiManager.DeviceCallback) {
        @Suppress("DEPRECATION")
        midiManager?.registerDeviceCallback(callback, null)
    }

    fun unregisterDeviceCallback(callback: MidiManager.DeviceCallback) {
        midiManager?.unregisterDeviceCallback(callback)
    }
}
