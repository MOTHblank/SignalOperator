package com.mothblank.signaloperator.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun FrequencyTuner(
    frequency: Float, 
    setFrequency: (Float) -> Unit,
    proximity: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "FREQUENCY: ${String.format(Locale.US, "%.2f", frequency)} MHz", 
                color = color, 
                style = MaterialTheme.typography.labelLarge
            )
            
            // Tuning LED indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LOCK SIGNAL:", color = color.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (proximity > 0.05f) color.copy(alpha = 0.2f + proximity * 0.8f) else color.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = color.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    if (proximity > 0.5f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .background(color = color, shape = CircleShape)
                        )
                    }
                }
            }
        }
        
        Slider(
            value = frequency,
            onValueChange = setFrequency,
            valueRange = 88f..108f,
            colors = SliderDefaults.colors(
                thumbColor = color, 
                activeTrackColor = color, 
                inactiveTrackColor = color.copy(alpha = 0.2f)
            ),
            modifier = Modifier.padding(vertical = 2.dp)
        )
        
        // Custom scale dial
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(top = 2.dp)
        ) {
            val width = size.width
            val height = size.height
            val scaleY = height / 3
            
            // Draw horizontal scale baseline
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = Offset(0f, scaleY),
                end = Offset(width, scaleY),
                strokeWidth = 1f
            )
            
            val range = 108f - 88f
            
            // Draw tick marks
            for (freqVal in 88..108) {
                val x = ((freqVal - 88f) / range) * width
                
                // Draw major tick
                drawLine(
                    color = color.copy(alpha = 0.7f),
                    start = Offset(x, scaleY - 6f),
                    end = Offset(x, scaleY + 6f),
                    strokeWidth = 1.5f
                )
                
                // Draw text labels
                if (freqVal % 2 == 0) {
                    drawContext.canvas.nativeCanvas.drawText(
                        freqVal.toString(),
                        x,
                        height - 2f,
                        android.graphics.Paint().apply {
                            this.color = color.toArgb()
                            this.textSize = 22f
                            this.isFakeBoldText = true
                            this.textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
            
            // Draw minor ticks (0.5 MHz marks)
            var minorFreq = 88.5f
            while (minorFreq < 108f) {
                if (minorFreq.toInt().toFloat() != minorFreq) {
                    val x = ((minorFreq - 88f) / range) * width
                    drawLine(
                        color = color.copy(alpha = 0.3f),
                        start = Offset(x, scaleY - 3f),
                        end = Offset(x, scaleY + 3f),
                        strokeWidth = 1f
                    )
                }
                minorFreq += 0.5f
            }
            
            // Draw cursor line for the active frequency
            val cursorX = ((frequency - 88f) / range) * width
            drawLine(
                color = Color.Red,
                start = Offset(cursorX, 0f),
                end = Offset(cursorX, scaleY + 12f),
                strokeWidth = 2f
            )
            
            // Pointer needle triangle at the top of the scale (at y = 0f)
            val needlePointer = Path()
            needlePointer.moveTo(cursorX, 6f)
            needlePointer.lineTo(cursorX - 5f, 0f)
            needlePointer.lineTo(cursorX + 5f, 0f)
            needlePointer.close()
            drawPath(
                path = needlePointer,
                color = Color.Red
            )
        }
    }
}

@Composable
fun StabilizerTuner(
    gain: Int, 
    setGain: (Int) -> Unit,
    filter: Int, 
    setFilter: (Int) -> Unit,
    color: Color,
    isLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("GAIN: $gain", color = color, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = gain.toFloat(),
                onValueChange = { setGain(it.toInt()) },
                valueRange = 0f..100f,
                enabled = isLocked,
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text("FILTER: $filter", color = color, style = MaterialTheme.typography.labelSmall)
            Slider(
                value = filter.toFloat(),
                onValueChange = { setFilter(it.toInt()) },
                valueRange = 0f..100f,
                enabled = isLocked,
                colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
            )
        }
    }
}
