package com.mothblank.signaloperator.engine

import android.content.Context
import android.util.Log
import com.mothblank.signaloperator.models.*
import org.json.JSONArray
import org.json.JSONObject

object SaveStateManager {
    private const val PREFS_NAME = "signal_operator_save_state"
    private const val KEY_SAVE_DATA = "save_data"
    private const val CURRENT_SCHEMA_VERSION = 4

    data class SavedData(
        val gameState: GameState,
        val logs: List<LogEntry>
    )

    fun hasSave(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_SAVE_DATA)
    }

    fun clearSave(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SAVE_DATA)
            .apply()
    }

    fun saveGame(context: Context, state: GameState, logs: List<LogEntry>) {
        try {
            val json = JSONObject()
            json.put("schemaVersion", CURRENT_SCHEMA_VERSION)
            json.put("phase", state.phase.name)
            json.put("corruptionLevel", state.corruptionLevel.toDouble())
            json.put("archivedSignals", state.archivedSignals)
            json.put("ignoredSignals", state.ignoredSignals)
            json.put("puzzlesSolved", state.puzzlesSolved)
            json.put("puzzlesRequired", state.puzzlesRequired)
            json.put("seed", state.seed)
            json.put("trustInEcho", state.trustInEcho)
            json.put("exposure", state.exposure)
            json.put("containmentIntegrity", state.containmentIntegrity)
            json.put("securityCharges", state.securityCharges)
            json.put("deadDropsRecovered", state.deadDropsRecovered)
            json.put("routineBroadcastsCleared", state.routineBroadcastsCleared)
            json.put("breachesPrevented", state.breachesPrevented)
            json.put("endingSummary", state.endingSummary ?: JSONObject.NULL)

            // Hotspots
            val hotspotsArray = JSONArray()
            state.solvedHotspots.forEach { hotspotsArray.put(it.toDouble()) }
            json.put("solvedHotspots", hotspotsArray)

            // Locations
            val locationsArray = JSONArray()
            state.locations.forEach { loc ->
                val locObj = JSONObject()
                locObj.put("id", loc.id)
                locObj.put("name", loc.name)
                locObj.put("x", loc.x.toDouble())
                locObj.put("y", loc.y.toDouble())
                locObj.put("status", loc.status.name)
                locObj.put("security", loc.security)
                locObj.put("threat", loc.threat)
                locationsArray.put(locObj)
            }
            json.put("locations", locationsArray)

            // Characters
            val charactersArray = JSONArray()
            state.characters.forEach { char ->
                val charObj = JSONObject()
                charObj.put("id", char.id)
                charObj.put("callsign", char.callsign)
                charObj.put("locationId", char.locationId ?: JSONObject.NULL)
                charObj.put("status", char.status.name)
                charactersArray.put(charObj)
            }
            json.put("characters", charactersArray)

            val linksArray = JSONArray()
            state.networkLinks.forEach { link ->
                val linkObj = JSONObject()
                linkObj.put("id", link.id)
                linkObj.put("fromLocationId", link.fromLocationId)
                linkObj.put("toLocationId", link.toLocationId)
                linkObj.put("status", link.status.name)
                linksArray.put(linkObj)
            }
            json.put("networkLinks", linksArray)

            val knownArray = JSONArray()
            state.knownFrequencies.forEach { known ->
                val knownObj = JSONObject()
                knownObj.put("frequency", known.frequency.toDouble())
                knownObj.put("label", known.label)
                knownObj.put("kind", known.kind.name)
                knownObj.put("lastPhase", known.lastPhase.name)
                knownObj.put("visits", known.visits)
                knownArray.put(knownObj)
            }
            json.put("knownFrequencies", knownArray)

            // Logs
            val logsArray = JSONArray()
            logs.forEach { log ->
                val logObj = JSONObject()
                logObj.put("id", log.id)
                logObj.put("timestamp", log.timestamp)
                logObj.put("text", log.text)
                logObj.put("type", log.type.name)
                logsArray.put(logObj)
            }
            json.put("logs", logsArray)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SAVE_DATA, json.toString()).apply()
            Log.d("SaveStateManager", "Game saved successfully.")
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error saving game", e)
        }
    }

    fun loadGame(context: Context): SavedData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dataStr = prefs.getString(KEY_SAVE_DATA, null) ?: return null
        try {
            val json = JSONObject(dataStr)
            val schemaVersion = json.optInt("schemaVersion", 1)
            if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
                Log.e("SaveStateManager", "Unsupported save schema version: $schemaVersion")
                return null
            }
            val phase = GamePhase.valueOf(json.getString("phase"))
            val corruptionLevel = json.getDouble("corruptionLevel").toFloat()
            val archivedSignals = json.getInt("archivedSignals")
            val ignoredSignals = json.getInt("ignoredSignals")
            val puzzlesSolved = json.getInt("puzzlesSolved")
            val puzzlesRequired = json.getInt("puzzlesRequired")
            val seed = json.getLong("seed")
            val trustInEcho = json.optInt("trustInEcho", 50)
            val exposure = json.optInt("exposure", 0)
            val containmentIntegrity = json.optInt("containmentIntegrity", 100)
            val securityCharges = json.optInt("securityCharges", 1)
            val deadDropsRecovered = json.optInt("deadDropsRecovered", 0)
            val routineBroadcastsCleared = json.optInt("routineBroadcastsCleared", 0)
            val breachesPrevented = json.optInt("breachesPrevented", 0)
            val endingSummary = if (json.isNull("endingSummary")) null else json.optString("endingSummary", null)

            // Hotspots
            val solvedHotspots = mutableSetOf<Float>()
            val hotspotsArray = json.getJSONArray("solvedHotspots")
            for (i in 0 until hotspotsArray.length()) {
                solvedHotspots.add(hotspotsArray.getDouble(i).toFloat())
            }

            // Locations
            val locations = mutableListOf<Location>()
            val locationsArray = json.getJSONArray("locations")
            for (i in 0 until locationsArray.length()) {
                val locObj = locationsArray.getJSONObject(i)
                locations.add(Location(
                    id = locObj.getString("id"),
                    name = locObj.getString("name"),
                    x = locObj.getDouble("x").toFloat(),
                    y = locObj.getDouble("y").toFloat(),
                    status = LocationStatus.valueOf(locObj.getString("status")),
                    security = locObj.optInt("security", 50),
                    threat = locObj.optInt("threat", 0)
                ))
            }

            // Characters
            val characters = mutableListOf<Character>()
            val charactersArray = json.getJSONArray("characters")
            for (i in 0 until charactersArray.length()) {
                val charObj = charactersArray.getJSONObject(i)
                val locationId = if (charObj.isNull("locationId")) null else charObj.getString("locationId")
                characters.add(Character(
                    id = charObj.getString("id"),
                    callsign = charObj.getString("callsign"),
                    locationId = locationId,
                    status = CharacterStatus.valueOf(charObj.getString("status"))
                ))
            }

            val networkLinks = mutableListOf<NetworkLink>()
            val linksArray = json.optJSONArray("networkLinks")
            if (linksArray != null) {
                for (i in 0 until linksArray.length()) {
                    val linkObj = linksArray.getJSONObject(i)
                    networkLinks.add(
                        NetworkLink(
                            id = linkObj.getString("id"),
                            fromLocationId = linkObj.getString("fromLocationId"),
                            toLocationId = linkObj.getString("toLocationId"),
                            status = LinkStatus.valueOf(linkObj.optString("status", LinkStatus.ACTIVE.name))
                        )
                    )
                }
            } else {
                networkLinks.addAll(
                    listOf(
                        NetworkLink("link-1", "loc-1", "loc-2"),
                        NetworkLink("link-2", "loc-2", "loc-3"),
                        NetworkLink("link-3", "loc-2", "loc-4"),
                        NetworkLink("link-4", "loc-1", "loc-3")
                    )
                )
            }

            val knownFrequencies = mutableListOf<KnownFrequency>()
            val knownArray = json.optJSONArray("knownFrequencies")
            if (knownArray != null) {
                for (i in 0 until knownArray.length()) {
                    val knownObj = knownArray.getJSONObject(i)
                    knownFrequencies.add(
                        KnownFrequency(
                            frequency = knownObj.getDouble("frequency").toFloat(),
                            label = knownObj.optString("label", "UNKNOWN"),
                            kind = SignalKind.valueOf(
                                knownObj.optString("kind", SignalKind.MISSION.name)
                            ),
                            lastPhase = GamePhase.valueOf(
                                knownObj.optString("lastPhase", phase.name)
                            ),
                            visits = knownObj.optInt("visits", 1)
                        )
                    )
                }
            }

            // Logs
            val logs = mutableListOf<LogEntry>()
            val logsArray = json.getJSONArray("logs")
            for (i in 0 until logsArray.length()) {
                val logObj = logsArray.getJSONObject(i)
                logs.add(LogEntry(
                    id = logObj.getString("id"),
                    timestamp = logObj.getString("timestamp"),
                    text = logObj.getString("text"),
                    type = LogType.valueOf(logObj.getString("type"))
                ))
            }

            val gameState = GameState(
                phase = phase,
                corruptionLevel = corruptionLevel,
                archivedSignals = archivedSignals,
                ignoredSignals = ignoredSignals,
                puzzlesSolved = puzzlesSolved,
                puzzlesRequired = puzzlesRequired,
                seed = seed,
                solvedHotspots = solvedHotspots,
                locations = locations,
                characters = characters,
                networkLinks = networkLinks,
                knownFrequencies = knownFrequencies,
                trustInEcho = trustInEcho,
                exposure = exposure,
                containmentIntegrity = containmentIntegrity,
                securityCharges = securityCharges,
                deadDropsRecovered = deadDropsRecovered,
                routineBroadcastsCleared = routineBroadcastsCleared,
                breachesPrevented = breachesPrevented,
                endingSummary = endingSummary,
                isMapViewActive = false,
                activeRouterGame = null,
                selectedLogEntry = null,
                downloadProgress = 0f
            )

            return SavedData(gameState, logs)
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error loading game", e)
            return null
        }
    }
}
