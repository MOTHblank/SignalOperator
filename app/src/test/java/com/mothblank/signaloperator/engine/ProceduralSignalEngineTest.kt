package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.PuzzleType
import com.mothblank.signaloperator.models.SignalKind
import com.mothblank.signaloperator.models.GameState
import com.mothblank.signaloperator.models.Location
import com.mothblank.signaloperator.models.Character
import com.mothblank.signaloperator.models.CharacterStatus
import org.junit.Assert.*
import org.junit.Test

class ProceduralSignalEngineTest {

    private val engine = ProceduralSignalEngine()

    @Test
    fun testGenerateSignalProducesValidSolution() {
        // Run generation multiple times across all phases to ensure no crashes
        // and that a valid solution is always provided.
        for (phase in GamePhase.values()) {
            for (i in 0..50) {
                val signal = engine.generateSignal(SignalRequest(phase, 100.0f, i.toLong()))
                
                assertNotNull(signal.id)
                assertNotNull(signal.solution)
                assertTrue(signal.solution.isNotEmpty())
                assertNotNull(signal.encodedMessage)
                
                // Specific checks per puzzle type
                when (signal.puzzleType) {
                    PuzzleType.SEQUENCE -> {
                        assertTrue("Sequence encoded message must contain a '?' for the missing element", 
                            signal.encodedMessage.contains("?") || signal.encodedMessage.contains("THE END"))
                    }
                    PuzzleType.LOGIC -> {
                        if (phase == GamePhase.THE_INTERVIEW) {
                            assertTrue("Interview solution must contain options", signal.solution.contains("|"))
                        } else {
                            val validLogicAnswers = listOf("NORTH", "SOUTH", "EAST", "WEST", "YES", "A", "B", "0", "1")
                            assertTrue("Solution must be a direction or logical answer: ${signal.solution}", 
                                validLogicAnswers.contains(signal.solution))
                        }
                    }
                    PuzzleType.OBSERVATION -> {
                        if (signal.solution != "DISCARD" && !signal.metadata.contains("REAL-WORLD INTERCEPT")) {
                            if (!signal.metadata.contains("Q:")) {
                                println("FAILING SIGNAL: phase=$phase, frequency=${signal.frequency}, puzzleType=${signal.puzzleType}, solution=${signal.solution}, metadata=${signal.metadata}")
                            }
                            assertTrue("Metadata must contain a question", signal.metadata.contains("Q:"))
                        }
                    }
                    PuzzleType.CRYPTOGRAPHY -> {
                        // The encoded message shouldn't equal solution unless cipher is NONE
                        if (signal.cipherType != "NONE") {
                            assertNotEquals("Encoded message must not equal solution if encrypted", 
                                signal.encodedMessage, signal.solution)
                        }
                    }
                }
            }
        }
    }
    @Test
    fun specialSignalsKeepTheirClassificationInActiveInvestigation() {
        val systemData = SystemData(
            batteryLevel = 67,
            deviceModel = "TEST-DEVICE",
            currentTime = "12:34:56"
        )

        val deadDrop = (0L..10_000L)
            .asSequence()
            .map { seed ->
                engine.generateSignal(
                    SignalRequest(
                        phase = GamePhase.ACTIVE_INVESTIGATION,
                        frequency = 97.1f,
                        seed = seed,
                        systemData = systemData
                    )
                )
            }
            .first { it.kind == SignalKind.DEAD_DROP }

        assertEquals(SignalKind.DEAD_DROP, deadDrop.kind)
        assertTrue(deadDrop.metadata.startsWith("TYPE: REAL-WORLD INTERCEPT"))
        assertEquals("VERIFY SYSTEM TELEMETRY", deadDrop.objective)

        val mundane = (0L..10_000L)
            .asSequence()
            .map { seed ->
                engine.generateSignal(
                    SignalRequest(
                        phase = GamePhase.ACTIVE_INVESTIGATION,
                        frequency = 99.3f,
                        seed = seed,
                        systemData = systemData
                    )
                )
            }
            .first { it.kind == SignalKind.MUNDANE_BROADCAST }

        assertEquals(SignalKind.MUNDANE_BROADCAST, mundane.kind)
        assertTrue(mundane.metadata.startsWith("TYPE: PUBLIC BAND"))
        assertEquals("IDENTIFY NON-ESSENTIAL BROADCAST", mundane.objective)
    }

    @Test
    fun missionSignalsCarryStructuredWorldOutcomes() {
        val world = GameState(
            phase = GamePhase.LIVE_INTRUSION,
            locations = listOf(
                Location("loc-1", "SITE ALPHA", 0.2f, 0.3f),
                Location("loc-2", "SECTOR 4 RELAY", 0.5f, 0.5f),
                Location("loc-3", "ALPHA OUTPOST", 0.8f, 0.2f),
                Location("loc-4", "EXCLUSION ZONE", 0.6f, 0.8f)
            ),
            characters = listOf(
                Character("char-1", "ECHO-ACTUAL", "loc-1"),
                Character("char-2", "ECHO-2", "loc-3", CharacterStatus.ACTIVE)
            )
        )

        val signal = engine.generateSignal(
            SignalRequest(
                phase = GamePhase.LIVE_INTRUSION,
                frequency = 96.4f,
                seed = 814L,
                solvedPuzzlesCount = 1,
                requestedKind = SignalKind.MISSION,
                worldState = world
            )
        )

        assertEquals(SignalKind.MISSION, signal.kind)
        assertNotNull(signal.outcome)
        assertTrue(signal.outcome!!.effects.isNotEmpty())
        assertNotNull(signal.outcome!!.briefing)
    }

    @Test
    fun deadDropOutcomeRewardsStrategicResources() {
        val signal = engine.generateSignal(
            SignalRequest(
                phase = GamePhase.ACTIVE_INVESTIGATION,
                frequency = 97.1f,
                seed = 23L,
                systemData = SystemData(67, "TEST", "12:00:00"),
                requestedKind = SignalKind.DEAD_DROP
            )
        )

        assertEquals(SignalKind.DEAD_DROP, signal.kind)
        assertNotNull(signal.outcome)
        assertTrue(signal.outcome!!.effects.any { it.type.name == "ADD_SECURITY_CHARGES" })
    }

}
