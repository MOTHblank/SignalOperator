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
import com.mothblank.signaloperator.engine.ProceduralSignalEngine
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
        val signal = _activeSignal.value
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

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _frequency = MutableStateFlow(88.0f)
    val frequency: StateFlow<Float> = _frequency.asStateFlow()

    private val _gain = MutableStateFlow(50)
    val gain: StateFlow<Int> = _gain.asStateFlow()

    private val _filter = MutableStateFlow(50)
    val filter: StateFlow<Int> = _filter.asStateFlow()

    private val _activeSignal = MutableStateFlow<SignalData?>(null)
    val activeSignal: StateFlow<SignalData?> = _activeSignal.asStateFlow()

    private val _stability = MutableStateFlow(0f)
    val stability: StateFlow<Float> = _stability.asStateFlow()

    private val _proximity = MutableStateFlow(0f)
    val proximity: StateFlow<Float> = _proximity.asStateFlow()

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _isMapViewActive = MutableStateFlow(false)
    val isMapViewActive: StateFlow<Boolean> = _isMapViewActive.asStateFlow()

    private val hotspots = mutableListOf<Float>()
    private var isScanning = false
    private var lockedHotspot: Float? = null
    private var activeSignalFrequency: Float? = null
    private var hardwareFailureJob: Job? = null
    private var radioLoopJob: Job? = null
    private var minHotspotDistance = 100f
    private var breachMonitorJob: Job? = null

    init {
        loadSettings()
        val saved = com.mothblank.signaloperator.engine.SaveStateManager.loadGame(application)
        if (saved != null) {
            _gameState.value = saved.gameState.copy(
                isCrtEffectEnabled = _gameState.value.isCrtEffectEnabled,
                isSoundEnabled = _gameState.value.isSoundEnabled,
                isTtsEnabled = _gameState.value.isTtsEnabled,
                isInMenu = true
            )
            _logs.value = saved.logs
            generateHotspots()
        } else {
            initializeWorld()
            generateHotspots()
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
            Location("loc-1", "SITE ALPHA", 0.2f, 0.3f),
            Location("loc-2", "SECTOR 4 RELAY", 0.5f, 0.5f),
            Location("loc-3", "ALPHA OUTPOST", 0.8f, 0.2f),
            Location("loc-4", "EXCLUSION ZONE", 0.6f, 0.8f)
        )
        val initialCharacters = listOf(
            Character("char-1", "ECHO-ACTUAL", "loc-1"),
            Character("char-2", "ECHO-2", "loc-3"),
            Character("char-3", "THE SUBJECT", null, CharacterStatus.ANOMALY)
        )
        _gameState.value = _gameState.value.copy(
            locations = initialLocations,
            characters = initialCharacters
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
        
        if (list.isEmpty()) {
            list.add(HighScoreEntry("OP-102", "THE_INTERVIEW", 6, 5400))
            list.add(HighScoreEntry("OP-412", "ACTIVE_INVESTIGATION", 5, 4500))
            list.add(HighScoreEntry("OP-209", "LIVE_INTRUSION", 3, 2700))
            list.add(HighScoreEntry("OP-901", "APTITUDE_TEST", 2, 1800))
            list.add(HighScoreEntry("OP-012", "APTITUDE_TEST", 1, 900))
            saveHighScoresRaw(list)
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

    fun playAlert() {
        soundManager.playAlert()
    }

    fun clearData() {
        val settingsPrefs = getApplication<Application>().getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        settingsPrefs.edit().clear().apply()
        
        val scorePrefs = getApplication<Application>().getSharedPreferences(PREFS_HIGHSCORES, Context.MODE_PRIVATE)
        scorePrefs.edit().clear().apply()
        
        _gameState.value = GameState(
            isInMenu = true,
            currentMenuScreen = MenuSubScreen.OPTIONS,
            isCrtEffectEnabled = true,
            isSoundEnabled = true,
            isTtsEnabled = true
        )
        
        soundManager.startStatic()
        updateAudioParameters()
        loadHighScores()
    }

    fun startGame() {
        initializeWorld()
        generateHotspots()
        
        _gameState.value = _gameState.value.copy(
            isInMenu = false,
            phase = GamePhase.APTITUDE_TEST,
            corruptionLevel = 0f,
            archivedSignals = 0,
            ignoredSignals = 0,
            puzzlesSolved = 0,
            puzzlesRequired = 3,
            solvedHotspots = emptySet(),
            downloadProgress = 0f
        )
        
        _activeSignal.value = null
        lockedHotspot = null
        activeSignalFrequency = null
        _stability.value = 0f
        
        if (_gameState.value.isSoundEnabled) {
            soundManager.startStatic()
            updateAudioParameters()
        } else {
            soundManager.stopStatic()
        }
        
        _logs.value = emptyList()
        addLog("SYSTEM INITIALIZED. SCANNER STANDBY.", LogType.SYSTEM)
        saveGame()
    }

    fun returnToMenu() {
        _gameState.value = _gameState.value.copy(
            isInMenu = true,
            currentMenuScreen = MenuSubScreen.MAIN
        )
        if (!_gameState.value.isSoundEnabled) {
            soundManager.stopStatic()
        }
        saveGame()
    }

    private fun saveGame() {
        com.mothblank.signaloperator.engine.SaveStateManager.saveGame(
            getApplication(),
            _gameState.value,
            _logs.value
        )
    }

    fun resetGame() {
        com.mothblank.signaloperator.engine.SaveStateManager.saveGame(
            getApplication(),
            GameState(),
            emptyList()
        )
        _logs.value = emptyList()
        _gameState.value = GameState()
        initializeWorld()
        generateHotspots()
        addLog("SYSTEM REBOOT IN PROGRESS...", LogType.SYSTEM)
        addLog("ALL LOGS PURGED.", LogType.SYSTEM)
        addLog("READY.", LogType.SYSTEM)
        saveGame()
    }

    private fun startBreachMonitor() {
        breachMonitorJob?.cancel()
        breachMonitorJob = viewModelScope.launch {
            while (true) {
                delay(25000) // check every 25 seconds
                val state = _gameState.value
                if (state.activeRouterGame == null && 
                    (state.phase == GamePhase.ACTIVE_INVESTIGATION || state.phase == GamePhase.THE_INTERVIEW)) {
                    val secureLocations = state.locations.filter { it.status == LocationStatus.SECURE }
                    if (secureLocations.isNotEmpty()) {
                        val targetLoc = secureLocations.random()
                        startRouterGame(targetLoc.id)
                        addLog("WARNING: SECURITY COMPROMISE AT ${targetLoc.name}. FIREWALL OFFLINE.", LogType.ERROR)
                        saveGame()
                    }
                }
            }
        }
    }

    fun toggleMapView() {
        _isMapViewActive.value = !_isMapViewActive.value
        _gameState.value = _gameState.value.copy(isMapViewActive = _isMapViewActive.value)
    }

    fun handleLocationClick(location: Location) {
        if (location.status == LocationStatus.INVESTIGATING) {
            startRouterGame(location.id)
        }
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
                val currentPhase = _gameState.value.phase
                if (currentPhase == GamePhase.ACTIVE_INVESTIGATION || currentPhase == GamePhase.THE_INTERVIEW) {
                    val intensity = if (currentPhase == GamePhase.THE_INTERVIEW) 1.5f else 0.5f
                    applyHardwareDrift(intensity)
                }

                // Drift target frequency and update download progress
                val activeSignalVal = _activeSignal.value
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

                    val currentFreq = _frequency.value
                    val finalTargetFreq = activeSignalFrequency ?: baseHotspot
                    val distance = abs(currentFreq - finalTargetFreq)
                    val isClose = distance <= 0.2f
                    val isStable = _stability.value >= 90f

                    val currentProgress = _gameState.value.downloadProgress
                    val delta = if (currentProgress >= 100f) {
                        0f // Lock at 100% once complete
                    } else if (isClose && isStable) {
                        2.0f // Download progresses when aligned & stable
                    } else {
                        0f // Pause download progress instead of draining it to prevent frustration
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
            _frequency.value = (_frequency.value + freqNudge).coerceIn(88.0f, 108.0f)
        }
        if (Random.nextFloat() < 0.05f * intensity) {
            val gainNudge = if (Random.nextBoolean()) 1 else -1
            _gain.value = (_gain.value + gainNudge).coerceIn(0, 100)
        }
        if (Random.nextFloat() < 0.05f * intensity) {
            val filterNudge = if (Random.nextBoolean()) 1 else -1
            _filter.value = (_filter.value + filterNudge).coerceIn(0, 100)
        }

        updateProximity()
        updateStability()
        updateAudioParameters()
    }

    private fun generateHotspots() {
        val random = Random(_gameState.value.seed + _gameState.value.phase.ordinal)
        hotspots.clear()
        
        // Increase number of hotspots to simulate "actual radio" scanning
        // 5 game-critical signals + 12 mundane/noise signals
        repeat(17) {
            hotspots.add(88f + random.nextFloat() * 20f)
        }
    }

    private var lastProximityTick = 0f

    fun setFrequency(f: Float) {
        _frequency.value = f.coerceIn(88.0f, 108.0f)
        val oldProximity = _proximity.value
        updateProximity()
        val newProximity = _proximity.value
        if (newProximity > 0.1f && abs(newProximity - lastProximityTick) > 0.15f) {
            soundManager.triggerHaptic("SCAN_NOTCH")
            lastProximityTick = newProximity
        }
        checkHotspots()
        updateAudioParameters()
    }

    private fun updateProximity() {
        val currentFreq = _frequency.value
        val targetFreq = activeSignalFrequency ?: lockedHotspot
        minHotspotDistance = if (targetFreq != null) {
            abs(targetFreq - currentFreq)
        } else {
            val unsolved = hotspots.filter { it !in _gameState.value.solvedHotspots }
            unsolved.minOfOrNull { abs(it - currentFreq) } ?: 100f
        }
        // Proximity is 1.0 when on hotspot, 0.0 when 2.0+ MHz away
        _proximity.value = (1.0f - (minHotspotDistance / 2.0f)).coerceIn(0f, 1.0f)
    }

    private fun updateAudioParameters() {
        val masterVol = if (_gameState.value.isSoundEnabled) 1.0f else 0.0f
        val baseVol = ((0.2f + _proximity.value * 0.8f).coerceIn(0f, 1f)) * masterVol
        val vol = if (_activeSignal.value != null) baseVol * 0.10f else baseVol
        val pitch = (0.8f + _proximity.value * 0.4f).coerceIn(0.5f, 2.0f)
        soundManager.updateStaticParameters(vol, pitch, _stability.value)

        if (_gameState.value.isSoundEnabled && _activeSignal.value == null && minHotspotDistance < 0.6f) {
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
        _gain.value = g.coerceIn(0, 100)
        updateStability()
        updateAudioParameters()
    }

    fun setFilter(f: Int) {
        _filter.value = f.coerceIn(0, 100)
        updateStability()
        updateAudioParameters()
    }

    fun addLog(text: String, type: LogType = LogType.SYSTEM) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val entry = LogEntry(UUID.randomUUID().toString(), sdf.format(Date()), text, type)
        _logs.value = _logs.value + entry
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
        val currentFreq = _frequency.value
        
        val hasActiveLock = lockedHotspot != null
        val isStillLocked = if (hasActiveLock) {
            val targetFreq = activeSignalFrequency ?: lockedHotspot!!
            abs(targetFreq - currentFreq) < 1.2f
        } else {
            false
        }

        if (hasActiveLock && !isStillLocked) {
            _activeSignal.value = null
            lockedHotspot = null
            activeSignalFrequency = null
            _stability.value = 0f
            _gameState.value = _gameState.value.copy(downloadProgress = 0f)
            addLog("SIGNAL LOST.", LogType.SYSTEM)
            stopRadioLoop()
            updateAudioParameters()
        } else if (!hasActiveLock) {
            val activeHotspot = hotspots.find { abs(it - currentFreq) < 0.8f }
            if (activeHotspot != null && _activeSignal.value == null && !_gameState.value.solvedHotspots.contains(activeHotspot)) {
                isScanning = true
                lockedHotspot = activeHotspot
                
                viewModelScope.launch {
                    delay(500) 
                    val signal = engine.generateSignal(_gameState.value.phase, activeHotspot, _gameState.value.seed, getSystemData(), _gameState.value.puzzlesSolved)
                    activeSignalFrequency = activeHotspot
                    _activeSignal.value = signal
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
        val signal = _activeSignal.value
        if (signal == null) {
            _stability.value = 0f
            return
        }

        // Precision calibration: total diff of 5 allowed for 95% stability
        val gainDiff = abs(signal.targetGain - _gain.value)
        val filterDiff = abs(signal.targetFilter - _filter.value)
        
        val totalDiff = gainDiff + filterDiff
        // 100 - (10) = 90. We want 100 - (5) = 95.
        var newStability = (100f - (totalDiff.toFloat())).coerceAtLeast(0f)
        
        if (_gameState.value.corruptionLevel > 0) {
            newStability -= (Math.random() * (_gameState.value.corruptionLevel * 5)).toFloat()
        }
        
        _stability.value = newStability.coerceIn(0f, 100f)
    }

    fun handleAction(action: String, solutionInput: String = "") {
        val signal = _activeSignal.value ?: return
        val currentHotspot = lockedHotspot ?: return
        
        if (action == "COMMIT") {
            if (_gameState.value.downloadProgress < 100f) {
                addLog("ERROR: DECRYPTION INCOMPLETE. DOWNLOAD IN PROGRESS.", LogType.ERROR)
                soundManager.playAlert()
                return
            }
            if (_stability.value < 95f) {
                addLog("ERROR: SIGNAL UNSTABLE. TRANSMISSION FAILED.", LogType.ERROR)
                soundManager.playAlert()
                return
            }
        }
        
        val current = _gameState.value
        val isCorrect = if (current.phase == GamePhase.THE_INTERVIEW) {
            val allowed = signal.solution.split("|")
            allowed.any { it.trim().equals(solutionInput.trim(), ignoreCase = true) }
        } else if (signal.puzzleType == PuzzleType.CRYPTOGRAPHY) {
            val cleanInput = solutionInput.filter { it.isLetter() }
            val cleanSolution = signal.solution.filter { it.isLetter() }
            cleanInput.equals(cleanSolution, ignoreCase = true)
        } else {
            solutionInput.trim().equals(signal.solution, ignoreCase = true)
        }
        
        if (action == "COMMIT") {
            if (!isCorrect) {
                addLog("ERROR: DATA MISMATCH. TRANSMISSION ABORTED.", LogType.ERROR)
                soundManager.playAlert()
                return
            }
            addLog("TRANSMISSION SUCCESSFUL. INTEL LOGGED.", LogType.ACTION)
            if (current.phase != GamePhase.THE_INTERVIEW) {
                addLog("DECODED: ${signal.solution.uppercase()}", LogType.SYSTEM)
            } else {
                addLog("TRANSMITTED: ${solutionInput.uppercase()}", LogType.SYSTEM)
            }
            stopRadioLoop() // Stop sequence loop upon success
        } else {
            addLog("SIGNAL DISCARDED.", LogType.ACTION)
            stopRadioLoop()
        }

        var archived = current.archivedSignals
        var ignored = current.ignoredSignals
        var solved = current.puzzlesSolved
        val solvedHotspots = current.solvedHotspots.toMutableSet()
        
        if (action == "COMMIT") {
            archived += 1
            solved += 1
            solvedHotspots.add(currentHotspot)
            
            if (current.phase != GamePhase.THE_INTERVIEW) {
                addLog("PROGRESS: $solved / ${current.puzzlesRequired} INTEL RECOVERED.", LogType.SYSTEM)
            } else {
                addLog("ASSESSMENT PROGRESS: $solved / ${current.puzzlesRequired}", LogType.SYSTEM)
            }

            // Dynamic World Update: Mark a location as investigating based on intel
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

            // Move characters randomly to simulate activity
            val updatedCharacters = current.characters.toMutableList()
            if (Random.nextFloat() < 0.5f) {
                val charIndex = Random.nextInt(updatedCharacters.size)
                val randomLoc = updatedLocations.random()
                updatedCharacters[charIndex] = updatedCharacters[charIndex].copy(locationId = randomLoc.id)
            }

            _gameState.value = _gameState.value.copy(
                locations = updatedLocations,
                characters = updatedCharacters
            )
        } else {
            ignored += 1
            if (signal.solution == "DISCARD") {
                solvedHotspots.add(currentHotspot)
            }
        }

        val totalProcessed = archived + ignored
        var nextPhase = current.phase
        var corruption = current.corruptionLevel
        var required = current.puzzlesRequired

        if (solved >= current.puzzlesRequired) {
            when(current.phase) {
                GamePhase.APTITUDE_TEST -> {
                    nextPhase = GamePhase.LIVE_INTRUSION
                    required = 5
                    solved = 0
                    solvedHotspots.clear()
                    generateHotspots()
                    addLog("LOCAL BUFFER PURGED. OVERRIDE DETECTED FROM EXTERNAL NODE.", LogType.ERROR)
                }
                GamePhase.LIVE_INTRUSION -> {
                    nextPhase = GamePhase.ACTIVE_INVESTIGATION
                    required = 6
                    solved = 0
                    solvedHotspots.clear()
                    corruption = 1f
                    generateHotspots()
                    addLog("SIGNAL INDUCED COGNITIVE DISTORTION DETECTED. NEURAL LINK COMPROMISED.", LogType.ERROR)
                }
                GamePhase.ACTIVE_INVESTIGATION -> {
                    nextPhase = GamePhase.THE_INTERVIEW
                    required = 3
                    solved = 0
                    solvedHotspots.clear()
                    corruption = 2f
                    generateHotspots()
                    addLog("CRITICAL: DIRECT COGNITIVE ASSESSMENT INITIALIZED. RESPOND.", LogType.ERROR)
                }
                GamePhase.THE_INTERVIEW -> {
                    nextPhase = when (solutionInput.uppercase().trim()) {
                        "I ACCEPT" -> GamePhase.ENDING_COMPLIANCE
                        "I REFUSE" -> GamePhase.ENDING_SEVERED
                        else -> GamePhase.ENDING_CONTAINMENT // "I AM AFRAID"
                    }
                    solved = 0
                    solvedHotspots.clear()
                    
                    when (nextPhase) {
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
                        else -> {}
                    }
                    
                    val finalScore = (archived * 1000 - ignored * 200).coerceAtLeast(0)
                    val opId = "OP-${current.seed % 1000}"
                    saveHighScore(HighScoreEntry(
                        operatorId = opId,
                        maxPhase = nextPhase.name.replace("ENDING_", ""),
                        intelSaved = archived,
                        score = finalScore
                    ))
                }
                else -> {}
            }
        }

        _gameState.value = current.copy(
            phase = nextPhase,
            corruptionLevel = corruption,
            archivedSignals = archived,
            ignoredSignals = ignored,
            puzzlesSolved = solved,
            puzzlesRequired = required,
            solvedHotspots = solvedHotspots,
            downloadProgress = 0f
        )

        _activeSignal.value = null
        lockedHotspot = null
        activeSignalFrequency = null
        _stability.value = 0f
        updateAudioParameters()
        saveGame()
    }

    fun pauseAudio() {
        soundManager.pause()
        androidTts.stop()
        radioLoopJob?.cancel()
    }

    fun resumeAudio() {
        soundManager.resume()
        // Radio loop will restart naturally when the signal is updated or if we manually trigger it
        _activeSignal.value?.let { startRadioLoop(it.encodedMessage, it.isAnomalous) }
    }

    private var routerCountdownJob: Job? = null

    fun startRouterGame(locationId: String) {
        val size = 3
        val random = Random(System.currentTimeMillis())
        val tiles = mutableListOf<RouterTile>()
        val tilePaths = listOf(TilePath.STRAIGHT, TilePath.CORNER, TilePath.CROSS)
        val rotations = listOf(0, 90, 180, 270)
        for (x in 0 until size) {
            for (y in 0 until size) {
                tiles.add(RouterTile(
                    x = x,
                    y = y,
                    type = tilePaths.random(random),
                    rotationDegrees = rotations.random(random)
                ))
            }
        }
        
        val routerState = RouterGameState(
            locationId = locationId,
            grid = tiles,
            size = size,
            entryY = 1,
            exitY = 1,
            timeLeftSeconds = 15
        )
        _gameState.value = _gameState.value.copy(activeRouterGame = routerState)
        soundManager.triggerHaptic("ALARM")
        startRouterCountdown()
    }

    private fun startRouterCountdown() {
        routerCountdownJob?.cancel()
        routerCountdownJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val current = _gameState.value.activeRouterGame ?: break
                if (current.timeLeftSeconds <= 1) {
                    failRouterGame()
                    break
                } else {
                    _gameState.value = _gameState.value.copy(
                        activeRouterGame = current.copy(timeLeftSeconds = current.timeLeftSeconds - 1)
                    )
                }
            }
        }
    }

    private fun failRouterGame() {
        routerCountdownJob?.cancel()
        val game = _gameState.value.activeRouterGame ?: return
        addLog("SECURITY BREACH: NODE CONTROL LOST.", LogType.ERROR)
        soundManager.playAlert()
        
        val updatedLocations = _gameState.value.locations.map {
            if (it.id == game.locationId) {
                it.copy(status = LocationStatus.CORRUPTED)
            } else {
                it
            }
        }
        
        _gameState.value = _gameState.value.copy(
            locations = updatedLocations,
            activeRouterGame = null
        )
        saveGame()
    }

    fun rotateRouterTile(x: Int, y: Int) {
        val game = _gameState.value.activeRouterGame ?: return
        val updatedGrid = game.grid.map {
            if (it.x == x && it.y == y) {
                it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360)
            } else {
                it
            }
        }
        
        soundManager.triggerHaptic("SCAN_NOTCH")
        val nextState = game.copy(grid = updatedGrid)
        _gameState.value = _gameState.value.copy(activeRouterGame = nextState)
        
        if (checkPathConnectivity(nextState)) {
            solveRouterGame()
        }
    }

    private fun solveRouterGame() {
        routerCountdownJob?.cancel()
        val game = _gameState.value.activeRouterGame ?: return
        addLog("FIREWALL SYNC SUCCESSFUL. NODE SECURED.", LogType.ACTION)
        soundManager.triggerHaptic("BUTTON_CLICK")
        
        val updatedLocations = _gameState.value.locations.map {
            if (it.id == game.locationId) {
                it.copy(status = LocationStatus.SECURE)
            } else {
                it
            }
        }
        
        _gameState.value = _gameState.value.copy(
            locations = updatedLocations,
            activeRouterGame = null
        )
        saveGame()
    }

    fun closeRouterGame() {
        failRouterGame()
    }

    private fun getTilePorts(tile: RouterTile): Set<Int> {
        val rotFactor = (tile.rotationDegrees / 90) % 4
        return when (tile.type) {
            TilePath.STRAIGHT -> {
                if (rotFactor % 2 == 0) {
                    setOf(1, 3) // RIGHT, LEFT
                } else {
                    setOf(0, 2) // UP, DOWN
                }
            }
            TilePath.CORNER -> {
                val base = setOf(1, 2)
                base.map { (it + rotFactor) % 4 }.toSet()
            }
            TilePath.CROSS -> {
                setOf(0, 1, 2, 3) // UP, RIGHT, DOWN, LEFT
            }
        }
    }

    private fun checkPathConnectivity(game: RouterGameState): Boolean {
        val size = game.size
        val gridMap = game.grid.associateBy { Pair(it.x, it.y) }
        val visited = mutableSetOf<Pair<Int, Int>>()
        
        fun dfs(x: Int, y: Int, fromDir: Int): Boolean {
            if (x == size && y == game.exitY && fromDir == 3) {
                return true
            }
            if (x !in 0 until size || y !in 0 until size) {
                return false
            }
            if (visited.contains(Pair(x, y))) {
                return false
            }
            
            val tile = gridMap[Pair(x, y)] ?: return false
            val ports = getTilePorts(tile)
            
            if (!ports.contains(fromDir)) {
                return false
            }
            
            visited.add(Pair(x, y))
            
            for (port in ports) {
                if (port == fromDir) continue
                val nextX = x + when (port) {
                    1 -> 1
                    3 -> -1
                    else -> 0
                }
                val nextY = y + when (port) {
                    2 -> 1
                    0 -> -1
                    else -> 0
                }
                val nextFromDir = (port + 2) % 4
                if (dfs(nextX, nextY, nextFromDir)) {
                    return true
                }
            }
            
            visited.remove(Pair(x, y))
            return false
        }
        
        return dfs(0, game.entryY, 3)
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
