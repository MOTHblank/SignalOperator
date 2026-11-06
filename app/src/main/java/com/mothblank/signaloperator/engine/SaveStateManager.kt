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
    private const val CURRENT_SCHEMA_VERSION = 10

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
            json.put("deceptionsRejected", state.deceptionsRejected)
            json.put("deceptionsAccepted", state.deceptionsAccepted)
            json.put("analysisCycles", state.analysisCycles)
            json.put("investigationsPerformed", state.investigationsPerformed)
            json.put("adversaryStrategy", encodeAdversaryStrategy(state.adversaryStrategy))
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

            val recentEvidence = JSONArray()
            EvidenceCorrelation.activeWindow(evidence).forEach { item ->
                recentEvidence.put(encodeEvidence(item))
            }
            json.put("evidence", recentEvidence)

            // Older records have a compact positional representation, but are not discarded.
            val archivedEvidence = JSONArray()
            EvidenceCorrelation.archive(evidence).forEach { item ->
                archivedEvidence.put(encodeArchivedEvidence(item))
            }
            json.put("evidenceArchive", archivedEvidence)

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

    private fun encodeAdversaryStrategy(strategy: AdversaryStrategyState): JSONObject =
        JSONObject().apply {
            put("doctrine", strategy.doctrine.name)
            put("alertLevel", strategy.alertLevel)
            put("spoofSuccesses", strategy.spoofSuccesses)
            put("compromisedSuccesses", strategy.compromisedSuccesses)
            put("spoofExposures", strategy.spoofExposures)
            put("compromisedExposures", strategy.compromisedExposures)
            put("deceptionAttempts", strategy.deceptionAttempts)
            put("vulnerableSectorId", strategy.vulnerableSectorId ?: JSONObject.NULL)
            put("exposedIdentities", JSONArray().apply {
                strategy.exposedIdentities.sorted().forEach { put(it) }
            })
        }

    private fun decodeAdversaryStrategy(
        json: JSONObject?,
        seed: Long,
        locations: List<Location>
    ): AdversaryStrategyState {
        val fallback = AdversaryDirector.initial(seed, locations)
        if (json == null) return fallback

        val exposed = mutableSetOf<String>()
        json.optJSONArray("exposedIdentities")?.let { array ->
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(exposed::add)
            }
        }
        return AdversaryDirector.refreshVulnerability(
            fallback.copy(
                doctrine = runCatching {
                    AdversaryMethod.valueOf(json.optString("doctrine", fallback.doctrine.name))
                }.getOrDefault(fallback.doctrine),
                alertLevel = json.optInt("alertLevel", 0).coerceIn(0, 100),
                spoofSuccesses = json.optInt("spoofSuccesses", 0).coerceAtLeast(0),
                compromisedSuccesses = json.optInt("compromisedSuccesses", 0).coerceAtLeast(0),
                spoofExposures = json.optInt("spoofExposures", 0).coerceAtLeast(0),
                compromisedExposures = json.optInt("compromisedExposures", 0).coerceAtLeast(0),
                deceptionAttempts = json.optInt("deceptionAttempts", 0).coerceAtLeast(0),
                exposedIdentities = exposed,
                vulnerableSectorId = if (json.isNull("vulnerableSectorId")) {
                    fallback.vulnerableSectorId
                } else {
                    json.optString("vulnerableSectorId").takeIf { it.isNotBlank() }
                        ?: fallback.vulnerableSectorId
                }
            ),
            locations
        )
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
        put("carrierFingerprint", item.carrierFingerprint ?: JSONObject.NULL)
        put("suspectedDeception", item.suspectedDeception)
        put("operatorDisposition", item.operatorDisposition.name)
        put("authenticationStatus", item.authenticationStatus.name)
        put("investigationActions", JSONArray().apply {
            item.investigationActions.sortedBy { it.name }.forEach { put(it.name) }
        })
        put("investigationNotes", JSONArray().apply {
            item.investigationNotes.forEach { put(it) }
        })

        val claimsObject = JSONObject()
        item.claims.forEach { (key, value) -> claimsObject.put(key, value) }
        put("claims", claimsObject)
        put("claimProvenance", encodeClaimProvenance(item.claimProvenance))

        val missingArray = JSONArray()
        item.missingFields.sorted().forEach { missingArray.put(it) }
        put("missingFields", missingArray)
    }

    private fun encodeClaimProvenance(observations: Map<String, ClaimObservation>): JSONObject =
        JSONObject().apply {
            observations.forEach { (key, observation) ->
                put(key, JSONArray().apply {
                    put(observation.category.name)
                    put(observation.observedSequence)
                    put(observation.reportedBy ?: JSONObject.NULL)
                    put(observation.transmissionId ?: JSONObject.NULL)
                })
            }
        }

    private fun encodeArchivedEvidence(item: InterceptEvidence): JSONArray = JSONArray().apply {
        put(item.id)
        put(item.sequence)
        put(item.frequencyMhz.toDouble())
        put(item.callsign ?: JSONObject.NULL)
        put(item.sectorId ?: JSONObject.NULL)
        put(item.message)
        put(item.reliability.name)
        put(JSONObject().apply { item.claims.forEach { (key, value) -> put(key, value) } })
        put(item.carrierFingerprint ?: JSONObject.NULL)
        put(JSONArray().apply { item.missingFields.sorted().forEach { put(it) } })
        put(item.suspectedDeception)
        put(item.operatorDisposition.name)
        put(encodeClaimProvenance(item.claimProvenance))
        put(JSONArray().apply {
            item.investigationActions.sortedBy { it.name }.forEach { put(it.name) }
        })
        put(JSONArray().apply { item.investigationNotes.forEach { put(it) } })
        put(item.authenticationStatus.name)
    }

    private fun decodeArchivedEvidence(row: JSONArray): InterceptEvidence =
        decodeEvidence(JSONObject().apply {
            put("id", row.getString(0))
            put("sequence", row.getLong(1))
            put("frequencyMhz", row.getDouble(2))
            put("callsign", if (row.isNull(3)) JSONObject.NULL else row.getString(3))
            put("sectorId", if (row.isNull(4)) JSONObject.NULL else row.getString(4))
            put("message", row.getString(5))
            put("reliability", row.getString(6))
            put("claims", row.getJSONObject(7))
            put("carrierFingerprint", if (row.isNull(8)) JSONObject.NULL else row.getString(8))
            put("missingFields", row.getJSONArray(9))
            put("suspectedDeception", row.getBoolean(10))
            put("operatorDisposition", row.getString(11))
            put("claimProvenance", row.optJSONObject(12) ?: JSONObject())
            put("investigationActions", row.optJSONArray(13) ?: JSONArray())
            put("investigationNotes", row.optJSONArray(14) ?: JSONArray())
            put(
                "authenticationStatus",
                row.optString(15, AuthenticationStatus.NOT_REQUESTED.name)
            )
        })

    private fun decodeEvidence(json: JSONObject): InterceptEvidence {
        val claims = mutableMapOf<String, String>()
        json.optJSONObject("claims")?.let { objectJson ->
            val keys = objectJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                claims[key] = objectJson.optString(key)
            }
        }
        val observations = mutableMapOf<String, ClaimObservation>()
        json.optJSONObject("claimProvenance")?.let { objectJson ->
            val keys = objectJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = objectJson.optJSONArray(key) ?: continue
                observations[key] = ClaimObservation(
                    category = runCatching {
                        EvidenceClaimCategory.valueOf(value.getString(0))
                    }.getOrDefault(EvidenceCorrelation.categoryFor(key)),
                    observedSequence = value.optLong(1, json.optLong("sequence", 0L)),
                    reportedBy = if (value.isNull(2)) null else value.optString(2),
                    transmissionId = if (value.isNull(3)) null else value.optString(3)
                )
            }
        }
        val missing = mutableSetOf<String>()
        json.optJSONArray("missingFields")?.let { array ->
            for (i in 0 until array.length()) {
                missing += array.optString(i)
            }
        }
        val investigationActions = mutableSetOf<InvestigationActionType>()
        json.optJSONArray("investigationActions")?.let { array ->
            for (i in 0 until array.length()) {
                runCatching {
                    InvestigationActionType.valueOf(array.optString(i))
                }.getOrNull()?.let(investigationActions::add)
            }
        }
        val investigationNotes = mutableListOf<String>()
        json.optJSONArray("investigationNotes")?.let { array ->
            for (i in 0 until array.length()) {
                array.optString(i).takeIf { it.isNotBlank() }?.let(investigationNotes::add)
            }
        }
        val authenticationStatus = runCatching {
            AuthenticationStatus.valueOf(
                json.optString(
                    "authenticationStatus",
                    AuthenticationStatus.NOT_REQUESTED.name
                )
            )
        }.getOrDefault(AuthenticationStatus.NOT_REQUESTED)
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
            carrierFingerprint = if (json.isNull("carrierFingerprint")) null else json.optString("carrierFingerprint"),
            missingFields = missing,
            suspectedDeception = json.optBoolean(
                "suspectedDeception",
                json.optBoolean("suspectedSpoof", false)
            ),
            operatorDisposition = runCatching {
                EvidenceDisposition.valueOf(
                    json.optString("operatorDisposition", EvidenceDisposition.UNREVIEWED.name)
                )
            }.getOrDefault(EvidenceDisposition.UNREVIEWED),
            claimProvenance = observations,
            investigationActions = investigationActions,
            investigationNotes = investigationNotes,
            authenticationStatus = authenticationStatus
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
            val storedPuzzlesRequired = json.getInt("puzzlesRequired")
            val puzzlesRequired = if (
                schemaVersion < 7 &&
                phase == GamePhase.ACTIVE_INVESTIGATION &&
                storedPuzzlesRequired == 6
            ) {
                8
            } else {
                storedPuzzlesRequired
            }
            val seed = json.getLong("seed")
            val trustInEcho = json.optInt("trustInEcho", 50)
            val exposure = json.optInt("exposure", 0)
            val containmentIntegrity = json.optInt("containmentIntegrity", 100)
            val securityCharges = json.optInt("securityCharges", 1)
            val deadDropsRecovered = json.optInt("deadDropsRecovered", 0)
            val routineBroadcastsCleared = json.optInt("routineBroadcastsCleared", 0)
            val breachesPrevented = json.optInt("breachesPrevented", 0)
            val deceptionsRejected = json.optInt("deceptionsRejected", 0)
            val deceptionsAccepted = json.optInt("deceptionsAccepted", 0)
            val analysisCycles = json.optInt("analysisCycles", 3)
                .coerceIn(0, InvestigationActions.MAX_ANALYSIS_CYCLES)
            val investigationsPerformed = json.optInt("investigationsPerformed", 0)
                .coerceAtLeast(0)
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
            val archivedEvidence = mutableListOf<InterceptEvidence>()
            json.optJSONArray("evidenceArchive")?.let { array ->
                for (i in 0 until array.length()) {
                    archivedEvidence += decodeArchivedEvidence(array.getJSONArray(i))
                }
            }
            val retainedEvidence = EvidenceCorrelation.retain(archivedEvidence + evidence)
            val adversaryStrategy = decodeAdversaryStrategy(
                json.optJSONObject("adversaryStrategy"),
                seed,
                locations
            )

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
                deceptionsRejected = deceptionsRejected,
                deceptionsAccepted = deceptionsAccepted,
                analysisCycles = analysisCycles,
                investigationsPerformed = investigationsPerformed,
                adversaryStrategy = adversaryStrategy,
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
