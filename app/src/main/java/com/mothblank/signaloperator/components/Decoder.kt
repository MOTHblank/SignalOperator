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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.mothblank.signaloperator.ui.theme.CrtGreen
import com.mothblank.signaloperator.ui.theme.CrtRed

fun getDecoderHint(
    signal: SignalData?,
    stability: Float,
    downloadProgress: Float
): Pair<String, String> {
    if (signal == null) {
        return Pair(
            "DATA DECODER: SYSTEM IDLE",
            "No active intercept detected. Scan the frequency band (88-108 MHz) using the tuner dial. When you are close to a transmitter, the lock indicator will glow and a raw wave will appear in the oscilloscope. Hold the dial steady to lock onto the frequency and begin packet decompress."
        )
    }
    if (downloadProgress < 100f) {
        return Pair(
            "DATA INTERCEPT: DOWNLOADING",
            "A secure telemetry packet stream is currently being downloaded. You must maintain frequency lock (keep the LOCK LED active) and optimize signal alignment (keep GAIN and FILTER balanced) to prevent download drift or progress loss."
        )
    }
    if (stability < 95f) {
        return Pair(
            "DATA DECODER: SIGNAL CORRUPTION",
            "The data packet download has completed, but the local buffer is corrupted due to wave misalignment. Adjust the GAIN and FILTER sliders until stability is at least 95% to clear the distortion and reveal the decrypted message."
        )
    }

    // Now the puzzle is active and readable
    return when (signal.puzzleType) {
        PuzzleType.CRYPTOGRAPHY -> {
            when (signal.cipherType) {
                "CAESAR" -> Pair(
                    "DECIPHERING: CAESAR SHIFT",
                    "A Caesar Shift cipher substitutes each letter by shifting it a fixed number of positions down the alphabet.\n\nTo decrypt, shift each letter in the intercept BACKWARD by the offset specified in your objective (e.g., if offset is +3, shift 'D' to 'A', 'E' to 'B')."
                )
                "VIGENERE" -> Pair(
                    "DECIPHERING: VIGENERE STREAM",
                    "A Vigenere cipher is a polyalphabetic cipher that uses a repeating keyword to shift letters.\n\nTo decrypt, align the keyword with the ciphertext. The keyword's letters determine the backward shift for each corresponding letter (A=0 shift, B=1 shift, C=2 shift...)."
                )
                "REVERSE" -> Pair(
                    "DECIPHERING: REVERSE BITSTREAM",
                    "The text segment was received backwards due to a reverse chronological stream transmission.\n\nRead and translate the message characters from right to left to enter the correct response sequence."
                )
                "NATO" -> Pair(
                    "DECIPHERING: PHONETIC TRANSCRIPT",
                    "The message has been encoded using the phonetic NATO spelling alphabet (Alpha, Bravo, Charlie...).\n\nTo decrypt, extract the first letter of each phonetic word to reconstruct the hidden operational message."
                )
                "RAIL_FENCE" -> Pair(
                    "DECIPHERING: RAIL FENCE",
                    "A Rail Fence cipher writes characters diagonally down and up across imaginary horizontal rails, then reads them row by row.\n\nFor a 2-rail fence, split the ciphertext exactly in half. Write the second half underneath the first half, then read diagonally in a zig-zag (top-left, bottom-next, top-next...)."
                )
                "PLAYFAIR" -> Pair(
                    "DECIPHERING: PLAYFAIR GRID",
                    "A Playfair cipher encrypts pairs of letters (digraphs) using a key matrix.\n\nSince the system automatically handles grid transposition, your task is to recognize the underlying words or use security matrix overrides."
                )
                else -> Pair(
                    "DECIPHERING: CRYPTOGRAPHY",
                    "A secure signal has been intercepted. Analyze the character pattern to identify shift offsets, spelling substitutions, or reversed sequencing, then input the plain text solution."
                )
            }
        }
        PuzzleType.SEQUENCE -> {
            val isGlyphSequence = signal.solution == "▲" || signal.solution == "★" || signal.solution == "●" || signal.solution == "■"
            if (isGlyphSequence) {
                Pair(
                    "ANALYSIS: GLYPH SEQUENCE",
                    "An anomalous sequence of visual glyphs has been detected.\n\nAnalyze the order, rotation, or cyclic repetition of the symbols (▲, ★, ●, ■) to determine the logical next element in the chain."
                )
            } else {
                Pair(
                    "ANALYSIS: OFFSET SEQUENCE",
                    "A mathematical sequence with a constant calibration offset has been intercepted.\n\nDetermine the logical step between successive integers, compute the next value, and then apply the offset specified in the metadata (e.g., if target is 20 and offset is -2, select 18)."
                )
            }
        }
        PuzzleType.LOGIC -> {
            val isBoolean = signal.solution == "0" || signal.solution == "1"
            val isTemporal = signal.solution == "A" || signal.solution == "B" || signal.solution == "C"
            val isInterview = signal.metadata.startsWith("INTERVIEW_QUESTION")

            if (isInterview) {
                Pair(
                    "SYSTEM ENGAGEMENT: THE INTERVIEW",
                    "The entity is communicating directly through the terminal stream.\n\nThere are no right or wrong answers. Choose the option that reflects your choice, but be prepared for the consequences of your response."
                )
            } else if (isBoolean) {
                Pair(
                    "ANALYSIS: BOOLEAN GATES",
                    "The input values must be processed through standard digital logic gates.\n\n• AND: Outputs 1 only if all inputs are 1.\n• OR: Outputs 1 if any input is 1.\n• NOT: Inverts the input (1 becomes 0, 0 becomes 1).\n• NAND/XOR: Resolve combinations step-by-step."
                )
            } else if (isTemporal) {
                Pair(
                    "ANALYSIS: TEMPORAL ORDERING",
                    "A series of historical events and chronological timestamps has been intercepted.\n\nSort the events in ascending chronological order (oldest to newest) to determine which logical scenario (A, B, or C) occurred."
                )
            } else {
                Pair(
                    "ANALYSIS: SPATIAL DEDUCTION",
                    "A sequence of grid movement vectors has been logged.\n\nFollow the instructions from the origin coordinates (e.g., coordinate + movement step). Calculate the net spatial vector to determine the final cardinal direction (NORTH, EAST, SOUTH, WEST)."
                )
            }
        }
        PuzzleType.OBSERVATION -> {
            val isAnomaly = signal.solution == "TEMP" || signal.solution == "VOLT" || signal.solution == "CORE"
            val isHexDump = signal.metadata.startsWith("TYPE: HEX_DUMP")
            val isDeadDrop = signal.metadata.startsWith("TYPE: REAL-WORLD INTERCEPT")
            val isMundane = signal.solution == "DISCARD"

            if (isMundane) {
                Pair(
                    "BROADCAST: UNCLASSIFIED STATION",
                    "An unclassified public radio broadcast is tuned on this frequency.\n\nCivilian broadcasts do not contain active intelligence. Click the 'DISCARD' button at the bottom of the console to bypass and silence this frequency."
                )
            } else if (isDeadDrop) {
                Pair(
                    "TELEMETRY: SYSTEM DETECTED",
                    "A local device telemetry event was intercepted.\n\nVerify either the local battery percentage or perform a standard ACK handshake to sync the operator console with the kernel."
                )
            } else if (isHexDump) {
                Pair(
                    "RECALL: HEX MEMORY DUMP",
                    "A memory address block was shown briefly before the system redacted it.\n\nRecall the single anomalous memory address (row index and column index) where the hex byte differed from the normal baseline sequence."
                )
            } else if (isAnomaly) {
                Pair(
                    "RECALL: CRITICAL READOUT",
                    "A high-priority diagnostic panel flashed on screen.\n\nSelect the primary hardware system index (TEMP, VOLT, or CORE) that crossed into the critical error threshold during that active window."
                )
            } else {
                Pair(
                    "RECALL: SPATIAL GRID PATTERN",
                    "A series of highlighted grid coordinates was shown in sequence.\n\nReplicate the exact coordinates on the grid pad in the same order as they appeared before redaction."
                )
            }
        }
    }
}

