package com.mothblank.signaloperator.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun BlinkingCursor(
    color: Color,
    modifier: Modifier = Modifier,
    cursorSymbol: String = "█",
    blinkRate: Int = 500
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = blinkRate * 2
                0f at 0
                0f at blinkRate
                1f at blinkRate + 1
                1f at blinkRate * 2
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "cursor_alpha"
    )

    Text(
        text = cursorSymbol,
        color = color.copy(alpha = alpha),
        fontFamily = FontFamily.Monospace,
        modifier = modifier.clearAndSetSemantics { } // Prevent screen reader spam
    )
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    style: TextStyle = TextStyle.Default,
    delayPerChar: Long = 50L,
    showCursor: Boolean = false,
    cursorColor: Color = color,
    onAnimationComplete: () -> Unit = {}
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        displayedText = ""
        for (i in text.indices) {
            displayedText = text.substring(0, i + 1)
            kotlinx.coroutines.delay(delayPerChar)
        }
        onAnimationComplete()
    }

    Row(modifier = modifier) {
        Text(
            text = displayedText,
            color = color,
            style = style.copy(fontFamily = FontFamily.Monospace)
        )
        if (showCursor) {
            BlinkingCursor(
                color = cursorColor,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}
