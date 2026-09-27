package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.Character
import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.GameState
import com.mothblank.signaloperator.models.Location
import com.mothblank.signaloperator.models.LocationStatus
import com.mothblank.signaloperator.models.PuzzleType
import com.mothblank.signaloperator.models.SignalData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

class GameSessionReducerTest {
    private val signal = SignalData(
        id = "test",
        frequency = 99.1f,
        targetGain = 50,
        targetFilter = 50,
        puzzleType = PuzzleType.CRYPTOGRAPHY,
        encodedMessage = "SITE ALPHA",
        solution = "SITE ALPHA",
        objective = "TEST",
        cipherType = "NONE",
        sender = "TEST",
        isAnomalous = false,
        metadata = ""
    )

    @Test
    fun committedWorldMutationSurvivesProgressUpdate() {
        val current = GameState(
            locations = listOf(Location("loc-1", "SITE ALPHA", 0.2f, 0.3f)),
            characters = listOf(Character("char-1", "ECHO", "loc-1"))
        )

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = signal,
            currentHotspot = 99.1f,
            action = "COMMIT",
            solutionInput = "SITE ALPHA",
            random = Random(1234)
        )

        assertEquals(1, resolution.state.archivedSignals)
        assertEquals(
            LocationStatus.INVESTIGATING,
            resolution.state.locations.single().status
        )
    }

    @Test
    fun completingPhaseProducesNextPhaseAtomically() {
        val current = GameState(
            phase = GamePhase.APTITUDE_TEST,
            puzzlesSolved = 2,
            puzzlesRequired = 3,
            solvedHotspots = setOf(91f)
        )

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = signal,
            currentHotspot = 99.1f,
            action = "COMMIT",
            solutionInput = "SITE ALPHA",
            random = Random(1)
        )

        assertEquals(GamePhase.LIVE_INTRUSION, resolution.state.phase)
        assertEquals(0, resolution.state.puzzlesSolved)
        assertEquals(5, resolution.state.puzzlesRequired)
        assertEquals(emptySet<Float>(), resolution.state.solvedHotspots)
        assertEquals(DialogueCue.LIVE_INTRUSION, resolution.dialogueCue)
    }

    @Test
    fun finalInterviewChoiceProducesScoreAndEnding() {
        val current = GameState(
            phase = GamePhase.THE_INTERVIEW,
            puzzlesSolved = 2,
            puzzlesRequired = 3,
            archivedSignals = 8,
            ignoredSignals = 1,
            seed = 814L
        )
        val interviewSignal = signal.copy(solution = "I ACCEPT|I REFUSE|I AM AFRAID")

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = interviewSignal,
            currentHotspot = 101f,
            action = "COMMIT",
            solutionInput = "I REFUSE",
            random = Random(1)
        )

        assertEquals(GamePhase.ENDING_SEVERED, resolution.state.phase)
        assertNotNull(resolution.highScore)
        assertEquals("SEVERED", resolution.highScore?.maxPhase)
    }
}
