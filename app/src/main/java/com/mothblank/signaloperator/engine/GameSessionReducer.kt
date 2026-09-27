package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.*
import kotlin.math.max
import kotlin.random.Random

enum class DialogueCue {
    LIVE_INTRUSION,
    ACTIVE_INVESTIGATION,
    THE_INTERVIEW
}

data class SessionResolution(
    val state: GameState,
    val dialogueCue: DialogueCue? = null,
    val highScore: HighScoreEntry? = null
)

object GameSessionReducer {
    fun resolveProcessedSignal(
        current: GameState,
        signal: SignalData,
        currentHotspot: Float,
        action: String,
        solutionInput: String,
        random: Random = Random.Default
    ): SessionResolution {
        var state = current
        val solvedHotspots = current.solvedHotspots.toMutableSet()
        solvedHotspots.add(currentHotspot)

        when (signal.kind) {
            SignalKind.MISSION -> {
                state = if (action == "COMMIT") {
                    applyOutcome(
                        state.copy(
                            archivedSignals = state.archivedSignals + 1,
                            puzzlesSolved = state.puzzlesSolved + 1
                        ),
                        signal.outcome
                    )
                } else {
                    state.copy(
                        ignoredSignals = state.ignoredSignals + 1,
                        puzzlesSolved = state.puzzlesSolved + 1,
                        trustInEcho = (state.trustInEcho - 4).coerceIn(0, 100),
                        containmentIntegrity = (state.containmentIntegrity - 3).coerceIn(0, 100)
                    )
                }
            }

            SignalKind.DEAD_DROP -> {
                state = if (action == "COMMIT") {
                    applyOutcome(
                        state.copy(deadDropsRecovered = state.deadDropsRecovered + 1),
                        signal.outcome
                    )
                } else {
                    state.copy(
                        ignoredSignals = state.ignoredSignals + 1,
                        exposure = (state.exposure - 2).coerceIn(0, 100)
                    )
                }
            }

            SignalKind.MUNDANE_BROADCAST -> {
                state = if (action == "DISCARD") {
                    state.copy(routineBroadcastsCleared = state.routineBroadcastsCleared + 1)
                } else {
                    state.copy(exposure = (state.exposure + 2).coerceIn(0, 100))
                }
            }
        }

        state = state.copy(
            solvedHotspots = solvedHotspots,
            downloadProgress = 0f
        )

        var dialogueCue: DialogueCue? = null
        var highScore: HighScoreEntry? = null

        if (signal.kind == SignalKind.MISSION && state.puzzlesSolved >= state.puzzlesRequired) {
            when (state.phase) {
                GamePhase.APTITUDE_TEST -> {
                    state = state.copy(
                        phase = GamePhase.LIVE_INTRUSION,
                        puzzlesRequired = 5,
                        puzzlesSolved = 0,
                        solvedHotspots = emptySet()
                    )
                    dialogueCue = DialogueCue.LIVE_INTRUSION
                }

                GamePhase.LIVE_INTRUSION -> {
                    state = state.copy(
                        phase = GamePhase.ACTIVE_INVESTIGATION,
                        puzzlesRequired = 6,
                        puzzlesSolved = 0,
                        solvedHotspots = emptySet(),
                        corruptionLevel = max(state.corruptionLevel, 1f)
                    )
                    dialogueCue = DialogueCue.ACTIVE_INVESTIGATION
                }

                GamePhase.ACTIVE_INVESTIGATION -> {
                    state = state.copy(
                        phase = GamePhase.THE_INTERVIEW,
                        puzzlesRequired = 3,
                        puzzlesSolved = 0,
                        solvedHotspots = emptySet(),
                        corruptionLevel = max(state.corruptionLevel, 2f)
                    )
                    dialogueCue = DialogueCue.THE_INTERVIEW
                }

                GamePhase.THE_INTERVIEW -> {
                    val ending = resolveEnding(state, solutionInput)
                    state = state.copy(
                        phase = ending.first,
                        puzzlesSolved = 0,
                        solvedHotspots = emptySet(),
                        endingSummary = ending.second
                    )

                    val strategicBonus =
                        state.containmentIntegrity * 10 +
                        state.trustInEcho * 4 +
                        state.breachesPrevented * 500 +
                        state.deadDropsRecovered * 250

                    highScore = HighScoreEntry(
                        operatorId = "OP-${current.seed % 1000}",
                        maxPhase = state.phase.name.replace("ENDING_", ""),
                        intelSaved = state.archivedSignals,
                        score = (
                            state.archivedSignals * 1000 -
                                state.ignoredSignals * 250 +
                                strategicBonus -
                                state.exposure * 5
                            ).coerceAtLeast(0)
                    )
                }

                else -> Unit
            }
        }

        val corruptedNodes = state.locations.count { it.status == LocationStatus.CORRUPTED }
        val systemicCorruption = (
            state.exposure / 45f +
                corruptedNodes * 0.45f +
                (100 - state.containmentIntegrity) / 80f
            ).coerceIn(0f, 3f)

        val phaseFloor = when (state.phase) {
            GamePhase.ACTIVE_INVESTIGATION -> 1f
            GamePhase.THE_INTERVIEW -> 2f
            GamePhase.ENDING_COMPLIANCE,
            GamePhase.ENDING_SEVERED,
            GamePhase.ENDING_CONTAINMENT -> 0f
            else -> 0f
        }
        state = state.copy(corruptionLevel = max(phaseFloor, systemicCorruption))

        return SessionResolution(
            state = state,
            dialogueCue = dialogueCue,
            highScore = highScore
        )
    }

