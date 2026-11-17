package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlayerSettings
import com.example.data.RenderMode
import com.example.game.GameEngine
import com.example.game.GamePhase
import com.example.game.HudSnapshot
import com.example.gl.ProceduralTextures
import com.example.input.GamepadStatus
import com.example.input.MenuNavAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.example.ui.theme.BloodCrimson
import com.example.ui.theme.BorderSmoke
import com.example.ui.theme.CryptSurface
import com.example.ui.theme.MistGrey
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentAsh
import com.example.ui.theme.SpectralTeal
import com.example.ui.theme.TarnishedBrass
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GamePlayScreen(
    engine: GameEngine,
    hud: HudSnapshot,
    settings: PlayerSettings,
    gamepadStatus: GamepadStatus = GamepadStatus(),
    menuActions: Flow<MenuNavAction> = emptyFlow(),
    onToggleRenderMode: () -> Unit,
    onRecenter: () -> Unit,
    onPauseToggle: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit
) {
    BackHandler {
        when (hud.phase) {
            GamePhase.PLAYING -> onPauseToggle()
            GamePhase.PAUSED -> onResume()
            GamePhase.GAME_OVER, GamePhase.VICTORY -> onExitToMenu()
            else -> onExitToMenu()
        }
    }

    val context = LocalContext.current
    val slendrinaPortrait = remember(context) {
        ProceduralTextures.loadSlendrinaPortraitBitmap(context)?.asImageBitmap()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Stare / Jumpscare Crimson Edge Vignette
        if (hud.stareWarning > 0.05f || hud.phase == GamePhase.JUMPSCARE) {
            val alpha = if (hud.phase == GamePhase.JUMPSCARE) 0.72f else (hud.stareWarning * 0.55f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.Transparent, BloodCrimson.copy(alpha = alpha))
                        )
                    )
            )
        }

        // Touch Look Zone (active during gameplay across center/right area)
        if (hud.phase == GamePhase.PLAYING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(settings.lookSensitivity) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val yawScale = 0.0045f * settings.lookSensitivity
                            val pitchScale = 0.0036f * settings.lookSensitivity
                            engine.addLookDelta(
                                deltaYawRad = dragAmount.x * yawScale,
                                deltaPitchRad = -dragAmount.y * pitchScale
                            )
                        }
                    }
            )
        }

        // Render Stereoscopic SBS HUD or 2D Flat HUD
        if (hud.phase == GamePhase.PLAYING) {
            if (settings.renderMode == RenderMode.VR_SBS) {
                Row(modifier = Modifier.fillMaxSize()) {
                    VrEyeHudOverlay(hud = hud, gamepadStatus = gamepadStatus, modifier = Modifier.weight(1f).fillMaxHeight())
                    VrEyeHudOverlay(hud = hud, gamepadStatus = gamepadStatus, modifier = Modifier.weight(1f).fillMaxHeight())
                }
                // Quick corner toggles so touchscreen / emulator users can switch to 2D or pause easily
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallHudIconButton(
                        icon = Icons.Default.Smartphone,
                        description = "Switch to 2D",
                        onClick = onToggleRenderMode,
                        modifier = Modifier.testTag("hud_toggle_render_mode")
                    )
                    SmallHudIconButton(
                        icon = Icons.Default.CenterFocusStrong,
                        description = "Recenter 3DoF",
                        onClick = onRecenter,
                        modifier = Modifier.testTag("hud_recenter")
                    )
                    SmallHudIconButton(
                        icon = Icons.Default.Pause,
                        description = "Pause",
                        onClick = onPauseToggle,
                        modifier = Modifier.testTag("hud_pause")
                    )
                }
            } else {
                Flat2DHudOverlay(
                    hud = hud,
                    engine = engine,
                    onToggleRenderMode = onToggleRenderMode,
                    onRecenter = onRecenter,
                    onPauseToggle = onPauseToggle
                )
            }
        }

        // Pause / Game Over / Victory Overlays (stereoscopic SBS in VR mode)
        when (hud.phase) {
            GamePhase.PAUSED -> PauseOverlay(
                hud = hud,
                renderMode = settings.renderMode,
                menuActions = menuActions,
                onResume = onResume,
                onToggleRenderMode = onToggleRenderMode,
                onRecenter = {
                    onRecenter()
                    onResume()
                },
                onRestart = onRestart,
                onExitToMenu = onExitToMenu
            )
            GamePhase.GAME_OVER -> EndGameOverlay(
                victory = false,
                hud = hud,
                renderMode = settings.renderMode,
                slendrinaPortrait = slendrinaPortrait,
                menuActions = menuActions,
                onRestart = onRestart,
                onExitToMenu = onExitToMenu
            )
            GamePhase.VICTORY -> EndGameOverlay(
                victory = true,
                hud = hud,
                renderMode = settings.renderMode,
                slendrinaPortrait = slendrinaPortrait,
                menuActions = menuActions,
                onRestart = onRestart,
                onExitToMenu = onExitToMenu
            )
            else -> Unit
        }
    }
}

