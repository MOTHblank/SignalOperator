package com.mothblank.signaloperator.engine

import android.content.Context
import android.util.Log
import com.mothblank.signaloperator.models.*
import org.json.JSONArray
import org.json.JSONObject

object SaveStateManager {
    private const val PREFS_NAME = "signal_operator_save_state"
    private const val KEY_SAVE_DATA = "save_data"

    data class SavedData(
        val gameState: GameState,
        val logs: List<LogEntry>
    )

    fun saveGame(context: Context, state: GameState, logs: List<LogEntry>) {
        try {
            val json = JSONObject()
            json.put("phase", state.phase.name)
            json.put("corruptionLevel", state.corruptionLevel.toDouble())
            json.put("archivedSignals", state.archivedSignals)
            json.put("ignoredSignals", state.ignoredSignals)
            json.put("puzzlesSolved", state.puzzlesSolved)
            json.put("puzzlesRequired", state.puzzlesRequired)
            json.put("seed", state.seed)

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
            val phase = GamePhase.valueOf(json.getString("phase"))
            val corruptionLevel = json.getDouble("corruptionLevel").toFloat()
            val archivedSignals = json.getInt("archivedSignals")
            val ignoredSignals = json.getInt("ignoredSignals")
            val puzzlesSolved = json.getInt("puzzlesSolved")
            val puzzlesRequired = json.getInt("puzzlesRequired")
            val seed = json.getLong("seed")

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
                    status = LocationStatus.valueOf(locObj.getString("status"))
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
