package com.mothblank.signaloperator.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mothblank.signaloperator.models.RouterGameState
import com.mothblank.signaloperator.models.RouterTile
import com.mothblank.signaloperator.models.TilePath

@Composable
fun RouterModal(
    game: RouterGameState,
    color: Color,
    onRotateTile: (Int, Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SYSTEM ALERT: FIREWALL BREACH",
                color = Color.Red,
                style = MaterialTheme.typography.titleLarge
            )
            
            Text(
                text = "SECURE RELAY FOR NODE [ ${game.locationId.uppercase()} ]",
                color = color,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.width(280.dp)
            ) {
                Text(
                    text = "TIME REMAINING:",
                    color = color.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "${game.timeLeftSeconds}s",
                    color = if (game.timeLeftSeconds <= 5) Color.Red else color,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Grid rendering
            val tileMap = game.grid.associateBy { it.x to it.y }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .border(2.dp, color.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                for (y in 0 until game.size) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (x in 0 until game.size) {
                            val tile = tileMap[x to y]
                            if (tile != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .border(1.dp, color.copy(alpha = 0.3f))
                                ) {
                                    // Connection Indicators
                                    if (x == 0 && y == game.entryY) {
                                        // Left Entry Indicator
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .size(8.dp)
                                                .background(Color.Green)
                                        )
                                    }
                                    if (x == game.size - 1 && y == game.exitY) {
                                        // Right Exit Indicator
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .size(8.dp)
                                                .background(Color.Green)
                                        )
                                    }

                                    val animatedRotation by animateFloatAsState(
                                        targetValue = tile.rotationDegrees.toFloat(),
                                        label = "tileRot_${x}_${y}"
                                    )

                                    RouterTileView(
                                        tile = tile,
                                        color = color,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rotate(animatedRotation)
                                            .clickable { onRotateTile(x, y) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("ABORT ATTEMPT", color = Color.White)
            }
        }
    }
}

@Composable
fun RouterTileView(
    tile: RouterTile,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 6.dp.toPx()
        
        // Draw the core socket
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = 12.dp.toPx(),
            center = Offset(w / 2, h / 2)
        )

        when (tile.type) {
            TilePath.STRAIGHT -> {
                // Draws path from left edge to right edge (at 0 degrees)
                drawLine(
                    color = color,
                    start = Offset(0f, h / 2),
                    end = Offset(w, h / 2),
                    strokeWidth = stroke
                )
            }
            TilePath.CORNER -> {
                // Draws path from right edge to bottom edge (at 0 degrees)
                drawLine(
                    color = color,
                    start = Offset(w / 2, h / 2),
                    end = Offset(w, h / 2),
                    strokeWidth = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(w / 2, h / 2),
                    end = Offset(w / 2, h),
                    strokeWidth = stroke
                )
            }
            TilePath.CROSS -> {
                // Draws left-right and top-bottom crossing paths
                drawLine(
                    color = color,
                    start = Offset(0f, h / 2),
                    end = Offset(w, h / 2),
                    strokeWidth = stroke
                )
                drawLine(
                    color = color,
                    start = Offset(w / 2, 0f),
                    end = Offset(w / 2, h),
                    strokeWidth = stroke
                )
            }
        }
    }
}
