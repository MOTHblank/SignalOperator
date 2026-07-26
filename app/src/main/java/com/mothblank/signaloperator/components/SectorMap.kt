package com.mothblank.signaloperator.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mothblank.signaloperator.models.Character
import com.mothblank.signaloperator.models.Location
import com.mothblank.signaloperator.models.LocationStatus

@Composable
fun SectorMap(
    locations: List<Location>,
    characters: List<Character>,
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Draw grid lines
            val gridStep = 50.dp.toPx()
            for (x in 0..(width / gridStep).toInt()) {
                drawLine(
                    color = color.copy(alpha = 0.1f),
                    start = Offset(x * gridStep, 0f),
                    end = Offset(x * gridStep, height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..(height / gridStep).toInt()) {
                drawLine(
                    color = color.copy(alpha = 0.1f),
                    start = Offset(0f, y * gridStep),
                    end = Offset(width, y * gridStep),
                    strokeWidth = 1f
                )
            }

            // Draw connections between neighboring locations (mock simple network)
            locations.forEachIndexed { i, loc ->
                if (i < locations.size - 1) {
                    val nextLoc = locations[i + 1]
                    drawLine(
                        color = color.copy(alpha = 0.3f),
                        start = Offset(loc.x * width, loc.y * height),
                        end = Offset(nextLoc.x * width, nextLoc.y * height),
                        strokeWidth = 2f
                    )
                }
            }

            // Draw locations
            locations.forEach { loc ->
                val nodeColor = when (loc.status) {
                    LocationStatus.SECURE -> color
                    LocationStatus.INVESTIGATING -> Color.Yellow.copy(alpha = 0.8f)
                    LocationStatus.CORRUPTED -> Color.Red.copy(alpha = 0.8f)
                }

                drawCircle(
                    color = nodeColor,
                    radius = 8.dp.toPx(),
                    center = Offset(loc.x * width, loc.y * height),
                    style = Stroke(width = 2.dp.toPx())
                )

                if (loc.status == LocationStatus.CORRUPTED) {
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.3f),
                        radius = 12.dp.toPx(),
                        center = Offset(loc.x * width, loc.y * height)
                    )
                }
            }
        }

        // Overlay location labels
        locations.forEach { loc ->
            val nodeColor = when (loc.status) {
                LocationStatus.SECURE -> color
                LocationStatus.INVESTIGATING -> Color.Yellow.copy(alpha = 0.8f)
                LocationStatus.CORRUPTED -> Color.Red.copy(alpha = 0.8f)
            }

            SectorMapNode(
                loc = loc,
                nodeColor = nodeColor,
                onLocationClick = { onLocationClick(loc) },
                modifier = Modifier.offset(x = widthDp * loc.x, y = heightDp * loc.y)
            )
        }

        // Overlay Animated Character Pins
        characters.forEach { char ->
            val targetLoc = locations.find { it.id == char.locationId }
            if (targetLoc != null) {
                val targetX = targetLoc.x
                val targetY = targetLoc.y

                val animX by animateFloatAsState(
                    targetValue = targetX,
                    animationSpec = tween(durationMillis = 2500, easing = LinearEasing),
                    label = "charX_${char.id}"
                )
                val animY by animateFloatAsState(
                    targetValue = targetY,
                    animationSpec = tween(durationMillis = 2500, easing = LinearEasing),
                    label = "charY_${char.id}"
                )

                Box(
                    modifier = Modifier
                        .offset(x = widthDp * animX, y = (heightDp * animY) + 16.dp)
                ) {
                    Text(
                        text = "[${char.callsign}]",
                        color = color,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun SectorMapNode(
    loc: Location,
    nodeColor: Color,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val isActive = isHovered || isFocused || isPressed

    val statusDescription = when (loc.status) {
        LocationStatus.SECURE -> "Secure"
        LocationStatus.INVESTIGATING -> "Under Investigation"
        LocationStatus.CORRUPTED -> "Corrupted"
    }

    Box(
        modifier = modifier
            .background(if (isActive) nodeColor.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onLocationClick,
                role = Role.Button
            )
            .padding(4.dp)
            .clearAndSetSemantics {
                contentDescription = "Location: ${loc.name}, Status: $statusDescription"
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isActive) "> ${loc.name} <" else loc.name,
                color = if (isActive) Color.White else nodeColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
