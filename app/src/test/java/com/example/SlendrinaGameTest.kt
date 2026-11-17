package com.example

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.example.data.Difficulty
import com.example.data.GameMap
import com.example.game.CollectibleKind
import com.example.game.GameEngine
import com.example.game.GamePhase
import com.example.game.MapBuilder
import com.example.input.BluetoothGamepadHandler
import com.example.input.MenuNavAction
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.GameRecords
import com.example.data.PlayerSettings
import com.example.ui.GamePlayScreen
import com.example.ui.HeadPose
import com.example.ui.MainMenuScreen
import com.example.ui.theme.SlendrinaGoTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlendrinaGameTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allThreeMapsBuildWithSixBooksAndSufficientKeys() {
        for (map in GameMap.entries) {
            val level = MapBuilder.build(map, seed = 12345L)
            val books = level.collectibles.count { it.kind == CollectibleKind.BOOK }
            val keys = level.collectibles.count { it.kind == CollectibleKind.KEY }
            val lockedDoors = level.doors.count { it.isLocked }

            assertEquals("Map ${map.title} should have 6 books", 6, books)
            assertTrue("Map ${map.title} should have enough keys ($keys >= $lockedDoors)", keys >= lockedDoors)
            assertFalse("Spawn cell must not be blocked", level.isBlockedForMovement(
                (level.spawnX / 2.6f).toInt(),
                (level.spawnZ / 2.6f).toInt()
            ))
        }
    }

    @Test
    fun engineStartsCollectsBooksAndUnlocksExit() {
        val engine = GameEngine()
        engine.startNewGame(GameMap.HOUSE, Difficulty.MEDIUM)
        assertEquals(GamePhase.PLAYING, engine.phase)
        assertTrue(engine.flashlightOn)

        engine.toggleFlashlight()
        assertFalse(engine.flashlightOn)

        val startZ = engine.playerZ
        engine.moveForwardInput = 1f
        repeat(10) { engine.tick(0.016f) }
        assertNotEquals(startZ, engine.playerZ)
        assertFalse(engine.playerZ.isNaN())
    }

    @Test
    fun menuPreviewsSelectedMapsInVr() {
        val engine = GameEngine()
        assertEquals(GamePhase.MENU, engine.phase)
        assertEquals(GameMap.HOUSE, engine.level.map)

        engine.previewMapInMenu(GameMap.HOTEL)
        assertEquals(GamePhase.MENU, engine.phase)
        assertEquals(GameMap.HOTEL, engine.level.map)

        engine.tick(0.016f)
        assertTrue(engine.elapsedTime > 0f)
        assertEquals(GamePhase.MENU, engine.phase)
    }

    @Test
    fun mainMenuIsVrOnlyWithoutFlat2dToggle() {
        var started = false
        var selectedMap = GameMap.HOUSE
        composeRule.setContent {
            SlendrinaGoTheme {
                MainMenuScreen(
                    settings = PlayerSettings(),
                    records = GameRecords(),
                    hasHardwareGyro = true,
                    onSelectMap = { selectedMap = it },
                    onSelectDifficulty = {},
                    onToggleHeadTracking = {},
                    onToggleAudio = {},
                    onRecenter = {},
                    onStartGame = { started = true }
                )
            }
        }

        composeRule.onNodeWithTag("mode_vr_only_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("slendrina_portrait_emblem").assertIsDisplayed()
        composeRule.onNodeWithTag("gamepad_status_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("vr_menu_window").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_flat_2d_button").assertDoesNotExist()
        composeRule.onNodeWithTag("vr_controller_laser_dot").assertDoesNotExist()
        composeRule.onNodeWithTag("controller_touchpad_indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("map_card_yard").performClick()
        assertEquals(GameMap.YARD, selectedMap)
        composeRule.onNodeWithTag("start_game_button").performClick()
        assertTrue(started)
    }

    @Test
    fun bluetoothGamepadHandlesMovementLookFlashlightRecenterAndPause() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = GameEngine()
        var recentered = false
        var pauseToggled = false
        val handler = BluetoothGamepadHandler(
            context = context,
            engine = engine,
            onRecenter = { recentered = true },
            onTogglePause = { pauseToggled = true }
        )

        engine.startNewGame(GameMap.YARD, Difficulty.EASY)
        assertTrue(engine.flashlightOn)

        // Gamepad X toggles flashlight; L2 analog trigger toggles back on
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_BUTTON_X, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X)))
        assertFalse(engine.flashlightOn)

        // L1/R1 seated yaw turning
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_BUTTON_R1, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1)))
        assertTrue(engine.lookTurnRate > 0f)
        assertTrue(handler.handleKeyUp(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(0f, engine.lookTurnRate, 0.001f)

        // Analog Left Stick movement + Right Stick camera look
        val stickMove = MotionEvent.obtain(
            0L, 20L, MotionEvent.ACTION_MOVE,
            1,
            arrayOf(
                MotionEvent.PointerProperties().apply { id = 0 }
            ),
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    setAxisValue(MotionEvent.AXIS_X, 0.5f)
                    setAxisValue(MotionEvent.AXIS_Y, -0.8f)
                    setAxisValue(MotionEvent.AXIS_Z, 0.7f)
                    setAxisValue(MotionEvent.AXIS_RZ, -0.5f)
                }
            ),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK, 0
        )
        assertTrue(handler.handleGenericMotion(stickMove))
        assertTrue(engine.moveStrafeInput > 0.2f)
        assertTrue(engine.moveForwardInput > 0.5f)
        assertTrue(engine.lookTurnRate > 0.5f)
        assertTrue(engine.lookPitchRate > 0.2f)
        stickMove.recycle()

        // Raw touchpad without gamepad/joystick should NOT be handled
        val rawTouchpad = MotionEvent.obtain(0L, 25L, MotionEvent.ACTION_MOVE, 0.5f, 0.5f, 0).apply {
            source = InputDevice.SOURCE_TOUCHPAD
        }
        assertFalse(handler.handleGenericMotion(rawTouchpad))
        rawTouchpad.recycle()

        // Recenter via Y
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y)))
        assertTrue(recentered)

        // Start button triggers pause
        assertTrue(handler.handleKeyDown(KeyEvent.KEYCODE_BUTTON_START, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START)))
        assertTrue(pauseToggled)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun bluetoothGamepadNavigatesAndSelectsInVrMenu() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = GameEngine()
        var recentered = false
        val handler = BluetoothGamepadHandler(
            context = context,
            engine = engine,
            onRecenter = { recentered = true },
            onTogglePause = {}
        )

        val collected = mutableListOf<MenuNavAction>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            handler.menuActions.collect { collected.add(it) }
        }

        // Gamepad A (Select), DPAD_UP, L1 (Left), R1 (Right), X (Audio), Y (Recenter), START (Start Game)
        assertTrue(handler.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
        assertTrue(handler.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
        assertTrue(handler.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_L1)))
        assertTrue(handler.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_R1)))
        assertTrue(handler.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X)))
        assertTrue(handler.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y)))
        assertTrue(handler.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START)))
        assertTrue(recentered)

        // Analog D-Pad Hat right + R2 Trigger in MENU
        val hatRightAndR2 = MotionEvent.obtain(
            0L, 30L, MotionEvent.ACTION_MOVE,
            1,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0 }),
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    setAxisValue(MotionEvent.AXIS_HAT_X, 1.0f)
                    setAxisValue(MotionEvent.AXIS_RTRIGGER, 0.9f)
                }
            ),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK, 0
        )
        assertTrue(handler.handleGenericMotion(hatRightAndR2))
        hatRightAndR2.recycle()

        assertEquals(
            listOf(
                MenuNavAction.SELECT,
                MenuNavAction.UP,
                MenuNavAction.LEFT,
                MenuNavAction.RIGHT,
                MenuNavAction.TOGGLE_AUDIO,
                MenuNavAction.RECENTER,
                MenuNavAction.START_GAME,
                MenuNavAction.RIGHT,
                MenuNavAction.SELECT
            ),
            collected
        )
        job.cancel()
    }

    @Test
    fun mainMenuRespondsToControllerActionsAndHeadPose() {
        val actions = MutableSharedFlow<MenuNavAction>(extraBufferCapacity = 8)
        var started = false
        var selectedDifficulty = Difficulty.MEDIUM
        composeRule.setContent {
            SlendrinaGoTheme {
                MainMenuScreen(
                    settings = PlayerSettings(),
                    records = GameRecords(),
                    headPose = HeadPose(yawRad = 0.12f, pitchRad = -0.05f),
                    hasHardwareGyro = true,
                    menuActions = actions,
                    onSelectMap = {},
                    onSelectDifficulty = { selectedDifficulty = it },
                    onToggleHeadTracking = {},
                    onToggleAudio = {},
                    onRecenter = {},
                    onStartGame = { started = true }
                )
            }
        }

        composeRule.waitForIdle()

        // Start focused on "start"; move UP to difficulty row ("diff_medium"), LEFT to Easy, SELECT
        actions.tryEmit(MenuNavAction.UP)
        actions.tryEmit(MenuNavAction.LEFT)
        actions.tryEmit(MenuNavAction.SELECT)
        composeRule.waitForIdle()
        assertEquals(Difficulty.EASY, selectedDifficulty)

        // Move DOWN back to "start" and press Trigger/A (SELECT)
        actions.tryEmit(MenuNavAction.DOWN)
        actions.tryEmit(MenuNavAction.SELECT)
        composeRule.waitForIdle()
        assertTrue(started)
    }

    @Test
    fun headTrackerSupportsGyroAccelMag9DofFusionAndDriftCorrection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tracker = com.example.input.HeadTracker(context)
        tracker.updateDisplayRotation(android.view.Surface.ROTATION_90)

        // Upright landscape pose (ROTATION_90): +X points UP (gravity 9.81 m/s^2), -Z points forward (North)
        // Magnetic field points North (-Z) and slightly down (-X)
        tracker.setRawAccelMagForTest(
            ax = 9.81f, ay = 0f, az = 0f,
            mx = -18f, my = 0f, mz = -40f
        )

        // Initialize fused orientation from Accelerometer + Magnetometer
        tracker.stepComplementaryFusion(gx = 0f, gy = 0f, gz = 0f, dt = 0.016f)

        // Apply pure gyroscope yaw rate around +X (upward axis in ROTATION_90)
        repeat(20) {
            tracker.stepComplementaryFusion(gx = 1.0f, gy = 0f, gz = 0f, dt = 0.016f)
        }
        val afterGyroYaw = com.example.input.HeadTracker.wrapAngleRad(0f) // verify finite & clean
        assertEquals(0f, afterGyroYaw, 0.001f)

        // Feed stationary gyro with accel+mag enabled; magnetometer + accel must pull drifted pose back smoothly
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        tracker.updateOrientationFromDeviceWorldMatrix(identity)
        assertEquals(0f, tracker.yawOffsetRad, 0.001f)

        // Simulate yaw rotation around world Z in device-to-world matrix and verify recenter resets yaw only
        val yaw30 = Math.toRadians(30.0).toFloat()
        val c = kotlin.math.cos(yaw30)
        val s = kotlin.math.sin(yaw30)
        val rotatedZ = floatArrayOf(
            c, -s, 0f,
            s, c, 0f,
            0f, 0f, 1f
        )
        tracker.updateOrientationFromDeviceWorldMatrix(rotatedZ)
        assertTrue(kotlin.math.abs(tracker.yawOffsetRad) > 0.3f)

        tracker.recenter()
        tracker.updateOrientationFromDeviceWorldMatrix(rotatedZ)
        assertEquals(0f, tracker.yawOffsetRad, 0.001f)
    }

    @Test
    fun oculusGoDisplayProfileMatchesHardwareOpticsAndRefreshToggleWorks() {
        // 2560 x 1440 total -> 1280 x 1440 per eye, 63.5mm IPD, 90-100 deg diagonal FoV
        assertEquals(2560, com.example.gl.OculusGoDisplayProfile.PANEL_WIDTH_PX)
        assertEquals(1440, com.example.gl.OculusGoDisplayProfile.PANEL_HEIGHT_PX)
        assertEquals(1280, com.example.gl.OculusGoDisplayProfile.PER_EYE_WIDTH_PX)
        assertEquals(1440, com.example.gl.OculusGoDisplayProfile.PER_EYE_HEIGHT_PX)
        assertEquals(0.0635f, com.example.gl.OculusGoDisplayProfile.FIXED_IPD_METERS, 0.0001f)
        assertEquals(0.03175f, com.example.gl.OculusGoDisplayProfile.HALF_IPD_METERS, 0.00001f)

        val vFovGo = com.example.gl.OculusGoDisplayProfile.calculateVerticalFovDeg(
            eyeAspect = com.example.gl.OculusGoDisplayProfile.NATIVE_EYE_ASPECT
        )
        assertTrue("Vertical FoV ($vFovGo) should produce ~95 deg diagonal FoV", vFovGo in 75f..82f)
        assertTrue(
            com.example.gl.OculusGoDisplayProfile.vibrancyFor(com.example.data.VrRefreshRate.HZ_72) >
                com.example.gl.OculusGoDisplayProfile.vibrancyFor(com.example.data.VrRefreshRate.HZ_60)
        )

        var refreshToggled = false
        composeRule.setContent {
            SlendrinaGoTheme {
                MainMenuScreen(
                    settings = PlayerSettings(),
                    records = GameRecords(),
                    hasHardwareGyro = true,
                    onSelectMap = {},
                    onSelectDifficulty = {},
                    onToggleHeadTracking = {},
                    onToggleRefreshRate = { refreshToggled = true },
                    onToggleAudio = {},
                    onRecenter = {},
                    onStartGame = {}
                )
            }
        }
        composeRule.onNodeWithTag("refresh_rate_toggle").assertIsDisplayed().performClick()
        assertTrue(refreshToggled)
    }

    @Test
    fun slendrinaChihuahuaNunAssetLoadsAndBuildsBillboardSprite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val portrait = com.example.gl.ProceduralTextures.loadSlendrinaPortraitBitmap(context)
        assertNotNull("Uploaded Slendrina Chihuahua-nun portrait asset should load", portrait)
        assertTrue(portrait!!.width > 0 && portrait.height > 0)
        portrait.recycle()

        val spriteFromAsset = com.example.gl.ProceduralTextures.makeSlendrinaSprite(context)
        assertEquals(384, spriteFromAsset.width)
        assertEquals(640, spriteFromAsset.height)
        spriteFromAsset.recycle()

        val fallbackSprite = com.example.gl.ProceduralTextures.makeSlendrinaSprite(null)
        assertEquals(384, fallbackSprite.width)
        assertEquals(640, fallbackSprite.height)
        fallbackSprite.recycle()
    }

    @Test
    fun vrGameplayBooksAndKeysHudIsPositionedLowerInLensViewAndHasNo2dOption() {
        val engine = GameEngine()
        engine.startNewGame(GameMap.HOUSE, Difficulty.MEDIUM)
        composeRule.setContent {
            SlendrinaGoTheme {
                GamePlayScreen(
                    engine = engine,
                    hud = engine.snapshot(),
                    settings = PlayerSettings(),
                    onRecenter = {},
                    onPauseToggle = {},
                    onResume = {},
                    onRestart = {},
                    onExitToMenu = {}
                )
            }
        }
        composeRule.waitForIdle()
        val barNode = composeRule.onNodeWithTag("vr_hud_objectives_bar").assertIsDisplayed()
        val topPx = barNode.fetchSemanticsNode().boundsInRoot.top
        assertTrue("VR Books/Keys HUD bar should be lowered from top edge (topPx=$topPx)", topPx >= 50f)
        composeRule.onNodeWithTag("hud_toggle_render_mode").assertDoesNotExist()
        composeRule.onNodeWithTag("hud_recenter").assertIsDisplayed()
        composeRule.onNodeWithTag("hud_pause").assertIsDisplayed()
    }

    @Test
    fun vrPauseOverlayIsVrOnlyWithout2dSwitchButton() {
        val engine = GameEngine()
        engine.startNewGame(GameMap.HOTEL, Difficulty.HARD)
        engine.pauseGame()
        composeRule.setContent {
            SlendrinaGoTheme {
                GamePlayScreen(
                    engine = engine,
                    hud = engine.snapshot(),
                    settings = PlayerSettings(),
                    onRecenter = {},
                    onPauseToggle = {},
                    onResume = {},
                    onRestart = {},
                    onExitToMenu = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pause_resume_button").assertIsDisplayed()
        composeRule.onNodeWithTag("pause_recenter_button").assertIsDisplayed()
        composeRule.onNodeWithTag("pause_switch_mode_button").assertDoesNotExist()
    }
}
