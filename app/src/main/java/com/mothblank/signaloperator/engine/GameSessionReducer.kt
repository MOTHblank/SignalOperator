package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.Character
import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.GameState
import com.mothblank.signaloperator.models.HighScoreEntry
import com.mothblank.signaloperator.models.Location
import com.mothblank.signaloperator.models.LocationStatus
import com.mothblank.signaloperator.models.SignalData
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
        var archived = current.archivedSignals
        var ignored = current.ignoredSignals
        var solved = current.puzzlesSolved
        val solvedHotspots = current.solvedHotspots.toMutableSet()
        var locations: List<Location> = current.locations
        var characters: List<Character> = current.characters

        if (action == "COMMIT") {
            archived += 1
            solved += 1
            solvedHotspots.add(currentHotspot)

            val updatedLocations = current.locations.toMutableList()
            val searchText = "${signal.solution} ${signal.encodedMessage}".uppercase()
            val targetLoc = when {
                searchText.contains("SITE ALPHA") -> updatedLocations.find { it.name == "SITE ALPHA" }
                searchText.contains("SECTOR 4") -> updatedLocations.find { it.name == "SECTOR 4 RELAY" }
                searchText.contains("OUTPOST") -> updatedLocations.find { it.name == "ALPHA OUTPOST" }
                else -> null
            }

            if (targetLoc != null) {
                val index = updatedLocations.indexOf(targetLoc)
                updatedLocations[index] = targetLoc.copy(status = LocationStatus.INVESTIGATING)
            }

            val updatedCharacters = current.characters.toMutableList()
            if (
                updatedCharacters.isNotEmpty() &&
                updatedLocations.isNotEmpty() &&
                random.nextFloat() < 0.5f
            ) {
                val charIndex = random.nextInt(updatedCharacters.size)
                val randomLoc = updatedLocations.random(random)
                updatedCharacters[charIndex] =
                    updatedCharacters[charIndex].copy(locationId = randomLoc.id)
            }

            locations = updatedLocations
            characters = updatedCharacters
        } else {
            ignored += 1
            if (signal.solution == "DISCARD") {
                solvedHotspots.add(currentHotspot)
            }
        }

        var nextPhase = current.phase
        var corruption = current.corruptionLevel
        var required = current.puzzlesRequired
        var dialogueCue: DialogueCue? = null
        var highScore: HighScoreEntry? = null

        if (solved >= current.puzzlesRequired) {
            when (current.phase) {
                GamePhase.APTITUDE_TEST -> {
                    nextPhase = GamePhase.LIVE_INTRUSION
                    required = 5
                    solved = 0
                    solvedHotspots.clear()
                    dialogueCue = DialogueCue.LIVE_INTRUSION
                }

                GamePhase.LIVE_INTRUSION -> {
                    nextPhase = GamePhase.ACTIVE_INVESTIGATION
                    required = 6
                    solved = 0
                    solvedHotspots.clear()
                    corruption = 1f
                    dialogueCue = DialogueCue.ACTIVE_INVESTIGATION
                }

                GamePhase.ACTIVE_INVESTIGATION -> {
                    nextPhase = GamePhase.THE_INTERVIEW
                    required = 3
                    solved = 0
                    solvedHotspots.clear()
                    corruption = 2f
                    dialogueCue = DialogueCue.THE_INTERVIEW
                }

                GamePhase.THE_INTERVIEW -> {
                    nextPhase = when (solutionInput.uppercase().trim()) {
                        "I ACCEPT" -> GamePhase.ENDING_COMPLIANCE
                        "I REFUSE" -> GamePhase.ENDING_SEVERED
                        else -> GamePhase.ENDING_CONTAINMENT
                    }
                    solved = 0
                    solvedHotspots.clear()

                    highScore = HighScoreEntry(
                        operatorId = "OP-${current.seed % 1000}",
                        maxPhase = nextPhase.name.replace("ENDING_", ""),
                        intelSaved = archived,
                        score = (archived * 1000 - ignored * 200).coerceAtLeast(0)
                    )
                }

                else -> Unit
            }
        }

        return SessionResolution(
            state = current.copy(
                phase = nextPhase,
                corruptionLevel = corruption,
                archivedSignals = archived,
                ignoredSignals = ignored,
                puzzlesSolved = solved,
                puzzlesRequired = required,
                solvedHotspots = solvedHotspots,
                locations = locations,
                characters = characters,
                downloadProgress = 0f
            ),
            dialogueCue = dialogueCue,
            highScore = highScore
        )
    }
}
