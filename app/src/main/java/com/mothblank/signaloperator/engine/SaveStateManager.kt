package com.mothblank.signaloperator.engine

import android.content.Context
import android.util.Log
import java.io.IOException
import com.mothblank.signaloperator.models.*
import org.json.JSONArray
import org.json.JSONObject

object SaveStateManager {
    private const val PREFS_NAME = "signal_operator_save_state"
    private const val KEY_SAVE_DATA = "save_data"
    private const val CURRENT_SCHEMA_VERSION = 5

    data class SavedData(
        val gameState: GameState,
        val logs: List<LogEntry>,
        val importantLogs: List<LogEntry> = emptyList(),
        val evidence: List<InterceptEvidence> = emptyList()
    )

    sealed interface LoadOutcome {
        data object NoSave : LoadOutcome
        data class Loaded(val data: SavedData) : LoadOutcome
        data class Corrupt(val description: String) : LoadOutcome
        data class UnsupportedVersion(val version: Int) : LoadOutcome
    }

    private const val KEY_RECOVERY_PREFIX = "recovery_backup_"

    fun hasRecoveryBackup(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .all.keys.any { it.startsWith(KEY_RECOVERY_PREFIX) }

    fun hasSave(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_SAVE_DATA)
    }

    /**
     * Removes the active campaign only after a synchronous disk write. Unreadable
     * saves are copied verbatim to a separately named recovery slot beforehand.
     * The backup is never cleared by ordinary new-game or reset operations.
     */
    fun clearSave(context: Context): Result<Unit> = runCatching {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val outcome = loadGame(context)
        val editor = prefs.edit()
        if (outcome is LoadOutcome.Corrupt || outcome is LoadOutcome.UnsupportedVersion) {
            val raw = prefs.all[KEY_SAVE_DATA]?.toString()
                ?: throw IOException("Unreadable campaign could not be backed up")
            val recoveryKey = KEY_RECOVERY_PREFIX + System.currentTimeMillis() +
                "_" + java.util.UUID.randomUUID().toString()
            editor.putString(recoveryKey, raw)
        }
        editor.remove(KEY_SAVE_DATA)
        if (!editor.commit()) throw IOException("Failed to clear campaign; original data remains")
    }

    /** Raw recovery copies are left untouched by normal gameplay and may be exported later. */
    fun recoveryCopies(context: Context): Map<String, String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .all.filterKeys { it.startsWith(KEY_RECOVERY_PREFIX) }
            .mapValues { it.value?.toString().orEmpty() }

    fun saveGame(
        context: Context,
        state: GameState,
        logs: List<LogEntry>,
        importantLogs: List<LogEntry> = emptyList(),
        evidence: List<InterceptEvidence> = emptyList()
    ): Result<Unit> {
        try {
            val json = JSONObject()
            val retainedLogs = OperatorLogRetention.restore(logs, importantLogs)
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
            retainedLogs.recent.forEach { log ->
                logsArray.put(encodeLog(log))
            }
            json.put("logs", logsArray)

            val importantArray = JSONArray()
            retainedLogs.important.forEach { importantArray.put(encodeLog(it)) }
            json.put("importantLogs", importantArray)

            val evidenceArray = JSONArray()
            EvidenceCorrelation.retain(evidence).forEach { item ->
                evidenceArray.put(encodeEvidence(item))
            }
            json.put("evidence", evidenceArray)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // commit() reports actual persistence failures; apply() only enqueues a write.
            if (prefs.edit().putString(KEY_SAVE_DATA, json.toString()).commit()) {
                Log.d("SaveStateManager", "Game saved successfully.")
                Result.success(Unit)
            } else {
                val failure = IOException("SharedPreferences could not persist the game save")
                Log.e("SaveStateManager", "Error saving game", failure)
                Result.failure(failure)
            }
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error saving game", e)
            Result.failure(e)
        }
    }

    private fun encodeLog(entry: LogEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("timestamp", entry.timestamp)
        put("text", entry.text)
        put("type", entry.type.name)
    }

