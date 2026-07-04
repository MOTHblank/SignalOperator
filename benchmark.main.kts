import kotlin.system.measureTimeMillis

data class TilePath(val id: Int)
data class RouterTile(
    val x: Int,
    val y: Int,
    val type: TilePath,
    val rotationDegrees: Int
)

data class RouterGameState(
    val locationId: String,
    val grid: List<RouterTile>,
    val size: Int = 3,
    val entryY: Int = 1,
    val exitY: Int = 1,
    val timeLeftSeconds: Int = 15
)

val size = 20
val grid = mutableListOf<RouterTile>()
for (y in 0 until size) {
    for (x in 0 until size) {
        grid.add(RouterTile(x, y, TilePath(1), 0))
    }
}
val game = RouterGameState("loc", grid, size, 0, 0, 15)

// Warmup
for(i in 0 until 100) {
    runO_N_Lookup(game)
    runO_1_Lookup(game)
}

val iterations = 5000

val timeON = measureTimeMillis {
    for(i in 0 until iterations) {
        runO_N_Lookup(game)
    }
}

val timeO1 = measureTimeMillis {
    for(i in 0 until iterations) {
        runO_1_Lookup(game)
    }
}

println("Baseline O(N) lookup time for ${iterations} iterations: ${timeON}ms")
println("Optimized O(1) lookup time for ${iterations} iterations: ${timeO1}ms")

fun runO_N_Lookup(game: RouterGameState) {
    var count = 0
    for (y in 0 until game.size) {
        for (x in 0 until game.size) {
            val tile = game.grid.find { it.x == x && it.y == y }
            if (tile != null) count++
        }
    }
}

fun runO_1_Lookup(game: RouterGameState) {
    var count = 0
    val tileMap = game.grid.associateBy { Pair(it.x, it.y) }
    for (y in 0 until game.size) {
        for (x in 0 until game.size) {
            val tile = tileMap[Pair(x, y)]
            if (tile != null) count++
        }
    }
}
