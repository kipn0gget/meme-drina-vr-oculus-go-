package com.example.game

import com.example.data.GameMap
import kotlin.math.abs
import kotlin.random.Random

const val CELL_SIZE = 2.6f
const val WALL_HEIGHT = 3.1f

enum class CellType {
    EMPTY,
    WALL,
    PROP,
    DOOR_UNLOCKED,
    DOOR_LOCKED,
    EXIT_PORTAL
}

enum class CollectibleKind {
    BOOK,
    KEY
}

data class DoorEntity(
    val id: Int,
    val gx: Int,
    val gz: Int,
    val isHorizontal: Boolean,
    var isLocked: Boolean,
    var isOpen: Boolean = false,
    var openProgress: Float = 0f
)

data class CollectibleEntity(
    val id: Int,
    val kind: CollectibleKind,
    var x: Float,
    var z: Float,
    var collected: Boolean = false,
    val phaseOffset: Float = 0f
)

data class PropEntity(
    val gx: Int,
    val gz: Int,
    val x: Float,
    val z: Float,
    val style: Int
)

class LevelData(
    val map: GameMap,
    val width: Int,
    val height: Int,
    val grid: Array<Array<CellType>>,
    val doors: MutableList<DoorEntity>,
    val collectibles: MutableList<CollectibleEntity>,
    val props: List<PropEntity>,
    val spawnX: Float,
    val spawnZ: Float,
    val exitX: Float,
    val exitZ: Float,
    val outdoors: Boolean
) {
    fun cellAt(gx: Int, gz: Int): CellType {
        if (gx < 0 || gz < 0 || gx >= width || gz >= height) return CellType.WALL
        return grid[gz][gx]
    }

    fun doorAt(gx: Int, gz: Int): DoorEntity? =
        doors.firstOrNull { it.gx == gx && it.gz == gz }

    fun isBlockedForMovement(gx: Int, gz: Int): Boolean {
        return when (cellAt(gx, gz)) {
            CellType.WALL, CellType.PROP -> true
            CellType.DOOR_UNLOCKED, CellType.DOOR_LOCKED -> {
                val door = doorAt(gx, gz)
                door != null && door.openProgress < 0.65f
            }
            else -> false
        }
    }

    fun isBlockedForLineOfSight(gx: Int, gz: Int): Boolean {
        return when (cellAt(gx, gz)) {
            CellType.WALL -> true
            CellType.DOOR_UNLOCKED, CellType.DOOR_LOCKED -> {
                val door = doorAt(gx, gz)
                door != null && door.openProgress < 0.45f
            }
            else -> false
        }
    }
}

object MapBuilder {

    // 18x18 grid layouts for all 3 maps from Slendrina Free:
    // '#' = wall, '.' = floor, 'P' = prop/pillar/tombstone/furniture,
    // 'D' = unlocked door, 'L' = locked door, 'S' = spawn & exit,
    // 'B' = candidate book spot, 'K' = candidate key spot
    private val HOUSE_LAYOUT = listOf(
        "##################",
        "#B..#...B#..P...B#",
        "#...D....D.......#",
        "#P..#....#..#L####",
        "##D##.##.#..#B...#",
        "#...K....#..#..P.#",
        "#..P..#..D..#....#",
        "#.....#..#..###D##",
        "###D###..#.......#",
        "#B....#..#####D###",
        "#..P..L......#...#",
        "#.....#..P...D.K.#",
        "###.###......#...#",
        "#...D...K....#B..#",
        "#B..#..P..#..##L##",
        "#...#.....#..#..B#",
        "#......S.....#...#",
        "##################"
    )

    private val YARD_LAYOUT = listOf(
        "##################",
        "#B.P.....#B..P...#",
        "#.....P..D.......#",
        "#..####..#...P..B#",
        "#..#K.#..####D####",
        "#..D..#..........#",
        "#..#..#..P....P..#",
        "#.....#..........#",
        "###L###..##D##.###",
        "#B....#..#...#..B#",
        "#..P..#..#K..L...#",
        "#.....D..#...#..P#",
        "###.###..###.#...#",
        "#.....P......D...#",
        "#B..P....P...#..B#",
        "#..K.........#...#",
        "#......S.....#...#",
        "##################"
    )

    private val HOTEL_LAYOUT = listOf(
        "##################",
        "#B..#...B#...#..B#",
        "#...D....D...L...#",
        "#P..#....#...#..P#",
        "##D###L###D###D###",
        "#........K.......#",
        "#..#####..#####..#",
        "#..#B..#..#..B#..#",
        "#..#...D..D...#..#",
        "#..#..P#..#P..#..#",
        "#..#####..#####..#",
        "#...K........K...#",
        "##D###D###L###D###",
        "#...#....#...#...#",
        "#B..D....D...D..B#",
        "#...#..P.#...#...#",
        "#......S.....#...#",
        "##################"
    )

