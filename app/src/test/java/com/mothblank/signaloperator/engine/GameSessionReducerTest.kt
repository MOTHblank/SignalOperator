package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        metadata = "",
        outcome = SignalOutcome(
            eventId = "test-outcome",
            briefing = "Site Alpha pressure rising.",
            urgency = Urgency.HIGH,
            locationId = "loc-1",
            effects = listOf(
                WorldEffect(WorldEffectType.ADD_LOCATION_THREAT, "loc-1", amount = 70),
                WorldEffect(WorldEffectType.MARK_LOCATION_INVESTIGATING, "loc-1")
            )
        )
    )

    @Test
    fun committedWorldOutcomeSurvivesProgressUpdate() {
        val current = GameState(
            locations = listOf(Location("loc-1", "SITE ALPHA", 0.2f, 0.3f, security = 50)),
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
        assertEquals(70, resolution.state.locations.single().threat)
        assertEquals(LocationStatus.INVESTIGATING, resolution.state.locations.single().status)
    }

    @Test
    fun discardingMissionAdvancesOperationButDamagesRunState() {
        val current = GameState(
            puzzlesSolved = 0,
            puzzlesRequired = 3,
            trustInEcho = 50,
            containmentIntegrity = 100
        )

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = signal,
            currentHotspot = 99.1f,
            action = "DISCARD",
            solutionInput = "",
            random = Random(1)
        )

        assertEquals(1, resolution.state.puzzlesSolved)
        assertEquals(1, resolution.state.ignoredSignals)
        assertEquals(46, resolution.state.trustInEcho)
        assertEquals(97, resolution.state.containmentIntegrity)
    }

    @Test
    fun deadDropRewardsSecurityWithoutAdvancingMissionProgress() {
        val deadDrop = signal.copy(
            kind = SignalKind.DEAD_DROP,
            outcome = SignalOutcome(
                eventId = "dead-drop",
                briefing = "Kernel telemetry.",
                effects = listOf(
                    WorldEffect(WorldEffectType.ADD_SECURITY_CHARGES, amount = 1),
                    WorldEffect(WorldEffectType.ADD_CONTAINMENT, amount = 4)
                )
            )
        )
        val current = GameState(securityCharges = 1, containmentIntegrity = 80)

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = deadDrop,
            currentHotspot = 97f,
            action = "COMMIT",
            solutionInput = "ACK"
        )

        assertEquals(0, resolution.state.puzzlesSolved)
        assertEquals(1, resolution.state.deadDropsRecovered)
        assertEquals(2, resolution.state.securityCharges)
        assertEquals(84, resolution.state.containmentIntegrity)
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
    fun earlierNetworkStateCanChangeFinalRefusalOutcome() {
        val fragileRun = GameState(
            phase = GamePhase.THE_INTERVIEW,
            puzzlesSolved = 2,
            puzzlesRequired = 3,
            archivedSignals = 8,
            ignoredSignals = 3,
            seed = 814L,
            trustInEcho = 20,
            containmentIntegrity = 20,
            characters = listOf(
                Character("char-1", "ECHO-ACTUAL", "loc-1", CharacterStatus.MIA),
                Character("char-2", "ECHO-2", "loc-2", CharacterStatus.COMPROMISED)
            )
        )
        val interviewSignal = signal.copy(
            solution = "I ACCEPT|I REFUSE|I AM AFRAID",
            outcome = null
        )

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = fragileRun,
            signal = interviewSignal,
            currentHotspot = 101f,
            action = "COMMIT",
            solutionInput = "I REFUSE"
        )

        assertEquals(GamePhase.ENDING_CONTAINMENT, resolution.state.phase)
        assertNotNull(resolution.state.endingSummary)
        assertNotNull(resolution.highScore)
        assertTrue(resolution.highScore!!.score >= 0)
    }

    @Test
    fun healthyNetworkAllowsCleanSever() {
        val healthyRun = GameState(
            phase = GamePhase.THE_INTERVIEW,
            puzzlesSolved = 2,
            puzzlesRequired = 3,
            archivedSignals = 8,
            seed = 814L,
            trustInEcho = 75,
            containmentIntegrity = 85,
            exposure = 25
        )
        val interviewSignal = signal.copy(
            solution = "I ACCEPT|I REFUSE|I AM AFRAID",
            outcome = null
        )

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = healthyRun,
            signal = interviewSignal,
            currentHotspot = 101f,
            action = "COMMIT",
            solutionInput = "I REFUSE"
        )

        assertEquals(GamePhase.ENDING_SEVERED, resolution.state.phase)
        assertEquals("SEVERED", resolution.highScore?.maxPhase)
    }
    @Test
    fun ignoredMissionStillAppliesUnderlyingWorldEvent() {
        val current = GameState(
            locations = listOf(Location("loc-1", "SITE ALPHA", 0.2f, 0.3f, security = 50))
        )

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = signal,
            currentHotspot = 99.1f,
            action = "DISCARD",
            solutionInput = ""
        )

        assertEquals(70, resolution.state.locations.single().threat)
        assertEquals(LocationStatus.INVESTIGATING, resolution.state.locations.single().status)
        assertEquals(1, resolution.state.ignoredSignals)
    }

    @Test
    fun interviewDiscardIsRejectedByDomainReducer() {
        val current = GameState(
            phase = GamePhase.THE_INTERVIEW,
            puzzlesSolved = 1,
            puzzlesRequired = 3
        )
        val interviewSignal = signal.copy(
            solution = "YES|NO|I CANNOT FEEL",
            interaction = PuzzleInteraction.INTERVIEW_CHOICE,
            outcome = null
        )

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = interviewSignal,
            currentHotspot = 101f,
            action = "DISCARD",
            solutionInput = ""
        )

        assertEquals(current, resolution.state)
    }

}
