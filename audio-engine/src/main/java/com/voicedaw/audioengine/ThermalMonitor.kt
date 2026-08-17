package com.voicedaw.audioengine

import android.content.Context
import android.os.PowerManager
import android.util.Log

class ThermalMonitor(context: Context) {
    enum class PerformanceTier(val level: Int) {
        HIGH_PERFORMANCE(0),
        ECO_MODE(1)
    }

    private var currentTier = PerformanceTier.HIGH_PERFORMANCE
    private var listener: ((PerformanceTier) -> Unit)? = null
    private val powerManager: PowerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    init {
        powerManager.addThermalStatusListener { status ->
            val newTier = when (status) {
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY -> PerformanceTier.ECO_MODE
                else -> PerformanceTier.HIGH_PERFORMANCE
            }
            if (newTier != currentTier) {
                currentTier = newTier
                Log.w("ThermalMonitor", "Thermal status changed to $status, scaling to $newTier")
                listener?.invoke(newTier)
            }
        }
    }

    fun setOnTierChangeListener(l: (PerformanceTier) -> Unit) {
        listener = l
        listener?.invoke(currentTier)
    }
}
