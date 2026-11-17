package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.HorrorAudioEngine
import com.example.data.Difficulty
import com.example.data.GameMap
import com.example.data.GamePreferencesRepository
import com.example.data.GameRecords
import com.example.data.PlayerSettings
import com.example.data.VrRefreshRate
import com.example.game.GameEngine
import com.example.game.GamePhase
import com.example.game.HudSnapshot
import com.example.input.BluetoothGamepadHandler
import com.example.input.HeadTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HeadPose(
    val yawRad: Float = 0f,
    val pitchRad: Float = 0f,
    val rollRad: Float = 0f
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GamePreferencesRepository(application)
    val audioEngine = HorrorAudioEngine()
    val headTracker = HeadTracker(application)
    val engine = GameEngine(audioEvents = audioEngine)

    val gamepadHandler = BluetoothGamepadHandler(
        context = application,
        engine = engine,
        onRecenter = { recenterHeadTracking() },
        onTogglePause = { togglePause() }
    )

    val gamepadStatus = gamepadHandler.status
    val menuActions = gamepadHandler.menuActions

    val settings: StateFlow<PlayerSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerSettings())

    val records: StateFlow<GameRecords> = repository.recordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GameRecords())

    private val _hud = MutableStateFlow(HudSnapshot())
    val hud: StateFlow<HudSnapshot> = _hud.asStateFlow()

    private val _headPose = MutableStateFlow(HeadPose())
    val headPose: StateFlow<HeadPose> = _headPose.asStateFlow()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var lastHudSyncMs = 0L
    private var recordedVictoryForRun = false
    private var lastPhase = GamePhase.MENU

    init {
        viewModelScope.launch {
            settings.collect { s ->
                audioEngine.enabled = s.audioEnabled
                headTracker.enabled = s.headTrackingEnabled
                headTracker.sensitivity = s.lookSensitivity
                if (engine.phase == GamePhase.MENU) {
                    engine.previewMapInMenu(s.selectedMap)
                }
            }
        }
    }

    fun onStartSession() {
        headTracker.start()
        gamepadHandler.startListening()
        if (engine.phase != GamePhase.MENU) {
            audioEngine.start()
        }
    }

    fun onStopSession() {
        headTracker.stop()
        gamepadHandler.stopListening()
        audioEngine.stop()
    }

    fun selectMap(map: GameMap) {
        engine.previewMapInMenu(map)
        viewModelScope.launch { repository.setSelectedMap(map) }
    }

    fun selectDifficulty(difficulty: Difficulty) {
        viewModelScope.launch { repository.setSelectedDifficulty(difficulty) }
    }

    fun toggleRefreshRate() {
        val next = if (settings.value.refreshRate == VrRefreshRate.HZ_72) VrRefreshRate.HZ_60 else VrRefreshRate.HZ_72
        viewModelScope.launch { repository.setRefreshRate(next) }
    }

    fun toggleHeadTracking() {
        val next = !settings.value.headTrackingEnabled
        viewModelScope.launch { repository.setHeadTrackingEnabled(next) }
    }

    fun toggleAudio() {
        val next = !settings.value.audioEnabled
        viewModelScope.launch { repository.setAudioEnabled(next) }
    }

    fun startSelectedGame() {
        val s = settings.value
        recordedVictoryForRun = false
        headTracker.recenter()
        audioEngine.start()
        engine.startNewGame(s.selectedMap, s.selectedDifficulty)
        _hud.value = engine.snapshot()
    }

    fun restartCurrentGame() {
        val snap = engine.snapshot()
        recordedVictoryForRun = false
        headTracker.recenter()
        audioEngine.start()
        engine.startNewGame(snap.map, snap.difficulty)
        _hud.value = engine.snapshot()
    }

    fun togglePause() {
        when (engine.phase) {
            GamePhase.PLAYING -> engine.pauseGame()
            GamePhase.PAUSED -> engine.resumeGame()
            else -> Unit
        }
        _hud.value = engine.snapshot()
    }

    fun resumeGame() {
        engine.resumeGame()
        _hud.value = engine.snapshot()
    }

    fun returnToMenu() {
        engine.returnToMenu()
        engine.previewMapInMenu(settings.value.selectedMap)
        audioEngine.tensionLevel = 0f
        audioEngine.stareLevel = 0f
        audioEngine.stop()
        _hud.value = engine.snapshot()
    }

    fun recenterHeadTracking() {
        headTracker.recenter()
        _headPose.value = HeadPose()
        pulseVibration(40L)
    }

    fun updateDisplayRotation(rotation: Int?) {
        headTracker.updateDisplayRotation(rotation)
    }

    /**
     * Called on every GL render frame to sync 3DoF headset & controller sensors, audio tension, and throttled HUD updates.
     */
    fun onGlFrame() {
        if (settings.value.headTrackingEnabled) {
            engine.headYawOffsetRad = headTracker.yawOffsetRad
            engine.headPitchOffsetRad = headTracker.pitchOffsetRad
            engine.headRollRad = headTracker.rollRad
            _headPose.value = HeadPose(
                yawRad = headTracker.yawOffsetRad,
                pitchRad = headTracker.pitchOffsetRad,
                rollRad = headTracker.rollRad
            )
        } else {
            engine.headYawOffsetRad = 0f
            engine.headPitchOffsetRad = 0f
            engine.headRollRad = 0f
            _headPose.value = HeadPose()
        }

        val currentPhase = engine.phase

        if (currentPhase == GamePhase.PLAYING) {
            audioEngine.tensionLevel = (engine.booksCollected / 6f).coerceIn(0f, 1f)
            audioEngine.stareLevel = engine.stareIntensity
        } else {
            audioEngine.tensionLevel = 0f
            audioEngine.stareLevel = if (currentPhase == GamePhase.JUMPSCARE) 1f else 0f
        }

        if (currentPhase == GamePhase.JUMPSCARE && lastPhase != GamePhase.JUMPSCARE) {
            pulseVibration(320L)
        }
        if (currentPhase == GamePhase.VICTORY && !recordedVictoryForRun) {
            recordedVictoryForRun = true
            val clearTime = engine.elapsedTime.toInt().coerceAtLeast(1)
            val map = engine.level.map
            viewModelScope.launch { repository.recordEscape(map, clearTime) }
        }
        lastPhase = currentPhase

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastHudSyncMs >= 65L) {
            lastHudSyncMs = nowMs
            _hud.value = engine.snapshot()
        }
    }

    private fun pulseVibration(durationMs: Long) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMs)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        onStopSession()
    }
}
