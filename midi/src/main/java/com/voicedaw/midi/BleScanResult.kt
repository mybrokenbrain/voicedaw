package com.voicedaw.midi

data class BleScanResult(
    val address: String,
    val name: String,
    val rssi: Int,
    val device: android.bluetooth.BluetoothDevice,
)
