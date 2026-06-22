package com.mothblank.signaloperator.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun HelpOverlay(onDismiss: () -> Unit, color: Color) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .border(2.dp, color),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "SIGNAL OPERATOR: FIELD MANUAL",
                    style = MaterialTheme.typography.headlineSmall,
                    color = color
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                HelpSection("TUNING", "Adjust FREQUENCY until a coherent waveform appears in the visualizer. Use GAIN and FILTER to stabilize the signal. 80% strength required for data recovery.", color)
                
                HelpSection("CAESAR SHIFT", "A simple substitution cipher. Each letter is shifted a fixed number of positions down the alphabet. Shift back to decrypt.", color)
                
                HelpSection("REVERSE BITSTREAM", "Characters are transmitted in reverse chronological order. Read from right to left.", color)
                
                HelpSection("VIGENERE STREAM", "A more complex cipher using a keyword. Each letter of the keyword determines the shift for the corresponding message letter.", color)
                
                HelpSection("LOGIC SEQUENCES", "Identify the mathematical or logical pattern in the numeric sequence to predict the next value.", color)
                
                HelpSection("SPATIAL DEDUCTION", "Analyze movement vectors and relative positions to calculate the final intercept point.", color)
                
                HelpSection("OBSERVATION", "Intel flashes briefly before redaction. You must memorize the specific requested detail immediately.", color)

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = color),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("CLOSE MANUAL", color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun HelpSection(title: String, description: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.8f))
        HorizontalDivider(color = color.copy(alpha = 0.3f), modifier = Modifier.padding(top = 8.dp))
    }
}