@Composable
private fun VrEyeHudOverlay(
    hud: HudSnapshot,
    gamepadStatus: GamepadStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 28.dp, vertical = 22.dp)
    ) {
        // Minimal top status strip per eye
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BOOKS ${hud.booksCollected}/${hud.totalBooks}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hud.exitUnlocked) SpectralTeal else TarnishedBrass,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "KEYS ${hud.keysHeld}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentAsh
                )
                Text(
                    text = formatClock(hud.elapsedSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MistGrey
                )
            }
            LinearProgressIndicator(
                progress = { (hud.sanity / 100f).coerceIn(0f, 1f) },
                color = if (hud.sanity < 35f) BloodCrimson else SpectralTeal,
                trackColor = CryptSurface.copy(alpha = 0.65f),
                modifier = Modifier
                    .width(120.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }

        // Interaction Prompt below gaze reticle
        if (hud.promptText != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianVoid.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, TarnishedBrass.copy(alpha = 0.7f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            ) {
                Text(
                    text = hud.promptText,
                    style = MaterialTheme.typography.labelSmall,
                    color = ParchmentAsh,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        } else if (hud.toastText != null) {
            Text(
                text = hud.toastText,
                style = MaterialTheme.typography.labelSmall,
                color = TarnishedBrass,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            )
        }

        if (gamepadStatus.connected) {
            Text(
                text = gamepadStatus.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = SpectralTeal.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun Flat2DHudOverlay(
    hud: HudSnapshot,
    engine: GameEngine,
    onToggleRenderMode: () -> Unit,
    onRecenter: () -> Unit,
    onPauseToggle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        // Top-Left Objectives & Sanity
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianVoid.copy(alpha = 0.74f),
            border = BorderStroke(1.dp, BorderSmoke),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Books", tint = if (hud.exitUnlocked) SpectralTeal else BloodCrimson, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${hud.booksCollected}/${hud.totalBooks}",
                            style = MaterialTheme.typography.titleMedium,
                            color = ParchmentAsh
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Keys", tint = TarnishedBrass, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${hud.keysHeld}",
                            style = MaterialTheme.typography.titleMedium,
                            color = ParchmentAsh
                        )
                    }
                    Text(
                        text = formatClock(hud.elapsedSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        color = MistGrey
                    )
                }

                LinearProgressIndicator(
                    progress = { (hud.sanity / 100f).coerceIn(0f, 1f) },
                    color = if (hud.sanity < 35f) BloodCrimson else SpectralTeal,
                    trackColor = CryptSurface,
                    modifier = Modifier
                        .width(170.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        // Top-Right Action Bar
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallHudIconButton(
                icon = Icons.Default.RemoveRedEye,
                description = "Switch to VR SBS",
                onClick = onToggleRenderMode,
                modifier = Modifier.testTag("hud_toggle_render_mode")
            )
            SmallHudIconButton(
                icon = Icons.Default.CenterFocusStrong,
                description = "Recenter 3DoF",
                onClick = onRecenter,
                modifier = Modifier.testTag("hud_recenter")
            )
            SmallHudIconButton(
                icon = Icons.Default.Pause,
                description = "Pause",
                onClick = onPauseToggle,
                modifier = Modifier.testTag("hud_pause")
            )
        }

        // Center-Bottom Prompt / Toast
        val banner = hud.promptText ?: hud.toastText
        if (banner != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianVoid.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, if (hud.promptText != null) TarnishedBrass else BorderSmoke),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = banner,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (hud.promptText != null) TarnishedBrass else ParchmentAsh,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }

        // Bottom-Left Virtual Thumbstick for 2D / Emulator Play
        VirtualThumbstick(
            onMove = { strafe, forward ->
                engine.moveStrafeInput = strafe
                engine.moveForwardInput = forward
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )

        // Bottom-Right Action Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = CryptSurface.copy(alpha = 0.86f),
                border = BorderStroke(1.5.dp, if (hud.flashlightOn) TarnishedBrass else BorderSmoke),
                modifier = Modifier
                    .size(56.dp)
                    .clickable { engine.toggleFlashlight() }
                    .testTag("hud_flashlight_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (hud.flashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        contentDescription = "Toggle flashlight",
                        tint = if (hud.flashlightOn) TarnishedBrass else MistGrey
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (hud.promptText != null) BloodCrimson else CryptSurface.copy(alpha = 0.86f),
                border = BorderStroke(2.dp, if (hud.promptText != null) ParchmentAsh else BorderSmoke),
                modifier = Modifier
                    .size(66.dp)
                    .clickable { engine.interact() }
                    .testTag("hud_interact_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Interact",
                        tint = ParchmentAsh,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualThumbstick(
    onMove: (strafe: Float, forward: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 95f

    Box(
        modifier = modifier
            .size(124.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val next = knobOffset + dragAmount
                    val len = hypot(next.x, next.y)
                    val clamped = if (len > maxRadiusPx) next * (maxRadiusPx / len) else next
                    knobOffset = clamped
                    onMove(
                        (clamped.x / maxRadiusPx).coerceIn(-1f, 1f),
                        (-clamped.y / maxRadiusPx).coerceIn(-1f, 1f)
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color(0x55141015),
                radius = size.minDimension * 0.46f,
                center = center
            )
            drawCircle(
                color = Color(0xAA3A2E3D),
                radius = size.minDimension * 0.46f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
            )
            drawCircle(
                color = Color(0xCCEDE4DC),
                radius = size.minDimension * 0.18f,
                center = center + knobOffset
            )
        }
    }
}

@Composable
private fun SmallHudIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianVoid.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, BorderSmoke),
        modifier = modifier.size(48.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(imageVector = icon, contentDescription = description, tint = ParchmentAsh)
        }
    }
}

private val PAUSE_GRID = listOf(
    listOf("resume"),
    listOf("mode"),
    listOf("recenter"),
    listOf("restart", "menu")
)

@Composable
private fun PauseOverlay(
    hud: HudSnapshot,
    renderMode: RenderMode,
    menuActions: Flow<MenuNavAction>,
    onResume: () -> Unit,
    onToggleRenderMode: () -> Unit,
    onRecenter: () -> Unit,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit
) {
    var focusedKey by remember { mutableStateOf("resume") }

    LaunchedEffect(menuActions) {
        menuActions.collect { action ->
            var r = PAUSE_GRID.indexOfFirst { it.contains(focusedKey) }.coerceAtLeast(0)
            var c = PAUSE_GRID[r].indexOf(focusedKey).coerceAtLeast(0)
            when (action) {
                MenuNavAction.UP -> {
                    r = if (r > 0) r - 1 else PAUSE_GRID.lastIndex
                    focusedKey = PAUSE_GRID[r][c.coerceIn(0, PAUSE_GRID[r].lastIndex)]
                }
                MenuNavAction.DOWN -> {
                    r = if (r < PAUSE_GRID.lastIndex) r + 1 else 0
                    focusedKey = PAUSE_GRID[r][c.coerceIn(0, PAUSE_GRID[r].lastIndex)]
                }
                MenuNavAction.LEFT -> {
                    val row = PAUSE_GRID[r]
                    c = if (c > 0) c - 1 else row.lastIndex
                    focusedKey = row[c]
                }
                MenuNavAction.RIGHT -> {
                    val row = PAUSE_GRID[r]
                    c = if (c < row.lastIndex) c + 1 else 0
                    focusedKey = row[c]
                }
                MenuNavAction.SELECT -> when (focusedKey) {
                    "resume" -> onResume()
                    "mode" -> onToggleRenderMode()
                    "recenter" -> onRecenter()
                    "restart" -> onRestart()
                    "menu" -> onExitToMenu()
                }
                MenuNavAction.BACK, MenuNavAction.START_GAME -> onResume()
                MenuNavAction.RECENTER -> onRecenter()
                MenuNavAction.TOGGLE_AUDIO -> Unit
            }
        }
    }

    if (renderMode == RenderMode.VR_SBS) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid.copy(alpha = 0.78f))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                PauseCard(
                    hud = hud,
                    renderMode = renderMode,
                    compact = true,
                    isPrimaryFocusEye = true,
                    focusedKey = focusedKey,
                    onResume = onResume,
                    onToggleRenderMode = onToggleRenderMode,
                    onRecenter = onRecenter,
                    onRestart = onRestart,
                    onExitToMenu = onExitToMenu,
                    modifier = Modifier.offset(x = (-2).dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(ObsidianVoid)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                PauseCard(
                    hud = hud,
                    renderMode = renderMode,
                    compact = true,
                    isPrimaryFocusEye = false,
                    focusedKey = focusedKey,
                    onResume = onResume,
                    onToggleRenderMode = onToggleRenderMode,
                    onRecenter = onRecenter,
                    onRestart = onRestart,
                    onExitToMenu = onExitToMenu,
                    modifier = Modifier.offset(x = 2.dp)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid.copy(alpha = 0.84f)),
            contentAlignment = Alignment.Center
        ) {
            PauseCard(
                hud = hud,
                renderMode = renderMode,
                compact = false,
                isPrimaryFocusEye = true,
                focusedKey = focusedKey,
                onResume = onResume,
                onToggleRenderMode = onToggleRenderMode,
                onRecenter = onRecenter,
                onRestart = onRestart,
                onExitToMenu = onExitToMenu,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PauseCard(
    hud: HudSnapshot,
    renderMode: RenderMode,
    compact: Boolean,
    isPrimaryFocusEye: Boolean,
    focusedKey: String,
    onResume: () -> Unit,
    onToggleRenderMode: () -> Unit,
    onRecenter: () -> Unit,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun Modifier.vrControl(): Modifier = this.focusProperties { canFocus = false }
    fun Modifier.eyeTag(tag: String): Modifier = if (isPrimaryFocusEye) this.testTag(tag) else this

    val cardScale = if (compact) 0.84f else 1f
    val cardWidthMod = if (compact) {
        Modifier.widthIn(max = 280.dp).fillMaxWidth(0.76f)
    } else {
        Modifier.widthIn(max = 380.dp).fillMaxWidth()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CryptSurface.copy(alpha = 0.92f),
        border = BorderStroke(1.5.dp, BorderSmoke),
        modifier = modifier
            .then(cardWidthMod)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PAUSED",
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                color = ParchmentAsh
            )
            Text(
                text = "${hud.map.title} • ${hud.booksCollected}/${hud.totalBooks} Books • ${formatClock(hud.elapsedSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = TarnishedBrass,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(containerColor = BloodCrimson),
                border = if (focusedKey == "resume") BorderStroke(2.dp, TarnishedBrass) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .vrControl()
                    .eyeTag("pause_resume_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("RESUME", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onToggleRenderMode,
                border = BorderStroke(
                    if (focusedKey == "mode") 2.dp else 1.dp,
                    if (focusedKey == "mode") TarnishedBrass else BorderSmoke
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .vrControl()
                    .eyeTag("pause_switch_mode_button")
            ) {
                Text(
                    text = "SWITCH TO ${if (renderMode == RenderMode.VR_SBS) "2D FLAT" else "VR SBS"}",
                    color = ParchmentAsh,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = onRecenter,
                border = BorderStroke(
                    if (focusedKey == "recenter") 2.dp else 1.dp,
                    if (focusedKey == "recenter") TarnishedBrass else BorderSmoke
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .vrControl()
                    .eyeTag("pause_recenter_button")
            ) {
                Text("RECENTER 3DOF VIEW", color = ParchmentAsh, maxLines = 1)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    border = BorderStroke(
                        if (focusedKey == "restart") 2.dp else 1.dp,
                        if (focusedKey == "restart") TarnishedBrass else BorderSmoke
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .vrControl()
                        .eyeTag("pause_restart_button")
                ) {
                    Text("RESTART", color = TarnishedBrass, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onExitToMenu,
                    border = BorderStroke(
                        if (focusedKey == "menu") 2.dp else 1.dp,
                        if (focusedKey == "menu") TarnishedBrass else BorderSmoke
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .vrControl()
                        .eyeTag("pause_menu_button")
                ) {
                    Text("VR MENU", color = MistGrey, maxLines = 1)
                }
            }
            Text(
                text = "L-Stick: Move • R-Stick: Look • A/R2: Act • X/L2: Light",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MistGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EndGameOverlay(
    victory: Boolean,
    hud: HudSnapshot,
    renderMode: RenderMode,
    slendrinaPortrait: ImageBitmap?,
    menuActions: Flow<MenuNavAction>,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit
) {
    var focusedKey by remember { mutableStateOf("restart") }

    LaunchedEffect(menuActions) {
        menuActions.collect { action ->
            when (action) {
                MenuNavAction.LEFT, MenuNavAction.RIGHT,
                MenuNavAction.UP, MenuNavAction.DOWN -> {
                    focusedKey = if (focusedKey == "restart") "menu" else "restart"
                }
                MenuNavAction.SELECT -> {
                    if (focusedKey == "restart") onRestart() else onExitToMenu()
                }
                MenuNavAction.BACK -> onExitToMenu()
                MenuNavAction.START_GAME -> onRestart()
                else -> Unit
            }
        }
    }

    val backdropColor = if (victory) ObsidianVoid.copy(alpha = 0.84f) else Color(0xDD1A0407)

    if (renderMode == RenderMode.VR_SBS) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                EndGameCard(
                    victory = victory,
                    hud = hud,
                    slendrinaPortrait = slendrinaPortrait,
                    compact = true,
                    isPrimaryFocusEye = true,
                    focusedKey = focusedKey,
                    onRestart = onRestart,
                    onExitToMenu = onExitToMenu,
                    modifier = Modifier.offset(x = (-2).dp)
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(ObsidianVoid)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                EndGameCard(
                    victory = victory,
                    hud = hud,
                    slendrinaPortrait = slendrinaPortrait,
                    compact = true,
                    isPrimaryFocusEye = false,
                    focusedKey = focusedKey,
                    onRestart = onRestart,
                    onExitToMenu = onExitToMenu,
                    modifier = Modifier.offset(x = 2.dp)
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropColor),
            contentAlignment = Alignment.Center
        ) {
            EndGameCard(
                victory = victory,
                hud = hud,
                slendrinaPortrait = slendrinaPortrait,
                compact = false,
                isPrimaryFocusEye = true,
                focusedKey = focusedKey,
                onRestart = onRestart,
                onExitToMenu = onExitToMenu,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EndGameCard(
    victory: Boolean,
    hud: HudSnapshot,
    slendrinaPortrait: ImageBitmap?,
    compact: Boolean,
    isPrimaryFocusEye: Boolean,
    focusedKey: String,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    fun Modifier.vrControl(): Modifier = this.focusProperties { canFocus = false }
    fun Modifier.eyeTag(tag: String): Modifier = if (isPrimaryFocusEye) this.testTag(tag) else this

    val cardScale = if (compact) 0.84f else 1f
    val cardWidthMod = if (compact) {
        Modifier.widthIn(max = 286.dp).fillMaxWidth(0.76f)
    } else {
        Modifier.widthIn(max = 400.dp).fillMaxWidth()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CryptSurface.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, if (victory) SpectralTeal else BloodCrimson),
        modifier = modifier
            .then(cardWidthMod)
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 12.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp)
        ) {
            if (!victory && slendrinaPortrait != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianVoid,
                    border = BorderStroke(2.dp, BloodCrimson),
                    modifier = Modifier
                        .size(if (compact) 52.dp else 78.dp)
                        .eyeTag("end_slendrina_portrait")
                ) {
                    Image(
                        bitmap = slendrinaPortrait,
                        contentDescription = "Slendrina Chihuahua nun",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = if (victory) "YOU ESCAPED" else "CAUGHT BY SLENDRINA",
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                color = if (victory) SpectralTeal else BloodCrimson,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (victory) {
                    "Escaped ${hud.map.title} (${hud.difficulty.label}) in ${formatClock(hud.elapsedSeconds)}!"
                } else {
                    "Collected ${hud.booksCollected}/${hud.totalBooks} books in ${hud.map.title}. Look away the moment she appears!"
                },
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = ParchmentAsh,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (victory) SpectralTeal else BloodCrimson,
                        contentColor = if (victory) ObsidianVoid else ParchmentAsh
                    ),
                    border = if (focusedKey == "restart") BorderStroke(2.dp, TarnishedBrass) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .vrControl()
                        .eyeTag("end_restart_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("RETRY", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onExitToMenu,
                    border = BorderStroke(
                        if (focusedKey == "menu") 2.dp else 1.dp,
                        if (focusedKey == "menu") TarnishedBrass else BorderSmoke
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .vrControl()
                        .eyeTag("end_menu_button")
                ) {
                    Text("VR MENU", color = ParchmentAsh, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}
