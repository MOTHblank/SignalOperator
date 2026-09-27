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

enum class SignalKind {
    MISSION,
    MUNDANE_BROADCAST,
    DEAD_DROP
}

enum class Urgency {
    ROUTINE,
    ELEVATED,
    HIGH,
    CRITICAL
}

enum class WorldEffectType {
    ADD_LOCATION_THREAT,
    ADD_LOCATION_SECURITY,
    MARK_LOCATION_INVESTIGATING,
    MOVE_CHARACTER,
    SET_CHARACTER_ACTIVE,
    SET_CHARACTER_MIA,
    SET_CHARACTER_COMPROMISED,
    ADD_TRUST,
    ADD_EXPOSURE,
    ADD_CONTAINMENT,
    ADD_SECURITY_CHARGES
}

data class WorldEffect(
    val type: WorldEffectType,
    val targetId: String? = null,
    val amount: Int = 0,
    val destinationId: String? = null
)

data class SignalOutcome(
    val eventId: String,
    val briefing: String,
    val urgency: Urgency = Urgency.ROUTINE,
    val locationId: String? = null,
    val actorId: String? = null,
    val intelValue: Int = 1,
    val effects: List<WorldEffect> = emptyList()
)

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
    val metadata: String,
    val kind: SignalKind = SignalKind.MISSION,
    val outcome: SignalOutcome? = null
)

data class SignalRuntimeState(
    val frequency: Float = 88f,
    val gain: Int = 50,
    val filter: Int = 50,
    val activeSignal: SignalData? = null,
    val stability: Float = 0f,
    val proximity: Float = 0f
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
    val status: LocationStatus = LocationStatus.SECURE,
    val security: Int = 50,
    val threat: Int = 0
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

enum class LinkStatus {
    ACTIVE, JAMMED, CORRUPTED
}

data class NetworkLink(
    val id: String,
    val fromLocationId: String,
    val toLocationId: String,
    val status: LinkStatus = LinkStatus.ACTIVE
)

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
    val networkLinks: List<NetworkLink> = emptyList(),
    val trustInEcho: Int = 50,
    val exposure: Int = 0,
    val containmentIntegrity: Int = 100,
    val securityCharges: Int = 1,
    val deadDropsRecovered: Int = 0,
    val routineBroadcastsCleared: Int = 0,
    val breachesPrevented: Int = 0,
    val endingSummary: String? = null,
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
    val rotationDegrees: Int
)

data class RouterGameState(
    val locationId: String,
    val grid: List<RouterTile>,
    val size: Int = 3,
    val entryY: Int = 1,
    val exitY: Int = 1,
    val timeLeftSeconds: Int = 15
)

data class DialogueLine(
    val speaker: String,
    val text: String
)
