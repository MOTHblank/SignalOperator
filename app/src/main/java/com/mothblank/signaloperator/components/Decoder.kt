package com.mothblank.signaloperator.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mothblank.signaloperator.models.PuzzleType
import com.mothblank.signaloperator.models.SignalData
import kotlinx.coroutines.delay

@Composable
fun Decoder(
    signal: SignalData?,
    stability: Float,
    proximity: Float,
    downloadProgress: Float,
    color: Color,
    onAction: (String, String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showObservationText by remember { mutableStateOf(true) }
    var lastSubmittedInput by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(signal) {
        input = ""
        lastSubmittedInput = null
        showObservationText = true
        if (signal?.puzzleType == PuzzleType.OBSERVATION) {
            delay(4000) // Show the report for 4 seconds
            showObservationText = false
        }
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, color.copy(alpha = 0.5f))
        .padding(12.dp)
    ) {
        Text("CONSOLE OUTPUT: DATA STREAM", color = color, style = MaterialTheme.typography.labelLarge)
        
        if (signal == null) {
            Text("WAITING FOR SIGNAL LOCK...", color = color.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp))
        } else {
            // Signal Strength always visible
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("STRENGTH:", color = color, style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { stability / 100f },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(8.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.2f)
                )
                Text("${stability.toInt()}%", color = color, style = MaterialTheme.typography.bodySmall)
            }

            if (downloadProgress < 100f) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("CONSOLE OUTPUT: DOWNLOADING INTERCEPT...", color = color, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("DOWNLOAD:", color = color, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(8.dp),
                        color = color,
                        trackColor = color.copy(alpha = 0.2f)
                    )
                    Text("${downloadProgress.toInt()}%", color = color, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isDetuned = proximity < 0.9f
                val isUncalibrated = stability < 90f

                if (isDetuned) {
                    Text(
                        "▲ WARNING: SIGNAL DETUNING - ADJUST FREQUENCY DIAL",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (isUncalibrated) {
                    Text(
                        "▲ WARNING: CALIBRATION LOSS - ADJUST GAIN/FILTER SLIDERS",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (!isDetuned && !isUncalibrated) {
                    Text(
                        "● LINK STABLE. RETRIEVING DATA PACKETS...",
                        color = color,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                if (stability < 95f) {
                    Text(
                        "DATA CORRUPTED. STABILIZE SIGNAL (GAIN/FILTER) TO RECOVER (95%+).", 
                        color = color.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                val label = when(signal.puzzleType) {
                    PuzzleType.CRYPTOGRAPHY -> "DECRYPTED MESSAGE"
                    PuzzleType.SEQUENCE -> "NEXT IN SEQUENCE"
                    PuzzleType.LOGIC -> "DEDUCTION RESULT"
                    PuzzleType.OBSERVATION -> "OBSERVATION"
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (signal.puzzleType == PuzzleType.OBSERVATION && !showObservationText) {
                    Text("QUESTION: ${signal.metadata.removePrefix("TYPE: OBSERVATION | Q: ")}", color = color)
                } else {
                    Text("INTERCEPT: ${signal.encodedMessage}", color = color)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Character-level error highlighting
                val isCorrectAttempt = if (signal.puzzleType == PuzzleType.CRYPTOGRAPHY) {
                    lastSubmittedInput?.filter { it.isLetter() }?.equals(signal.solution.filter { it.isLetter() }, ignoreCase = true) == true
                } else {
                    lastSubmittedInput?.equals(signal.solution, ignoreCase = true) == true
                }
                
                if (lastSubmittedInput != null && !isCorrectAttempt) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text("LAST ATTEMPT: ", color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                        if (signal.puzzleType == PuzzleType.CRYPTOGRAPHY) {
                            val target = signal.solution.filter { it.isLetter() }.uppercase()
                            val attempt = lastSubmittedInput!!.filter { it.isLetter() }.uppercase()
                            attempt.forEachIndexed { index, char ->
                                val isCorrect = index < target.length && char == target[index]
                                Text(
                                    text = char.toString(),
                                    color = if (isCorrect) color else Color.Red,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            val target = signal.solution.uppercase()
                            val attempt = lastSubmittedInput!!.uppercase()
                            attempt.forEachIndexed { index, char ->
                                val isCorrect = index < target.length && char == target[index]
                                Text(
                                    text = char.toString(),
                                    color = if (isCorrect) color else Color.Red,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                val isGlyphSequence = signal.puzzleType == PuzzleType.SEQUENCE && 
                        (signal.solution == "▲" || signal.solution == "★" || signal.solution == "●" || signal.solution == "■")

                val isInterview = signal.metadata.startsWith("INTERVIEW_QUESTION")

                if (isInterview) {
                    Column {
                        Text("RESPOND TO ENTITY:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        val choices = signal.solution.split("|")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            choices.forEach { choice ->
                                val selected = input == choice
                                OutlinedButton(
                                    onClick = { input = choice },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (selected) Color.Black else color,
                                        containerColor = if (selected) color else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                ) {
                                    Text(choice, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                } else if (signal.puzzleType == PuzzleType.CRYPTOGRAPHY) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(label, color = color) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = color,
                            unfocusedBorderColor = color,
                            focusedTextColor = color,
                            unfocusedTextColor = color,
                            cursorColor = color
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (signal.puzzleType == PuzzleType.LOGIC) {
                    val isBoolean = signal.solution == "0" || signal.solution == "1"
                    val isTemporal = signal.solution == "A" || signal.solution == "B" || signal.solution == "C"
                    
                    Column {
                        if (isBoolean) {
                            Text("SELECT BOOLEAN OUTPUT:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("0", "1").forEach { valStr ->
                                    val selected = input == valStr
                                    OutlinedButton(
                                        onClick = { input = valStr },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (selected) Color.Black else color,
                                            containerColor = if (selected) color else Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                    ) {
                                        Text(valStr, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else if (isTemporal) {
                            Text("SELECT DEDUCTION RESULT:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("A", "B", "C").forEach { valStr ->
                                    val selected = input == valStr
                                    OutlinedButton(
                                        onClick = { input = valStr },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (selected) Color.Black else color,
                                            containerColor = if (selected) color else Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                    ) {
                                        Text(valStr, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        } else {
                            Text("SELECT DIRECTION:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("NORTH", "EAST", "SOUTH", "WEST").forEach { dir ->
                                    val selected = input == dir
                                    OutlinedButton(
                                        onClick = { input = dir },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (selected) Color.Black else color,
                                            containerColor = if (selected) color else Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                    ) {
                                        Text(dir, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                } else if (isGlyphSequence) {
                    Column {
                        Text("SELECT TARGET GLYPH:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("▲", "★", "●", "■").forEach { glyph ->
                                val selected = input == glyph
                                OutlinedButton(
                                    onClick = { input = glyph },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (selected) Color.Black else color,
                                        containerColor = if (selected) color else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                ) {
                                    Text(glyph, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                } else if (signal.puzzleType == PuzzleType.SEQUENCE) {
                    val solInt = signal.solution.toIntOrNull() ?: 0
                    val options = remember(signal.id) {
                        val correct = solInt
                        val diff = if (correct > 10) 5 else 2
                        val list = mutableListOf(correct, correct + diff, correct - diff, correct + diff * 2)
                        val rand = java.util.Random(signal.id.hashCode().toLong())
                        list.distinct().shuffled(rand)
                    }
                    
                    Column {
                        Text("SELECT VALUE:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            options.forEach { opt ->
                                val optStr = opt.toString()
                                val selected = input == optStr
                                OutlinedButton(
                                    onClick = { input = optStr },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (selected) Color.Black else color,
                                        containerColor = if (selected) color else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                ) {
                                    Text(optStr, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                } else if (signal.puzzleType == PuzzleType.OBSERVATION) {
                    val isAnomaly = signal.solution == "TEMP" || signal.solution == "VOLT" || signal.solution == "CORE"
                    val isHexDump = signal.metadata.startsWith("TYPE: HEX_DUMP")
                    val isCoordinate = !isAnomaly && !isHexDump && signal.solution.all { it == 'A' || it == 'B' || it == 'C' || it == '1' || it == '2' || it == '3' }
                    
                    Column {
                        if (isHexDump) {
                            Text("SELECT ANOMALOUS HEX ADDRESS:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (r in 0 until 3) {
                                    val rowOffsetLabel = String.format("0x%02X", r * 16)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(rowOffsetLabel, color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                                        for (c in 0 until 4) {
                                            val addr = "$r$c"
                                            val selected = input == addr
                                            OutlinedButton(
                                                onClick = { input = addr },
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = if (selected) Color.Black else color,
                                                    containerColor = if (selected) color else Color.Transparent
                                                ),
                                                modifier = Modifier.weight(1f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, color),
                                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Text(addr, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (isAnomaly) {
                            Text("SELECT ANOMALOUS READOUT:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("TEMP", "VOLT", "CORE").forEach { sys ->
                                    val selected = input == sys
                                    OutlinedButton(
                                        onClick = { input = sys },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (selected) Color.Black else color,
                                            containerColor = if (selected) color else Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                    ) {
                                        Text(sys, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        } else if (isCoordinate) {
                            Text("REPLICATE GRID SEQUENCE:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val rows = listOf(
                                    listOf("A1", "A2", "A3"),
                                    listOf("B1", "B2", "B3"),
                                    listOf("C1", "C2", "C3")
                                )
                                rows.forEach { row ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        row.forEach { cell ->
                                            OutlinedButton(
                                                onClick = { if (input.length < 12) input += cell },
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                                                modifier = Modifier.weight(1f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                            ) {
                                                Text(cell, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("GRID: $input", color = color, style = MaterialTheme.typography.titleMedium)
                                Button(
                                    onClick = { input = "" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("CLEAR", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Text("REPLICATE SEQUENCE:", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                listOf("▲", "★", "●", "■").forEach { glyph ->
                                    OutlinedButton(
                                        onClick = { if (input.length < 6) input += glyph },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = color,
                                            containerColor = Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                    ) {
                                        Text(glyph, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("SEQUENCE: $input", color = color, style = MaterialTheme.typography.titleLarge)
                                Button(
                                    onClick = { input = "" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                ) {
                                    Text("CLEAR", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { 
                            lastSubmittedInput = input
                            onAction("COMMIT", input) 
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = color)
                    ) {
                        Text("COMMIT INTEL", color = Color.Black)
                    }
                    Button(onClick = { onAction("DISCARD", input) }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)) {
                        Text("DISCARD", color = Color.White)
                    }
                }
                
                val isMatch = if (signal.metadata.startsWith("INTERVIEW_QUESTION")) {
                    signal.solution.split("|").any { it.trim().equals(input.trim(), ignoreCase = true) }
                } else if (signal.puzzleType == PuzzleType.CRYPTOGRAPHY) {
                    input.filter { it.isLetter() }.equals(signal.solution.filter { it.isLetter() }, ignoreCase = true)
                } else {
                    input.trim().equals(signal.solution, ignoreCase = true)
                }
                if (isMatch) {
                    Text("MATCH FOUND. READY FOR TRANSMISSION.", color = color, modifier = Modifier.padding(top = 8.dp))
                }
            }
            }
        }
    }
}
