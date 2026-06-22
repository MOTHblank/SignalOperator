package com.mothblank.signaloperator.models

enum class GamePhase {
    APTITUDE_TEST,
    LIVE_INTRUSION,
    ACTIVE_INVESTIGATION,
    THE_INTERVIEW,
    ENDING_COMPLIANCE,
    ENDING_SEVERED,
    ENDING_CONTAINMENT
}

enum class PuzzleType {
    CRYPTOGRAPHY,
    SEQUENCE,
    LOGIC,
    OBSERVATION
}

data class SignalData(
    val id: String,
    val frequency: Float,
    val targetGain: Int,
    val targetFilter: Int,
    val puzzleType: PuzzleType,
    val encodedMessage: String,
    val solution: String,
    val objective: String,
    val cipherType: String,
    val sender: String,
    val isAnomalous: Boolean,
    val metadata: String
)

data class LogEntry(
    val id: String,
    val timestamp: String,
    val text: String,
    val type: LogType
)

enum class LogType {
    SYSTEM, INTERCEPT, ERROR, ACTION
}

data class Location(
    val id: String,
    val name: String,
    val x: Float,
    val y: Float,
    val status: LocationStatus = LocationStatus.SECURE
)

enum class LocationStatus {
    SECURE, INVESTIGATING, CORRUPTED
}

data class Character(
    val id: String,
    val callsign: String,
    val locationId: String?,
    val status: CharacterStatus = CharacterStatus.ACTIVE
)

enum class CharacterStatus {
    ACTIVE, MIA, COMPROMISED, ANOMALY
}

enum class MenuSubScreen {
    MAIN,
    OPTIONS,
    HIGHSCORES,
    HELP
}

data class GameState(
    val phase: GamePhase = GamePhase.APTITUDE_TEST,
    val corruptionLevel: Float = 0f,
    val archivedSignals: Int = 0,
    val ignoredSignals: Int = 0,
    val puzzlesSolved: Int = 0,
    val puzzlesRequired: Int = 3,
    val seed: Long = System.currentTimeMillis(),
    val solvedHotspots: Set<Float> = emptySet(),
    val locations: List<Location> = emptyList(),
    val characters: List<Character> = emptyList(),
    val isMapViewActive: Boolean = false,
    val activeRouterGame: RouterGameState? = null,
    val selectedLogEntry: LogEntry? = null,
    val downloadProgress: Float = 0f,
    val isInMenu: Boolean = true,
    val currentMenuScreen: MenuSubScreen = MenuSubScreen.MAIN,
    val isCrtEffectEnabled: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val isTtsEnabled: Boolean = true
)

data class HighScoreEntry(
    val operatorId: String,
    val maxPhase: String,
    val intelSaved: Int,
    val score: Int
)

enum class TilePath { STRAIGHT, CORNER, CROSS }

data class RouterTile(
    val x: Int,
    val y: Int,
    val type: TilePath,
    val rotationDegrees: Int // 0, 90, 180, 270
)

data class RouterGameState(
    val locationId: String,
    val grid: List<RouterTile>,
    val size: Int = 3,
    val entryY: Int = 1,
    val exitY: Int = 1,
    val timeLeftSeconds: Int = 15
)
