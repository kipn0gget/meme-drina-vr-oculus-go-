package com.example.game

import com.example.data.Difficulty
import com.example.data.GameMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

enum class GamePhase {
    MENU,
    PLAYING,
    PAUSED,
    JUMPSCARE,
    GAME_OVER,
    VICTORY
}

interface GameAudioEvents {
    fun onFootstep()
    fun onBookCollected(collected: Int, total: Int)
    fun onKeyCollected()
    fun onDoorOpened()
    fun onDoorLocked()
    fun onSlendrinaAppear(closeRange: Boolean)
    fun onJumpscare()
    fun onVictory()
}

data class HudSnapshot(
    val phase: GamePhase = GamePhase.MENU,
    val map: GameMap = GameMap.HOUSE,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val booksCollected: Int = 0,
    val totalBooks: Int = 6,
    val keysHeld: Int = 0,
    val sanity: Float = 100f,
    val flashlightOn: Boolean = true,
    val elapsedSeconds: Int = 0,
    val promptText: String? = null,
    val toastText: String? = null,
    val stareWarning: Float = 0f,
    val exitUnlocked: Boolean = false,
    val playerGridX: Int = 0,
    val playerGridZ: Int = 0,
    val playerYawDeg: Float = 0f
)

class GameEngine(
    private val audioEvents: GameAudioEvents? = null
) {
    private val lock = Any()
    private val rng = Random(System.currentTimeMillis())

    @Volatile
    var phase: GamePhase = GamePhase.MENU
        private set

    var level: LevelData = MapBuilder.build(GameMap.HOUSE)
        private set

    var difficulty: Difficulty = Difficulty.MEDIUM
        private set

    // Player state
    var playerX: Float = level.spawnX
        private set
    var playerZ: Float = level.spawnZ
        private set
    var baseYawRad: Float = -PI.toFloat() / 2f // Face North (-Z)
        private set
    var basePitchRad: Float = 0f
        private set
    var headYawOffsetRad: Float = 0f
    var headPitchOffsetRad: Float = 0f
    var headRollRad: Float = 0f

    val totalYawRad: Float get() = baseYawRad + headYawOffsetRad
    val totalPitchRad: Float get() = (basePitchRad + headPitchOffsetRad).coerceIn(-1.25f, 1.25f)

    // Movement & Action Inputs (updated by touch, controller, and sensors)
    @Volatile var moveForwardInput: Float = 0f
    @Volatile var moveStrafeInput: Float = 0f
    @Volatile var lookTurnRate: Float = 0f
    @Volatile var lookPitchRate: Float = 0f

    var bobPhase: Float = 0f
        private set
    private var stepAccum: Float = 0f

    var flashlightOn: Boolean = true
        private set
    var sanity: Float = 100f
        private set
    var booksCollected: Int = 0
        private set
    var keysHeld: Int = 0
        private set
    var elapsedTime: Float = 0f
        private set

    // Slendrina Stalker AI State
    var slendrinaActive: Boolean = false
        private set
    var slendrinaX: Float = 0f
        private set
    var slendrinaZ: Float = 0f
        private set
    var slendrinaAlpha: Float = 0f
        private set
    var stareIntensity: Float = 0f
        private set
    var screenShake: Float = 0f
        private set
    var flashlightFlicker: Float = 1f
        private set

    private var stalkTimer: Float = 6.0f
    private var slendrinaDespawnTimer: Float = 0f
    private var jumpscareTimer: Float = 0f
    private var slendrinaCloseTimer: Float = 0f

    private var activeBookTarget: CollectibleEntity? = null
    private var activeKeyTarget: CollectibleEntity? = null
    private var activeDoorTarget: DoorEntity? = null
    private var activeExitTarget: Boolean = false

    private var promptText: String? = null
    private var toastText: String? = null
    private var toastTimer: Float = 0f

    fun startNewGame(map: GameMap, diff: Difficulty) {
        synchronized(lock) {
            difficulty = diff
            level = MapBuilder.build(map)
            playerX = level.spawnX
            playerZ = level.spawnZ
            baseYawRad = -PI.toFloat() / 2f
            basePitchRad = 0f
            headYawOffsetRad = 0f
            headPitchOffsetRad = 0f
            headRollRad = 0f
            bobPhase = 0f
            stepAccum = 0f
            flashlightOn = true
            sanity = 100f
            booksCollected = 0
            keysHeld = 0
            elapsedTime = 0f
            slendrinaActive = false
            slendrinaAlpha = 0f
            stareIntensity = 0f
            screenShake = 0f
            flashlightFlicker = 1f
            stalkTimer = diff.stalkIntervalBaseSec * 0.7f
            slendrinaDespawnTimer = 0f
            slendrinaCloseTimer = 0f
            jumpscareTimer = 0f
            activeBookTarget = null
            activeKeyTarget = null
            activeDoorTarget = null
            activeExitTarget = false
            promptText = null
            showToast("${map.title}: Find ${map.targetBooks} books. Don't look at her!")
            phase = GamePhase.PLAYING
        }
    }

    fun pauseGame() {
        synchronized(lock) {
            if (phase == GamePhase.PLAYING) phase = GamePhase.PAUSED
        }
    }

    fun resumeGame() {
        synchronized(lock) {
            if (phase == GamePhase.PAUSED) phase = GamePhase.PLAYING
        }
    }

    fun returnToMenu() {
        synchronized(lock) {
            phase = GamePhase.MENU
            moveForwardInput = 0f
            moveStrafeInput = 0f
            lookTurnRate = 0f
            lookPitchRate = 0f
            flashlightOn = true
            flashlightFlicker = 1f
            stareIntensity = 0f
            screenShake = 0f
        }
    }

    fun previewMapInMenu(map: GameMap) {
        synchronized(lock) {
            if (phase != GamePhase.MENU) return
            if (level.map == map) return
            level = MapBuilder.build(map)
            playerX = level.spawnX
            playerZ = level.spawnZ
            baseYawRad = -PI.toFloat() / 2f
            basePitchRad = 0f
            flashlightOn = true
            flashlightFlicker = 1f
        }
    }

    fun toggleFlashlight() {
        synchronized(lock) {
            if (phase != GamePhase.PLAYING) return
            flashlightOn = !flashlightOn
            showToast(if (flashlightOn) "Flashlight ON" else "Flashlight OFF")
        }
    }

    fun addLookDelta(deltaYawRad: Float, deltaPitchRad: Float) {
        synchronized(lock) {
            if (phase != GamePhase.PLAYING) return
            baseYawRad += deltaYawRad
            basePitchRad = (basePitchRad + deltaPitchRad).coerceIn(-1.0f, 1.0f)
        }
    }

    fun interact() {
        synchronized(lock) {
            if (phase != GamePhase.PLAYING) return

            val book = activeBookTarget
            if (book != null && !book.collected) {
                book.collected = true
                booksCollected++
                audioEvents?.onBookCollected(booksCollected, level.map.targetBooks)
                if (booksCollected >= level.map.targetBooks) {
                    showToast("All ${level.map.targetBooks} books found! Return to the Entrance to escape!")
                    // Slendrina spawns aggressively when all books are gathered
                    spawnSlendrina(forceClose = false)
                } else {
                    showToast("Book $booksCollected / ${level.map.targetBooks} collected")
                }
                updateTargetsLocked()
                return
            }

            val key = activeKeyTarget
            if (key != null && !key.collected) {
                key.collected = true
                keysHeld++
                audioEvents?.onKeyCollected()
                showToast("Brass Key acquired ($keysHeld held)")
                updateTargetsLocked()
                return
            }

            val door = activeDoorTarget
            if (door != null) {
                if (door.isLocked) {
                    if (keysHeld > 0) {
                        keysHeld--
                        door.isLocked = false
                        door.isOpen = true
                        audioEvents?.onDoorOpened()
                        showToast("Door unlocked with Brass Key!")
                    } else {
                        audioEvents?.onDoorLocked()
                        showToast("Locked! Find a Brass Key first.")
                    }
                } else {
                    door.isOpen = !door.isOpen
                    audioEvents?.onDoorOpened()
                }
                updateTargetsLocked()
                return
            }

            if (activeExitTarget && booksCollected >= level.map.targetBooks) {
                triggerVictoryLocked()
            }
        }
    }

    fun tick(rawDt: Float, headBobScale: Float = 1f) {
        val dt = rawDt.coerceIn(0.001f, 0.05f)
        synchronized(lock) {
            if (toastTimer > 0f) {
                toastTimer -= dt
                if (toastTimer <= 0f) toastText = null
            }

            if (phase == GamePhase.JUMPSCARE) {
                jumpscareTimer -= dt
                screenShake = 0.08f
                stareIntensity = 1f
                if (jumpscareTimer <= 0f) {
                    phase = GamePhase.GAME_OVER
                    screenShake = 0f
                }
                return
            }

            if (phase == GamePhase.MENU) {
                elapsedTime += dt
                val flickerNoise = sin(elapsedTime * 19f) * sin(elapsedTime * 7f)
                flashlightFlicker = if (flickerNoise > 0.78f) 0.55f else 0.96f
                return
            }

            if (phase != GamePhase.PLAYING) return

            elapsedTime += dt

            // Continuous turning/pitch from gamepad/controller if held
            if (abs(lookTurnRate) > 0.01f) {
                baseYawRad += lookTurnRate * dt
            }
            if (abs(lookPitchRate) > 0.01f) {
                basePitchRad = (basePitchRad + lookPitchRate * dt).coerceIn(-1.0f, 1.0f)
            }

            // Animate doors smoothly
            for (door in level.doors) {
                val target = if (door.isOpen) 1f else 0f
                if (door.openProgress < target) {
                    door.openProgress = (door.openProgress + dt * 2.4f).coerceAtMost(1f)
                } else if (door.openProgress > target) {
                    door.openProgress = (door.openProgress - dt * 2.4f).coerceAtLeast(0f)
                }
            }

            // Player movement relative to current total yaw
            val yaw = totalYawRad
            val fwdX = cos(yaw)
            val fwdZ = sin(yaw)
            val rightX = -sin(yaw)
            val rightZ = cos(yaw)

            var moveX = fwdX * moveForwardInput + rightX * moveStrafeInput
            var moveZ = fwdZ * moveForwardInput + rightZ * moveStrafeInput
            val moveLen = hypot(moveX, moveZ)
            if (moveLen > 1f) {
                moveX /= moveLen
                moveZ /= moveLen
            }

            val speed = 3.55f
            if (moveLen > 0.05f) {
                val nx = playerX + moveX * speed * dt
                val nz = playerZ + moveZ * speed * dt
                tryMove(nx, nz)
                bobPhase += dt * 9.5f * moveLen.coerceAtMost(1f) * headBobScale
                stepAccum += moveLen * speed * dt
                if (stepAccum >= 1.55f) {
                    stepAccum = 0f
                    audioEvents?.onFootstep()
                }
            }

            // Check automatic escape if player walks onto unlocked exit portal
            if (booksCollected >= level.map.targetBooks) {
                val distToExit = hypot(playerX - level.exitX, playerZ - level.exitZ)
                if (distToExit < 1.15f) {
                    triggerVictoryLocked()
                    return
                }
            }

            updateTargetsLocked()
            updateSlendrinaLocked(dt)
        }
    }

    private fun tryMove(targetX: Float, targetZ: Float) {
        val r = 0.44f
        if (!collides(targetX, playerZ, r)) {
            playerX = targetX
        }
        if (!collides(playerX, targetZ, r)) {
            playerZ = targetZ
        }
    }

    private fun collides(wx: Float, wz: Float, radius: Float): Boolean {
        val minGx = floor((wx - radius) / CELL_SIZE).toInt()
        val maxGx = floor((wx + radius) / CELL_SIZE).toInt()
        val minGz = floor((wz - radius) / CELL_SIZE).toInt()
        val maxGz = floor((wz + radius) / CELL_SIZE).toInt()
        for (gz in minGz..maxGz) {
            for (gx in minGx..maxGx) {
                if (level.isBlockedForMovement(gx, gz)) {
                    return true
                }
            }
        }
        return false
    }

    private fun updateTargetsLocked() {
        val yaw = totalYawRad
        val lookX = cos(yaw)
        val lookZ = sin(yaw)

        activeBookTarget = level.collectibles
            .filter { !it.collected && it.kind == CollectibleKind.BOOK }
            .mapNotNull { item ->
                val dx = item.x - playerX
                val dz = item.z - playerZ
                val dist = hypot(dx, dz)
                val dot = if (dist > 0.001f) (dx * lookX + dz * lookZ) / dist else 1f
                if (dist < 2.25f && (dot > 0.58f || dist < 1.0f)) item to dist else null
            }
            .minByOrNull { it.second }?.first

        activeKeyTarget = if (activeBookTarget == null) {
            level.collectibles
                .filter { !it.collected && it.kind == CollectibleKind.KEY }
                .mapNotNull { item ->
                    val dx = item.x - playerX
                    val dz = item.z - playerZ
                    val dist = hypot(dx, dz)
                    val dot = if (dist > 0.001f) (dx * lookX + dz * lookZ) / dist else 1f
                    if (dist < 2.25f && (dot > 0.58f || dist < 1.0f)) item to dist else null
                }
                .minByOrNull { it.second }?.first
        } else null

        activeDoorTarget = if (activeBookTarget == null && activeKeyTarget == null) {
            level.doors
                .mapNotNull { door ->
                    val cx = (door.gx + 0.5f) * CELL_SIZE
                    val cz = (door.gz + 0.5f) * CELL_SIZE
                    val dx = cx - playerX
                    val dz = cz - playerZ
                    val dist = hypot(dx, dz)
                    val dot = if (dist > 0.001f) (dx * lookX + dz * lookZ) / dist else 1f
                    if (dist < 2.35f && dot > 0.55f) door to dist else null
                }
                .minByOrNull { it.second }?.first
        } else null

        val exitDist = hypot(playerX - level.exitX, playerZ - level.exitZ)
        activeExitTarget = booksCollected >= level.map.targetBooks && exitDist < 2.5f

        promptText = when {
            activeBookTarget != null -> "A / R2: Take Book (${booksCollected + 1}/${level.map.targetBooks})"
            activeKeyTarget != null -> "A / R2: Take Brass Key"
            activeDoorTarget != null -> {
                val d = activeDoorTarget!!
                when {
                    d.isLocked && keysHeld > 0 -> "A / R2: Unlock Door (Uses Key)"
                    d.isLocked -> "Locked — Find a Brass Key"
                    d.isOpen -> "A / R2: Close Door"
                    else -> "A / R2: Open Door"
                }
            }
            activeExitTarget -> "Step onto Exit Seal to Escape!"
            else -> null
        }
    }

    private fun updateSlendrinaLocked(dt: Float) {
        val aggression = 1f + booksCollected * 0.16f
        if (!slendrinaActive) {
            stalkTimer -= dt * aggression
            stareIntensity = (stareIntensity - dt * 2.5f).coerceAtLeast(0f)
            sanity = (sanity + difficulty.sanityRegenPerSecond * dt).coerceAtMost(100f)
            screenShake = (screenShake - dt * 0.15f).coerceAtLeast(0f)
            flashlightFlicker = 1f

            if (stalkTimer <= 0f) {
                spawnSlendrina(forceClose = false)
            }
            return
        }

        slendrinaDespawnTimer -= dt
        val dx = slendrinaX - playerX
        val dz = slendrinaZ - playerZ
        val dist = hypot(dx, dz).coerceAtLeast(0.05f)
        val yaw = totalYawRad
        val lookX = cos(yaw)
        val lookZ = sin(yaw)
        val dot = (dx * lookX + dz * lookZ) / dist
        val hasLos = hasLineOfSight(playerX, playerZ, slendrinaX, slendrinaZ)

        // Subtle creeping towards player when not looked at
        if (hasLos && dot < 0.35f && dist > difficulty.slendrinaMinDist * 0.65f) {
            val creepSpeed = (1.1f + booksCollected * 0.22f)
            val stepX = slendrinaX - (dx / dist) * creepSpeed * dt
            val stepZ = slendrinaZ - (dz / dist) * creepSpeed * dt
            val gx = floor(stepX / CELL_SIZE).toInt()
            val gz = floor(stepZ / CELL_SIZE).toInt()
            if (!level.isBlockedForLineOfSight(gx, gz)) {
                slendrinaX = stepX
                slendrinaZ = stepZ
            }
        }

        // Flicker flashlight when she is nearby
        if (dist < 10f && hasLos) {
            val flickerNoise = sin(elapsedTime * 37f) * sin(elapsedTime * 19f)
            flashlightFlicker = if (flickerNoise > 0.55f) 0.25f else 0.92f
        } else {
            flashlightFlicker = 1f
        }

        val lookingAtHer = hasLos && dist < 13.5f && dot > 0.70f
        if (lookingAtHer) {
            val distFactor = ((14f - dist) / 10f).coerceIn(0.45f, 1.8f)
            stareIntensity = (stareIntensity + dt * 2.4f).coerceAtMost(1f)
            sanity -= difficulty.stareDrainPerSecond * distFactor * dt
            screenShake = 0.015f + 0.035f * stareIntensity
            slendrinaAlpha = 1f
        } else {
            stareIntensity = (stareIntensity - dt * 1.9f).coerceAtLeast(0f)
            screenShake = (screenShake - dt * 0.1f).coerceAtLeast(0f)
            slendrinaAlpha = (slendrinaAlpha - dt * 0.12f).coerceAtLeast(0.75f)
        }

        if (dist < 1.45f && hasLos) {
            slendrinaCloseTimer += dt
            if (slendrinaCloseTimer > 1.4f) {
                sanity = 0f
            }
        } else {
            slendrinaCloseTimer = 0f
        }

        if (sanity <= 0f) {
            sanity = 0f
            triggerJumpscareLocked()
            return
        }

        if (slendrinaDespawnTimer <= 0f && !lookingAtHer) {
            slendrinaActive = false
            slendrinaAlpha = 0f
            stalkTimer = (difficulty.stalkIntervalBaseSec / aggression).coerceAtLeast(2.4f) + rng.nextFloat() * 2.5f
        }
    }

    private fun spawnSlendrina(forceClose: Boolean) {
        val baseAngle = totalYawRad
        val spawnBehind = !forceClose && rng.nextFloat() < 0.42f
        val minD = if (forceClose) 2.8f else difficulty.slendrinaMinDist
        val maxD = if (forceClose) 5.0f else (10.5f - booksCollected * 0.6f).coerceAtLeast(5.5f)

        for (attempt in 0 until 18) {
            val angleOffset = if (spawnBehind) {
                PI.toFloat() + (rng.nextFloat() - 0.5f) * 1.2f
            } else {
                (rng.nextFloat() - 0.5f) * 2.4f
            }
            val angle = baseAngle + angleOffset
            val dist = minD + rng.nextFloat() * (maxD - minD)
            val tx = playerX + cos(angle) * dist
            val tz = playerZ + sin(angle) * dist
            val gx = floor(tx / CELL_SIZE).toInt()
            val gz = floor(tz / CELL_SIZE).toInt()
            if (gx in 1 until level.width - 1 && gz in 1 until level.height - 1) {
                if (!level.isBlockedForMovement(gx, gz) && hasLineOfSight(playerX, playerZ, tx, tz)) {
                    slendrinaX = tx
                    slendrinaZ = tz
                    slendrinaActive = true
                    slendrinaAlpha = 0.95f
                    slendrinaDespawnTimer = 4.2f + rng.nextFloat() * 2.8f
                    slendrinaCloseTimer = 0f
                    audioEvents?.onSlendrinaAppear(dist < 6.5f)
                    return
                }
            }
        }
        // Retry shortly if no unobstructed line-of-sight spot was found
        stalkTimer = 1.8f
    }

    private fun triggerJumpscareLocked() {
        phase = GamePhase.JUMPSCARE
        jumpscareTimer = 1.65f
        slendrinaActive = true
        // Snap Slendrina right in front of camera for visceral jumpscare
        val yaw = totalYawRad
        slendrinaX = playerX + cos(yaw) * 0.88f
        slendrinaZ = playerZ + sin(yaw) * 0.88f
        slendrinaAlpha = 1f
        stareIntensity = 1f
        audioEvents?.onJumpscare()
    }

    private fun triggerVictoryLocked() {
        phase = GamePhase.VICTORY
        slendrinaActive = false
        stareIntensity = 0f
        screenShake = 0f
        moveForwardInput = 0f
        moveStrafeInput = 0f
        audioEvents?.onVictory()
    }

    private fun hasLineOfSight(x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        val dx = x1 - x0
        val dz = z1 - z0
        val dist = hypot(dx, dz)
        val steps = (dist / 0.35f).toInt().coerceAtLeast(1)
        for (i in 1 until steps) {
            val t = i.toFloat() / steps
            val sx = x0 + dx * t
            val sz = z0 + dz * t
            val gx = floor(sx / CELL_SIZE).toInt()
            val gz = floor(sz / CELL_SIZE).toInt()
            if (level.isBlockedForLineOfSight(gx, gz)) return false
        }
        return true
    }

    private fun showToast(msg: String) {
        toastText = msg
        toastTimer = 3.2f
    }

    fun snapshot(): HudSnapshot = synchronized(lock) {
        val deg = ((atan2(sin(totalYawRad), cos(totalYawRad)) * 180f / PI.toFloat()) + 360f) % 360f
        HudSnapshot(
            phase = phase,
            map = level.map,
            difficulty = difficulty,
            booksCollected = booksCollected,
            totalBooks = level.map.targetBooks,
            keysHeld = keysHeld,
            sanity = sanity,
            flashlightOn = flashlightOn,
            elapsedSeconds = elapsedTime.toInt(),
            promptText = promptText,
            toastText = toastText,
            stareWarning = stareIntensity,
            exitUnlocked = booksCollected >= level.map.targetBooks,
            playerGridX = floor(playerX / CELL_SIZE).toInt(),
            playerGridZ = floor(playerZ / CELL_SIZE).toInt(),
            playerYawDeg = deg
        )
    }

    fun isHighlightedTarget(item: CollectibleEntity): Boolean = synchronized(lock) {
        item == activeBookTarget || item == activeKeyTarget
    }

    fun isHighlightedDoor(door: DoorEntity): Boolean = synchronized(lock) {
        door == activeDoorTarget
    }
}
