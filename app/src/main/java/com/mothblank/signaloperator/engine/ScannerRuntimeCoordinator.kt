package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.SignalData
import kotlin.math.abs
import kotlin.math.sin

/**
 * Scanner-domain calculations independent of Android sound, UI, and campaign
 * persistence. The ViewModel still determines when a scan is accepted or solved.
 */
class ScannerRuntimeCoordinator {
    data class ProximityReading(val distance: Float, val strength: Float)

    private var lastFrequencyNotch: Int = -1

    fun crossesNotch(frequency: Float): Boolean {
        val nextNotch = ((frequency.coerceIn(88f, 108f) - 88f) * 4f).toInt()
        if (nextNotch == lastFrequencyNotch) return false
        lastFrequencyNotch = nextNotch
        return true
    }

    fun proximity(
        frequency: Float,
        activeTarget: Float?,
        lockedTarget: Float?,
        hotspots: List<Float>,
        solved: Set<Float>
    ): ProximityReading {
        val target = activeTarget ?: lockedTarget
        val distance = if (target != null) {
            abs(target - frequency)
        } else {
            hotspots.asSequence()
                .filter { it !in solved }
                .map { abs(it - frequency) }
                .minOrNull() ?: 100f
        }
        return ProximityReading(distance, (1f - distance / 2f).coerceIn(0f, 1f))
    }

    fun calibration(
        signal: SignalData,
        gain: Int,
        filter: Int,
        corruptionLevel: Float
    ): Float {
        var stability = (100f - (abs(signal.targetGain - gain) +
            abs(signal.targetFilter - filter)).toFloat()).coerceAtLeast(0f)
        if (corruptionLevel > 0f) {
            val phase = signal.id.hashCode() * 0.001f +
                gain * 0.071f + filter * 0.113f
            val interference = ((sin(phase) + 1f) * 0.5f) *
                (corruptionLevel * 5f)
            stability -= interference
        }
        return stability.coerceIn(0f, 100f)
    }
}
