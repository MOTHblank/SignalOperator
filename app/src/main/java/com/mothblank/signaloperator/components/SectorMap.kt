package com.mothblank.signaloperator.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
    var selectedLocationId by remember { mutableStateOf<String?>(null) }
    val selectedLocation = locations.firstOrNull { it.id == selectedLocationId }
    var previousLocationState by remember {
        mutableStateOf<Map<String, Triple<LocationStatus, Int, Int>>>(emptyMap())
    }
    var previousLinkState by remember {
        mutableStateOf<Map<String, LinkStatus>>(emptyMap())
    }
    var changedLocationIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var changedLinkIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val networkPulse = remember { Animatable(0f) }

    LaunchedEffect(locations, networkLinks) {
        val currentLocations = locations.associate {
            it.id to Triple(it.status, it.security, it.threat)
        }
        val currentLinks = networkLinks.associate { it.id to it.status }

        if (previousLocationState.isNotEmpty() || previousLinkState.isNotEmpty()) {
            changedLocationIds = currentLocations.keys.filterTo(mutableSetOf()) { id ->
                previousLocationState[id] != currentLocations[id]
            }
            changedLinkIds = currentLinks.keys.filterTo(mutableSetOf()) { id ->
                previousLinkState[id] != currentLinks[id]
            }

            if (changedLocationIds.isNotEmpty() || changedLinkIds.isNotEmpty()) {
                networkPulse.snapTo(1f)
                networkPulse.animateTo(0f, tween(560))
                changedLocationIds = emptySet()
                changedLinkIds = emptySet()
            }
        }

        previousLocationState = currentLocations
        previousLinkState = currentLinks
    }

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

            val pulse = networkPulse.value
            networkLinks.forEach { link ->
                val from = locationById[link.fromLocationId] ?: return@forEach
                val to = locationById[link.toLocationId] ?: return@forEach
                val changed = link.id in changedLinkIds
                val linkColor = when (link.status) {
                    LinkStatus.ACTIVE -> color.copy(alpha = 0.35f)
                    LinkStatus.JAMMED -> Color.Yellow.copy(alpha = 0.55f)
                    LinkStatus.CORRUPTED -> Color.Red.copy(alpha = 0.7f)
                }
                drawLine(
                    color = linkColor,
                    start = Offset(from.x * width, from.y * height),
                    end = Offset(to.x * width, to.y * height),
                    strokeWidth = (if (link.status == LinkStatus.ACTIVE) 2f else 3f) +
                        if (changed) pulse * 4f else 0f,
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
                val center = Offset(loc.x * width, loc.y * height)

                if (loc.id in changedLocationIds && pulse > 0f) {
                    drawCircle(
                        color = nodeColor.copy(alpha = pulse * 0.65f),
                        radius = (16f + (1f - pulse) * 18f).dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                if (loc.id == selectedLocationId) {
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.45f),
                        radius = 16.dp.toPx(),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                drawCircle(
                    color = nodeColor,
                    radius = 8.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                if (loc.status == LocationStatus.CORRUPTED || pressure > 0) {
                    drawCircle(
                        color = nodeColor.copy(alpha = 0.2f),
                        radius = (12 + pressure / 8).dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.88f))
                .border(1.dp, color.copy(alpha = 0.25f))
                .padding(horizontal = 7.dp, vertical = 5.dp)
        ) {
            Text(
                "NETWORK // CONTAINMENT $containmentIntegrity%",
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
            Text(
                "EXPOSURE $exposure% // ECHO TRUST $trustInEcho% // SEC CHARGES $securityCharges",
                color = color.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        // Measure label contents before placing them: font scaling and narrow viewports
        // can change their actual bounds substantially.
        val labelWidth = minOf(110.dp, widthDp)
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                locations.forEach { loc ->
                    val labelColor = nodeColor(loc.status, color)
                    val isSelected = loc.id == selectedLocationId
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(labelWidth)
                            .clickable { selectedLocationId = loc.id }
                            .background(Color.Black.copy(alpha = if (isSelected) 0.94f else 0.78f))
                            .border(
                                1.dp,
                                if (isSelected) labelColor else labelColor.copy(alpha = 0.25f)
                            )
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = loc.name,
                            color = labelColor,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "SEC ${loc.security} / THR ${loc.threat}",
                            color = labelColor.copy(alpha = 0.72f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        ) { measurables, constraints ->
            val placeables = measurables.map {
                it.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
            }
            val occupied = mutableListOf<IntRect>()
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            // Avoid covering the network HUD and the bottom action/status panel.
            occupied += IntRect(
                0, 0,
                minOf(width, 340.dp.roundToPx()),
                minOf(height, 48.dp.roundToPx())
            )
            val bottomReserved = if (selectedLocation != null) 110.dp else 24.dp
            occupied += IntRect(
                0,
                (height - bottomReserved.roundToPx()).coerceAtLeast(0),
                width,
                height
            )

            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    val location = locations[index]
                    val position = findLabelPosition(
                        anchorX = location.x * width,
                        anchorY = location.y * height,
                        labelWidth = placeable.width,
                        labelHeight = placeable.height,
                        viewportWidth = width,
                        viewportHeight = height,
                        gap = 4.dp.roundToPx(),
                        occupied = occupied
                    )
                    placeable.place(position.x, position.y)
                    occupied += IntRect(
                        position.x, position.y,
                        position.x + placeable.width,
                        position.y + placeable.height
                    )
                }
            }
        }

        characters.forEach { character ->
            val targetLoc = locations.find { it.id == character.locationId }
            if (targetLoc != null) {
                val animX by animateFloatAsState(
                    targetValue = targetLoc.x,
                    animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
                    label = "charX_${character.id}"
                )
                val animY by animateFloatAsState(
                    targetValue = targetLoc.y,
                    animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
                    label = "charY_${character.id}"
                )

                val charColor = when (character.status) {
                    CharacterStatus.ACTIVE -> color
                    CharacterStatus.MIA -> Color.Gray
                    CharacterStatus.COMPROMISED -> Color.Red
                    CharacterStatus.ANOMALY -> Color.Magenta
                }

                Box(
                    modifier = Modifier
                        .offset(
                            x = widthDp * animX - 55.dp,
                            y = heightDp * animY + 22.dp
                        )
                        .width(110.dp)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 3.dp)
                ) {
                    Text(
                        text = "[${character.callsign}:${character.status.name}]",
                        color = charColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        val selected = selectedLocation
        if (selected != null) {
            val actionLabel = when (selected.status) {
                LocationStatus.SECURE -> "REINFORCE NODE // COST 1"
                LocationStatus.INVESTIGATING -> "OPEN FIREWALL"
                LocationStatus.CORRUPTED -> "BEGIN RECOVERY // COST 2"
            }
            val canAct = when (selected.status) {
                LocationStatus.SECURE ->
                    securityCharges >= 1 && !(selected.security >= 90 && selected.threat == 0)
                LocationStatus.INVESTIGATING -> true
                LocationStatus.CORRUPTED -> securityCharges >= 2
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .border(1.dp, nodeColor(selected.status, color))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        selected.name,
                        color = nodeColor(selected.status, color),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                    Text(
                        selected.status.name,
                        color = nodeColor(selected.status, color),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "SECURITY ${selected.security}% // THREAT ${selected.threat}%",
                    color = color.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { onLocationClick(selected) },
                    enabled = canAct,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = nodeColor(selected.status, color),
                        contentColor = Color.Black,
                        disabledContainerColor = Color.DarkGray,
                        disabledContentColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        if (canAct) {
                            actionLabel
                        } else {
                            when (selected.status) {
                                LocationStatus.SECURE -> if (securityCharges < 1) {
                                    "INSUFFICIENT SECURITY CHARGES"
                                } else {
                                    "NODE ALREADY HARDENED"
                                }
                                LocationStatus.CORRUPTED -> "INSUFFICIENT SECURITY CHARGES"
                                LocationStatus.INVESTIGATING -> actionLabel
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        } else {
            Text(
                text = "SELECT A NETWORK NODE FOR STATUS AND ACTIONS",
                color = color.copy(alpha = 0.58f),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.88f))
                    .padding(5.dp)
            )
        }
    }
}


/**
 * Chooses the nearest unobstructed rectangle for a label without moving its
 * normalized network node. If a viewport is too small to fit every label,
 * minimize the overlap rather than placing labels beyond the screen.
 */
private fun findLabelPosition(
    anchorX: Float,
    anchorY: Float,
    labelWidth: Int,
    labelHeight: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    gap: Int,
    occupied: List<IntRect>
): IntOffset {
    val maxX = (viewportWidth - labelWidth).coerceAtLeast(0)
    val maxY = (viewportHeight - labelHeight).coerceAtLeast(0)
    val preferredX = (anchorX - labelWidth / 2f).roundToInt().coerceIn(0, maxX)
    val preferredY = (anchorY - labelHeight / 2f).roundToInt().coerceIn(0, maxY)

    val xPositions = linkedSetOf(preferredX, 0, maxX)
    val yPositions = linkedSetOf(preferredY, 0, maxY)
    for (x in 0..maxX step (labelWidth / 3).coerceAtLeast(1)) xPositions += x
    for (y in 0..maxY step (labelHeight / 2).coerceAtLeast(1)) yPositions += y

    var best = IntOffset(preferredX, preferredY)
    var bestOverlap = Long.MAX_VALUE
    var bestDistance = Long.MAX_VALUE

    for (y in yPositions) {
        for (x in xPositions) {
            val overlap = occupied.sumOf { rect ->
                val intersectWidth =
                    (minOf(x + labelWidth + gap, rect.right) - maxOf(x - gap, rect.left))
                        .coerceAtLeast(0)
                val intersectHeight =
                    (minOf(y + labelHeight + gap, rect.bottom) - maxOf(y - gap, rect.top))
                        .coerceAtLeast(0)
                intersectWidth.toLong() * intersectHeight
            }
            val dx = (x - preferredX).toLong()
            val dy = (y - preferredY).toLong()
            val distance = dx * dx + dy * dy
            if (overlap < bestOverlap || (overlap == bestOverlap && distance < bestDistance)) {
                best = IntOffset(x, y)
                bestOverlap = overlap
                bestDistance = distance
            }
        }
    }
    return best
}

private fun nodeColor(status: LocationStatus, base: Color): Color {
    return when (status) {
        LocationStatus.SECURE -> base
        LocationStatus.INVESTIGATING -> Color.Yellow.copy(alpha = 0.9f)
        LocationStatus.CORRUPTED -> Color.Red.copy(alpha = 0.9f)
    }
}
