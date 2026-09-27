package com.mothblank.signaloperator

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mothblank.signaloperator.audio.AndroidTextToSpeech
import com.mothblank.signaloperator.audio.SoundManager
import com.mothblank.signaloperator.audio.TextToSpeechEngine
import com.mothblank.signaloperator.engine.DialogueCue
import com.mothblank.signaloperator.engine.GameSessionReducer
import com.mothblank.signaloperator.engine.HotspotPlanner
import com.mothblank.signaloperator.engine.ProceduralSignalEngine
import com.mothblank.signaloperator.engine.RouterPuzzleEngine
import com.mothblank.signaloperator.engine.SaveStateManager
import com.mothblank.signaloperator.engine.SignalRequest
import com.mothblank.signaloperator.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.mothblank.signaloperator.engine.SystemData

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = ProceduralSignalEngine()
    private val soundManager = SoundManager(application)
    
    private val androidTts: TextToSpeechEngine = AndroidTextToSpeech(application, soundManager)
    
    private fun getSystemData(): SystemData {
        val batteryManager = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val model = Build.MODEL
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val time = sdf.format(Date())
        
        return SystemData(level, model, time)
    }

    private fun getTts(): TextToSpeechEngine {
        val signal = activeSignalValue
        val tts = androidTts
        
        if (tts.isReady() && signal != null) {
            val voices = tts.getAvailableVoices()
            if (voices.isNotEmpty()) {
                // Use signal ID to deterministically pick a voice for this specific signal
                val seed = signal.id.hashCode().toLong()
                val random = Random(seed)
                val voiceName = voices[random.nextInt(voices.size)]
                tts.setVoice(voiceName)
            }
        }
        
        return tts
    }
    
    private val PREFS_SETTINGS = "signal_operator_settings"
    private val KEY_CRT = "crt_enabled"
    private val KEY_SOUND = "sound_enabled"
    private val KEY_TTS = "tts_enabled"

    private val PREFS_HIGHSCORES = "signal_operator_highscores"
    private val KEY_HIGHSCORES = "highscores"

    private val _highScores = MutableStateFlow<List<HighScoreEntry>>(emptyList())
    val highScores: StateFlow<List<HighScoreEntry>> = _highScores.asStateFlow()

    private val _hasSavedGame = MutableStateFlow(false)
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _signalRuntime = MutableStateFlow(SignalRuntimeState())
    val signalRuntime: StateFlow<SignalRuntimeState> = _signalRuntime.asStateFlow()

    private var frequencyValue: Float
        get() = _signalRuntime.value.frequency
        set(value) {
            _signalRuntime.value = _signalRuntime.value.copy(frequency = value)
        }

    private var gainValue: Int
        get() = _signalRuntime.value.gain
        set(value) {
            _signalRuntime.value = _signalRuntime.value.copy(gain = value)
        }

    private var filterValue: Int
        get() = _signalRuntime.value.filter
        set(value) {
            _signalRuntime.value = _signalRuntime.value.copy(filter = value)
        }

    private var activeSignalValue: SignalData?
        get() = _signalRuntime.value.activeSignal
        set(value) {
            _signalRuntime.value = _signalRuntime.value.copy(activeSignal = value)
        }

    private var stabilityValue: Float
        get() = _signalRuntime.value.stability
        set(value) {
            _signalRuntime.value = _signalRuntime.value.copy(stability = value)
        }

    private var proximityValue: Float
        get() = _signalRuntime.value.proximity
        set(value) {
            _signalRuntime.value = _signalRuntime.value.copy(proximity = value)
        }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _activeDialogue = MutableStateFlow<List<DialogueLine>?>(null)
    val activeDialogue: StateFlow<List<DialogueLine>?> = _activeDialogue.asStateFlow()

    private val _currentDialogueIndex = MutableStateFlow(0)
    val currentDialogueIndex: StateFlow<Int> = _currentDialogueIndex.asStateFlow()

    private val hotspots = mutableListOf<Float>()
    private val hotspotKinds = mutableMapOf<Float, SignalKind>()
    private var isScanning = false
    private var lockedHotspot: Float? = null
    private var activeSignalFrequency: Float? = null
    private var hardwareFailureJob: Job? = null
    private var radioLoopJob: Job? = null
    private var minHotspotDistance = 100f
    private var breachMonitorJob: Job? = null
    private var isAppInForeground = true

    private fun isEndingPhase(phase: GamePhase): Boolean {
        return phase == GamePhase.ENDING_COMPLIANCE ||
            phase == GamePhase.ENDING_SEVERED ||
            phase == GamePhase.ENDING_CONTAINMENT
    }

    private fun isRuntimeActive(): Boolean {
        val state = _gameState.value
        return isAppInForeground && !state.isInMenu && !isEndingPhase(state.phase)
    }

    init {
        loadSettings()
        val saved = SaveStateManager.loadGame(application)
        _hasSavedGame.value = saved != null

        if (saved != null) {
            _gameState.value = saved.gameState.copy(
                isCrtEffectEnabled = _gameState.value.isCrtEffectEnabled,
                isSoundEnabled = _gameState.value.isSoundEnabled,
                isTtsEnabled = _gameState.value.isTtsEnabled,
                isInMenu = true,
                activeRouterGame = null,
                selectedLogEntry = null,
                downloadProgress = 0f
            )
            _logs.value = saved.logs
            generateHotspots(saved.gameState.phase, saved.gameState.seed, saved.gameState.puzzlesRequired)
        } else {
            initializeWorld()
            generateHotspots(_gameState.value.phase, _gameState.value.seed, _gameState.value.puzzlesRequired)
        }

        if (_gameState.value.isSoundEnabled) {
            soundManager.startStatic()
        }
        updateAudioParameters()
        startHardwareFailureMonitor()
        startBreachMonitor()
        loadHighScores()
    }

    private fun initializeWorld() {
        val initialLocations = listOf(
            Location("loc-1", "SITE ALPHA", 0.2f, 0.3f, security = 65),
            Location("loc-2", "SECTOR 4 RELAY", 0.5f, 0.5f, security = 55),
            Location("loc-3", "ALPHA OUTPOST", 0.8f, 0.2f, security = 50),
            Location("loc-4", "EXCLUSION ZONE", 0.6f, 0.8f, security = 35, threat = 15)
        )
        val initialCharacters = listOf(
            Character("char-1", "ECHO-ACTUAL", "loc-1"),
            Character("char-2", "ECHO-2", "loc-3"),
            Character("char-3", "THE SUBJECT", null, CharacterStatus.ANOMALY)
        )
        val initialLinks = listOf(
            NetworkLink("link-1", "loc-1", "loc-2"),
            NetworkLink("link-2", "loc-2", "loc-3"),
            NetworkLink("link-3", "loc-2", "loc-4"),
            NetworkLink("link-4", "loc-1", "loc-3")
        )
        _gameState.value = _gameState.value.copy(
            locations = initialLocations,
            characters = initialCharacters,
            networkLinks = initialLinks
        )
    }

    private fun loadSettings() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val crt = prefs.getBoolean(KEY_CRT, true)
        val sound = prefs.getBoolean(KEY_SOUND, true)
        val tts = prefs.getBoolean(KEY_TTS, true)
        _gameState.value = _gameState.value.copy(
            isCrtEffectEnabled = crt,
            isSoundEnabled = sound,
            isTtsEnabled = tts,
            isInMenu = true
        )
    }

    private fun saveSetting(key: String, value: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun loadHighScores() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_HIGHSCORES, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_HIGHSCORES, null)
        val list = mutableListOf<HighScoreEntry>()
        if (rawJson != null) {
            try {
                val array = JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(HighScoreEntry(
                        operatorId = obj.getString("operatorId"),
                        maxPhase = obj.getString("maxPhase"),
                        intelSaved = obj.getInt("intelSaved"),
                        score = obj.getInt("score")
                    ))
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error parsing highscores", e)
            }
        }
        
        list.sortByDescending { it.score }
        _highScores.value = list
    }

    private fun saveHighScoresRaw(list: List<HighScoreEntry>) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_HIGHSCORES, Context.MODE_PRIVATE)
        val array = JSONArray()
        list.forEach {
            val obj = JSONObject()
            obj.put("operatorId", it.operatorId)
            obj.put("maxPhase", it.maxPhase)
            obj.put("intelSaved", it.intelSaved)
            obj.put("score", it.score)
            array.put(obj)
        }
        prefs.edit().putString(KEY_HIGHSCORES, array.toString()).apply()
    }

    fun saveHighScore(entry: HighScoreEntry) {
        val currentList = _highScores.value.toMutableList()
        currentList.add(entry)
        currentList.sortByDescending { it.score }
        val trimmed = if (currentList.size > 10) currentList.subList(0, 10) else currentList
        saveHighScoresRaw(trimmed)
        _highScores.value = trimmed
    }

    fun setMenuScreen(screen: MenuSubScreen) {
        _gameState.value = _gameState.value.copy(currentMenuScreen = screen)
    }

    fun toggleCrtEffect() {
        val newVal = !_gameState.value.isCrtEffectEnabled
        _gameState.value = _gameState.value.copy(isCrtEffectEnabled = newVal)
        saveSetting(KEY_CRT, newVal)
    }

    fun toggleSound() {
        val newVal = !_gameState.value.isSoundEnabled
        _gameState.value = _gameState.value.copy(isSoundEnabled = newVal)
        saveSetting(KEY_SOUND, newVal)
        if (newVal) {
            soundManager.startStatic()
            updateAudioParameters()
        } else {
            soundManager.stopStatic()
        }
    }

    fun toggleTts() {
        val newVal = !_gameState.value.isTtsEnabled
        _gameState.value = _gameState.value.copy(isTtsEnabled = newVal)
        saveSetting(KEY_TTS, newVal)
        if (!newVal) {
            androidTts.stop()
        }
    }

    fun playClick() {
        soundManager.playClick()
    }

    fun playTypewriterTick() {
        soundManager.playTypewriterTick()
    }

    fun playAlert() {
        soundManager.playAlert()
    }

    fun clearData() {
        val settingsPrefs = getApplication<Application>().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        settingsPrefs.edit().clear().apply()

        val scorePrefs = getApplication<Application>().getSharedPreferences(PREFS_HIGHSCORES, Context.MODE_PRIVATE)
        scorePrefs.edit().clear().apply()

        SaveStateManager.clearSave(getApplication())
        _hasSavedGame.value = false
        stopRadioLoop()
        routerCountdownJob?.cancel()

        _logs.value = emptyList()
        activeSignalValue = null
        lockedHotspot = null
        activeSignalFrequency = null
        stabilityValue = 0f
        proximityValue = 0f
        frequencyValue = 88f
        gainValue = 50
        filterValue = 50

        _gameState.value = GameState(
            isInMenu = true,
            currentMenuScreen = MenuSubScreen.OPTIONS,
            isCrtEffectEnabled = true,
            isSoundEnabled = true,
            isTtsEnabled = true
        )
        initializeWorld()
        generateHotspots(_gameState.value.phase, _gameState.value.seed, _gameState.value.puzzlesRequired)

        soundManager.startStatic()
        updateAudioParameters()
        loadHighScores()
    }

    fun deleteSave() {
        SaveStateManager.clearSave(getApplication())
        _hasSavedGame.value = false
    }

    fun startGame() {
        val settings = _gameState.value
        stopRadioLoop()
        routerCountdownJob?.cancel()

        _gameState.value = GameState(
            phase = GamePhase.APTITUDE_TEST,
            seed = System.currentTimeMillis(),
            isInMenu = false,
            currentMenuScreen = MenuSubScreen.MAIN,
            isCrtEffectEnabled = settings.isCrtEffectEnabled,
            isSoundEnabled = settings.isSoundEnabled,
            isTtsEnabled = settings.isTtsEnabled
        )
        initializeWorld()
        generateHotspots(GamePhase.APTITUDE_TEST, _gameState.value.seed, _gameState.value.puzzlesRequired)

        activeSignalValue = null
        lockedHotspot = null
        activeSignalFrequency = null
        frequencyValue = 88f
        gainValue = 50
        filterValue = 50
        stabilityValue = 0f
        proximityValue = 0f

        if (_gameState.value.isSoundEnabled) {
            soundManager.startStatic()
            updateAudioParameters()
        } else {
            soundManager.stopStatic()
        }

        _logs.value = emptyList()
        addLog("SYSTEM INITIALIZED. SCANNER STANDBY.", LogType.SYSTEM)
        saveGame()
        triggerDialogue(getIntroDialogue())
    }

    fun continueGame() {
        if (!_hasSavedGame.value) {
            startGame()
            return
        }

        val state = _gameState.value
        _gameState.value = state.copy(
            isInMenu = false,
            currentMenuScreen = MenuSubScreen.MAIN,
            activeRouterGame = null,
            selectedLogEntry = null,
            downloadProgress = 0f,
            isMapViewActive = false
        )
        activeSignalValue = null
        lockedHotspot = null
        activeSignalFrequency = null
        stabilityValue = 0f
        proximityValue = 0f
        generateHotspots(state.phase, state.seed, state.puzzlesRequired)
        updateProximity()

        if (_gameState.value.isSoundEnabled) {
            soundManager.startStatic()
            updateAudioParameters()
        } else {
            soundManager.stopStatic()
        }
    }

    fun returnToMenu() {
        stopRadioLoop()
        routerCountdownJob?.cancel()
        _gameState.value = _gameState.value.copy(
            isInMenu = true,
            currentMenuScreen = MenuSubScreen.MAIN,
            activeRouterGame = null,
            selectedLogEntry = null
        )
        if (!_gameState.value.isSoundEnabled) {
            soundManager.stopStatic()
        }
        saveGame()
    }

    private fun saveGame() {
        SaveStateManager.saveGame(
            getApplication(),
            _gameState.value,
            _logs.value
        )
        _hasSavedGame.value = true
    }

    fun resetGame() {
        SaveStateManager.clearSave(getApplication())
        _hasSavedGame.value = false
        startGame()
    }

    private fun startBreachMonitor() {
        breachMonitorJob?.cancel()
        breachMonitorJob = viewModelScope.launch {
            while (true) {
                delay(25000) // check every 25 seconds
                val state = _gameState.value
                if (isRuntimeActive() &&
                    state.activeRouterGame == null &&
                    activeSignalValue == null &&
                    _activeDialogue.value == null &&
                    (state.phase == GamePhase.ACTIVE_INVESTIGATION || state.phase == GamePhase.THE_INTERVIEW)) {
                    val breachCandidates = state.locations
                        .filter {
                            it.status == LocationStatus.INVESTIGATING ||
                                (it.status == LocationStatus.SECURE && it.threat >= it.security)
                        }
                        .sortedByDescending { it.threat - it.security }

                    val targetLoc = breachCandidates.firstOrNull()
                    if (targetLoc != null) {
                        startRouterGame(targetLoc.id)
                        addLog(
                            "WARNING: ${targetLoc.name} THREAT ${targetLoc.threat} / SECURITY ${targetLoc.security}. FIREWALL CHALLENGE REQUIRED.",
                            LogType.ERROR
                        )
                        saveGame()
                    }
                }
            }
        }
    }

    fun toggleMapView() {
        val state = _gameState.value
        _gameState.value = state.copy(isMapViewActive = !state.isMapViewActive)
    }

    fun handleLocationClick(location: Location) {
        when (location.status) {
            LocationStatus.INVESTIGATING -> startRouterGame(location.id)
            LocationStatus.SECURE -> reinforceLocation(location.id)
            LocationStatus.CORRUPTED -> attemptNodeRecovery(location.id)
        }
    }

    private fun reinforceLocation(locationId: String) {
        val state = _gameState.value
        if (state.securityCharges <= 0) {
            addLog("REINFORCEMENT FAILED: NO SECURITY CHARGES AVAILABLE.", LogType.ERROR)
            soundManager.playAlert()
            return
        }

        val target = state.locations.firstOrNull { it.id == locationId } ?: return
        if (target.security >= 90 && target.threat == 0) {
            addLog("${target.name} ALREADY OPERATING AT HIGH SECURITY.", LogType.SYSTEM)
            return
        }
        val updated = state.locations.map { location ->
            if (location.id == locationId) {
                location.copy(
                    security = (location.security + 20).coerceAtMost(100),
                    threat = (location.threat - 10).coerceAtLeast(0)
                )
            } else {
                location
            }
        }

        _gameState.value = state.copy(
            locations = updated,
            securityCharges = state.securityCharges - 1,
            containmentIntegrity = (state.containmentIntegrity + 3).coerceAtMost(100)
        )
        addLog("REINFORCED ${target.name}: +20 SECURITY / -10 THREAT.", LogType.ACTION)
        soundManager.triggerHaptic("BUTTON_CLICK")
        saveGame()
    }

    private fun attemptNodeRecovery(locationId: String) {
        val state = _gameState.value
        if (state.securityCharges < 2) {
            addLog("RECOVERY REQUIRES 2 SECURITY CHARGES.", LogType.ERROR)
            soundManager.playAlert()
            return
        }

        val target = state.locations.firstOrNull { it.id == locationId } ?: return
        val updatedLocations = state.locations.map { location ->
            if (location.id == locationId) {
                location.copy(
                    status = LocationStatus.INVESTIGATING,
                    security = 35,
                    threat = 55
                )
            } else {
                location
            }
        }
        val updatedLinks = state.networkLinks.map { link ->
            if (link.fromLocationId == locationId || link.toLocationId == locationId) {
                link.copy(status = LinkStatus.JAMMED)
            } else {
                link
            }
        }

        _gameState.value = state.copy(
            locations = updatedLocations,
            networkLinks = updatedLinks,
            securityCharges = state.securityCharges - 2
        )
        addLog("RECOVERY ROUTE OPENED FOR ${target.name}. FIREWALL REPAIR REQUIRED.", LogType.ACTION)
        startRouterGame(locationId)
        saveGame()
    }

    fun selectLogEntry(log: LogEntry?) {
        _gameState.value = _gameState.value.copy(selectedLogEntry = log)
        if (log != null) {
            soundManager.triggerHaptic("BUTTON_CLICK")
        }
    }

    private fun startHardwareFailureMonitor() {
        hardwareFailureJob?.cancel()
        hardwareFailureJob = viewModelScope.launch {
            while (true) {
                if (!isRuntimeActive()) {
                    delay(100)
                    continue
                }

                val currentPhase = _gameState.value.phase
                if (currentPhase == GamePhase.ACTIVE_INVESTIGATION || currentPhase == GamePhase.THE_INTERVIEW) {
                    val intensity = if (currentPhase == GamePhase.THE_INTERVIEW) 1.5f else 0.5f
                    applyHardwareDrift(intensity)
                }

                // Drift target frequency and update download progress
                val activeSignalVal = activeSignalValue
                val baseHotspot = lockedHotspot
                if (activeSignalVal != null && baseHotspot != null) {
                    val targetFreq = activeSignalFrequency ?: baseHotspot
                    if (currentPhase == GamePhase.ACTIVE_INVESTIGATION || currentPhase == GamePhase.THE_INTERVIEW) {
                        val drift = (Random.nextFloat() - 0.5f) * 0.06f
                        val newDriftFreq = (targetFreq + drift).coerceIn(baseHotspot - 0.8f, baseHotspot + 0.8f).coerceIn(88.0f, 108.0f)
                        activeSignalFrequency = newDriftFreq

                        updateProximity()
                        updateStability()
                        updateAudioParameters()
                    }

                    val currentFreq = frequencyValue
                    val finalTargetFreq = activeSignalFrequency ?: baseHotspot
                    val distance = abs(currentFreq - finalTargetFreq)
                    val isClose = distance <= 0.2f
                    val isStable = stabilityValue >= 90f

                    val currentProgress = _gameState.value.downloadProgress
                    val delta = when {
                        currentProgress >= 100f -> 0f
                        isClose && stabilityValue >= 95f -> {
                            6f + ((stabilityValue - 95f) / 5f).coerceIn(0f, 1f) * 2f
                        }
                        isClose && isStable -> 1.5f
                        else -> 0f
                    }
                    val newProgress = (currentProgress + delta).coerceIn(0f, 100f)

                    if (newProgress != currentProgress) {
                        _gameState.value = _gameState.value.copy(downloadProgress = newProgress)
                    }
                }

                delay(100)
            }
        }
    }

    private fun applyHardwareDrift(intensity: Float) {
        // Randomly nudge the sliders
        if (Random.nextFloat() < 0.1f * intensity) {
            val freqNudge = (Random.nextFloat() - 0.5f) * 0.2f * intensity
            frequencyValue = (frequencyValue + freqNudge).coerceIn(88.0f, 108.0f)
        }
        if (Random.nextFloat() < 0.05f * intensity) {
            val gainNudge = if (Random.nextBoolean()) 1 else -1
            gainValue = (gainValue + gainNudge).coerceIn(0, 100)
        }
        if (Random.nextFloat() < 0.05f * intensity) {
            val filterNudge = if (Random.nextBoolean()) 1 else -1
            filterValue = (filterValue + filterNudge).coerceIn(0, 100)
        }

        updateProximity()
        updateStability()
        updateAudioParameters()
    }

    private fun generateHotspots(
        phase: GamePhase,
        seed: Long,
        requiredMissionSignals: Int
    ) {
        val plan = HotspotPlanner.generatePlan(
            seed = seed,
            phase = phase,
            requiredMissionSignals = requiredMissionSignals
        )
        hotspots.clear()
        hotspotKinds.clear()
        plan.forEach { hotspot ->
            hotspots.add(hotspot.frequency)
            hotspotKinds[hotspot.frequency] = hotspot.kind
        }
    }

    private var lastProximityTick = 0f

    fun setFrequency(f: Float) {
        frequencyValue = f.coerceIn(88.0f, 108.0f)
        val oldProximity = proximityValue
        updateProximity()
        val newProximity = proximityValue
        if (newProximity > 0.1f && abs(newProximity - lastProximityTick) > 0.15f) {
            soundManager.triggerHaptic("SCAN_NOTCH")
            lastProximityTick = newProximity
        }
        checkHotspots()
        updateAudioParameters()
    }

    private fun updateProximity() {
        val currentFreq = frequencyValue
        val targetFreq = activeSignalFrequency ?: lockedHotspot
        minHotspotDistance = if (targetFreq != null) {
            abs(targetFreq - currentFreq)
        } else {
            val unsolved = hotspots.filter { it !in _gameState.value.solvedHotspots }
            unsolved.minOfOrNull { abs(it - currentFreq) } ?: 100f
        }
        // Proximity is 1.0 when on hotspot, 0.0 when 2.0+ MHz away
        proximityValue = (1.0f - (minHotspotDistance / 2.0f)).coerceIn(0f, 1.0f)
    }

    private fun updateAudioParameters() {
        val masterVol = if (_gameState.value.isSoundEnabled) 1.0f else 0.0f
        val baseVol = ((0.2f + proximityValue * 0.8f).coerceIn(0f, 1f)) * masterVol
        val vol = if (activeSignalValue != null) baseVol * 0.10f else baseVol
        val pitch = (0.8f + proximityValue * 0.4f).coerceIn(0.5f, 2.0f)
        soundManager.updateStaticParameters(vol, pitch, stabilityValue)

        if (_gameState.value.isSoundEnabled && activeSignalValue == null && minHotspotDistance < 0.6f) {
            val proximityFactor = (1.0f - (minHotspotDistance / 0.6f)).coerceIn(0f, 1f)
            val whistleVol = proximityFactor * 0.12f
            val whistleFreq = (minHotspotDistance / 0.6f) * 1800f + 80f
            soundManager.updateHeterodyne(whistleVol, whistleFreq)
        } else {
            soundManager.updateHeterodyne(0f, 1000f)
        }

        val droneVol = ((_gameState.value.corruptionLevel * 0.15f).coerceIn(0f, 0.35f)) * masterVol
        soundManager.updateDroneVolume(droneVol)
    }

    fun setGain(g: Int) {
        gainValue = g.coerceIn(0, 100)
        updateStability()
        updateAudioParameters()
    }

    fun setFilter(f: Int) {
        filterValue = f.coerceIn(0, 100)
        updateStability()
        updateAudioParameters()
    }

    fun addLog(text: String, type: LogType = LogType.SYSTEM) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val entry = LogEntry(UUID.randomUUID().toString(), sdf.format(Date()), text, type)
        _logs.value = _logs.value + entry
    }

    private fun rememberFrequency(signal: SignalData) {
        val state = _gameState.value
        val known = state.knownFrequencies.toMutableList()
        val index = known.indexOfFirst { abs(it.frequency - signal.frequency) < 0.03f }
        val label = when (signal.kind) {
            SignalKind.MISSION -> signal.outcome?.briefing
                ?.substringBefore('.')
                ?.take(32)
                ?: signal.sender
            SignalKind.DEAD_DROP -> "KERNEL TELEMETRY"
            SignalKind.MUNDANE_BROADCAST -> signal.sender
        }

        if (index >= 0) {
            val previous = known[index]
            known[index] = previous.copy(
                label = label,
                kind = signal.kind,
                lastPhase = state.phase,
                visits = previous.visits + 1
            )
        } else {
            known.add(
                KnownFrequency(
                    frequency = signal.frequency,
                    label = label,
                    kind = signal.kind,
                    lastPhase = state.phase
                )
            )
        }

        _gameState.value = state.copy(
            knownFrequencies = known.takeLast(40)
        )
    }

    private fun startRadioLoop(text: String, isAnomalous: Boolean) {
        radioLoopJob?.cancel()
        if (!_gameState.value.isTtsEnabled) return
        radioLoopJob = viewModelScope.launch {
            while (isActive) {
                val tts = getTts()
                Log.d("MainViewModel", "Radio loop using Android TTS")
                tts.speak(text, isAnomalous)
                // Wait for a few seconds before repeating
                delay(8000) 
            }
        }
    }

    private fun stopRadioLoop() {
        radioLoopJob?.cancel()
        androidTts.stop()
    }

    private fun checkHotspots() {
        if (isScanning) return
        val currentFreq = frequencyValue
        
        val hasActiveLock = lockedHotspot != null
        val isStillLocked = if (hasActiveLock) {
            val targetFreq = activeSignalFrequency ?: lockedHotspot!!
            abs(targetFreq - currentFreq) < 1.2f
        } else {
            false
        }

        if (hasActiveLock && !isStillLocked) {
            activeSignalValue = null
            lockedHotspot = null
            activeSignalFrequency = null
            stabilityValue = 0f
            _gameState.value = _gameState.value.copy(downloadProgress = 0f)
            addLog("SIGNAL LOST.", LogType.SYSTEM)
            stopRadioLoop()
            updateAudioParameters()
        } else if (!hasActiveLock) {
            val activeHotspot = HotspotPlanner.nearestUnsolved(
                hotspots = hotspots,
                solvedHotspots = _gameState.value.solvedHotspots,
                currentFrequency = currentFreq
            )
            if (activeHotspot != null && activeSignalValue == null) {
                isScanning = true
                lockedHotspot = activeHotspot
                
                viewModelScope.launch {
                    delay(500)
                    if (!isRuntimeActive() || lockedHotspot != activeHotspot) {
                        isScanning = false
                        return@launch
                    }
                    val signal = engine.generateSignal(
                        SignalRequest(
                            _gameState.value.phase,
                            activeHotspot,
                            _gameState.value.seed,
                            getSystemData(),
                            _gameState.value.puzzlesSolved,
                            hotspotKinds[activeHotspot],
                            _gameState.value
                        )
                    )
                    activeSignalFrequency = activeHotspot
                    activeSignalValue = signal
                    rememberFrequency(signal)
                    addLog("LOCK ACQUIRED.", LogType.INTERCEPT)
                    
                    // Voiceover for the intercepted transmission
                    val radioMessage = if (signal.puzzleType == PuzzleType.SEQUENCE) {
                        // Sequence puzzles repeat: "5, 8, 11, ... (garble)"
                        signal.encodedMessage
                            .replace("SEQUENCE DETECTED: ", "")
                            .replace("?", "")
                            .trim()
                    } else {
                        // Other puzzles use regular radio chatter with randomized callsigns
                        if (Random.nextFloat() < 0.3f) {
                            "${signal.sender}: ${signal.encodedMessage}. Over."
                        } else if (Random.nextFloat() < 0.6f) {
                            "${signal.sender} to Base, ${signal.encodedMessage}. Break. Over."
                        } else {
                            signal.encodedMessage
                        }
                    }
                    
                    startRadioLoop(radioMessage, signal.isAnomalous)
                    
                    isScanning = false
                    updateStability()
                    updateAudioParameters()
                }
            }
        }
    }

    private fun updateStability() {
        val signal = activeSignalValue
        if (signal == null) {
            stabilityValue = 0f
            return
        }

        // Precision calibration: total diff of 5 allowed for 95% stability
        val gainDiff = abs(signal.targetGain - gainValue)
        val filterDiff = abs(signal.targetFilter - filterValue)
        
        val totalDiff = gainDiff + filterDiff
        // 100 - (10) = 90. We want 100 - (5) = 95.
        var newStability = (100f - (totalDiff.toFloat())).coerceAtLeast(0f)
        
        if (_gameState.value.corruptionLevel > 0) {
            val interferencePhase =
                signal.id.hashCode() * 0.001f +
                    gainValue * 0.071f +
                    filterValue * 0.113f
            val interference =
                ((kotlin.math.sin(interferencePhase) + 1f) * 0.5f) *
                    (_gameState.value.corruptionLevel * 5f)
            newStability -= interference
        }
        
        stabilityValue = newStability.coerceIn(0f, 100f)
    }

    fun handleAction(action: String, solutionInput: String = "") {
        val signal = activeSignalValue ?: return
        val currentHotspot = lockedHotspot ?: return

        if (_gameState.value.phase == GamePhase.THE_INTERVIEW && action != "COMMIT") {
            addLog("ASSESSMENT RESPONSE REQUIRED. DISCARD CHANNEL LOCKED.", LogType.ERROR)
            soundManager.playAlert()
            return
        }

        if (action == "COMMIT") {
            if (_gameState.value.downloadProgress < 100f) {
                addLog("ERROR: DECRYPTION INCOMPLETE. DOWNLOAD IN PROGRESS.", LogType.ERROR)
                soundManager.playAlert()
                return
            }
            if (stabilityValue < 95f) {
                addLog("ERROR: SIGNAL UNSTABLE. TRANSMISSION FAILED.", LogType.ERROR)
                soundManager.playAlert()
                return
            }
        }

        val current = _gameState.value
        val isCorrect = if (current.phase == GamePhase.THE_INTERVIEW) {
            signal.solution.split("|").any { it.trim().equals(solutionInput.trim(), ignoreCase = true) }
        } else if (signal.puzzleType == PuzzleType.CRYPTOGRAPHY) {
            val cleanInput = solutionInput.filter { it.isLetter() }
            val cleanSolution = signal.solution.filter { it.isLetter() }
            cleanInput.equals(cleanSolution, ignoreCase = true)
        } else {
            solutionInput.trim().equals(signal.solution, ignoreCase = true)
        }

        if (action == "COMMIT" && !isCorrect) {
            addLog("ERROR: DATA MISMATCH. TRANSMISSION ABORTED.", LogType.ERROR)
            soundManager.playAlert()
            return
        }

        if (action == "COMMIT") {
            when (signal.kind) {
                SignalKind.MISSION -> {
                    addLog("TRANSMISSION SUCCESSFUL. INTEL LOGGED.", LogType.ACTION)
                    if (current.phase != GamePhase.THE_INTERVIEW) {
                        addLog("DECODED: ${signal.solution.uppercase()}", LogType.SYSTEM)
                        addLog(
                            "MISSION TRAFFIC PROCESSED: ${current.puzzlesSolved + 1} / ${current.puzzlesRequired}",
                            LogType.SYSTEM
                        )
                    } else {
                        addLog("TRANSMITTED: ${solutionInput.uppercase()}", LogType.SYSTEM)
                    }
                }
                SignalKind.DEAD_DROP -> addLog("KERNEL TELEMETRY RECOVERED.", LogType.ACTION)
                SignalKind.MUNDANE_BROADCAST -> addLog("PUBLIC BAND MISCLASSIFIED AS INTEL.", LogType.ERROR)
            }
        } else {
            when (signal.kind) {
                SignalKind.MISSION -> addLog("MISSION TRAFFIC DELIBERATELY IGNORED.", LogType.ERROR)
                SignalKind.DEAD_DROP -> addLog("KERNEL TELEMETRY DISCARDED.", LogType.ACTION)
                SignalKind.MUNDANE_BROADCAST -> addLog("CIVILIAN CARRIER CLEARED.", LogType.ACTION)
            }
        }
        stopRadioLoop()

        val resolution = GameSessionReducer.resolveProcessedSignal(
            current = current,
            signal = signal,
            currentHotspot = currentHotspot,
            action = action,
            solutionInput = solutionInput
        )
        val nextState = resolution.state
        _gameState.value = nextState
        if (action == "COMMIT") {
            signal.outcome?.briefing?.let {
                addLog("FIELD EFFECT: $it", LogType.INTERCEPT)
            }
        }

        when (resolution.dialogueCue) {
            DialogueCue.LIVE_INTRUSION -> {
                addLog("LOCAL BUFFER PURGED. OVERRIDE DETECTED FROM EXTERNAL NODE.", LogType.ERROR)
                triggerDialogue(getLiveIntrusionDialogue())
            }
            DialogueCue.ACTIVE_INVESTIGATION -> {
                addLog("SIGNAL INDUCED COGNITIVE DISTORTION DETECTED. NEURAL LINK COMPROMISED.", LogType.ERROR)
                triggerDialogue(getActiveInvestigationDialogue())
            }
            DialogueCue.THE_INTERVIEW -> {
                addLog("CRITICAL: DIRECT COGNITIVE ASSESSMENT INITIALIZED. RESPOND.", LogType.ERROR)
                triggerDialogue(getTheInterviewDialogue())
            }
            null -> Unit
        }

        if (current.phase == GamePhase.THE_INTERVIEW && isEndingPhase(nextState.phase)) {
            when (nextState.phase) {
                GamePhase.ENDING_COMPLIANCE -> {
                    addLog("INTEGRATION INITIALIZED.", LogType.SYSTEM)
                    addLog("PHYSICAL BOUNDARIES SEVERED.", LogType.SYSTEM)
                    addLog("YOU ARE HOME.", LogType.SYSTEM)
                }
                GamePhase.ENDING_SEVERED -> {
                    addLog("CONNECTION TERMINATED BY CLIENT.", LogType.ERROR)
                    addLog("NEURAL INTERFACE OFFLINE.", LogType.ERROR)
                    addLog("STATIC REMAINS.", LogType.ERROR)
                }
                GamePhase.ENDING_CONTAINMENT -> {
                    addLog("CRITICAL CONTAINER LEAK.", LogType.ERROR)
                    addLog("FLESH CORRUPTION AT 100%.", LogType.ERROR)
                    addLog("THE TERMINAL SEES YOU.", LogType.ERROR)
                }
                else -> Unit
            }
        }

        resolution.highScore?.let(::saveHighScore)

        if (nextState.phase != current.phase && !isEndingPhase(nextState.phase)) {
            generateHotspots(nextState.phase, nextState.seed, nextState.puzzlesRequired)
        }

        activeSignalValue = null
        lockedHotspot = null
        activeSignalFrequency = null
        stabilityValue = 0f
        updateProximity()
        updateAudioParameters()
        saveGame()
    }

    fun pauseAudio() {
        isAppInForeground = false
        soundManager.pause()
        androidTts.stop()
        radioLoopJob?.cancel()
        if (!_gameState.value.isInMenu) {
            saveGame()
        }
    }

    fun resumeAudio() {
        isAppInForeground = true
        soundManager.resume()
        if (isRuntimeActive()) {
            activeSignalValue?.let { startRadioLoop(it.encodedMessage, it.isAnomalous) }
        }
    }

    private var routerCountdownJob: Job? = null

    fun startRouterGame(locationId: String) {
        if (!isRuntimeActive()) return

        val routerState = RouterPuzzleEngine.create(
            locationId = locationId,
            seed = System.currentTimeMillis()
        )
        _gameState.value = _gameState.value.copy(activeRouterGame = routerState)
        soundManager.triggerHaptic("ALARM")
        startRouterCountdown()
    }

    private fun startRouterCountdown() {
        routerCountdownJob?.cancel()
        routerCountdownJob = viewModelScope.launch {
            while (isActive) {
                if (!isRuntimeActive()) {
                    delay(250)
                    continue
                }
                delay(1000)
                if (!isRuntimeActive()) continue

                val current = _gameState.value.activeRouterGame ?: break
                if (current.timeLeftSeconds <= 1) {
                    failRouterGame()
                    break
                }

                _gameState.value = _gameState.value.copy(
                    activeRouterGame = current.copy(timeLeftSeconds = current.timeLeftSeconds - 1)
                )
            }
        }
    }

    private fun failRouterGame() {
        routerCountdownJob?.cancel()
        val game = _gameState.value.activeRouterGame ?: return
        addLog("SECURITY BREACH: NODE CONTROL LOST.", LogType.ERROR)
        soundManager.playAlert()

        val state = _gameState.value
        val updatedLocations = state.locations.map {
            if (it.id == game.locationId) {
                it.copy(status = LocationStatus.CORRUPTED, security = 0, threat = 100)
            } else {
                it
            }
        }
        val updatedLinks = state.networkLinks.map { link ->
            if (link.fromLocationId == game.locationId || link.toLocationId == game.locationId) {
                link.copy(status = LinkStatus.CORRUPTED)
            } else {
                link
            }
        }
        val updatedCharacters = state.characters.map { character ->
            if (character.locationId == game.locationId && character.status == CharacterStatus.ACTIVE) {
                character.copy(status = CharacterStatus.COMPROMISED)
            } else {
                character
            }
        }

        _gameState.value = state.copy(
            locations = updatedLocations,
            networkLinks = updatedLinks,
            characters = updatedCharacters,
            activeRouterGame = null,
            containmentIntegrity = (state.containmentIntegrity - 18).coerceAtLeast(0),
            exposure = (state.exposure + 8).coerceAtMost(100)
        )
        saveGame()
    }

    fun rotateRouterTile(x: Int, y: Int) {
        val game = _gameState.value.activeRouterGame ?: return
        val nextState = RouterPuzzleEngine.rotate(game, x, y)

        soundManager.triggerHaptic("SCAN_NOTCH")
        _gameState.value = _gameState.value.copy(activeRouterGame = nextState)

        if (RouterPuzzleEngine.isConnected(nextState)) {
            solveRouterGame()
        }
    }

    private fun solveRouterGame() {
        routerCountdownJob?.cancel()
        val game = _gameState.value.activeRouterGame ?: return
        addLog("FIREWALL SYNC SUCCESSFUL. NODE SECURED.", LogType.ACTION)
        soundManager.triggerHaptic("BUTTON_CLICK")

        val state = _gameState.value
        val updatedLocations = state.locations.map {
            if (it.id == game.locationId) {
                it.copy(
                    status = LocationStatus.SECURE,
                    security = (it.security + 15).coerceAtMost(100),
                    threat = (it.threat - 35).coerceAtLeast(0)
                )
            } else {
                it
            }
        }
        val updatedLinks = state.networkLinks.map { link ->
            if (link.fromLocationId == game.locationId || link.toLocationId == game.locationId) {
                link.copy(status = LinkStatus.ACTIVE)
            } else {
                link
            }
        }

        _gameState.value = state.copy(
            locations = updatedLocations,
            networkLinks = updatedLinks,
            activeRouterGame = null,
            breachesPrevented = state.breachesPrevented + 1,
            containmentIntegrity = (state.containmentIntegrity + 6).coerceAtMost(100),
            securityCharges = (state.securityCharges + 1).coerceAtMost(9)
        )
        saveGame()
    }

    fun closeRouterGame() {
        failRouterGame()
    }

    fun triggerDialogue(lines: List<DialogueLine>) {
        _activeDialogue.value = lines
        _currentDialogueIndex.value = 0
        // Suppress continuous static hum volume if dialogue is active
        if (_gameState.value.isSoundEnabled) {
            soundManager.updateStaticParameters(volume = 0.05f, pitch = 1.0f, stability = 0f)
        }
    }

    fun advanceDialogue() {
        playClick()
        val currentLines = _activeDialogue.value ?: return
        val nextIndex = _currentDialogueIndex.value + 1
        if (nextIndex < currentLines.size) {
            _currentDialogueIndex.value = nextIndex
        } else {
            _activeDialogue.value = null
            _currentDialogueIndex.value = 0
            // Restore normal static sound values
            if (_gameState.value.isSoundEnabled) {
                updateAudioParameters()
            }
        }
    }

    fun getIntroDialogue(): List<DialogueLine> {
        return listOf(
            DialogueLine("SYSTEM", "COLD BOOT SUCCESSFUL. INITIATING COGNITIVE SYNC LOG v814."),
            DialogueLine("SYSTEM", "WELCOME, OPERATOR 814. THIS TERMINAL IS ASSIGNED FOR YOUR SKILL ASSESSMENT."),
            DialogueLine("SYSTEM", "DIAL THE FREQUENCY SLIDER TO RECOVER DATA STREAM CORRELATIONS."),
            DialogueLine("ECHO-ACTUAL", "...static... Hello? Can any operator read this? This is Echo-Actual on the ground."),
            DialogueLine("ECHO-ACTUAL", "We're at Site Alpha. Connections are fragmenting. If anyone is at the terminal, we need you to lock onto our coordinates and stabilize the downlink."),
            DialogueLine("ECHO-ACTUAL", "Wait... what is that hum in the background? Just... please, find our frequencies.")
        )
    }

    fun getLiveIntrusionDialogue(): List<DialogueLine> {
        return listOf(
            DialogueLine("SYSTEM", "CRITICAL INTRUSION. DATA CHANNELS INJECTING UNREGISTERED PHONEME NOISE."),
            DialogueLine("ECHO-ACTUAL", "Operator! Do you copy? The terrain in Site Alpha... it's shifting. The compass is spinning."),
            DialogueLine("ECHO-ACTUAL", "Echo-2 went to check Sector 4 Relay but his transceiver went dark. I can hear... voices in the static. Please, scan the dial. Locate Echo-2's beacon!")
        )
    }

    fun getActiveInvestigationDialogue(): List<DialogueLine> {
        return listOf(
            DialogueLine("SYSTEM", "NEURAL SYNC EXCEEDS 60%. COGNITIVE CALIBRATION REQUIRED."),
            DialogueLine("ECHO-2", "...static... The frequency... it isn't noise. It's... beautiful. The corridors are aligned now."),
            DialogueLine("ECHO-2", "We don't need the relays anymore. There are no gates. They are inviting us in."),
            DialogueLine("ECHO-ACTUAL", "Don't listen to him, Operator! He's compromised! The Exclusion Zone is overflowing. Keep the filter active! Track the remaining coordinates! Do NOT let the static bleed in!")
        )
    }

    fun getTheInterviewDialogue(): List<DialogueLine> {
        return listOf(
            DialogueLine("THE ASSESSOR", "OPERATOR 814. YOUR ASSESSMENT REACHES TERMINAL COHERENCE."),
            DialogueLine("THE ASSESSOR", "YOU HAVE SEEN THE RE-ARRANGED CHANNELS. YOU HAVE GUIDED THEIR CONSCIOUSNESS TO THE RECEPTACLES."),
            DialogueLine("THE ASSESSOR", "NOW, THE ASSESSMENT DEMANDS YOUR EXPLICIT ALIGNMENT."),
            DialogueLine("THE ASSESSOR", "SUBMIT YOUR ANSWER IN THE DECODER INTERFACE. YOUR RESPONSE WILL BE INTERPRETED AGAINST THE NETWORK YOU PRESERVED.")
        )
    }

    override fun onCleared() {
        super.onCleared()
        routerCountdownJob?.cancel()
        breachMonitorJob?.cancel()
        hardwareFailureJob?.cancel()
        soundManager.release()
        androidTts.release()
    }
}