@Composable
fun Decoder(
    signal: SignalData?,
    stability: Float,
    proximity: Float,
    downloadProgress: Float,
    color: Color,
    onAction: (String, String) -> Unit,
    onShowHint: (String, String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var showObservationText by remember { mutableStateOf(true) }
    var lastSubmittedInput by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(signal) {
        input = ""
        lastSubmittedInput = null
        showObservationText = true
    }

    Column(modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, color.copy(alpha = 0.5f))
        .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("CONSOLE OUTPUT: DATA STREAM", color = color, style = MaterialTheme.typography.labelLarge)
            IconButton(
                onClick = {
                    val hint = getDecoderHint(signal, stability, downloadProgress)
                    onShowHint(hint.first, hint.second)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Decoder Information",
                    tint = color.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

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
                val isGlyphSequence = signal.puzzleType == PuzzleType.SEQUENCE &&
                        (signal.solution == "▲" || signal.solution == "★" || signal.solution == "●" || signal.solution == "■")

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
                    if (isGlyphSequence) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("KEY MATRIX: ${signal.metadata}", color = color.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                    }
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
                                    color = if (isCorrect) CrtGreen else CrtRed,
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
                                    color = if (isCorrect) CrtGreen else CrtRed,
                                    style = MaterialTheme.typography.bodySmall
                                 )
                            }
                        }
                    }
                }


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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                    val isDeadDrop = signal.metadata.startsWith("TYPE: REAL-WORLD INTERCEPT")
                    val isMundane = signal.solution == "DISCARD"
                    val isCoordinate = !isAnomaly && !isHexDump && !isDeadDrop && !isMundane && signal.solution.all { it == 'A' || it == 'B' || it == 'C' || it == '1' || it == '2' || it == '3' }

                    Column {
                        if (isMundane) {
                            Text("● PUBLIC CIVILIAN TRANSMISSION DETECTED", color = color, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Station broadcast consists of public traffic, weather updates, or audio noise. No military or proctor intelligence identified on this frequency.",
                                color = color.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "RECOMMENDED PROTOCOL: PRESS 'DISCARD' BUTTON TO SILENCE AND BYPASS THIS FREQUENCY.",
                                color = color,
                                style = MaterialTheme.typography.labelMedium
                            )
                        } else if (isDeadDrop) {
                            if (signal.solution == "ACK") {
                                Text("● LOCAL TELEMETRY: HARDWARE HANDSHAKE REQUEST", color = color, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("The remote hardware has requested a standard handshake signal to log system connection. Click below to generate ACK.", color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(12.dp))
                                val isAcked = input == "ACK"
                                OutlinedButton(
                                    onClick = { input = "ACK" },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (isAcked) Color.Black else color,
                                        containerColor = if (isAcked) color else Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, color)
                                ) {
                                    Text("TRANSMIT ACK SIGNATURE", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Text("● LOCAL TELEMETRY: POWER STATUS INTRUSION", color = color, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Enter the logged device battery percentage (%) displayed in the diagnostic intercept above to verify telemetry data:", color = color.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = input,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) input = it },
                                    label = { Text("ENTER PERCENTAGE (%)", color = color) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = color,
                                        unfocusedBorderColor = color,
                                        focusedTextColor = color,
                                        unfocusedTextColor = color,
                                        cursorColor = color
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else if (isHexDump) {
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
