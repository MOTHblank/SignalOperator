package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.PuzzleType
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
}