    private fun applyOutcome(state: GameState, outcome: SignalOutcome?): GameState {
        if (outcome == null) return state

        var locations = state.locations
        var characters = state.characters
        var trust = state.trustInEcho
        var exposure = state.exposure
        var containment = state.containmentIntegrity
        var charges = state.securityCharges

        outcome.effects.forEach { effect ->
            when (effect.type) {
                WorldEffectType.ADD_LOCATION_THREAT -> {
                    locations = locations.map { location ->
                        if (location.id == effect.targetId) {
                            val threat = (location.threat + effect.amount).coerceIn(0, 100)
                            location.copy(
                                threat = threat,
                                status = if (
                                    threat >= location.security &&
                                    location.status == LocationStatus.SECURE
                                ) LocationStatus.INVESTIGATING else location.status
                            )
                        } else {
                            location
                        }
                    }
                }

                WorldEffectType.ADD_LOCATION_SECURITY -> {
                    locations = locations.map { location ->
                        if (location.id == effect.targetId) {
                            location.copy(
                                security = (location.security + effect.amount).coerceIn(0, 100)
                            )
                        } else {
                            location
                        }
                    }
                }

                WorldEffectType.MARK_LOCATION_INVESTIGATING -> {
                    locations = locations.map { location ->
                        if (
                            location.id == effect.targetId &&
                            location.status != LocationStatus.CORRUPTED
                        ) {
                            location.copy(status = LocationStatus.INVESTIGATING)
                        } else {
                            location
                        }
                    }
                }

                WorldEffectType.MOVE_CHARACTER -> {
                    characters = characters.map { character ->
                        if (character.id == effect.targetId) {
                            character.copy(locationId = effect.destinationId)
                        } else {
                            character
                        }
                    }
                }

                WorldEffectType.SET_CHARACTER_ACTIVE -> {
                    characters = characters.map { character ->
                        if (character.id == effect.targetId) {
                            character.copy(status = CharacterStatus.ACTIVE)
                        } else {
                            character
                        }
                    }
                }

                WorldEffectType.SET_CHARACTER_MIA -> {
                    characters = characters.map { character ->
                        if (character.id == effect.targetId) {
                            character.copy(status = CharacterStatus.MIA)
                        } else {
                            character
                        }
                    }
                }

                WorldEffectType.SET_CHARACTER_COMPROMISED -> {
                    characters = characters.map { character ->
                        if (character.id == effect.targetId) {
                            character.copy(status = CharacterStatus.COMPROMISED)
                        } else {
                            character
                        }
                    }
                }

                WorldEffectType.ADD_TRUST -> trust = (trust + effect.amount).coerceIn(0, 100)
                WorldEffectType.ADD_EXPOSURE -> exposure = (exposure + effect.amount).coerceIn(0, 100)
                WorldEffectType.ADD_CONTAINMENT -> containment = (containment + effect.amount).coerceIn(0, 100)
                WorldEffectType.ADD_SECURITY_CHARGES -> charges = (charges + effect.amount).coerceIn(0, 9)
            }
        }

        return state.copy(
            locations = locations,
            characters = characters,
            trustInEcho = trust,
            exposure = exposure,
            containmentIntegrity = containment,
            securityCharges = charges
        )
    }

    private fun resolveEnding(
        state: GameState,
        solutionInput: String
    ): Pair<GamePhase, String> {
        val response = solutionInput.uppercase().trim()
        val compromisedAgents = state.characters.count {
            it.status == CharacterStatus.COMPROMISED || it.status == CharacterStatus.MIA
        }
        val corruptedNodes = state.locations.count { it.status == LocationStatus.CORRUPTED }

        return when (response) {
            "I ACCEPT" -> {
                if (
                    state.containmentIntegrity >= 85 &&
                    state.exposure <= 30 &&
                    corruptedNodes == 0
                ) {
                    GamePhase.ENDING_CONTAINMENT to
                        "You opened the channel, but the network you preserved closed around the entity first. The terminal remains a prison rather than a doorway."
                } else {
                    GamePhase.ENDING_COMPLIANCE to
                        "Your accumulated exposure made the final invitation actionable. The entity inherits the channels you left open."
                }
            }

            "I REFUSE" -> {
                if (
                    state.containmentIntegrity >= 40 &&
                    state.trustInEcho >= 35 &&
                    compromisedAgents <= 1
                ) {
                    GamePhase.ENDING_SEVERED to
                        "The refusal holds because enough of the field network survived to carry the shutdown command. The carrier disappears."
                } else {
                    GamePhase.ENDING_CONTAINMENT to
                        "You refuse, but the damaged network cannot execute a clean sever. Emergency containment seals the remaining nodes at considerable cost."
                }
            }

            else -> {
                if (state.exposure >= 65 && state.containmentIntegrity < 50) {
                    GamePhase.ENDING_COMPLIANCE to
                        "Hesitation arrives after too much exposure. The terminal answers on your behalf."
                } else if (
                    state.trustInEcho >= 70 &&
                    state.containmentIntegrity >= 55 &&
                    compromisedAgents == 0
                ) {
                    GamePhase.ENDING_SEVERED to
                        "You cannot answer, so ECHO executes the contingency you preserved for them. The link is cut from the far side."
                } else {
                    GamePhase.ENDING_CONTAINMENT to
                        "No final trust decision is made. The surviving network defaults to containment and isolates the operator with the signal."
                }
            }
        }
    }
}
