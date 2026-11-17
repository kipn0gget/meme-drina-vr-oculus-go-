package com.example.gl

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.data.GameMap
import com.example.data.RenderMode
import com.example.data.VrRefreshRate
import com.example.game.CELL_SIZE
import com.example.game.CellType
import com.example.game.CollectibleKind
import com.example.game.GameEngine
import com.example.game.LevelData
import com.example.game.WALL_HEIGHT
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class HorrorRenderer(
    private val engine: GameEngine,
    private val context: Context? = null,
    private val onFrameTick: () -> Unit
) : GLSurfaceView.Renderer {

    @Volatile var renderMode: RenderMode = RenderMode.VR_SBS
    @Volatile var refreshRate: VrRefreshRate = VrRefreshRate.HZ_72

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    private var worldProgram = 0
    private var hudProgram = 0
    private var textures: TextureSet? = null

    private var builtLevelRef: LevelData? = null
    private var wallVbo = 0
    private var wallVertexCount = 0
    private var floorVbo = 0
    private var floorVertexCount = 0
    private var ceilingVbo = 0
    private var ceilingVertexCount = 0
    private var propVbo = 0
    private var propVertexCount = 0

    private var unitDoorVbo = 0
    private var unitBillboardVbo = 0
    private var reticleVbo = 0

    // Uniform locations
    private var uMvpLoc = 0
    private var uMvLoc = 0
    private var uTexLoc = 0
    private var uFlashOnLoc = 0
    private var uFlashFlickerLoc = 0
    private var uFogColorLoc = 0
    private var uFogDensityLoc = 0
    private var uEmissiveLoc = 0
    private var uTintLoc = 0
    private var uStaticLoc = 0
    private var uDamageTintLoc = 0
    private var uTimeLoc = 0
    private var uVrVigLoc = 0
    private var uEyeViewportLoc = 0
    private var uColorVibrancyLoc = 0

    private var uHudColorLoc = 0
    private var uHudMvpLoc = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var lastNs = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        worldProgram = createProgram(WORLD_VERT_SHADER, WORLD_FRAG_SHADER)
        uMvpLoc = GLES30.glGetUniformLocation(worldProgram, "uMVP")
        uMvLoc = GLES30.glGetUniformLocation(worldProgram, "uMV")
        uTexLoc = GLES30.glGetUniformLocation(worldProgram, "uTex")
        uFlashOnLoc = GLES30.glGetUniformLocation(worldProgram, "uFlashOn")
        uFlashFlickerLoc = GLES30.glGetUniformLocation(worldProgram, "uFlashFlicker")
        uFogColorLoc = GLES30.glGetUniformLocation(worldProgram, "uFogColor")
        uFogDensityLoc = GLES30.glGetUniformLocation(worldProgram, "uFogDensity")
        uEmissiveLoc = GLES30.glGetUniformLocation(worldProgram, "uEmissive")
        uTintLoc = GLES30.glGetUniformLocation(worldProgram, "uTint")
        uStaticLoc = GLES30.glGetUniformLocation(worldProgram, "uStaticAmount")
        uDamageTintLoc = GLES30.glGetUniformLocation(worldProgram, "uDamageTint")
        uTimeLoc = GLES30.glGetUniformLocation(worldProgram, "uTime")
        uVrVigLoc = GLES30.glGetUniformLocation(worldProgram, "uVrVignette")
        uEyeViewportLoc = GLES30.glGetUniformLocation(worldProgram, "uEyeViewport")
        uColorVibrancyLoc = GLES30.glGetUniformLocation(worldProgram, "uColorVibrancy")

        hudProgram = createProgram(HUD_VERT_SHADER, HUD_FRAG_SHADER)
        uHudColorLoc = GLES30.glGetUniformLocation(hudProgram, "uColor")
        uHudMvpLoc = GLES30.glGetUniformLocation(hudProgram, "uMVP")

        textures = ProceduralTextures.createAll(context)
        createUnitPrimitives()
        builtLevelRef = null
        lastNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        lastNs = now

        val isVrMenu = engine.phase == com.example.game.GamePhase.MENU
        val useVrSbs = isVrMenu || renderMode == RenderMode.VR_SBS
        val bobScale = if (useVrSbs) 0.25f else 1.0f
        engine.tick(dt, headBobScale = bobScale)
        onFrameTick()

        val level = engine.level
        if (builtLevelRef !== level) {
            rebuildStaticGeometry(level)
            builtLevelRef = level
        }

        val map = level.map
        GLES30.glClearColor(map.fogColorR, map.fogColorG, map.fogColorB, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (useVrSbs) {
            val halfW = (surfaceWidth / 2).coerceAtLeast(1)
            val rightW = (surfaceWidth - halfW).coerceAtLeast(1)
            val aspect = halfW.toFloat() / surfaceHeight.toFloat()
            val halfIpd = OculusGoDisplayProfile.HALF_IPD_METERS
            // Left Eye (1280x1440 on Oculus Go 2560x1440 Fast-Switch LCD, IPD 63.5mm)
            GLES30.glViewport(0, 0, halfW, surfaceHeight)
            renderEye(
                vpX = 0f, vpY = 0f, vpW = halfW.toFloat(), vpH = surfaceHeight.toFloat(),
                aspect = aspect, eyeOffset = -halfIpd, vrMode = true
            )
            // Right Eye
            GLES30.glViewport(halfW, 0, rightW, surfaceHeight)
            renderEye(
                vpX = halfW.toFloat(), vpY = 0f, vpW = rightW.toFloat(), vpH = surfaceHeight.toFloat(),
                aspect = rightW.toFloat() / surfaceHeight.toFloat(), eyeOffset = halfIpd, vrMode = true
            )
            // Thin soft-obsidian divider (reduces Fresnel glare vs pure black)
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
            drawCenterDivider()
        } else {
            GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
            val aspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
            renderEye(
                vpX = 0f, vpY = 0f, vpW = surfaceWidth.toFloat(), vpH = surfaceHeight.toFloat(),
                aspect = aspect, eyeOffset = 0f, vrMode = false
            )
        }
    }

    private fun renderEye(
        vpX: Float,
        vpY: Float,
        vpW: Float,
        vpH: Float,
        aspect: Float,
        eyeOffset: Float,
        vrMode: Boolean
    ) {
        val texSet = textures ?: return
        val level = engine.level
        val map = level.map

        val fov = if (vrMode) {
            OculusGoDisplayProfile.calculateVerticalFovDeg(aspect)
        } else {
            OculusGoDisplayProfile.FLAT_2D_FOV_DEG
        }
        Matrix.perspectiveM(projMatrix, 0, fov, aspect, 0.1f, 55f)

        val yaw = engine.totalYawRad
        val pitch = engine.totalPitchRad
        val shake = engine.screenShake
        val shakeX = if (shake > 0f) sin(engine.elapsedTime * 57f) * shake else 0f
        val shakeY = if (shake > 0f) cos(engine.elapsedTime * 63f) * shake else 0f

        val bobY = sin(engine.bobPhase) * 0.045f * (if (vrMode) 0.25f else 1f)
        val eyeY = 1.62f + bobY + shakeY
        val rightX = -sin(yaw)
        val rightZ = cos(yaw)

        val camX = engine.playerX + rightX * eyeOffset + shakeX
        val camZ = engine.playerZ + rightZ * eyeOffset

        val lookX = cos(pitch) * cos(yaw)
        val lookY = sin(pitch)
        val lookZ = cos(pitch) * sin(yaw)

        // Apply slight roll if 3DoF head tilt is active in VR
        val roll = if (vrMode) engine.headRollRad else 0f
        val upX = -sin(roll) * rightX
        val upY = cos(roll)
        val upZ = -sin(roll) * rightZ

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, eyeY, camZ,
            camX + lookX, eyeY + lookY, camZ + lookZ,
            upX, upY, upZ
        )

        GLES30.glUseProgram(worldProgram)
        GLES30.glUniform1i(uTexLoc, 0)
        GLES30.glUniform1f(uFlashOnLoc, if (engine.flashlightOn) 1f else 0f)
        GLES30.glUniform1f(uFlashFlickerLoc, engine.flashlightFlicker)
        GLES30.glUniform3f(uFogColorLoc, map.fogColorR, map.fogColorG, map.fogColorB)
        GLES30.glUniform1f(uFogDensityLoc, map.fogDensity)
        GLES30.glUniform1f(uStaticLoc, engine.stareIntensity)
        GLES30.glUniform1f(uDamageTintLoc, ((100f - engine.sanity) / 100f).coerceIn(0f, 1f))
        GLES30.glUniform1f(uTimeLoc, engine.elapsedTime)
        GLES30.glUniform1f(uVrVigLoc, if (vrMode) 1f else 0f)
        GLES30.glUniform4f(uEyeViewportLoc, vpX, vpY, vpW, vpH)
        GLES30.glUniform1f(uColorVibrancyLoc, OculusGoDisplayProfile.vibrancyFor(refreshRate))

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0)
        GLES30.glUniformMatrix4fv(uMvLoc, 1, false, mvMatrix, 0)
        GLES30.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(uEmissiveLoc, 0f)
        GLES30.glUniform3f(uTintLoc, 1f, 1f, 1f)

        val wallTex = when (map) {
            GameMap.HOUSE -> texSet.wallHouse
            GameMap.YARD -> texSet.wallYard
            GameMap.HOTEL -> texSet.wallHotel
        }
        val floorTex = when (map) {
            GameMap.HOUSE -> texSet.floorHouse
            GameMap.YARD -> texSet.floorYard
            GameMap.HOTEL -> texSet.floorHotel
        }
        val ceilTex = if (level.outdoors) texSet.ceilingSky else texSet.ceilingIndoors

        drawStaticBatch(wallVbo, wallVertexCount, wallTex)
        drawStaticBatch(floorVbo, floorVertexCount, floorTex)
        if (level.outdoors) {
            GLES30.glUniform1f(uEmissiveLoc, 0.55f)
        }
        drawStaticBatch(ceilingVbo, ceilingVertexCount, ceilTex)
        GLES30.glUniform1f(uEmissiveLoc, 0f)
        drawStaticBatch(propVbo, propVertexCount, texSet.prop)

        // Draw dynamic doors
        for (door in level.doors) {
            val cellX = door.gx * CELL_SIZE
            val cellZ = door.gz * CELL_SIZE
            Matrix.setIdentityM(modelMatrix, 0)
            if (door.isHorizontal) {
                Matrix.translateM(modelMatrix, 0, cellX, 0f, cellZ + CELL_SIZE * 0.5f)
                Matrix.rotateM(modelMatrix, 0, -door.openProgress * 88f, 0f, 1f, 0f)
                Matrix.scaleM(modelMatrix, 0, CELL_SIZE, WALL_HEIGHT, 0.14f)
            } else {
                Matrix.translateM(modelMatrix, 0, cellX + CELL_SIZE * 0.5f, 0f, cellZ)
                Matrix.rotateM(modelMatrix, 0, door.openProgress * 88f, 0f, 1f, 0f)
                Matrix.rotateM(modelMatrix, 0, -90f, 0f, 1f, 0f)
                Matrix.scaleM(modelMatrix, 0, CELL_SIZE, WALL_HEIGHT, 0.14f)
            }
            Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvLoc, 1, false, mvMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
            val highlighted = engine.isHighlightedDoor(door)
            GLES30.glUniform1f(uEmissiveLoc, if (highlighted) 0.25f else 0f)
            GLES30.glUniform3f(uTintLoc, 1f, if (highlighted) 1.15f else 1f, 1f)
            drawUnitBuffer(unitDoorVbo, 36, if (door.isLocked) texSet.doorLocked else texSet.door)
        }

        // Draw billboarded items (Books, Keys, Exit Seal, and Slendrina)
        GLES30.glDepthMask(false)

        // Exit Seal on the floor at spawn
        run {
            val unlocked = engine.booksCollected >= map.targetBooks
            val pulse = 0.55f + 0.45f * sin(engine.elapsedTime * 3.5f)
            drawBillboard(
                x = level.exitX,
                y = 0.72f + pulse * 0.08f,
                z = level.exitZ,
                scaleX = 1.1f,
                scaleY = 1.1f,
                camX = camX,
                camZ = camZ,
                texId = texSet.exitPortal,
                emissive = if (unlocked) 0.95f else 0.15f,
                tintR = if (unlocked) 0.4f else 0.5f,
                tintG = if (unlocked) 1.0f else 0.5f,
                tintB = if (unlocked) 0.85f else 0.6f
            )
        }

        // Books & Keys
        for (item in level.collectibles) {
            if (item.collected) continue
            val bob = sin(engine.elapsedTime * 2.6f + item.phaseOffset) * 0.08f
            val highlighted = engine.isHighlightedTarget(item)
            val isBook = item.kind == CollectibleKind.BOOK
            drawBillboard(
                x = item.x,
                y = 0.95f + bob,
                z = item.z,
                scaleX = if (isBook) 0.62f else 0.50f,
                scaleY = if (isBook) 0.62f else 0.50f,
                camX = camX,
                camZ = camZ,
                texId = if (isBook) texSet.book else texSet.key,
                emissive = if (highlighted) 0.95f else 0.52f,
                tintR = 1f,
                tintG = if (highlighted) 1.25f else 1f,
                tintB = 1f
            )
        }

        // Slendrina Stalker (Demonic Chihuahua-Nun)
        if (engine.slendrinaActive && engine.slendrinaAlpha > 0.02f) {
            drawBillboard(
                x = engine.slendrinaX,
                y = 1.24f,
                z = engine.slendrinaZ,
                scaleX = 1.38f,
                scaleY = 2.30f,
                camX = camX,
                camZ = camZ,
                texId = texSet.slendrina,
                emissive = 0.62f + 0.42f * engine.stareIntensity,
                tintR = 1f,
                tintG = 1f - 0.30f * engine.stareIntensity,
                tintB = 1f - 0.32f * engine.stareIntensity
            )
        }

        GLES30.glDepthMask(true)

        // Draw center gaze reticle during active gameplay
        if (engine.phase == com.example.game.GamePhase.PLAYING) {
            drawCenterReticle(aspect)
        }
    }

    private fun drawBillboard(
        x: Float,
        y: Float,
        z: Float,
        scaleX: Float,
        scaleY: Float,
        camX: Float,
        camZ: Float,
        texId: Int,
        emissive: Float,
        tintR: Float,
        tintG: Float,
        tintB: Float
    ) {
        val angleDeg = atan2(camX - x, camZ - z) * 180f / PI.toFloat()
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, angleDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, 1f)

        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0)
        GLES30.glUniformMatrix4fv(uMvLoc, 1, false, mvMatrix, 0)
        GLES30.glUniformMatrix4fv(uMvpLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(uEmissiveLoc, emissive)
        GLES30.glUniform3f(uTintLoc, tintR, tintG, tintB)
        drawUnitBuffer(unitBillboardVbo, 6, texId)
    }

    private fun drawStaticBatch(vbo: Int, vertexCount: Int, texId: Int) {
        if (vbo == 0 || vertexCount <= 0) return
        drawUnitBuffer(vbo, vertexCount, texId)
    }

    private fun drawUnitBuffer(vbo: Int, vertexCount: Int, texId: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)

        val stride = 8 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawCenterReticle(aspect: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(hudProgram)
        val hasTarget = engine.snapshot().promptText != null
        val size = if (hasTarget) 0.017f else 0.009f

        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.scaleM(mvpMatrix, 0, size / aspect, size, 1f)
        GLES30.glUniformMatrix4fv(uHudMvpLoc, 1, false, mvpMatrix, 0)
        if (hasTarget) {
            GLES30.glUniform4f(uHudColorLoc, 0.92f, 0.74f, 0.34f, 0.92f)
        } else {
            GLES30.glUniform4f(uHudColorLoc, 0.92f, 0.88f, 0.85f, 0.55f)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, reticleVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawCenterDivider() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(hudProgram)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.scaleM(mvpMatrix, 0, 0.003f, 1.0f, 1f)
        GLES30.glUniformMatrix4fv(uHudMvpLoc, 1, false, mvpMatrix, 0)
        GLES30.glUniform4f(uHudColorLoc, 0.04f, 0.032f, 0.045f, 1f)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, reticleVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun rebuildStaticGeometry(level: LevelData) {
        deleteVbos()
        val walls = ArrayList<Float>(16384)
        val floors = ArrayList<Float>(16384)
        val ceilings = ArrayList<Float>(16384)
        val props = ArrayList<Float>(4096)

        val wallH = if (level.outdoors) 2.5f else WALL_HEIGHT
        val ceilH = if (level.outdoors) 9.5f else WALL_HEIGHT

        for (gz in 0 until level.height) {
            for (gx in 0 until level.width) {
                val x0 = gx * CELL_SIZE
                val z0 = gz * CELL_SIZE
                val x1 = x0 + CELL_SIZE
                val z1 = z0 + CELL_SIZE
                val cell = level.cellAt(gx, gz)

                if (cell == CellType.WALL) {
                    // Add exposed faces only for fast Adreno 530 fill-rate
                    if (level.cellAt(gx, gz - 1) != CellType.WALL) addQuad(walls, x1, 0f, z0, x0, 0f, z0, x0, wallH, z0, x1, wallH, z0, 0f, 0f, -1f)
                    if (level.cellAt(gx, gz + 1) != CellType.WALL) addQuad(walls, x0, 0f, z1, x1, 0f, z1, x1, wallH, z1, x0, wallH, z1, 0f, 0f, 1f)
                    if (level.cellAt(gx - 1, gz) != CellType.WALL) addQuad(walls, x0, 0f, z0, x0, 0f, z1, x0, wallH, z1, x0, wallH, z0, -1f, 0f, 0f)
                    if (level.cellAt(gx + 1, gz) != CellType.WALL) addQuad(walls, x1, 0f, z1, x1, 0f, z0, x1, wallH, z0, x1, wallH, z1, 1f, 0f, 0f)
                } else {
                    // Floor quad
                    addQuad(floors, x0, 0f, z1, x0, 0f, z0, x1, 0f, z0, x1, 0f, z1, 0f, 1f, 0f)
                    // Ceiling / night sky quad
                    if (!level.outdoors) {
                        addQuad(ceilings, x0, ceilH, z0, x0, ceilH, z1, x1, ceilH, z1, x1, ceilH, z0, 0f, -1f, 0f)
                    }
                    if (cell == CellType.PROP) {
                        val px0 = x0 + 0.65f
                        val px1 = x1 - 0.65f
                        val pz0 = z0 + 0.65f
                        val pz1 = z1 - 0.65f
                        val ph = if (level.outdoors) 1.55f else 1.25f
                        addBox(props, px0, 0f, pz0, px1, ph, pz1)
                    }
                }
            }
        }

        if (level.outdoors) {
            val maxW = level.width * CELL_SIZE
            val maxH = level.height * CELL_SIZE
            addQuad(ceilings, 0f, ceilH, 0f, 0f, ceilH, maxH, maxW, ceilH, maxH, maxW, ceilH, 0f, 0f, -1f, 0f)
        }

        wallVertexCount = walls.size / 8
        wallVbo = uploadBuffer(walls)
        floorVertexCount = floors.size / 8
        floorVbo = uploadBuffer(floors)
        ceilingVertexCount = ceilings.size / 8
        ceilingVbo = uploadBuffer(ceilings)
        propVertexCount = props.size / 8
        propVbo = uploadBuffer(props)
    }

    private fun createUnitPrimitives() {
        // Unit hinged door box [0..1] in X, [0..1] in Y, [-0.5..0.5] in Z
        val doorVerts = ArrayList<Float>(36 * 8)
        addBox(doorVerts, 0f, 0f, -0.5f, 1f, 1f, 0.5f)
        unitDoorVbo = uploadBuffer(doorVerts)

        // Unit billboard [-0.5..0.5] in X and Y
        val bbVerts = ArrayList<Float>(6 * 8)
        addQuad(bbVerts, -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f, 0f, 0f, 1f)
        unitBillboardVbo = uploadBuffer(bbVerts)

        // Reticle unit square [-1..1]
        val ret = floatArrayOf(
            -1f, -1f, 1f, -1f, 1f, 1f,
            -1f, -1f, 1f, 1f, -1f, 1f
        )
        reticleVbo = uploadRawFloatArray(ret)
    }

    private fun addBox(out: MutableList<Float>, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
        addQuad(out, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f)
        addQuad(out, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f)
        addQuad(out, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f)
        addQuad(out, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f)
        addQuad(out, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0f, 1f, 0f)
        addQuad(out, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f)
    }

    private fun addQuad(
        out: MutableList<Float>,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        nx: Float, ny: Float, nz: Float
    ) {
        // Triangle 1: 0, 1, 2
        pushVertex(out, x0, y0, z0, nx, ny, nz, 0f, 1f)
        pushVertex(out, x1, y1, z1, nx, ny, nz, 1f, 1f)
        pushVertex(out, x2, y2, z2, nx, ny, nz, 1f, 0f)
        // Triangle 2: 0, 2, 3
        pushVertex(out, x0, y0, z0, nx, ny, nz, 0f, 1f)
        pushVertex(out, x2, y2, z2, nx, ny, nz, 1f, 0f)
        pushVertex(out, x3, y3, z3, nx, ny, nz, 0f, 0f)
    }

    private fun pushVertex(
        out: MutableList<Float>,
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        u: Float, v: Float
    ) {
        out.add(x); out.add(y); out.add(z)
        out.add(nx); out.add(ny); out.add(nz)
        out.add(u); out.add(v)
    }

    private fun uploadBuffer(list: List<Float>): Int {
        if (list.isEmpty()) return 0
        return uploadRawFloatArray(list.toFloatArray())
    }

    private fun uploadRawFloatArray(data: FloatArray): Int {
        val buf: FloatBuffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(data)
        buf.position(0)
        val vbo = IntArray(1)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, buf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        return vbo[0]
    }

    private fun deleteVbos() {
        val ids = intArrayOf(wallVbo, floorVbo, ceilingVbo, propVbo).filter { it != 0 }.toIntArray()
        if (ids.isNotEmpty()) GLES30.glDeleteBuffers(ids.size, ids, 0)
        wallVbo = 0; floorVbo = 0; ceilingVbo = 0; propVbo = 0
    }

    fun release() {
        deleteVbos()
        val unitIds = intArrayOf(unitDoorVbo, unitBillboardVbo, reticleVbo).filter { it != 0 }.toIntArray()
        if (unitIds.isNotEmpty()) GLES30.glDeleteBuffers(unitIds.size, unitIds, 0)
        textures?.let {
            val texIds = it.allIds()
            GLES30.glDeleteTextures(texIds.size, texIds, 0)
        }
        textures = null
    }

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragSrc)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val WORLD_VERT_SHADER = """#version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aUv;
            uniform mat4 uMVP;
            uniform mat4 uMV;
            out vec3 vViewPos;
            out vec3 vViewNormal;
            out vec2 vUv;
            void main() {
                vec4 vp = uMV * vec4(aPos, 1.0);
                vViewPos = vp.xyz;
                vViewNormal = normalize((uMV * vec4(aNormal, 0.0)).xyz);
                vUv = aUv;
                gl_Position = uMVP * vec4(aPos, 1.0);
            }
        """

        private const val WORLD_FRAG_SHADER = """#version 300 es
            precision mediump float;
            in vec3 vViewPos;
            in vec3 vViewNormal;
            in vec2 vUv;
            uniform sampler2D uTex;
            uniform float uFlashOn;
            uniform float uFlashFlicker;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform float uEmissive;
            uniform vec3 uTint;
            uniform float uStaticAmount;
            uniform float uDamageTint;
            uniform float uTime;
            uniform float uVrVignette;
            uniform vec4 uEyeViewport;
            uniform float uColorVibrancy;
            out vec4 fragColor;
            void main() {
                vec4 tex = texture(uTex, vUv);
                if (tex.a < 0.08) discard;
                float dist = length(vViewPos);
                vec3 dirToCam = normalize(-vViewPos);
                float ndotl = max(dot(normalize(vViewNormal), dirToCam), 0.25);

                // Flashlight cone centered along camera -Z axis
                float spotCos = dot(normalize(vViewPos), vec3(0.0, 0.0, -1.0));
                float cone = smoothstep(0.76, 0.965, spotCos);
                float atten = 1.0 / (1.0 + 0.055 * dist + 0.022 * dist * dist);
                float flashlight = uFlashOn * uFlashFlicker * cone * atten * 2.15;

                float ambient = 0.09 + 0.05 * ndotl;
                vec3 lit = tex.rgb * uTint * (ambient + flashlight * ndotl + uEmissive);

                // Horror distance fog
                float fog = 1.0 - exp(-dist * uFogDensity);
                vec3 col = mix(lit, uFogColor, clamp(fog * (1.0 - uEmissive * 0.55), 0.0, 1.0));

                // Slendrina gaze static & crimson danger vignette
                if (uStaticAmount > 0.01) {
                    float grain = fract(sin(dot(gl_FragCoord.xy + uTime * 31.0, vec2(12.9898, 78.233))) * 43758.5453);
                    col = mix(col, vec3(grain * 0.85, grain * 0.22, grain * 0.22), uStaticAmount * 0.42);
                }
                if (uDamageTint > 0.05) {
                    col = mix(col, col * vec3(1.35, 0.45, 0.45), uDamageTint * 0.45);
                }
                if (uVrVignette > 0.5) {
                    // Radial Fresnel optical vignette & Fast-Switch RGB LCD 72Hz color vibrancy + god-ray soft cap
                    vec2 eyeUv = ((gl_FragCoord.xy - uEyeViewport.xy) / max(uEyeViewport.zw, vec2(1.0))) * 2.0 - 1.0;
                    float r2 = dot(eyeUv, eyeUv);
                    float lensVignette = smoothstep(1.62, 0.55, r2);
                    col *= mix(0.70, 1.0, lensVignette);
                    float luma = dot(col, vec3(0.299, 0.587, 0.114));
                    col = mix(vec3(luma), col, uColorVibrancy);
                    col = clamp(col * 0.94 + vec3(0.014, 0.012, 0.016), 0.0, 0.94);
                }
                fragColor = vec4(col, tex.a);
            }
        """

        private const val HUD_VERT_SHADER = """#version 300 es
            layout(location = 0) in vec2 aPos;
            uniform mat4 uMVP;
            void main() {
                gl_Position = uMVP * vec4(aPos, 0.0, 1.0);
            }
        """

        private const val HUD_FRAG_SHADER = """#version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = uColor;
            }
        """
    }
}
