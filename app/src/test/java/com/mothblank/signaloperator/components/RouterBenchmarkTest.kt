package com.mothblank.signaloperator.components

import com.mothblank.signaloperator.models.RouterGameState
import com.mothblank.signaloperator.models.RouterTile
import com.mothblank.signaloperator.models.TilePath
import org.junit.Test
import kotlin.system.measureTimeMillis

class RouterBenchmarkTest {

    @Test
    fun benchmarkGridLookup() {
        val size = 50
        val grid = mutableListOf<RouterTile>()
        for (y in 0 until size) {
            for (x in 0 until size) {
                grid.add(RouterTile(x, y, TilePath.STRAIGHT, 0))
            }
        }
        val game = RouterGameState("loc", grid, size, 0, 0, 15)

        // Warmup
        for(i in 0 until 10) {
            runO_N_Lookup(game)
            runO_1_Lookup(game)
        }

        val timeON = measureTimeMillis {
            for(i in 0 until 100) {
                runO_N_Lookup(game)
            }
        }

        val timeO1 = measureTimeMillis {
            for(i in 0 until 100) {
                runO_1_Lookup(game)
            }
        }

        println("Baseline O(N) lookup time: ${timeON}ms")
        println("Optimized O(1) lookup time: ${timeO1}ms")
    }

    private fun runO_N_Lookup(game: RouterGameState) {
        var count = 0
        for (y in 0 until game.size) {
            for (x in 0 until game.size) {
                val tile = game.grid.find { it.x == x && it.y == y }
                if (tile != null) count++
            }
        }
    }

    private fun runO_1_Lookup(game: RouterGameState) {
        var count = 0
        val tileMap = game.grid.associateBy { Pair(it.x, it.y) }
        for (y in 0 until game.size) {
            for (x in 0 until game.size) {
                val tile = tileMap[Pair(x, y)]
                if (tile != null) count++
            }
        }
    }
}
