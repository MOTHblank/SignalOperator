package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.RouterGameState
import com.mothblank.signaloperator.models.RouterTile
import com.mothblank.signaloperator.models.TilePath
import kotlin.random.Random

object RouterPuzzleEngine {
    fun create(
        locationId: String,
        seed: Long,
        size: Int = 3,
        timeLimitSeconds: Int = 15
    ): RouterGameState {
        val random = Random(seed)
        val tilePaths = TilePath.entries
        val rotations = listOf(0, 90, 180, 270)
        val center = size / 2
        val tiles = buildList {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val isGuaranteedRoute = y == center
                    add(
                        RouterTile(
                            x = x,
                            y = y,
                            type = if (isGuaranteedRoute) {
                                TilePath.STRAIGHT
                            } else {
                                tilePaths.random(random)
                            },
                            rotationDegrees = if (isGuaranteedRoute && x == 0) {
                                // Force the generated board to start unsolved. Rotating
                                // every route tile to 0 degrees always restores a path.
                                90
                            } else {
                                rotations.random(random)
                            }
                        )
                    )
                }
            }
        }

        return RouterGameState(
            locationId = locationId,
            grid = tiles,
            size = size,
            entryY = center,
            exitY = center,
            timeLeftSeconds = timeLimitSeconds
        )
    }

    fun rotate(game: RouterGameState, x: Int, y: Int): RouterGameState {
        return game.copy(
            grid = game.grid.map { tile ->
                if (tile.x == x && tile.y == y) {
                    tile.copy(rotationDegrees = (tile.rotationDegrees + 90) % 360)
                } else {
                    tile
                }
            }
        )
    }

    fun isConnected(game: RouterGameState): Boolean {
        val size = game.size
        val gridMap = game.grid.associateBy { it.x to it.y }
        val visited = mutableSetOf<Pair<Int, Int>>()

        fun dfs(x: Int, y: Int, fromDir: Int): Boolean {
            if (x == size && y == game.exitY && fromDir == 3) {
                return true
            }
            if (x !in 0 until size || y !in 0 until size) {
                return false
            }

            val point = x to y
            if (!visited.add(point)) {
                return false
            }

            val tile = gridMap[point]
            if (tile == null || fromDir !in ports(tile)) {
                visited.remove(point)
                return false
            }

            for (port in ports(tile)) {
                if (port == fromDir) continue

                val nextX = x + when (port) {
                    1 -> 1
                    3 -> -1
                    else -> 0
                }
                val nextY = y + when (port) {
                    2 -> 1
                    0 -> -1
                    else -> 0
                }
                val nextFromDir = (port + 2) % 4

                if (dfs(nextX, nextY, nextFromDir)) {
                    return true
                }
            }

            visited.remove(point)
            return false
        }

        return dfs(0, game.entryY, 3)
    }

    private fun ports(tile: RouterTile): Set<Int> {
        val rotation = (tile.rotationDegrees / 90) % 4
        return when (tile.type) {
            TilePath.STRAIGHT -> {
                if (rotation % 2 == 0) setOf(1, 3) else setOf(0, 2)
            }

            TilePath.CORNER -> {
                setOf(1, 2).mapTo(mutableSetOf()) { (it + rotation) % 4 }
            }

            TilePath.CROSS -> setOf(0, 1, 2, 3)
        }
    }
}
