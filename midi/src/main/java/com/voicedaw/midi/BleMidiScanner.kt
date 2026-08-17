package com.voicedaw.midi

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleMidiScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleMidiScanner"
        val BLE_MIDI_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString(
            "03B80E5A-EDE8-4B33-A751-6CE34EC4C700"
        )
    }

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var scanner: BluetoothLeScanner? = null

    private val _scanResults = MutableStateFlow<List<BleScanResult>>(emptyList())
    val scanResults: StateFlow<List<BleScanResult>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val foundDevices = mutableMapOf<String, BleScanResult>()

    // Public API

    fun startScan() {
        if (_isScanning.value) return
        val adapter = bluetoothAdapter ?: run {
            Log.w(TAG, "Bluetooth not available on this device")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is not enabled")
            return
        }
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            Log.w(TAG, "BLUETOOTH_SCAN permission not granted")
            return
        }

        scanner = adapter.bluetoothLeScanner ?: run {
            Log.w(TAG, "BLE scanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(BLE_MIDI_SERVICE_UUID)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "BLE MIDI scan started")
        } catch (e: SecurityException) {
            Log.e(TAG, "BLE scan failed — permission denied at runtime", e)
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not stop scan — permission revoked?", e)
        }
        _isScanning.value = false
        Log.i(TAG, "BLE MIDI scan stopped")
    }

    fun clearResults() {
        foundDevices.clear()
        _scanResults.value = emptyList()
    }

    // Private

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val address = result.device.address ?: return
            if (foundDevices.containsKey(address)) return

            val name = try {
                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT))
                    result.device.name ?: "BLE MIDI Device"
                else "BLE MIDI Device"
            } catch (_: SecurityException) { "BLE MIDI Device" }

            val entry = BleScanResult(
                address   = address,
                name      = name,
                rssi      = result.rssi,
                device    = result.device,
            )
            foundDevices[address] = entry
            _scanResults.value = foundDevices.values.toList()
                .sortedByDescending { it.rssi }
            Log.d(TAG, "Found BLE MIDI device: $name ($address) RSSI=${result.rssi}")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _isScanning.value = false
        }
    }

    private fun hasPermission(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
