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

        locations.forEach { loc ->
            val nodeColor = nodeColor(loc.status, color)
            val selected = loc.id == selectedLocationId
            Box(
                modifier = Modifier
                    .offset(
                        x = widthDp * loc.x - 55.dp,
                        y = heightDp * loc.y - 22.dp
                    )
                    .width(110.dp)
                    .clickable { selectedLocationId = loc.id }
                    .background(Color.Black.copy(alpha = if (selected) 0.94f else 0.78f))
                    .border(
                        1.dp,
                        if (selected) nodeColor else nodeColor.copy(alpha = 0.25f)
                    )
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = loc.name,
                        color = nodeColor,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "SEC ${loc.security} / THR ${loc.threat}",
                        color = nodeColor.copy(alpha = 0.72f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
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

private fun nodeColor(status: LocationStatus, base: Color): Color {
    return when (status) {
        LocationStatus.SECURE -> base
        LocationStatus.INVESTIGATING -> Color.Yellow.copy(alpha = 0.9f)
        LocationStatus.CORRUPTED -> Color.Red.copy(alpha = 0.9f)
    }
}
