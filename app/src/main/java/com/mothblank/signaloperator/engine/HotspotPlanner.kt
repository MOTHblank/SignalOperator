package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.GamePhase
import kotlin.math.abs
import kotlin.random.Random

object HotspotPlanner {
    const val MIN_FREQUENCY = 88f
    const val MAX_FREQUENCY = 108f
    const val DEFAULT_COUNT = 17
    const val LOCK_RANGE = 0.8f

    /**
     * Generates deterministic, phase-specific frequencies using stratified slots.
     * The bounded jitter prevents neighboring hotspots from overlapping enough to
     * shadow one another during acquisition.
     */
    fun generate(
        seed: Long,
        phase: GamePhase,
        count: Int = DEFAULT_COUNT
    ): List<Float> {
        require(count > 0)

        val width = MAX_FREQUENCY - MIN_FREQUENCY
        val slotWidth = width / count
        val maxJitter = slotWidth * 0.2f
        val phaseSeed = seed xor (phase.ordinal.toLong() * 0x9E3779B9L)
        val random = Random(phaseSeed)

        return List(count) { index ->
            val center = MIN_FREQUENCY + (index + 0.5f) * slotWidth
            val jitter = (random.nextFloat() * 2f - 1f) * maxJitter
            (center + jitter).coerceIn(MIN_FREQUENCY, MAX_FREQUENCY)
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
}
