package com.mothblank.signaloperator.ui.theme

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

const val CRT_SHADER_SRC = """
    uniform float2 resolution;
    uniform float time;
    uniform float corruption;
    uniform shader composable;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        
        // Curvature
        float2 crtUV = uv * 2.0 - 1.0;
        float2 offset = crtUV.yx / 5.0;
        crtUV = crtUV + crtUV * offset * offset;
        crtUV = crtUV * 0.5 + 0.5;

        // Bounds check
        if (crtUV.x < 0.0 || crtUV.x > 1.0 || crtUV.y < 0.0 || crtUV.y > 1.0) {
            return half4(0.0, 0.0, 0.0, 1.0);
        }

        // Glitch / Chromatic Aberration based on corruption
        float glitchOffset = corruption * 0.02 * sin(time * 10.0 + crtUV.y * 20.0);
        
        half4 r = composable.eval(float2(crtUV.x + glitchOffset, crtUV.y) * resolution);
        half4 g = composable.eval(crtUV * resolution);
        half4 b = composable.eval(float2(crtUV.x - glitchOffset, crtUV.y) * resolution);
        
        half4 color = half4(r.r, g.g, b.b, g.a);

        // Scanlines
        float scanline = sin(crtUV.y * 800.0) * 0.04;
        color.rgb -= scanline;

        return color;
    }
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.crtEffect(corruption: Float): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "crt-time")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "crt-time-value"
    )
    val shader = remember { RuntimeShader(CRT_SHADER_SRC) }
    val effect = remember(shader) {
        RenderEffect.createRuntimeShaderEffect(shader, "composable").asComposeRenderEffect()
    }

    this.graphicsLayer {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("corruption", corruption)
        renderEffect = effect
    }
}
