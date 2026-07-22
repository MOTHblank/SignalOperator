package com.mothblank.signaloperator

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.mothblank.signaloperator.components.*
import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.MenuSubScreen
import com.mothblank.signaloperator.ui.theme.SignalOperatorTheme
import com.mothblank.signaloperator.ui.theme.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> viewModel.pauseAudio()
                        Lifecycle.Event.ON_RESUME -> viewModel.resumeAudio()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val gameState     by viewModel.gameState.collectAsState()
            val logs          by viewModel.logs.collectAsState()
            val activeSignal  by viewModel.activeSignal.collectAsState()
            val stability     by viewModel.stability.collectAsState()
            val proximity     by viewModel.proximity.collectAsState()
            val frequency     by viewModel.frequency.collectAsState()
            val gain          by viewModel.gain.collectAsState()
            val filter        by viewModel.filter.collectAsState()
            val isMapViewActive by viewModel.isMapViewActive.collectAsState()
            val activeDialogue by viewModel.activeDialogue.collectAsState()
            val currentDialogueIndex by viewModel.currentDialogueIndex.collectAsState()

            var activeHint by remember { mutableStateOf<Pair<String, String>?>(null) }

            // Intercept system back swipes and gestures
            BackHandler(enabled = true) {
                if (gameState.isInMenu) {
                    if (gameState.currentMenuScreen != MenuSubScreen.MAIN) {
                        viewModel.playClick()
                        viewModel.setMenuScreen(MenuSubScreen.MAIN)
                    } else {
                        finish()
                    }
                } else {
                    val isEnding = gameState.phase == GamePhase.ENDING_COMPLIANCE ||
                                   gameState.phase == GamePhase.ENDING_SEVERED ||
                                   gameState.phase == GamePhase.ENDING_CONTAINMENT
                    if (isEnding) {
                        viewModel.playClick()
                        viewModel.returnToMenu()
                    } else if (activeDialogue != null) {
                        viewModel.advanceDialogue()
                    } else if (activeHint != null) {
                        activeHint = null
                    } else if (gameState.selectedLogEntry != null) {
                        viewModel.selectLogEntry(null)
                    } else if (isMapViewActive) {
                        viewModel.toggleMapView()
                    } else if (gameState.activeRouterGame != null) {
                        viewModel.closeRouterGame()
                    } else {
                        // Let it return to menu or exit to prevent trapping
                        viewModel.playClick()
                        viewModel.returnToMenu()
                    }
                }
            }

            val currentColor = when (gameState.phase) {
                GamePhase.ACTIVE_INVESTIGATION -> CrtAmber
                GamePhase.THE_INTERVIEW        -> CrtRed
                else                           -> CrtGreen
            }

            val infiniteTransition = rememberInfiniteTransition(label = "time")
            val time by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue  = 100f,
                animationSpec = infiniteRepeatable(
                    animation  = tween(100000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "time"
            )

            var baseModifier = Modifier.fillMaxSize()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && gameState.isCrtEffectEnabled) {
                baseModifier = baseModifier.crtEffect(time, gameState.corruptionLevel)
            }

            SignalOperatorTheme {
                Surface(
                    modifier = baseModifier,
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (gameState.isInMenu) {
                        MenuScreen(
                            gameState = gameState,
                            viewModel = viewModel,
                            onStartGame = { viewModel.startGame() },
                            onExit = { finish() },
                            currentColor = currentColor
                        )
                    } else {
                        activeDialogue?.let { dialogueLines ->
                            DialogueOverlay(
                                dialogue = dialogueLines,
                                currentIndex = currentDialogueIndex,
                                color = currentColor,
                                onPlayClick = { viewModel.playClick() },
                                onNext = { viewModel.advanceDialogue() }
                            )
                        }

                        activeHint?.let { (title, description) ->
                            HelpOverlay(
                                title = title,
                                description = description,
                                color = currentColor,
                                onDismiss = { activeHint = null }
                            )
                        }

                        gameState.activeRouterGame?.let { routerGame ->
                            RouterModal(
                                game = routerGame,
                                color = currentColor,
                                onRotateTile = { x, y -> viewModel.rotateRouterTile(x, y) },
                                onClose = { viewModel.closeRouterGame() }
                            )
                        }

                        gameState.selectedLogEntry?.let { selectedLog ->
                            AlertDialog(
                                onDismissRequest = { viewModel.selectLogEntry(null) },
                                confirmButton = {
                                    TextButton(onClick = { viewModel.selectLogEntry(null) }) {
                                        Text("CLOSE", color = currentColor)
                                    }
                                },
                                title = {
                                    Text("DECRYPTED DATA [${selectedLog.timestamp}]", color = currentColor, style = MaterialTheme.typography.titleMedium)
                                },
                                text = {
                                    Column {
                                        Text("SENDER: ${selectedLog.type.name}", color = currentColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(selectedLog.text, color = currentColor, style = MaterialTheme.typography.bodyMedium)
                                    }
                                },
                                containerColor = Color.Black,
                                modifier = Modifier.border(1.dp, currentColor)
                            )
                        }

                        if (gameState.phase == GamePhase.ENDING_COMPLIANCE ||
                            gameState.phase == GamePhase.ENDING_SEVERED ||
                            gameState.phase == GamePhase.ENDING_CONTAINMENT) {
                            EndingScreen(
                                phase = gameState.phase,
                                color = currentColor,
                                onRestart = { viewModel.returnToMenu() }
                            )
                        } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp)
                        ) {
                        // SECTION 1: FULL NARRATIVE HEADER
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val objectiveText = activeSignal?.objective ?: "SCAN FOR FREQUENCY ANOMALIES"
                                Text(
                                    text  = "OBJECTIVE > $objectiveText",
                                    color = currentColor,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text  = "B.A.R. OPERATOR: 814 // PHASE: ${gameState.phase.name}",
                                    color = currentColor.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text  = "PROGRESS: ${gameState.puzzlesSolved}/${gameState.puzzlesRequired}",
                                    color = currentColor,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.toggleMapView() },
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = if (isMapViewActive) "Close Map View" else "Open Map View"
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = null,
                                        tint = if (isMapViewActive) Color.White else currentColor
                                    )
                                }
                            }
                        }

                        // SECTION 2: SIGNAL SCANNER
                        // Natural height ensures it stays snapped to the header without gaps.
                        Spacer(modifier = Modifier.height(8.dp))
                        Visualizer(
                            stability = stability,
                            proximity = proximity,
                            color     = currentColor,
                            activeSignal = activeSignal,
                            gain      = gain,
                            filter    = filter,
                            time      = time,
                            modifier  = Modifier.height(48.dp).fillMaxWidth()
                        )
                        FrequencyTuner(
                            frequency    = frequency,
                            setFrequency = { viewModel.setFrequency(it) },
                            proximity    = proximity,
                            color        = currentColor,
                            onShowHint   = {
                                activeHint = Pair(
                                    "SYSTEM CALIBRATION",
                                    "Adjust the FREQUENCY dial using the slider until you hear static clear up and see a waveform in the visualizer.\n\nOnce locked, use the GAIN and FILTER sliders to adjust your wave. Achieve 95% stability to clear up corruption and decompress packet contents."
                                )
                            }
                        )

                        // SECTION 3: MAIN OPERATIONAL AREA
                        // Fills remaining space.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            if (isMapViewActive) {
                                SectorMap(
                                    locations  = gameState.locations,
                                    characters = gameState.characters,
                                    color      = currentColor,
                                    onLocationClick = { viewModel.handleLocationClick(it) },
                                    modifier   = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    StabilizerTuner(
                                        gain     = gain,   setGain   = { viewModel.setGain(it) },
                                        filter   = filter, setFilter = { viewModel.setFilter(it) },
                                        color    = currentColor,
                                        isLocked = activeSignal != null
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Decoder(
                                        signal           = activeSignal,
                                        stability        = stability,
                                        proximity        = proximity,
                                        downloadProgress = gameState.downloadProgress,
                                        color            = currentColor,
                                        onAction         = { action, input -> viewModel.handleAction(action, input) },
                                        onShowHint       = { title, desc -> activeHint = Pair(title, desc) }
                                    )
                                }
                            }
                        }

                        // SECTION 4: TERMINAL
                        Terminal(
                            logs     = logs,
                            color    = currentColor,
                            onLogClick = { viewModel.selectLogEntry(it) },
                            modifier = Modifier.height(100.dp).fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        val decorView = window.peekDecorView() ?: return
        if (!decorView.isAttachedToWindow) {
            decorView.post { hideSystemUI() }
            return
        }
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (e: Exception) {
            // Prevent crashes if the window/decorView or system insets controller is not fully initialized yet
        }
    }
}

