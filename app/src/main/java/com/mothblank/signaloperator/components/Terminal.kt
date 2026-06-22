package com.mothblank.signaloperator.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mothblank.signaloperator.models.LogEntry

import androidx.compose.ui.tooling.preview.Preview
import com.mothblank.signaloperator.models.LogType
import java.util.UUID

@Composable
fun Terminal(
    logs: List<LogEntry>, 
    color: Color, 
    onLogClick: (LogEntry) -> Unit, 
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier
        .fillMaxWidth()
        .border(1.dp, color)
        .padding(8.dp)
    ) {
        LazyColumn(reverseLayout = true) {
            items(logs.reversed()) { log ->
                Text(
                    text = "[${log.timestamp}] ${log.text}",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLogClick(log) }
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun TerminalPreview() {
    Terminal(
        logs = listOf(
            LogEntry(UUID.randomUUID().toString(), "12:00:00", "SYSTEM READY", LogType.SYSTEM),
            LogEntry(UUID.randomUUID().toString(), "12:00:05", "SIGNAL DETECTED", LogType.INTERCEPT)
        ),
        color = Color.Green,
        onLogClick = {}
    )
}
