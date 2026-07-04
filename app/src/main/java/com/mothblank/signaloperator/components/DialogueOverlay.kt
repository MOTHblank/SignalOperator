package com.mothblank.signaloperator.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mothblank.signaloperator.models.DialogueLine
import kotlinx.coroutines.delay

@Composable
fun DialogueOverlay(
    dialogue: List<DialogueLine>,
    currentIndex: Int,
    color: Color,
    onPlayClick: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentIndex >= dialogue.size) return
    val line = dialogue[currentIndex]

    var visibleTextLength by remember(currentIndex) { mutableStateOf(0) }
    val fullText = line.text

    LaunchedEffect(currentIndex) {
        visibleTextLength = 0
        for (i in 1..fullText.length) {
            delay(25) // Typing speed (ms per character)
            visibleTextLength = i
            if (i % 2 == 1) { // Play typewriter mechanical click on every alternate character
                onPlayClick()
            }
        }
    }

    val isDoneTyping = visibleTextLength >= fullText.length
    val typedText = fullText.substring(0, visibleTextLength)

    // Flashing terminal prompt block cursor animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                if (!isDoneTyping) {
                    visibleTextLength = fullText.length
                } else {
                    onNext()
                }
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(2.dp, color)
                .padding(24.dp)
        ) {
            // Header area: Speaker name and status
            Text(
                text = "● INCOMING_TRANSMISSION // SOURCE: ${line.speaker.uppercase()}",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Horizontal thin CRT separator line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(color.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Body Area: Scrolling typewriter text
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = buildString {
                        append(typedText)
                        if (!isDoneTyping || cursorAlpha > 0.5f) {
                            append("▮")
                        }
                    },
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer navigation options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (!isDoneTyping) "[TAP SCREEN TO SKIP]" else "[TAP SCREEN TO CONTINUE]",
                    color = color.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                Surface(
                    color = if (isDoneTyping) color else Color.Transparent,
                    modifier = Modifier
                        .border(1.dp, color)
                        .clickable(enabled = isDoneTyping) {
                            onNext()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "ACKNOWLEDGE >",
                        color = if (isDoneTyping) Color.Black else color.copy(alpha = 0.5f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
