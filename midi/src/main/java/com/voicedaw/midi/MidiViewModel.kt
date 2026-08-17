package com.voicedaw.midi

import android.app.Application
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class MidiViewModel(application: Application) : AndroidViewModel(application) {

    data class MidiState(
        val connectedDevices: List<MidiDeviceInfo> = emptyList(),
        val bleScanResults: List<BleScanResult> = emptyList(),
        val isBleScanActive: Boolean = false,
        val lastEvent: MidiNoteEvent? = null,
        val midiLearnActive: Boolean = false,
        val learnTargetName: String = "",
        val learnedMappings: Map<Int, String> = emptyMap()
    )

    private val _state = MutableStateFlow(MidiState())
    val state: StateFlow<MidiState> = _state.asStateFlow()

    private val midiManager: MidiManager? = application.getSystemService(MidiManager::class.java)
    val bleScanner = BleMidiScanner(application)
    
    private val openDevices = mutableMapOf<String, MidiDevice>()
    private val openPorts = mutableListOf<MidiOutputPort>()

    var onNoteEvent: ((MidiNoteEvent) -> Unit)? = null
    var onCcEvent: ((Int, Int) -> Unit)? = null

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            Log.i("MidiViewModel", "USB MIDI added: ${device.properties}")
            refreshDeviceList()
            openDevice(device)
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            Log.i("MidiViewModel", "USB MIDI removed")
            closeDevice(device.id.toString())
            refreshDeviceList()
        }
    }

    private val midiReceiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            if (count < 1) return
            val status = msg[offset].toInt() and 0xFF
            val type = status and 0xF0
            val data1 = if (count > 1) msg[offset + 1].toInt() and 0x7F else 0
            val data2 = if (count > 2) msg[offset + 2].toInt() and 0x7F else 0

            when (type) {
                MidiMessage.NOTE_OFF -> {
                    dispatchNote(MidiNoteEvent(data1, 0, false))
                }
                MidiMessage.NOTE_ON -> {
                    if (data2 > 0) {
                        dispatchNote(MidiNoteEvent(data1, data2, true))
                    } else {
                        dispatchNote(MidiNoteEvent(data1, 0, false))
                    }
                }
                MidiMessage.CONTROL_CHANGE -> {
                    handleCc(data1, data2)
                }
            }
        }
    }

    init {
        midiManager?.registerDeviceCallback(deviceCallback, null)
        refreshDeviceList()
        
        midiManager?.devices?.forEach { openDevice(it) }

        viewModelScope.launch {
            bleScanner.scanResults.collect { results ->
                _state.update { it.copy(bleScanResults = results) }
            }
        }

        viewModelScope.launch {
            bleScanner.isScanning.collect { scanning ->
                _state.update { it.copy(isBleScanActive = scanning) }
            }
        }
    }

    fun startBleScan() {
        bleScanner.startScan()
    }

    fun stopBleScan() {
        bleScanner.stopScan()
    }

    fun connectBleDevice(result: BleScanResult) {
        try {
            midiManager?.openBluetoothDevice(result.device, { device ->
                if (device == null) {
                    Log.w("MidiViewModel", "BLE MIDI connect failed for ${result.name}")
                    return@openBluetoothDevice
                }
                openDevices[result.address] = device
                val portInfo = device.info?.ports?.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
                if (portInfo != null) {
                    val port = device.openOutputPort(portInfo.portNumber)
                    if (port != null) {
                        port.connect(midiReceiver)
                        openPorts.add(port)
                        Log.i("MidiViewModel", "BLE MIDI port opened: ${result.name}")
                        refreshDeviceList()
                    }
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e("MidiViewModel", "BLUETOOTH_CONNECT permission not granted", e)
        }
    }

    fun startMidiLearn(targetName: String) {
        _state.update { it.copy(midiLearnActive = true, learnTargetName = targetName) }
        Log.d("MidiViewModel", "MIDI learn started for: $targetName")
    }

    fun cancelMidiLearn() {
        _state.update { it.copy(midiLearnActive = false, learnTargetName = "") }
    }

    fun clearMidiMapping(controlName: String) {
        _state.update { s ->
            val newMappings = s.learnedMappings.filterValues { it != controlName }
            s.copy(learnedMappings = newMappings)
        }
    }

    private fun refreshDeviceList() {
        viewModelScope.launch {
            val devices = midiManager?.devices?.toList() ?: emptyList()
            _state.update { it.copy(connectedDevices = devices) }
        }
    }

    private fun openDevice(info: MidiDeviceInfo) {
        midiManager?.openDevice(info, { device ->
            if (device == null) return@openDevice
            openDevices[info.id.toString()] = device
            val portInfo = info.ports.firstOrNull { it.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT }
            if (portInfo != null) {
                val port = device.openOutputPort(portInfo.portNumber)
                if (port != null) {
                    port.connect(midiReceiver)
                    openPorts.add(port)
                    Log.i("MidiViewModel", "USB MIDI port opened")
                }
            }
        }, null)
    }

    private fun closeDevice(key: String) {
        openDevices.remove(key)?.close()
    }

    private fun dispatchNote(event: MidiNoteEvent) {
        _state.update { it.copy(lastEvent = event) }
        onNoteEvent?.invoke(event)
    }

    private fun handleCc(cc: Int, value: Int) {
        val s = _state.value
        if (s.midiLearnActive) {
            val newMappings = s.learnedMappings.filterValues { it != s.learnTargetName }.toMutableMap()
            newMappings[cc] = s.learnTargetName
            _state.update { it.copy(midiLearnActive = false, learnTargetName = "", learnedMappings = newMappings) }
            Log.i("MidiViewModel", "MIDI learn: CC $cc mapped to '${s.learnTargetName}'")
        }
        onCcEvent?.invoke(cc, value)
    }

    override fun onCleared() {
        bleScanner.stopScan()
        openPorts.forEach { it.close() }
        openDevices.values.forEach { it.close() }
        midiManager?.unregisterDeviceCallback(deviceCallback)
        super.onCleared()
    }
}
