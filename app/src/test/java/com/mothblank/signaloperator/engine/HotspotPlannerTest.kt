package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.SignalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HotspotPlannerTest {
    @Test
    fun generationIsDeterministicAndPhaseSpecific() {
        val seed = 814L

        val first = HotspotPlanner.generate(seed, GamePhase.APTITUDE_TEST)
        val second = HotspotPlanner.generate(seed, GamePhase.APTITUDE_TEST)
        val nextPhase = HotspotPlanner.generate(seed, GamePhase.LIVE_INTRUSION)

        assertEquals(first, second)
        assertNotEquals(first, nextPhase)
    }

    @Test
    fun generatedHotspotsRemainSeparated() {
        val hotspots = HotspotPlanner.generate(42L, GamePhase.ACTIVE_INVESTIGATION)

        assertEquals(HotspotPlanner.DEFAULT_COUNT, hotspots.size)
        hotspots.zipWithNext().forEach { (left, right) ->
            assertTrue("Hotspots too close: $left and $right", abs(right - left) >= 0.65f)
        }
    }

    @Test
    fun nearestUnsolvedDoesNotLetSolvedHotspotShadowAnotherSignal() {
        val hotspots = listOf(99.95f, 100.30f, 103f)
        val result = HotspotPlanner.nearestUnsolved(
            hotspots = hotspots,
            solvedHotspots = setOf(99.95f),
            currentFrequency = 100.0f
        )

        assertEquals(100.30f, result)
    }
    @Test
    fun planGuaranteesEnoughMissionSignalsForProgression() {
        val plan = HotspotPlanner.generatePlan(
            seed = 814L,
            phase = GamePhase.ACTIVE_INVESTIGATION,
            requiredMissionSignals = 6
        )

        assertTrue(plan.count { it.kind == SignalKind.MISSION } >= 6)
        assertTrue(plan.any { it.kind == SignalKind.MUNDANE_BROADCAST })
        assertTrue(plan.any { it.kind == SignalKind.DEAD_DROP })
    }

}
