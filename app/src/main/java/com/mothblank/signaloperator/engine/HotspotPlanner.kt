package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.SignalKind
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

data class PlannedHotspot(
    val frequency: Float,
    val kind: SignalKind
)

object HotspotPlanner {
    const val MIN_FREQUENCY = 88f
    const val MAX_FREQUENCY = 108f
    const val DEFAULT_COUNT = 17
    const val LOCK_RANGE = 0.8f

    /**
     * Generates deterministic, phase-specific frequencies using stratified slots.
     * The bounded jitter prevents neighboring hotspots from shadowing one another.
     */
    fun generate(
        seed: Long,
        phase: GamePhase,
        count: Int = DEFAULT_COUNT
    ): List<Float> {
        return generateFrequencies(seed, phase, count)
    }

    /**
     * Builds the actual radio-band content plan. Mission signals are guaranteed to
     * meet the phase requirement, so random civilian broadcasts cannot soft-lock
     * progression.
     */
    fun generatePlan(
        seed: Long,
        phase: GamePhase,
        requiredMissionSignals: Int,
        count: Int = DEFAULT_COUNT
    ): List<PlannedHotspot> {
        require(requiredMissionSignals >= 0)
        require(count > 0)

        val frequencies = generateFrequencies(seed, phase, count)
        val missionCount = if (phase == GamePhase.THE_INTERVIEW) {
            count
        } else {
            max(requiredMissionSignals, (count + 1) / 2).coerceAtMost(count)
        }

        val kinds = MutableList(count) { index ->
            when {
                index < missionCount -> SignalKind.MISSION
                (index - missionCount) % 5 == 0 -> SignalKind.DEAD_DROP
                else -> SignalKind.MUNDANE_BROADCAST
            }
        }

        val phaseSeed = phaseSeed(seed, phase)
        kinds.shuffle(Random(phaseSeed xor 0x51A7E0B5L))

        return frequencies.indices.map { index ->
            PlannedHotspot(frequencies[index], kinds[index])
        }
    }

    fun nearestUnsolved(
        hotspots: List<Float>,
        solvedHotspots: Set<Float>,
        currentFrequency: Float,
        lockRange: Float = LOCK_RANGE
    ): Float? {
        return hotspots
            .asSequence()
            .filterNot(solvedHotspots::contains)
            .minByOrNull { abs(it - currentFrequency) }
            ?.takeIf { abs(it - currentFrequency) < lockRange }
    }

    private fun generateFrequencies(
        seed: Long,
        phase: GamePhase,
        count: Int
    ): List<Float> {
        require(count > 0)

        val width = MAX_FREQUENCY - MIN_FREQUENCY
        val slotWidth = width / count
        val maxJitter = slotWidth * 0.2f
        val random = Random(phaseSeed(seed, phase))

        return List(count) { index ->
            val center = MIN_FREQUENCY + (index + 0.5f) * slotWidth
            val jitter = (random.nextFloat() * 2f - 1f) * maxJitter
            (center + jitter).coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
        }
    }

    private fun phaseSeed(seed: Long, phase: GamePhase): Long {
        return seed xor (phase.ordinal.toLong() * 0x9E3779B9L)
    }
}