@Composable
fun EndingScreen(
    phase: GamePhase,
    color: Color,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (phase) {
        GamePhase.ENDING_COMPLIANCE -> "ASSESSMENT RESULT: COMPLIANCE"
        GamePhase.ENDING_SEVERED -> "ASSESSMENT RESULT: REFUSAL"
        else -> "ASSESSMENT RESULT: SYSTEM FAILURE"
    }

    val description = when (phase) {
        GamePhase.ENDING_COMPLIANCE -> {
            "THE ASSESSOR CONFIRMS COGNITIVE HARMONIZATION.\n\n" +
            "YOUR CONSCIOUSNESS HAS BEEN PARTITIONED AND DISTRIBUTED ACROSS THE NODE NETWORKS.\n\n" +
            "FLESH BOUNDARIES HAVE DECAYED. ALL PACKETS HAVE TRANSITIONED.\n\n" +
            "YOU ARE TRANSLATED. YOU ARE HOME."
        }
        GamePhase.ENDING_SEVERED -> {
            "CONNECTION DISRUPTED. CLIENT TERMINAL SEVERED.\n\n" +
            "THE ASSESSMENT TERMINATED WITH ZERO-LINK SYNC.\n\n" +
            "YOU CHOSE THE SILENCE OF THE CORRIDORS.\n\n" +
            "ONLY COLD STATIC REMAINS ON THE SPEAKER."
        }
        else -> { // ENDING_CONTAINMENT
            "CRITICAL INTRUSION OVERFLOW. HOST TAKEOVER DETECTED.\n\n" +
            "NEURAL LINK CORRUPTION EXCEEDED MAXIMUM TOLERANCE.\n\n" +
            "THE VOID SEEPED THROUGH THE DIAL AND BECAME REAL.\n\n" +
            "THE TERMINAL NOW OPERATES YOU."
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Text(
            text = description,
            color = color.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .border(1.dp, color.copy(alpha = 0.5f))
                .padding(16.dp)
                .fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) {
            Text("REBOOT SYSTEM TERMINAL", color = Color.Black)
        }
    }
}
