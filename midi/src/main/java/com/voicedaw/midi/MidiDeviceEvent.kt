package com.voicedaw.midi

import android.media.midi.MidiDeviceInfo

sealed class MidiDeviceEvent {
    data class Connected(val device: MidiDeviceInfo) : MidiDeviceEvent()
    data class Disconnected(val device: MidiDeviceInfo) : MidiDeviceEvent()
}
