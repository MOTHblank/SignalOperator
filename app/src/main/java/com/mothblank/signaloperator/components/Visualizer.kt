package com.mothblank.signaloperator.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import com.mothblank.signaloperator.models.SignalData

import androidx.compose.ui.tooling.preview.Preview

@Composable
fun Visualizer(
    stability: Float,
    proximity: Float,
    color: Color,
    activeSignal: SignalData?,
    gain: Int,
    filter: Int,
    time: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 0.dp)
        .border(1.dp, color.copy(alpha = 0.5f))
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val points = 80
        val step = width / points

        // 1. Draw Cathode-Ray Grid Background
        val gridAlpha = 0.08f
        val horizontalGridCount = 4
        for (j in 1 until horizontalGridCount) {
            val y = (height / horizontalGridCount) * j
            drawLine(
                color = color,
                alpha = gridAlpha,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
            )
        }
        val verticalGridCount = 8
        for (j in 1 until verticalGridCount) {
            val x = (width / verticalGridCount) * j
            drawLine(
                color = color,
                alpha = gridAlpha,
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
            )
        }

        // 2. Draw target reference wave if active signal is locked
        if (activeSignal != null) {
            val refPath = Path()
            refPath.moveTo(0f, centerY)
            val refAmp = (activeSignal.targetGain / 100f) * 35f
            val refFreq = activeSignal.targetFilter * 0.004f + 0.05f
            
            for (i in 0..points) {
                val x = i * step
                // Reference wave is a clean fundamental sine wave
                val wave = sin(i * refFreq + time * 10f) * refAmp
                refPath.lineTo(x, centerY + wave)
            }
            
            // Background beam glow for target wave
            drawPath(
                path = refPath,
                color = color.copy(alpha = 0.15f),
                style = Stroke(
                    width = 4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
            
            // Core beam line for target wave
            drawPath(
                path = refPath,
                color = color.copy(alpha = 0.45f),
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        // 3. Draw live user-controlled wave (with harmonics and dynamic noise)
        val livePath = Path()
        livePath.moveTo(0f, centerY)
        val liveAmp = if (activeSignal != null) (gain / 100f) * 35f else proximity * 30f
        val liveFreq = if (activeSignal != null) filter * 0.004f + 0.05f else 0.2f
        val noiseLevel = if (activeSignal != null) ((100f - stability) / 100f) * 15f else (1.0f - proximity) * 20f

        for (i in 0..points) {
            val x = i * step
            // Add secondary harmonic to simulate complex analog signals and static
            val fundamental = sin(i * liveFreq + time * 10f) * 0.8f
            val harmonic = sin(i * liveFreq * 2.2f + time * 16f) * 0.2f
            val wave = (fundamental + harmonic) * liveAmp
            
            val noise = (Math.random() - 0.5f) * noiseLevel
            livePath.lineTo(x, centerY + wave + noise.toFloat())
        }
        
        // Background beam glow for live wave
        drawPath(
            path = livePath,
            color = color.copy(alpha = 0.25f),
            style = Stroke(width = 5f)
        )
        
        // Core beam line for live wave
        drawPath(
            path = livePath,
            color = color,
            style = Stroke(width = 1.8f)
        )
    }
}

@Preview
@Composable
fun VisualizerPreview() {
    Visualizer(
        stability = 90f,
        proximity = 1.0f,
        color = Color.Green,
        activeSignal = null,
        gain = 50,
        filter = 50,
        time = 0f
    )
}