    private fun decodeLog(json: JSONObject): LogEntry = LogEntry(
        id = json.getString("id"),
        timestamp = json.getString("timestamp"),
        text = json.getString("text"),
        type = LogType.valueOf(json.getString("type"))
    )

    private fun encodeEvidence(item: InterceptEvidence): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("sequence", item.sequence)
        put("frequencyMhz", item.frequencyMhz.toDouble())
        put("callsign", item.callsign ?: JSONObject.NULL)
        put("sectorId", item.sectorId ?: JSONObject.NULL)
        put("message", item.message)
        put("reliability", item.reliability.name)
        put("suspectedSpoof", item.suspectedSpoof)

        val claimsObject = JSONObject()
        item.claims.forEach { (key, value) -> claimsObject.put(key, value) }
        put("claims", claimsObject)

        val missingArray = JSONArray()
        item.missingFields.sorted().forEach { missingArray.put(it) }
        put("missingFields", missingArray)
    }

    private fun decodeEvidence(json: JSONObject): InterceptEvidence {
        val claims = mutableMapOf<String, String>()
        json.optJSONObject("claims")?.let { objectJson ->
            val keys = objectJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                claims[key] = objectJson.optString(key)
            }
        }
        val missing = mutableSetOf<String>()
        json.optJSONArray("missingFields")?.let { array ->
            for (i in 0 until array.length()) {
                missing += array.optString(i)
            }
        }
        val reliability = runCatching {
            EvidenceReliability.valueOf(
                json.optString("reliability", EvidenceReliability.UNVERIFIED.name)
            )
        }.getOrDefault(EvidenceReliability.UNVERIFIED)

        return InterceptEvidence(
            id = json.getString("id"),
            sequence = json.optLong("sequence", 0L),
            frequencyMhz = json.getDouble("frequencyMhz").toFloat(),
            callsign = if (json.isNull("callsign")) null else json.optString("callsign"),
            sectorId = if (json.isNull("sectorId")) null else json.optString("sectorId"),
            message = json.optString("message"),
            reliability = reliability,
            claims = claims,
            missingFields = missing,
            suspectedSpoof = json.optBoolean("suspectedSpoof", false)
        )
    }

    fun loadGame(context: Context): LoadOutcome {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_SAVE_DATA)) return LoadOutcome.NoSave
        try {
            val dataStr = prefs.getString(KEY_SAVE_DATA, null)
                ?: return LoadOutcome.Corrupt("Save data is empty")
            val json = JSONObject(dataStr)
            val schemaVersion = if (json.has("schemaVersion")) json.getInt("schemaVersion") else 1
            if (schemaVersion > CURRENT_SCHEMA_VERSION) {
                Log.w("SaveStateManager", "Save from newer schema version: $schemaVersion")
                return LoadOutcome.UnsupportedVersion(schemaVersion)
            }
            if (schemaVersion < 1) {
                return LoadOutcome.Corrupt("Invalid schema version: $schemaVersion")
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
            val endingSummary = json.optString("endingSummary", "").takeIf { it.isNotBlank() }

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
                logs.add(decodeLog(logsArray.getJSONObject(i)))
            }
            val importantLogs = mutableListOf<LogEntry>()
            val importantArray = json.optJSONArray("importantLogs")
            if (importantArray != null) {
                for (i in 0 until importantArray.length()) {
                    importantLogs.add(decodeLog(importantArray.getJSONObject(i)))
                }
            }
            val retainedLogs = OperatorLogRetention.restore(logs, importantLogs)

            val evidence = mutableListOf<InterceptEvidence>()
            json.optJSONArray("evidence")?.let { array ->
                for (i in 0 until array.length()) {
                    evidence += decodeEvidence(array.getJSONObject(i))
                }
            }
            val retainedEvidence = EvidenceCorrelation.retain(evidence)

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

            return LoadOutcome.Loaded(
                SavedData(
                    gameState = gameState,
                    logs = retainedLogs.recent,
                    importantLogs = retainedLogs.important,
                    evidence = retainedEvidence
                )
            )
        } catch (e: Exception) {
            Log.e("SaveStateManager", "Error loading game; original saved bytes retained", e)
            return LoadOutcome.Corrupt("Campaign data could not be decoded")
        }
    }
}
