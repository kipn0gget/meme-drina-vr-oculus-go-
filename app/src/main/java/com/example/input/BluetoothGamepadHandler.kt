package com.example.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.game.GameEngine
import com.example.game.GamePhase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class GamepadStatus(
    val connected: Boolean = false,
    val deviceName: String = "No Bluetooth Gamepad",
    val shortLabel: String = "No Gamepad",
    val isBluetooth: Boolean = false
)

enum class MenuNavAction {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK,
    RECENTER,
    TOGGLE_AUDIO,
    START_GAME
}

/**
 * Input handler focused on Bluetooth & HID Gamepads (Xbox, DualSense/DualShock, 8BitDo,
 * MOGA, SteelSeries, generic Android Bluetooth controllers) across both VR Menu and Gameplay.
 */
class BluetoothGamepadHandler(
    private val context: Context,
    private val engine: GameEngine,
    private val onRecenter: () -> Unit,
    private val onTogglePause: () -> Unit
) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager

    private val _status = MutableStateFlow(GamepadStatus())
    val status: StateFlow<GamepadStatus> = _status.asStateFlow()

    private val _menuActions = MutableSharedFlow<MenuNavAction>(extraBufferCapacity = 24)
    val menuActions: SharedFlow<MenuNavAction> = _menuActions.asSharedFlow()

    private var dpadX = 0f
    private var dpadY = 0f
    private var rTriggerLatched = false
    private var lTriggerLatched = false
    private var lastMenuNavMs = 0L

    init {
        refreshConnectedDevices()
    }

    fun startListening() {
        inputManager?.registerInputDeviceListener(this, null)
        refreshConnectedDevices()
    }

    fun stopListening() {
        inputManager?.unregisterInputDeviceListener(this)
    }

    override fun onInputDeviceAdded(deviceId: Int) = refreshConnectedDevices()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshConnectedDevices()
    override fun onInputDeviceChanged(deviceId: Int) = refreshConnectedDevices()

    fun recenterView() {
        onRecenter()
        _menuActions.tryEmit(MenuNavAction.RECENTER)
    }

    fun refreshConnectedDevices() {
        val ids = InputDevice.getDeviceIds()
        var bestDevice: InputDevice? = null
        var bestIsBluetooth = false

        for (id in ids) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (dev.isVirtual) continue
            val src = dev.sources
            val isGamepad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            if (!isGamepad) continue

            val external = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) dev.isExternal else true
            val name = dev.name ?: ""
            val looksBluetooth = external && (
                name.contains("bluetooth", ignoreCase = true) ||
                    name.contains("wireless", ignoreCase = true) ||
                    name.contains("xbox", ignoreCase = true) ||
                    name.contains("dualsense", ignoreCase = true) ||
                    name.contains("dualshock", ignoreCase = true) ||
                    name.contains("8bitdo", ignoreCase = true) ||
                    name.contains("moga", ignoreCase = true) ||
                    name.contains("steelseries", ignoreCase = true) ||
                    name.contains("gamesir", ignoreCase = true) ||
                    name.contains("pro controller", ignoreCase = true) ||
                    name.contains("gamepad", ignoreCase = true)
                )

            if (looksBluetooth) {
                bestDevice = dev
                bestIsBluetooth = true
                break
            } else if (bestDevice == null) {
                bestDevice = dev
                bestIsBluetooth = external
            }
        }

        if (bestDevice == null) {
            _status.value = GamepadStatus(
                connected = false,
                deviceName = "No Bluetooth Gamepad",
                shortLabel = "BT Pad Idle",
                isBluetooth = false
            )
        } else {
            val rawName = bestDevice.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Gamepad"
            _status.value = GamepadStatus(
                connected = true,
                deviceName = rawName,
                shortLabel = formatShortGamepadLabel(rawName, bestIsBluetooth),
                isBluetooth = bestIsBluetooth
            )
        }
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDown(event.keyCode, event)
            KeyEvent.ACTION_UP -> handleKeyUp(event.keyCode)
            else -> false
        }
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (engine.phase != GamePhase.PLAYING) {
            return handleMenuKeyDown(keyCode, event)
        }

        if (event.repeatCount > 0) return isGameplayKey(keyCode)

        return when (keyCode) {
            // Interact: Gamepad A / R2 / D-Pad Center / Enter / Space
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE -> {
                engine.interact()
                true
            }
            // Flashlight: Gamepad X / L2 / L3 / F
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_F -> {
                engine.toggleFlashlight()
                true
            }
            // Recenter 3DoF Head Tracking: Gamepad Y / R3 / Select / Mode / R
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_R -> {
                recenterView()
                true
            }
            // Seated Smooth Turn: L1 / R1 / Q / E
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> {
                engine.lookTurnRate = -2.0f
                true
            }
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> {
                engine.lookTurnRate = 2.0f
                true
            }
            // Pause: Gamepad B / Start / Menu / Back / Escape
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                onTogglePause()
                true
            }
            // D-Pad Movement & Keyboard WASD fallback
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                dpadY = 1f
                applyDpad()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                dpadY = -1f
                applyDpad()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                dpadX = -1f
                applyDpad()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                dpadX = 1f
                applyDpad()
                true
            }
            else -> false
        }
    }

    private fun handleMenuKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isNavDirection = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> true
            else -> false
        }
        if (event.repeatCount > 0 && !isNavDirection) {
            return isMenuHandledKey(keyCode)
        }
        if (event.repeatCount > 0 && event.repeatCount % 2 != 0) {
            return true
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                _menuActions.tryEmit(MenuNavAction.UP)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                _menuActions.tryEmit(MenuNavAction.DOWN)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> {
                _menuActions.tryEmit(MenuNavAction.LEFT)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> {
                _menuActions.tryEmit(MenuNavAction.RIGHT)
                true
            }
            // Confirm / Select: Gamepad A / R2 / D-Pad Center / L3 / R3 / Enter / Space
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR -> {
                _menuActions.tryEmit(MenuNavAction.SELECT)
                true
            }
            // Quick Start / Resume: Start / Menu
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU -> {
                _menuActions.tryEmit(MenuNavAction.START_GAME)
                true
            }
            // Recenter 3DoF Head Tracking: Y / Select / Mode / R
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_R -> {
                recenterView()
                true
            }
            // Toggle Audio: X / M
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_M -> {
                _menuActions.tryEmit(MenuNavAction.TOGGLE_AUDIO)
                true
            }
            // Back / Resume: B / Back / Escape
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> {
                when (engine.phase) {
                    GamePhase.PAUSED -> {
                        engine.resumeGame()
                        _menuActions.tryEmit(MenuNavAction.BACK)
                        true
                    }
                    GamePhase.GAME_OVER, GamePhase.VICTORY -> {
                        _menuActions.tryEmit(MenuNavAction.BACK)
                        true
                    }
                    GamePhase.MENU -> keyCode == KeyEvent.KEYCODE_BUTTON_B
                    GamePhase.PLAYING, GamePhase.JUMPSCARE -> false
                }
            }
            else -> false
        }
    }

    fun handleKeyUp(keyCode: Int): Boolean {
        if (engine.phase != GamePhase.PLAYING) {
            return isMenuHandledKey(keyCode)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                dpadY = 0f
                applyDpad()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                dpadX = 0f
                applyDpad()
                true
            }
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> {
                engine.lookTurnRate = 0f
                true
            }
            else -> isGameplayKey(keyCode)
        }
    }

    fun handleGenericMotion(event: MotionEvent): Boolean {
        val src = event.source
        val isGamepadMotion =
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (src and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD

        if (!isGamepadMotion) return false

        val device = event.device
        val normX = normalizeAxis(event, device, MotionEvent.AXIS_X)
        val normY = normalizeAxis(event, device, MotionEvent.AXIS_Y)

        if (engine.phase != GamePhase.PLAYING) {
            return handleMenuMotion(event, normX, normY)
        }

        val hatX = deadzone(event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val hatY = deadzone(event.getAxisValue(MotionEvent.AXIS_HAT_Y))

        val rawStrafe = deadzone(if (abs(normX) > abs(hatX)) normX else hatX)
        // Standard gamepad stick Y is negative when pushed forward
        val rawForward = deadzone(if (abs(normY) > abs(hatY)) -normY else -hatY)

        val strafe = shapeResponseCurve(rawStrafe)
        val forward = shapeResponseCurve(rawForward)

        if (strafe != 0f || forward != 0f) {
            engine.moveStrafeInput = strafe
            engine.moveForwardInput = forward
        } else {
            engine.moveStrafeInput = dpadX
            engine.moveForwardInput = dpadY
        }

        // Right analog stick yaw & pitch (supports Xbox/DualSense Z/RZ and RX/RY mappings)
        val rawYaw = pickAxisWithFallback(event, device, src, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX)
        val rawPitch = pickAxisWithFallback(event, device, src, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY)
        val lookYaw = shapeResponseCurve(deadzone(rawYaw))
        val lookPitch = shapeResponseCurve(deadzone(rawPitch))
        engine.lookTurnRate = lookYaw * 2.25f
        engine.lookPitchRate = -lookPitch * 1.55f

        // Analog R2 / RT -> Interact
        val rTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        if (rTrigger > 0.55f && !rTriggerLatched) {
            rTriggerLatched = true
            engine.interact()
        } else if (rTrigger < 0.25f) {
            rTriggerLatched = false
        }

        // Analog L2 / LT -> Flashlight
        val lTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        if (lTrigger > 0.55f && !lTriggerLatched) {
            lTriggerLatched = true
            engine.toggleFlashlight()
        } else if (lTrigger < 0.25f) {
            lTriggerLatched = false
        }

        return true
    }

    private fun handleMenuMotion(
        event: MotionEvent,
        normX: Float,
        normY: Float
    ): Boolean {
        val device = event.device
        val src = event.source

        // Allow Left Stick, D-Pad Hat, or Right Stick to steer menu focus
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val rightX = pickAxisWithFallback(event, device, src, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX)
        val rightY = pickAxisWithFallback(event, device, src, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY)

        val navX = when {
            abs(hatX) >= abs(normX) && abs(hatX) >= abs(rightX) -> hatX
            abs(normX) >= abs(rightX) -> normX
            else -> rightX
        }
        val navY = when {
            abs(hatY) >= abs(normY) && abs(hatY) >= abs(rightY) -> hatY
            abs(normY) >= abs(rightY) -> normY
            else -> rightY
        }

        val now = SystemClock.uptimeMillis()
        if (abs(navX) > 0.55f || abs(navY) > 0.55f) {
            if (lastMenuNavMs == 0L || now - lastMenuNavMs >= 210L) {
                lastMenuNavMs = now.coerceAtLeast(1L)
                val action = if (abs(navX) >= abs(navY)) {
                    if (navX > 0f) MenuNavAction.RIGHT else MenuNavAction.LEFT
                } else {
                    if (navY > 0f) MenuNavAction.DOWN else MenuNavAction.UP
                }
                _menuActions.tryEmit(action)
            }
        } else if (abs(navX) < 0.28f && abs(navY) < 0.28f) {
            lastMenuNavMs = 0L
        }

        // Analog R2 / RT confirms focused menu control
        val rTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        if (rTrigger > 0.55f && !rTriggerLatched) {
            rTriggerLatched = true
            _menuActions.tryEmit(MenuNavAction.SELECT)
        } else if (rTrigger < 0.25f) {
            rTriggerLatched = false
        }

        // Analog L2 / LT steps back / resumes
        val lTrigger = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        if (lTrigger > 0.55f && !lTriggerLatched) {
            lTriggerLatched = true
            if (engine.phase == GamePhase.PAUSED) {
                engine.resumeGame()
            }
            _menuActions.tryEmit(MenuNavAction.BACK)
        } else if (lTrigger < 0.25f) {
            lTriggerLatched = false
        }

        return true
    }

    private fun pickAxisWithFallback(
        event: MotionEvent,
        device: InputDevice?,
        source: Int,
        primaryAxis: Int,
        fallbackAxis: Int
    ): Float {
        val primary = event.getAxisValue(primaryAxis)
        val fallback = event.getAxisValue(fallbackAxis)
        return when {
            device?.getMotionRange(primaryAxis, source) != null -> primary
            device?.getMotionRange(fallbackAxis, source) != null -> fallback
            abs(primary) >= abs(fallback) -> primary
            else -> fallback
        }
    }

    private fun normalizeAxis(event: MotionEvent, device: InputDevice?, axis: Int): Float {
        val raw = event.getAxisValue(axis)
        val range = device?.getMotionRange(axis, event.source) ?: return raw.coerceIn(-1f, 1f)
        val min = range.min
        val max = range.max
        val span = max - min
        if (span <= 0.001f) return 0f
        val normalized = ((raw - min) / span) * 2f - 1f
        return normalized.coerceIn(-1f, 1f)
    }

    private fun applyDpad() {
        engine.moveStrafeInput = dpadX
        engine.moveForwardInput = dpadY
    }

    private fun deadzone(v: Float): Float = if (abs(v) < 0.16f) 0f else v

    private fun shapeResponseCurve(v: Float): Float {
        if (v == 0f) return 0f
        return (v * (0.45f + 0.55f * abs(v))).coerceIn(-1f, 1f)
    }

    private fun formatShortGamepadLabel(name: String, isBluetooth: Boolean): String {
        val lower = name.lowercase()
        return when {
            lower.contains("xbox") -> "Xbox BT"
            lower.contains("dualsense") -> "DualSense"
            lower.contains("dualshock") || lower.contains("wireless controller") -> "PS Pad"
            lower.contains("8bitdo") -> "8BitDo"
            lower.contains("gamesir") -> "GameSir"
            lower.contains("moga") -> "MOGA BT"
            lower.contains("steelseries") -> "Stratus"
            lower.contains("pro controller") -> "Switch Pro"
            isBluetooth -> "BT Gamepad"
            else -> "Gamepad"
        }
    }

    private fun isMenuHandledKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_MODE,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE -> true
        else -> false
    }

    private fun isGameplayKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_MODE,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_BACK -> true
        else -> false
    }
}
