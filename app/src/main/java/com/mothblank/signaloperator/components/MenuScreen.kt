package com.mothblank.signaloperator.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.toggleable
import com.mothblank.signaloperator.MainViewModel
import com.mothblank.signaloperator.models.GameState
import com.mothblank.signaloperator.models.MenuSubScreen
import kotlinx.coroutines.delay

@Composable
fun MenuScreen(
    gameState: GameState,
    viewModel: MainViewModel,
    onStartGame: () -> Unit,
    onExit: () -> Unit,
    currentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (gameState.currentMenuScreen) {
            MenuSubScreen.MAIN -> MainMenuLayout(viewModel, onStartGame, onExit, currentColor)
            MenuSubScreen.OPTIONS -> OptionsLayout(gameState, viewModel, currentColor)
            MenuSubScreen.HIGHSCORES -> HighscoresLayout(viewModel, currentColor)
            MenuSubScreen.HELP -> HelpLayout(viewModel, currentColor)
        }
    }
}

@Composable
fun MainMenuLayout(
    viewModel: MainViewModel,
    onStartGame: () -> Unit,
    onExit: () -> Unit,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Blinking ASCII Art Title (Retro theme)
        val infiniteTransition = rememberInfiniteTransition(label = "title_blink")
        val titleAlpha by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "title_alpha"
        )

        Text(
            text = """
             ____ ___ ____ _   _  ____ _
            / ___|_ _/ ___| \ | |/ ___| |
            \___ \ | | |  |  \| | |  _| |
             ___) | | |___| |\  | |_| | |___
            |____/___\____|_| \_|\____|_____|
             ___  ____  _____ ____    _  _____ ___  ____
            / _ \|  _ \| ____|  _ \  / \|_   _/ _ \|  _ \
           | | | | |_) |  _| | |_) |/ _ \ | |/ | | | |_) |
           | |_| |  __/| |___|  _ </ ___ \| || |_| |  _ <
            \___/|_|   |_____|_| \_/_/   \_\_| \___/|_| \_\
            """.trimIndent(),
            color = color.copy(alpha = titleAlpha),
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            lineHeight = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "SECURITY SESSION TERMINAL ACCESS v2.8",
            color = color.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Terminal Prompt Box
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(1.dp, color.copy(alpha = 0.3f))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TerminalMenuItem(
                    label = "[01] START_COMMUNICATION_LINK",
                    onClick = {
                        viewModel.playClick()
                        onStartGame()
                    },
                    color = color
                )
                TerminalMenuItem(
                    label = "[02] CONFIGURE_INTERFACE_OPTIONS",
                    onClick = {
                        viewModel.playClick()
                        viewModel.setMenuScreen(MenuSubScreen.OPTIONS)
                    },
                    color = color
                )
                TerminalMenuItem(
                    label = "[03] RETRIEVE_OPERATOR_RECORDS",
                    onClick = {
                        viewModel.playClick()
                        viewModel.setMenuScreen(MenuSubScreen.HIGHSCORES)
                    },
                    color = color
                )
                TerminalMenuItem(
                    label = "[04] VIEW_FIELD_MANUAL",
                    onClick = {
                        viewModel.playClick()
                        viewModel.setMenuScreen(MenuSubScreen.HELP)
                    },
                    color = color
                )
                TerminalMenuItem(
                    label = "[05] TERMINATE_SESSION",
                    onClick = {
                        viewModel.playClick()
                        onExit()
                    },
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Blinking input cursor line
        var showCursor by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(600)
                showCursor = !showCursor
            }
        }
        Text(
            text = "OPERATOR_LOGGED_IN: OP-814${if (showCursor) "█" else " "}",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
fun OptionsLayout(
    gameState: GameState,
    viewModel: MainViewModel,
    color: Color
) {
    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = {
                viewModel.playClick()
                showResetConfirm = false
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.playClick()
                    viewModel.clearData()
                    showResetConfirm = false
                }) {
                    Text("YES, WIPE MEMORY", color = Color.Red, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.playClick()
                    showResetConfirm = false
                }) {
                    Text("CANCEL", color = color, fontFamily = FontFamily.Monospace)
                }
            },
            title = {
                Text("CAUTION: SECURE DATA PURGE", color = Color.Red, fontFamily = FontFamily.Monospace)
            },
            text = {
                Text("This action will restore all preferences and delete saved scores. Proceed?", color = color, fontFamily = FontFamily.Monospace)
            },
            containerColor = Color.Black,
            modifier = Modifier.border(1.dp, color)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(1.dp, color)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "--- CONFIGURE INTERFACE ---",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Option: CRT scanlines
        TerminalToggleOption(
            label = "CRT SCANLINE SHADER",
            description = "Applies structural retro-curvature & static distortion.",
            enabled = gameState.isCrtEffectEnabled,
            onToggle = { viewModel.toggleCrtEffect() },
            color = color
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Option: Static Sound
        TerminalToggleOption(
            label = "HETERODYNE STATIC HUM",
            description = "Procedural audio synthesizer tracking dial proximity.",
            enabled = gameState.isSoundEnabled,
            onToggle = { viewModel.toggleSound() },
            color = color
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Option: TTS
        TerminalToggleOption(
            label = "TTS INTERCEPT VOICE ASSIST",
            description = "Synthesizes voiceovers for anomalous transmissions.",
            enabled = gameState.isTtsEnabled,
            onToggle = { viewModel.toggleTts() },
            color = color
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Actions
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    viewModel.playAlert()
                    showResetConfirm = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Red),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("RESET HARDWARE MEMORY", color = Color.Red, fontFamily = FontFamily.Monospace)
            }

            Button(
                onClick = {
                    viewModel.playClick()
                    viewModel.setMenuScreen(MenuSubScreen.MAIN)
                },
                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, color),
                shape = MaterialTheme.shapes.extraSmall
            ) {
                Text("RETURN TO SESSION INDEX", color = color, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun HighscoresLayout(
    viewModel: MainViewModel,
    color: Color
) {
    val highscores by viewModel.highScores.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(1.dp, color)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "=== OPERATOR COGNITIVE RECORDS ===",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("OP ID", color = color.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
            Text("PHASE", color = color.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(2f))
            Text("INTEL", color = color.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            Text("SCORE", color = color.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
        }

        HorizontalDivider(color = color, modifier = Modifier.padding(bottom = 8.dp))

        // Highscore Rows
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (highscores.isEmpty()) {
                Text(
                    "NO LOG DATA FOUND.",
                    color = color.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                highscores.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${index + 1}. ${entry.operatorId}", color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1.5f))
                        Text(entry.maxPhase, color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(2f))
                        Text(entry.intelSaved.toString(), color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                        Text(entry.score.toString(), color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1.5f), textAlign = TextAlign.End)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.playClick()
                viewModel.setMenuScreen(MenuSubScreen.MAIN)
            },
            colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, color),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text("RETURN TO SESSION INDEX", color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun HelpLayout(
    viewModel: MainViewModel,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .border(1.dp, color)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "=== FIELD OPERATIONAL PROTOCOLS ===",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            HelpSection("01. SIGNAL ACQUISITION", "Turn the large FREQUENCY tuner (88-108 MHz) to scan for radio anomalies. Intercept locks automatically trigger the decoding terminal.", color)
            HelpSection("02. OSCILLOSCOPE CALIBRATION", "Use GAIN and FILTER sliders to match the solid active wave with the dotted reference wave. Achieving 95% stability is required to decrypt telemetry packet buffers.", color)
            HelpSection("03. CYPHER RESOLUTION", "Use the decoder keyboard to translate raw streams. Puzzles shift dynamically based on seed conditions.", color)
            HelpSection("04. SECTOR NETWORK", "Toggle map coordinates to observe character activity and site security indexes. Corrupted sites force logic firewalls that must be resolved.", color)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.playClick()
                viewModel.setMenuScreen(MenuSubScreen.MAIN)
            },
            colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, color),
            shape = MaterialTheme.shapes.extraSmall
        ) {
            Text("RETURN TO SESSION INDEX", color = color, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun TerminalMenuItem(
    label: String,
    onClick: () -> Unit,
    color: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val isActive = isHovered || isFocused || isPressed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isActive) "> " else "  ",
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = if (isActive) Color.Black else color,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(if (isActive) color else Color.Transparent)
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun TerminalToggleOption(
    label: String,
    description: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { onToggle() }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = color.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Surface(
                color = if (enabled) color else Color.Transparent,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.border(1.dp, color, MaterialTheme.shapes.extraSmall)
            ) {
                Text(
                    text = if (enabled) "ENABLED" else "DISABLED",
                    color = if (enabled) Color.Black else color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
        HorizontalDivider(color = color.copy(alpha = 0.15f), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun EndingShutdownScreen(
    gameState: GameState,
    onBackToMenu: () -> Unit,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val title = when (gameState.phase) {
            com.mothblank.signaloperator.models.GamePhase.ENDING_COMPLIANCE -> "=== COGNITIVE INTEGRATION SUCCESSFUL ==="
            com.mothblank.signaloperator.models.GamePhase.ENDING_SEVERED -> "=== SESSION TERMINATED BY CLIENT ==="
            else -> "=== CRITICAL SECURITY LEAK DETECTED ==="
        }

        Text(
            text = title,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(1.dp, color)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("OPERATOR ID: OP-${gameState.seed % 1000}", color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("PHASE: ${gameState.phase.name}", color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("INTEL FILES ARCHIVED: ${gameState.archivedSignals}", color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text("INTERCEPTS DISCARDED: ${gameState.ignoredSignals}", color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp)

                val finalScore = (gameState.archivedSignals * 1000 - gameState.ignoredSignals * 200).coerceAtLeast(0)
                Text("FINAL COGNITIVE SCORE: $finalScore PTS", color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        val blinkTransition = rememberInfiniteTransition(label = "ending_blink")
        val blinkAlpha by blinkTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ending_alpha"
        )

        Text(
            text = "> PRESS HERE TO RETURN TO INDEX <",
            color = color.copy(alpha = blinkAlpha),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(role = Role.Button) { onBackToMenu() }
                .padding(12.dp)
        )
    }
}
