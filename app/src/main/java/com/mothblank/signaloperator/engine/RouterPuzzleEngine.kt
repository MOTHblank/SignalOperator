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
        require(size >= 3)

        val generated = buildGrid(seed, size, scramble = true)
        return RouterGameState(
            locationId = locationId,
            grid = generated,
            size = size,
            entryY = size / 2,
            exitY = size / 2,
            timeLeftSeconds = timeLimitSeconds
        )
    }

    internal fun solvedTemplate(
        locationId: String,
        seed: Long,
        size: Int = 3
    ): RouterGameState {
        return RouterGameState(
            locationId = locationId,
            grid = buildGrid(seed, size, scramble = false),
            size = size,
            entryY = size / 2,
            exitY = size / 2,
            timeLeftSeconds = 15
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
            if (x == size && y == game.exitY && fromDir == WEST) {
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
                    EAST -> 1
                    WEST -> -1
                    else -> 0
                }
                val nextY = y + when (port) {
                    SOUTH -> 1
                    NORTH -> -1
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

        return dfs(0, game.entryY, WEST)
    }

    private fun buildGrid(seed: Long, size: Int, scramble: Boolean): List<RouterTile> {
        val random = Random(seed)
        val center = size / 2
        val detourY = chooseDetourRow(center, size, random)
        val route = buildRoute(size, center, detourY)
        val routeSet = route.toSet()
        val solvedRouteTiles = route.mapIndexed { index, point ->
            val requiredPorts = mutableSetOf<Int>()

            if (index == 0) {
                requiredPorts.add(WEST)
            } else {
                requiredPorts.add(direction(point, route[index - 1]))
            }

            if (index == route.lastIndex) {
                requiredPorts.add(EAST)
            } else {
                requiredPorts.add(direction(point, route[index + 1]))
            }

            point to tileForPorts(point.first, point.second, requiredPorts)
        }.toMap()

        return buildList {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    val point = x to y
                    val solved = solvedRouteTiles[point]
                    if (solved != null) {
                        val scrambleQuarterTurns = when {
                            !scramble -> 0
                            point == route.first() -> 1
                            else -> random.nextInt(4)
                        }
                        add(
                            solved.copy(
                                rotationDegrees =
                                    (solved.rotationDegrees + scrambleQuarterTurns * 90) % 360
                            )
                        )
                    } else {
                        add(
                            RouterTile(
                                x = x,
                                y = y,
                                type = TilePath.entries.random(random),
                                rotationDegrees = listOf(0, 90, 180, 270).random(random)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun buildRoute(
        size: Int,
        center: Int,
        detourY: Int
    ): List<Pair<Int, Int>> {
        val route = mutableListOf<Pair<Int, Int>>()
        route.add(0 to center)
        route.add(1 to center)
        route.add(1 to detourY)

        for (x in 2 until size) {
            route.add(x to detourY)
        }

        route.add((size - 1) to center)
        return route.distinct()
    }

    private fun chooseDetourRow(center: Int, size: Int, random: Random): Int {
        val candidates = listOf(center - 1, center + 1).filter { it in 0 until size }
        return candidates.random(random)
    }

    private fun direction(
        from: Pair<Int, Int>,
        to: Pair<Int, Int>
    ): Int {
        val dx = to.first - from.first
        val dy = to.second - from.second
        return when {
            dx == 1 && dy == 0 -> EAST
            dx == -1 && dy == 0 -> WEST
            dx == 0 && dy == 1 -> SOUTH
            dx == 0 && dy == -1 -> NORTH
            else -> error("Router route contains non-adjacent cells: $from -> $to")
        }
    }

    private fun tileForPorts(
        x: Int,
        y: Int,
        requiredPorts: Set<Int>
    ): RouterTile {
        require(requiredPorts.size == 2)

        if (requiredPorts == setOf(EAST, WEST)) {
            return RouterTile(x, y, TilePath.STRAIGHT, 0)
        }
        if (requiredPorts == setOf(NORTH, SOUTH)) {
            return RouterTile(x, y, TilePath.STRAIGHT, 90)
        }

        for (rotation in 0..3) {
            val rotated = setOf(
                (EAST + rotation) % 4,
                (SOUTH + rotation) % 4
            )
            if (rotated == requiredPorts) {
                return RouterTile(x, y, TilePath.CORNER, rotation * 90)
            }
        }

        error("Unsupported router port pair: $requiredPorts")
    }

    private fun ports(tile: RouterTile): Set<Int> {
        val rotation = (tile.rotationDegrees / 90) % 4
        return when (tile.type) {
            TilePath.STRAIGHT -> {
                if (rotation % 2 == 0) setOf(EAST, WEST) else setOf(NORTH, SOUTH)
            }

            TilePath.CORNER -> {
                setOf(EAST, SOUTH).mapTo(mutableSetOf()) { (it + rotation) % 4 }
            }

            TilePath.CROSS -> setOf(NORTH, EAST, SOUTH, WEST)
        }
    }

    private const val NORTH = 0
    private const val EAST = 1
    private const val SOUTH = 2
    private const val WEST = 3
}
