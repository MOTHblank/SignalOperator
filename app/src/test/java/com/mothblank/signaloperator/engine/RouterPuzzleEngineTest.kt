package com.mothblank.signaloperator.engine

import com.mothblank.signaloperator.models.RouterGameState
import com.mothblank.signaloperator.models.RouterTile
import com.mothblank.signaloperator.models.TilePath
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouterPuzzleEngineTest {
    @Test
    fun horizontalPathConnectsEntryToExit() {
        val game = RouterGameState(
            locationId = "loc",
            grid = buildList {
                for (x in 0 until 3) {
                    for (y in 0 until 3) {
                        add(RouterTile(x, y, TilePath.STRAIGHT, 0))
                    }
                }
            },
            size = 3,
            entryY = 1,
            exitY = 1,
            timeLeftSeconds = 15
        )

        assertTrue(RouterPuzzleEngine.isConnected(game))
    }

    @Test
    fun rotatingMiddleSegmentBreaksConnectedPath() {
        val game = RouterGameState(
            locationId = "loc",
            grid = buildList {
                for (x in 0 until 3) {
                    for (y in 0 until 3) {
                        add(RouterTile(x, y, TilePath.STRAIGHT, 0))
                    }
                }
            },
            size = 3,
            entryY = 1,
            exitY = 1,
            timeLeftSeconds = 15
        )

        val rotated = RouterPuzzleEngine.rotate(game, 1, 1)

        assertFalse(RouterPuzzleEngine.isConnected(rotated))
    }

    @Test
    fun generatedPuzzleStartsUnsolvedButUsesNontrivialGuaranteedRoute() {
        val generated = RouterPuzzleEngine.create(
            locationId = "loc",
            seed = 814L
        )
        val solved = RouterPuzzleEngine.solvedTemplate(
            locationId = "loc",
            seed = 814L
        )

        assertFalse(RouterPuzzleEngine.isConnected(generated))
        assertTrue(RouterPuzzleEngine.isConnected(solved))

        val solvedRouteContainsCorner = solved.grid.any {
            it.type == TilePath.CORNER
        }
        assertTrue(solvedRouteContainsCorner)
    }

    @Test
    fun manySeedsRetainGuaranteedSolution() {
        for (seed in 0L..100L) {
            val generated = RouterPuzzleEngine.create("loc", seed)
            val solved = RouterPuzzleEngine.solvedTemplate("loc", seed)

            assertFalse("seed=$seed should start unsolved", RouterPuzzleEngine.isConnected(generated))
            assertTrue("seed=$seed should have solved topology", RouterPuzzleEngine.isConnected(solved))
        }
    }
    @Test
    fun energizedTilesFollowOnlyMutuallyConnectedPathFromEntry() {
        val game = RouterGameState(
            locationId = "loc",
            grid = listOf(
                RouterTile(0, 0, TilePath.STRAIGHT, 90),
                RouterTile(1, 0, TilePath.STRAIGHT, 0),
                RouterTile(2, 0, TilePath.STRAIGHT, 0),
                RouterTile(0, 1, TilePath.STRAIGHT, 0),
                RouterTile(1, 1, TilePath.CORNER, 0),
                RouterTile(2, 1, TilePath.STRAIGHT, 90),
                RouterTile(0, 2, TilePath.STRAIGHT, 0),
                RouterTile(1, 2, TilePath.STRAIGHT, 0),
                RouterTile(2, 2, TilePath.STRAIGHT, 0)
            ),
            size = 3,
            entryY = 1,
            exitY = 1,
            timeLeftSeconds = 15
        )

        val energized = RouterPuzzleEngine.energizedTiles(game)

        assertTrue(0 to 1 in energized)
        assertTrue(1 to 1 in energized)
        assertFalse(2 to 1 in energized)
        assertFalse(1 to 0 in energized)
    }

}
