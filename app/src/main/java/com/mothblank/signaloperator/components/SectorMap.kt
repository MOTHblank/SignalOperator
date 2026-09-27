package com.mothblank.signaloperator.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mothblank.signaloperator.models.*

@Composable
fun SectorMap(
    locations: List<Location>,
    characters: List<Character>,
    networkLinks: List<NetworkLink>,
    securityCharges: Int,
    containmentIntegrity: Int,
    exposure: Int,
    trustInEcho: Int,
    color: Color,
    onLocationClick: (Location) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(16.dp)
    ) {
        val widthDp = maxWidth
        val heightDp = maxHeight
        val locationById = locations.associateBy { it.id }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val gridStep = 50.dp.toPx()
            for (x in 0..(width / gridStep).toInt()) {
                drawLine(
                    color = color.copy(alpha = 0.08f),
                    start = Offset(x * gridStep, 0f),
                    end = Offset(x * gridStep, height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(height / gridStep).toInt()) {
                drawLine(
                    color = color.copy(alpha = 0.08f),
                    start = Offset(0f, y * gridStep),
                    end = Offset(width, y * gridStep),
                    strokeWidth = 1f
                )
            }

            networkLinks.forEach { link ->
                val from = locationById[link.fromLocationId] ?: return@forEach
                val to = locationById[link.toLocationId] ?: return@forEach
                val linkColor = when (link.status) {
                    LinkStatus.ACTIVE -> color.copy(alpha = 0.35f)
                    LinkStatus.JAMMED -> Color.Yellow.copy(alpha = 0.55f)
                    LinkStatus.CORRUPTED -> Color.Red.copy(alpha = 0.7f)
                }
                drawLine(
                    color = linkColor,
                    start = Offset(from.x * width, from.y * height),
                    end = Offset(to.x * width, to.y * height),
                    strokeWidth = if (link.status == LinkStatus.ACTIVE) 2f else 3f,
                    pathEffect = if (link.status == LinkStatus.ACTIVE) {
                        null
                    } else {
                        PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    }
                )
            }

            locations.forEach { loc ->
                val nodeColor = nodeColor(loc.status, color)
                val pressure = (loc.threat - loc.security).coerceAtLeast(0)

                drawCircle(
                    color = nodeColor,
                    radius = 8.dp.toPx(),
                    center = Offset(loc.x * width, loc.y * height),
                    style = Stroke(width = 2.dp.toPx())
                )

                if (loc.status == LocationStatus.CORRUPTED || pressure > 0) {
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.2f),
                        radius = (12 + pressure / 8).dp.toPx(),
                        center = Offset(loc.x * width, loc.y * height),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(6.dp)
        ) {
            Text(
                "NETWORK // CONTAINMENT $containmentIntegrity%",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                "EXPOSURE $exposure% // ECHO TRUST $trustInEcho% // SEC CHARGES $securityCharges",
                color = color.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        locations.forEach { loc ->
            val nodeColor = nodeColor(loc.status, color)
            Box(
                modifier = Modifier
                    .offset(x = widthDp * loc.x, y = heightDp * loc.y)
                    .clickable { onLocationClick(loc) }
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(3.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = loc.name,
                        color = nodeColor,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "SEC ${loc.security} / THR ${loc.threat}",
                        color = nodeColor.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    )
                }
            }
        }

        characters.forEach { char ->
            val targetLoc = locations.find { it.id == char.locationId }
            if (targetLoc != null) {
                val animX by animateFloatAsState(
                    targetValue = targetLoc.x,
                    animationSpec = tween(durationMillis = 1600, easing = LinearEasing),
                    label = "charX_${char.id}"
                )
                val animY by animateFloatAsState(
                    targetValue = targetLoc.y,
                    animationSpec = tween(durationMillis = 1600, easing = LinearEasing),
                    label = "charY_${char.id}"
                )

                val charColor = when (char.status) {
                    CharacterStatus.ACTIVE -> color
                    CharacterStatus.MIA -> Color.Gray
                    CharacterStatus.COMPROMISED -> Color.Red
                    CharacterStatus.ANOMALY -> Color.Magenta
                }

                Box(
                    modifier = Modifier
                        .offset(x = widthDp * animX, y = (heightDp * animY) + 30.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 3.dp)
                ) {
                    Text(
                        text = "[${char.callsign}:${char.status.name}]",
                        color = charColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    )
                }
            }
        }

        Text(
            text = "TAP INVESTIGATING: FIREWALL // SECURE: REINFORCE (1) // CORRUPTED: RECOVER (2)",
            color = color.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(4.dp)
        )
    }
}

private fun nodeColor(status: LocationStatus, base: Color): Color {
    return when (status) {
        LocationStatus.SECURE -> base
        LocationStatus.INVESTIGATING -> Color.Yellow.copy(alpha = 0.9f)
        LocationStatus.CORRUPTED -> Color.Red.copy(alpha = 0.9f)
    }
}
