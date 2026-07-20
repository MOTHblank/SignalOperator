package com.mothblank.signaloperator.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun HelpOverlay(
    title: String,
    description: String,
    color: Color,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(1.dp, color),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "=== $title ===",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = title.replace("===", "").trim() }
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = color.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .align(Alignment.End)
                        .border(1.dp, color),
                    shape = MaterialTheme.shapes.extraSmall,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "CLOSE INTERCEPT INFO",
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
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
