package com.mothblank.signaloperator

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mothblank.signaloperator.components.*
import com.mothblank.signaloperator.models.FeedbackTone
import com.mothblank.signaloperator.models.GamePhase
import com.mothblank.signaloperator.models.MenuSubScreen
import com.mothblank.signaloperator.models.OperatorFeedback
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

            val gameState by viewModel.gameState.collectAsState()
            val logs by viewModel.logs.collectAsState()
            val operatorFeedback by viewModel.operatorFeedback.collectAsState()
            val bootStep by viewModel.bootStep.collectAsState()
            val signalRuntime by viewModel.signalRuntime.collectAsState()
            val activeSignal = signalRuntime.activeSignal
            val stability = signalRuntime.stability
            val proximity = signalRuntime.proximity
            val frequency = signalRuntime.frequency
            val gain = signalRuntime.gain
            val filter = signalRuntime.filter
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
                    } else if (gameState.activeRouterGame != null) {
                        // Firewall abort is destructive. Require the explicit modal action.
                        viewModel.playAlert()
                    } else if (gameState.isMapViewActive) {
                        viewModel.toggleMapView()
                    } else {
                        // Let it return to menu or exit to prevent trapping
                        viewModel.playClick()
                        viewModel.returnToMenu()
                    }
                }
            }

            val currentColor = when (gameState.phase) {
                GamePhase.ACTIVE_INVESTIGATION,
                GamePhase.ENDING_CONTAINMENT -> CrtAmber

                GamePhase.THE_INTERVIEW,
                GamePhase.ENDING_COMPLIANCE -> CrtRed

                else -> CrtGreen
            }

            var baseModifier = Modifier.fillMaxSize()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && gameState.isCrtEffectEnabled) {
                baseModifier = baseModifier.crtEffect(gameState.corruptionLevel)
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
                        bootStep?.let { step ->
                            BootSequenceOverlay(step = step, color = currentColor)
                        }

                        operatorFeedback?.let { feedback ->
                            OperatorFeedbackOverlay(
                                feedback = feedback,
                                color = currentColor
                            )
                        }

                        activeDialogue?.let { dialogueLines ->
                            DialogueOverlay(
                                dialogue = dialogueLines,
                                currentIndex = currentDialogueIndex,
                                color = currentColor,
                                onTypewriterTick = { viewModel.playTypewriterTick() },
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
                                locationName = gameState.locations
                                    .firstOrNull { it.id == routerGame.locationId }
                                    ?.name
                                    ?: "UNKNOWN NODE",
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
                                        Text("LOG CLASS: ${selectedLog.type.name}", color = currentColor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
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
                                endingSummary = gameState.endingSummary,
                                containmentIntegrity = gameState.containmentIntegrity,
                                exposure = gameState.exposure,
                                trustInEcho = gameState.trustInEcho,
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
                                    text = "B.A.R. OPERATOR: 814 // PHASE: ${gameState.phase.name.replace('_', ' ')}", 
                                    color = currentColor.copy(alpha = 0.7f), 
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "PROGRESS: ${gameState.puzzlesSolved}/${gameState.puzzlesRequired}",
                                    color = currentColor,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "NET: ${gameState.containmentIntegrity}%  EXP: ${gameState.exposure}%  TRUST: ${gameState.trustInEcho}%  SEC: ${gameState.securityCharges}",
                                    color = currentColor.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { viewModel.toggleMapView() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Place,
                                        contentDescription = "Sector Network",
                                        tint = if (gameState.isMapViewActive) Color.White else currentColor
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (gameState.isMapViewActive) {
                                            "RADIO"
                                        } else {
                                            val alerts = gameState.locations.count {
                                                it.status != com.mothblank.signaloperator.models.LocationStatus.SECURE
                                            }
                                            if (alerts > 0) "MAP [$alerts]" else "MAP"
                                        },
                                        color = if (gameState.isMapViewActive) Color.White else currentColor,
                                        style = MaterialTheme.typography.labelMedium
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
                            modifier  = Modifier.height(48.dp).fillMaxWidth()
                        )
                        FrequencyTuner(
                            frequency = frequency,
                            setFrequency = { viewModel.setFrequency(it) },
                            proximity = proximity,
                            knownFrequencies = gameState.knownFrequencies,
                            isLocked = activeSignal != null,
                            color = currentColor,
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
                            if (gameState.isMapViewActive) {
                                SectorMap(
                                    locations = gameState.locations,
                                    characters = gameState.characters,
                                    networkLinks = gameState.networkLinks,
                                    securityCharges = gameState.securityCharges,
                                    containmentIntegrity = gameState.containmentIntegrity,
                                    exposure = gameState.exposure,
                                    trustInEcho = gameState.trustInEcho,
                                    color = currentColor,
                                    onLocationClick = { viewModel.handleLocationClick(it) },
                                    modifier = Modifier.fillMaxSize()
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
                                        color = currentColor,
                                        isLocked = activeSignal != null,
                                        stability = stability
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
private fun BootSequenceOverlay(
    step: Int,
    color: Color
) {
    val lines = listOf(
        "POWER BUS ........ NOMINAL",
        "RF FRONT-END ..... ONLINE",
        "DECODER BUFFER ... CLEARED",
        "OPERATOR LINK .... READY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .border(1.dp, color.copy(alpha = 0.45f))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "B.A.R. SIGNAL OPERATIONS TERMINAL",
                color = color,
                style = MaterialTheme.typography.titleMedium
            )
            HorizontalDivider(color = color.copy(alpha = 0.3f))
            lines.forEachIndexed { index, line ->
                Text(
                    if (index <= step) "> $line" else "  ${line.substringBefore(" ....")} ........",
                    color = color.copy(alpha = if (index <= step) 0.9f else 0.25f),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun OperatorFeedbackOverlay(
    feedback: OperatorFeedback,
    color: Color
) {
    val feedbackColor = when (feedback.tone) {
        FeedbackTone.POSITIVE -> color
        FeedbackTone.NEGATIVE -> Color.Red
        FeedbackTone.SYSTEM -> color.copy(alpha = 0.75f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(12f)
            .padding(top = 76.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = feedback.text,
            color = feedbackColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.92f))
                .border(1.dp, feedbackColor.copy(alpha = 0.7f))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun EndingScreen(
    phase: GamePhase,
    endingSummary: String?,
    containmentIntegrity: Int,
    exposure: Int,
    trustInEcho: Int,
    color: Color,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (phase) {
        GamePhase.ENDING_COMPLIANCE -> "ASSESSMENT RESULT: COMPLIANCE"
        GamePhase.ENDING_SEVERED -> "ASSESSMENT RESULT: SEVERED"
        else -> "ASSESSMENT RESULT: CONTAINMENT"
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
            "EMERGENCY CONTAINMENT PROTOCOL COMPLETE.\n\n" +
            "THE SURVIVING NETWORK HAS BEEN ISOLATED FROM THE EXTERNAL CARRIER.\n\n" +
            "THE ENTITY REMAINS PRESENT, BUT ITS ROUTES ARE BOUNDED BY THE NODES YOU LEFT INTACT.\n\n" +
            "THE FINAL COST DEPENDS ON THE STATE OF THE FIELD NETWORK."
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
        if (!endingSummary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "RUN CONSEQUENCE > $endingSummary",
                color = color,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .border(1.dp, color.copy(alpha = 0.35f))
                    .padding(12.dp)
                    .fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "FINAL NETWORK // CONTAINMENT $containmentIntegrity% // EXPOSURE $exposure% // ECHO TRUST $trustInEcho%",
            color = color.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall
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
