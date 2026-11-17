package com.example

import android.content.res.Configuration
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.GamePhase
import com.example.gl.HorrorRenderer
import com.example.ui.GamePlayScreen
import com.example.ui.MainMenuScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.SlendrinaGoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullscreenWindow()

        setContent {
            SlendrinaGoTheme {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val records by viewModel.records.collectAsStateWithLifecycle()
                val hud by viewModel.hud.collectAsStateWithLifecycle()
                val headPose by viewModel.headPose.collectAsStateWithLifecycle()
                val gamepadStatus by viewModel.gamepadStatus.collectAsStateWithLifecycle()

                val renderer = remember(viewModel.engine) {
                    HorrorRenderer(
                        engine = viewModel.engine,
                        context = applicationContext,
                        onFrameTick = viewModel::onGlFrame
                    )
                }
                renderer.refreshRate = settings.refreshRate

                LaunchedEffect(settings.refreshRate) {
                    applyPreferredDisplayRefreshRate(settings.refreshRate.hz)
                }

                var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner, glViewRef) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> glViewRef?.onResume()
                            Lifecycle.Event.ON_PAUSE -> glViewRef?.onPause()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                DisposableEffect(Unit) {
                    onDispose {
                        glViewRef?.queueEvent { renderer.release() }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // Persistent OpenGL ES 3.0 3D VR viewport behind both VR Menu and Gameplay
                    AndroidView(
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                isFocusable = false
                                isFocusableInTouchMode = false
                                setEGLContextClientVersion(3)
                                setEGLConfigChooser(8, 8, 8, 0, 16, 0)
                                preserveEGLContextOnPause = true
                                setRenderer(renderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                glViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (hud.phase == GamePhase.MENU) {
                        MainMenuScreen(
                            settings = settings,
                            records = records,
                            headPose = headPose,
                            hasHardwareGyro = viewModel.headTracker.hasHardwareGyro,
                            imuStatus = viewModel.headTracker.imuStatus,
                            gamepadStatus = gamepadStatus,
                            menuActions = viewModel.menuActions,
                            onSelectMap = viewModel::selectMap,
                            onSelectDifficulty = viewModel::selectDifficulty,
                            onToggleHeadTracking = viewModel::toggleHeadTracking,
                            onToggleRefreshRate = viewModel::toggleRefreshRate,
                            onToggleAudio = viewModel::toggleAudio,
                            onRecenter = viewModel::recenterHeadTracking,
                            onStartGame = viewModel::startSelectedGame
                        )
                    } else {
                        GamePlayScreen(
                            engine = viewModel.engine,
                            hud = hud,
                            settings = settings,
                            gamepadStatus = gamepadStatus,
                            menuActions = viewModel.menuActions,
                            onRecenter = viewModel::recenterHeadTracking,
                            onPauseToggle = viewModel::togglePause,
                            onResume = viewModel::resumeGame,
                            onRestart = viewModel::restartCurrentGame,
                            onExitToMenu = viewModel::returnToMenu
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBarsForVr()
        syncDisplayRotation()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBarsForVr()
        syncDisplayRotation()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsForVr()
            syncDisplayRotation()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStartSession()
        syncDisplayRotation()
    }

    override fun onStop() {
        super.onStop()
        viewModel.onStopSession()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.gamepadHandler.dispatchKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (viewModel.gamepadHandler.handleGenericMotion(ev)) return true
        return super.dispatchGenericMotionEvent(ev)
    }

    private fun configureFullscreenWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBarsForVr()
    }

    private fun hideSystemBarsForVr() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }

    @Suppress("DEPRECATION")
    private fun syncDisplayRotation() {
        val rot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation
        } else {
            windowManager.defaultDisplay?.rotation
        }
        viewModel.updateDisplayRotation(rot)
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredDisplayRefreshRate(targetHz: Int) {
        try {
            val activeDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            }
            val modes = activeDisplay?.supportedModes
            val bestMode = modes?.maxWithOrNull(
                compareBy<Display.Mode>(
                    { it.physicalWidth.toLong() * it.physicalHeight.toLong() },
                    { -kotlin.math.abs(it.refreshRate - targetHz.toFloat()) }
                )
            )
            window.attributes = window.attributes.apply {
                if (bestMode != null) {
                    preferredDisplayModeId = bestMode.modeId
                }
                preferredRefreshRate = targetHz.toFloat()
            }
        } catch (_: Throwable) {
        }
    }
}