    fun build(map: GameMap, seed: Long = System.currentTimeMillis()): LevelData {
        val raw = when (map) {
            GameMap.HOUSE -> HOUSE_LAYOUT
            GameMap.YARD -> YARD_LAYOUT
            GameMap.HOTEL -> HOTEL_LAYOUT
        }
        val height = raw.size
        val width = raw[0].length
        val grid = Array(height) { Array(width) { CellType.EMPTY } }
        val doors = mutableListOf<DoorEntity>()
        val props = mutableListOf<PropEntity>()
        val bookCandidates = mutableListOf<Pair<Int, Int>>()
        val keyCandidates = mutableListOf<Pair<Int, Int>>()
        var spawnGx = width / 2
        var spawnGz = height - 2
        var doorId = 1

        for (z in 0 until height) {
            val row = raw[z]
            for (x in 0 until width) {
                when (row[x]) {
                    '#' -> grid[z][x] = CellType.WALL
                    'P' -> {
                        grid[z][x] = CellType.PROP
                        props.add(
                            PropEntity(
                                gx = x,
                                gz = z,
                                x = (x + 0.5f) * CELL_SIZE,
                                z = (z + 0.5f) * CELL_SIZE,
                                style = (x * 7 + z * 13) % 3
                            )
                        )
                    }
                    'D', 'L' -> {
                        val isLocked = row[x] == 'L'
                        grid[z][x] = if (isLocked) CellType.DOOR_LOCKED else CellType.DOOR_UNLOCKED
                        val leftWall = x > 0 && raw[z][x - 1] == '#'
                        val rightWall = x + 1 < width && raw[z][x + 1] == '#'
                        val isHorizontal = leftWall || rightWall
                        doors.add(
                            DoorEntity(
                                id = doorId++,
                                gx = x,
                                gz = z,
                                isHorizontal = isHorizontal,
                                isLocked = isLocked
                            )
                        )
                    }
                    'S' -> {
                        grid[z][x] = CellType.EXIT_PORTAL
                        spawnGx = x
                        spawnGz = z
                    }
                    'B' -> bookCandidates.add(x to z)
                    'K' -> keyCandidates.add(x to z)
                }
            }
        }

        // Guarantee solvability: find all cells reachable from spawn WITHOUT crossing locked doors.
        val unlockedReachable = bfsReachable(grid, width, height, spawnGx, spawnGz, passLocked = false)
        val allReachable = bfsReachable(grid, width, height, spawnGx, spawnGz, passLocked = true)

        val rng = Random(seed)
        val validBookSpots = bookCandidates.filter { it in allReachable }.toMutableList()
        // Fallback: add extra open floor tiles far from spawn if needed
        if (validBookSpots.size < map.targetBooks) {
            allReachable.filter { (x, z) ->
                grid[z][x] == CellType.EMPTY &&
                    (abs(x - spawnGx) + abs(z - spawnGz)) > 4 &&
                    (x to z) !in validBookSpots
            }.shuffled(rng).forEach {
                if (validBookSpots.size < map.targetBooks) validBookSpots.add(it)
            }
        }
        validBookSpots.shuffle(rng)
        val chosenBooks = validBookSpots.take(map.targetBooks)

        // Ensure at least as many keys as locked doors, with keys starting in unlocked areas!
        val lockedCount = doors.count { it.isLocked }.coerceAtLeast(1)
        val chosenKeys = mutableListOf<Pair<Int, Int>>()
        val unlockedKeySpots = keyCandidates.filter { it in unlockedReachable }.shuffled(rng)
        chosenKeys.addAll(unlockedKeySpots)

        if (chosenKeys.size < lockedCount) {
            val fallbackUnlocked = unlockedReachable.filter { (x, z) ->
                grid[z][x] == CellType.EMPTY &&
                    (x to z) != (spawnGx to spawnGz) &&
                    (x to z) !in chosenBooks &&
                    (x to z) !in chosenKeys &&
                    (abs(x - spawnGx) + abs(z - spawnGz)) >= 3
            }.shuffled(rng)
            for (spot in fallbackUnlocked) {
                if (chosenKeys.size >= lockedCount) break
                chosenKeys.add(spot)
            }
        }

        val collectibles = mutableListOf<CollectibleEntity>()
        var itemId = 1
        chosenBooks.forEach { (gx, gz) ->
            collectibles.add(
                CollectibleEntity(
                    id = itemId++,
                    kind = CollectibleKind.BOOK,
                    x = (gx + 0.5f) * CELL_SIZE,
                    z = (gz + 0.5f) * CELL_SIZE,
                    phaseOffset = rng.nextFloat() * 6.28f
                )
            )
        }
        chosenKeys.take(lockedCount).forEach { (gx, gz) ->
            collectibles.add(
                CollectibleEntity(
                    id = itemId++,
                    kind = CollectibleKind.KEY,
                    x = (gx + 0.5f) * CELL_SIZE,
                    z = (gz + 0.5f) * CELL_SIZE,
                    phaseOffset = rng.nextFloat() * 6.28f
                )
            )
        }

        val spawnWorldX = (spawnGx + 0.5f) * CELL_SIZE
        val spawnWorldZ = (spawnGz + 0.5f) * CELL_SIZE

        return LevelData(
            map = map,
            width = width,
            height = height,
            grid = grid,
            doors = doors,
            collectibles = collectibles,
            props = props,
            spawnX = spawnWorldX,
            spawnZ = spawnWorldZ,
            exitX = spawnWorldX,
            exitZ = spawnWorldZ,
            outdoors = map == GameMap.YARD
        )
    }

    private fun bfsReachable(
        grid: Array<Array<CellType>>,
        width: Int,
        height: Int,
        startX: Int,
        startZ: Int,
        passLocked: Boolean
    ): Set<Pair<Int, Int>> {
        val visited = HashSet<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        val start = startX to startZ
        visited.add(start)
        queue.add(start)

        val dirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        while (queue.isNotEmpty()) {
            val (cx, cz) = queue.removeFirst()
            for ((dx, dz) in dirs) {
                val nx = cx + dx
                val nz = cz + dz
                if (nx !in 0 until width || nz !in 0 until height) continue
                val p = nx to nz
                if (p in visited) continue
                val cell = grid[nz][nx]
                val walkable = when (cell) {
                    CellType.EMPTY, CellType.DOOR_UNLOCKED, CellType.EXIT_PORTAL -> true
                    CellType.DOOR_LOCKED -> passLocked
                    else -> false
                }
                if (walkable) {
                    visited.add(p)
                    queue.add(p)
                }
            }
        }
        return visited
    }
}
