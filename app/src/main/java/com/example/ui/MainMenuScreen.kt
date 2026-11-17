package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Difficulty
import com.example.data.GameMap
import com.example.data.GameRecords
import com.example.data.PlayerSettings
import com.example.data.VrRefreshRate
import com.example.gl.ProceduralTextures
import com.example.input.GamepadStatus
import com.example.input.ImuStatus
import com.example.input.MenuNavAction
import com.example.ui.theme.BloodCrimson
import com.example.ui.theme.BorderSmoke
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CryptSurface
import com.example.ui.theme.CryptSurfaceElevated
import com.example.ui.theme.MistGrey
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentAsh
import com.example.ui.theme.SpectralTeal
import com.example.ui.theme.TarnishedBrass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.math.roundToInt

private val MENU_GRID = listOf(
    listOf("hz", "recenter", "audio"),
    listOf("map_house", "map_yard", "map_hotel"),
    listOf("diff_easy", "diff_medium", "diff_hard", "gyro"),
    listOf("start")
)

@Composable
fun MainMenuScreen(
    settings: PlayerSettings,
    records: GameRecords,
    headPose: HeadPose = HeadPose(),
    hasHardwareGyro: Boolean,
    imuStatus: ImuStatus = ImuStatus(
        hasGyroscope = hasHardwareGyro,
        hasAccelerometer = hasHardwareGyro,
        hasMagnetometer = hasHardwareGyro,
        using9DofCompassFusion = hasHardwareGyro
    ),
    gamepadStatus: GamepadStatus = GamepadStatus(),
    menuActions: Flow<MenuNavAction> = emptyFlow(),
    onSelectMap: (GameMap) -> Unit,
    onSelectDifficulty: (Difficulty) -> Unit,
    onToggleHeadTracking: () -> Unit,
    onToggleRefreshRate: () -> Unit = {},
    onToggleAudio: () -> Unit,
    onRecenter: () -> Unit,
    onStartGame: () -> Unit
) {
    // 2D cursor across the VR menu grid driven by Bluetooth Gamepad & 3DoF Head Gaze
    var focusedItemKey by remember { mutableStateOf("start") }
    var lastGazeYaw by remember { mutableStateOf(headPose.yawRad) }
    var lastGazePitch by remember { mutableStateOf(headPose.pitchRad) }

    val context = LocalContext.current
    val slendrinaPortrait = remember(context) {
        ProceduralTextures.loadSlendrinaPortraitBitmap(context)?.asImageBitmap()
    }

    val leftEyeBounds = remember { mutableStateOf<Rect?>(null) }
    val leftItemBounds = remember { mutableStateMapOf<String, Rect>() }

    fun activateKey(key: String) {
        when (key) {
            "hz" -> onToggleRefreshRate()
            "recenter" -> onRecenter()
            "audio" -> onToggleAudio()
            "map_house" -> onSelectMap(GameMap.HOUSE)
            "map_yard" -> onSelectMap(GameMap.YARD)
            "map_hotel" -> onSelectMap(GameMap.HOTEL)
            "diff_easy" -> onSelectDifficulty(Difficulty.EASY)
            "diff_medium" -> onSelectDifficulty(Difficulty.MEDIUM)
            "diff_hard" -> onSelectDifficulty(Difficulty.HARD)
            "gyro" -> onToggleHeadTracking()
            "start" -> onStartGame()
        }
    }

    fun moveFocus(action: MenuNavAction) {
        var rowIdx = MENU_GRID.indexOfFirst { it.contains(focusedItemKey) }
        if (rowIdx < 0) rowIdx = 3
        var colIdx = MENU_GRID[rowIdx].indexOf(focusedItemKey).coerceAtLeast(0)

        when (action) {
            MenuNavAction.LEFT -> {
                val row = MENU_GRID[rowIdx]
                colIdx = if (colIdx > 0) colIdx - 1 else row.lastIndex
                focusedItemKey = row[colIdx]
            }
            MenuNavAction.RIGHT -> {
                val row = MENU_GRID[rowIdx]
                colIdx = if (colIdx < row.lastIndex) colIdx + 1 else 0
                focusedItemKey = row[colIdx]
            }
            MenuNavAction.UP -> {
                val nextRow = if (rowIdx > 0) rowIdx - 1 else MENU_GRID.lastIndex
                val ratio = if (MENU_GRID[rowIdx].size <= 1) 0.4f else colIdx.toFloat() / MENU_GRID[rowIdx].lastIndex
                val targetCol = (ratio * MENU_GRID[nextRow].lastIndex).roundToInt()
                    .coerceIn(0, MENU_GRID[nextRow].lastIndex)
                focusedItemKey = MENU_GRID[nextRow][targetCol]
            }
            MenuNavAction.DOWN -> {
                val nextRow = if (rowIdx < MENU_GRID.lastIndex) rowIdx + 1 else 0
                val ratio = if (MENU_GRID[rowIdx].size <= 1) 0.4f else colIdx.toFloat() / MENU_GRID[rowIdx].lastIndex
                val targetCol = (ratio * MENU_GRID[nextRow].lastIndex).roundToInt()
                    .coerceIn(0, MENU_GRID[nextRow].lastIndex)
                focusedItemKey = MENU_GRID[nextRow][targetCol]
            }
            MenuNavAction.SELECT -> activateKey(focusedItemKey)
            MenuNavAction.START_GAME -> onStartGame()
            MenuNavAction.RECENTER -> onRecenter()
            MenuNavAction.TOGGLE_AUDIO -> onToggleAudio()
            MenuNavAction.BACK -> Unit
        }
    }

    LaunchedEffect(menuActions) {
        menuActions.collect { action ->
            moveFocus(action)
        }
    }

    // 3DoF Head-Tracking Gaze Selection: highlight the button under the center reticle when looking around
    LaunchedEffect(headPose.yawRad, headPose.pitchRad, settings.headTrackingEnabled) {
        if (!settings.headTrackingEnabled || !hasHardwareGyro) return@LaunchedEffect
        val deltaYaw = abs(headPose.yawRad - lastGazeYaw)
        val deltaPitch = abs(headPose.pitchRad - lastGazePitch)
        if (deltaYaw > 0.015f || deltaPitch > 0.015f) {
            lastGazeYaw = headPose.yawRad
            lastGazePitch = headPose.pitchRad
            val gazePoint = leftEyeBounds.value?.center ?: return@LaunchedEffect
            val hit = leftItemBounds.entries.firstOrNull { (_, rect) -> rect.contains(gazePoint) }?.key
            if (hit != null && hit != focusedItemKey) {
                focusedItemKey = hit
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        VrEyeMenuViewport(
            isLeftEye = true,
            isPrimaryFocusEye = true,
            slendrinaPortrait = slendrinaPortrait,
            headPose = headPose,
            headTrackingEnabled = settings.headTrackingEnabled,
            hasHardwareGyro = hasHardwareGyro,
            imuStatus = imuStatus,
            focusedItemKey = focusedItemKey,
            onEyeBoundsUpdated = { leftEyeBounds.value = it },
            onItemBoundsUpdated = { id, rect -> leftItemBounds[id] = rect },
            settings = settings,
            records = records,
            gamepadStatus = gamepadStatus,
            onSelectMap = {
                focusedItemKey = "map_${it.id}"
                onSelectMap(it)
            },
            onSelectDifficulty = {
                focusedItemKey = "diff_${it.id}"
                onSelectDifficulty(it)
            },
            onToggleHeadTracking = {
                focusedItemKey = "gyro"
                onToggleHeadTracking()
            },
            onToggleRefreshRate = {
                focusedItemKey = "hz"
                onToggleRefreshRate()
            },
            onToggleAudio = {
                focusedItemKey = "audio"
                onToggleAudio()
            },
            onRecenter = {
                focusedItemKey = "recenter"
                onRecenter()
            },
            onStartGame = onStartGame,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        // Stereoscopic lens separator (soft obsidian to minimize Fresnel god rays)
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(ObsidianVoid)
        )

        VrEyeMenuViewport(
            isLeftEye = false,
            isPrimaryFocusEye = false,
            slendrinaPortrait = slendrinaPortrait,
            headPose = headPose,
            headTrackingEnabled = settings.headTrackingEnabled,
            hasHardwareGyro = hasHardwareGyro,
            imuStatus = imuStatus,
            focusedItemKey = focusedItemKey,
            onEyeBoundsUpdated = {},
            onItemBoundsUpdated = { _, _ -> },
            settings = settings,
            records = records,
            gamepadStatus = gamepadStatus,
            onSelectMap = {
                focusedItemKey = "map_${it.id}"
                onSelectMap(it)
            },
            onSelectDifficulty = {
                focusedItemKey = "diff_${it.id}"
                onSelectDifficulty(it)
            },
            onToggleHeadTracking = {
                focusedItemKey = "gyro"
                onToggleHeadTracking()
            },
            onToggleRefreshRate = {
                focusedItemKey = "hz"
                onToggleRefreshRate()
            },
            onToggleAudio = {
                focusedItemKey = "audio"
                onToggleAudio()
            },
            onRecenter = {
                focusedItemKey = "recenter"
                onRecenter()
            },
            onStartGame = onStartGame,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
private fun VrEyeMenuViewport(
    isLeftEye: Boolean,
    isPrimaryFocusEye: Boolean,
    slendrinaPortrait: ImageBitmap?,
    headPose: HeadPose,
    headTrackingEnabled: Boolean,
    hasHardwareGyro: Boolean,
    imuStatus: ImuStatus,
    focusedItemKey: String,
    onEyeBoundsUpdated: (Rect) -> Unit,
    onItemBoundsUpdated: (String, Rect) -> Unit,
    settings: PlayerSettings,
    records: GameRecords,
    gamepadStatus: GamepadStatus,
    onSelectMap: (GameMap) -> Unit,
    onSelectDifficulty: (Difficulty) -> Unit,
    onToggleHeadTracking: () -> Unit,
    onToggleRefreshRate: () -> Unit,
    onToggleAudio: () -> Unit,
    onRecenter: () -> Unit,
    onStartGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    // Spatial 3DoF head-tracking shift: turning head right shifts world-anchored panel left
    val headShiftXPx = with(density) {
        if (headTrackingEnabled) (-headPose.yawRad * 260f).coerceIn(-160f, 160f).dp.toPx() else 0f
    }
    val headShiftYPx = with(density) {
        if (headTrackingEnabled) (headPose.pitchRad * 220f).coerceIn(-110f, 110f).dp.toPx() else 0f
    }
    val stereoParallaxPx = with(density) {
        (if (isLeftEye) (-2.5f).dp else 2.5f.dp).toPx()
    }

    fun Modifier.vrControl(id: String): Modifier = this
        .focusProperties { canFocus = false }
        .let { m ->
            if (isPrimaryFocusEye) {
                m.onGloballyPositioned { coords ->
                    onItemBoundsUpdated(id, coords.boundsInRoot())
                }
            } else {
                m
            }
        }

    fun Modifier.eyeTestTag(tag: String): Modifier =
        if (isPrimaryFocusEye) this.testTag(tag) else this

    BoxWithConstraints(
        modifier = modifier
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ObsidianVoid.copy(alpha = 0.18f),
                        ObsidianVoid.copy(alpha = 0.50f),
                        ObsidianVoid.copy(alpha = 0.90f)
                    )
                )
            )
            .let { base ->
                if (isPrimaryFocusEye) {
                    base.onGloballyPositioned { coords ->
                        onEyeBoundsUpdated(coords.boundsInRoot())
                    }
                } else {
                    base
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        val isWideVrEye = maxWidth >= 240.dp
        val panelScale = if (isWideVrEye) 0.78f else 0.85f
        val panelWidthModifier = if (isWideVrEye) {
            Modifier.widthIn(max = 292.dp).fillMaxWidth(0.74f)
        } else {
            Modifier.fillMaxWidth()
        }

        // Smaller-scale floating 3D-tracked stereoscopic VR menu window
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CryptSurface.copy(alpha = 0.80f),
            border = BorderStroke(1.dp, BorderSmoke.copy(alpha = 0.9f)),
            modifier = panelWidthModifier
                .offset {
                    IntOffset(
                        x = (headShiftXPx + stereoParallaxPx).roundToInt(),
                        y = headShiftYPx.roundToInt()
                    )
                }
                .graphicsLayer {
                    scaleX = panelScale
                    scaleY = panelScale
                }
                .eyeTestTag("vr_menu_window")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Header Row (Portrait + Title + Recenter/Audio) & VR Optics Bar
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (slendrinaPortrait != null) {
                                Surface(
                                    shape = CircleShape,
                                    color = ObsidianVoid,
                                    border = BorderStroke(1.5.dp, BloodCrimson.copy(alpha = 0.9f)),
                                    modifier = Modifier
                                        .size(28.dp)
                                        .eyeTestTag("slendrina_portrait_emblem")
                                ) {
                                    Image(
                                        bitmap = slendrinaPortrait,
                                        contentDescription = "Slendrina Chihuahua nun emblem",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Text(
                                text = "SLENDRINA GO",
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.8.sp,
                                color = ParchmentAsh,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val recenterFocused = focusedItemKey == "recenter"
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = CryptSurfaceElevated,
                                border = BorderStroke(
                                    if (recenterFocused) 2.dp else 1.dp,
                                    if (recenterFocused) TarnishedBrass else BorderSmoke
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .vrControl("recenter")
                                    .eyeTestTag("recenter_vr_button")
                            ) {
                                IconButton(onClick = onRecenter) {
                                    Icon(
                                        imageVector = Icons.Default.CenterFocusStrong,
                                        contentDescription = "Recenter 3DoF VR view",
                                        tint = if (recenterFocused) TarnishedBrass else ParchmentAsh,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val audioFocused = focusedItemKey == "audio"
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = CryptSurfaceElevated,
                                border = BorderStroke(
                                    if (audioFocused) 2.dp else 1.dp,
                                    if (audioFocused) TarnishedBrass else BorderSmoke
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .vrControl("audio")
                                    .eyeTestTag("toggle_audio_button")
                            ) {
                                IconButton(onClick = onToggleAudio) {
                                    Icon(
                                        imageVector = if (settings.audioEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                        contentDescription = "Toggle audio",
                                        tint = if (settings.audioEnabled) TarnishedBrass else MistGrey,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BloodCrimson.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, BloodCrimson.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .heightIn(min = 24.dp)
                                .eyeTestTag("mode_vr_only_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RemoveRedEye,
                                    contentDescription = null,
                                    tint = ParchmentAsh,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "VR ONLY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.5.sp,
                                    color = ParchmentAsh
                                )
                            }
                        }

                        val hzFocused = focusedItemKey == "hz"
                        val is72Hz = settings.refreshRate == VrRefreshRate.HZ_72
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (is72Hz) SpectralTeal.copy(alpha = 0.20f) else CryptSurfaceElevated,
                            border = BorderStroke(
                                if (hzFocused) 2.dp else 1.dp,
                                when {
                                    hzFocused -> TarnishedBrass
                                    is72Hz -> SpectralTeal
                                    else -> BorderSmoke
                                }
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .vrControl("hz")
                                .clickable(onClick = onToggleRefreshRate)
                                .eyeTestTag("refresh_rate_toggle")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 7.dp)
                            ) {
                                Text(
                                    text = settings.refreshRate.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (is72Hz) SpectralTeal else MistGrey,
                                    maxLines = 1
                                )
                            }
                        }

                        Text(
                            text = "1280×1440 • 95° • Escapes: ${records.totalEscapes}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = TarnishedBrass,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                // Three Haunted Maps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GameMap.entries.forEach { map ->
                        val record = records.byMap[map]
                        VrMapCard(
                            map = map,
                            selected = settings.selectedMap == map,
                            focused = focusedItemKey == "map_${map.id}",
                            bestTimeSeconds = record?.bestTimeSeconds,
                            onClick = { onSelectMap(map) },
                            modifier = Modifier
                                .weight(1f)
                                .vrControl("map_${map.id}")
                                .eyeTestTag("map_card_${map.id}")
                        )
                    }
                }

                // Difficulty & 3DoF Sensor Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Difficulty.entries.forEach { diff ->
                        val selected = settings.selectedDifficulty == diff
                        val focused = focusedItemKey == "diff_${diff.id}"
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) BloodCrimson else CryptSurfaceElevated,
                            border = BorderStroke(
                                width = if (focused) 2.dp else 1.dp,
                                color = when {
                                    focused -> TarnishedBrass
                                    selected -> BloodCrimson
                                    else -> BorderSmoke
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .vrControl("diff_${diff.id}")
                                .clickable { onSelectDifficulty(diff) }
                                .eyeTestTag("difficulty_${diff.id}")
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 3.dp)) {
                                Text(
                                    text = diff.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) ParchmentAsh else MistGrey,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    val gyroFocused = focusedItemKey == "gyro"
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (settings.headTrackingEnabled) SpectralTeal.copy(alpha = 0.22f) else CryptSurfaceElevated,
                        border = BorderStroke(
                            width = if (gyroFocused) 2.dp else 1.dp,
                            color = when {
                                gyroFocused -> TarnishedBrass
                                settings.headTrackingEnabled -> SpectralTeal
                                else -> BorderSmoke
                            }
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .vrControl("gyro")
                            .clickable(onClick = onToggleHeadTracking)
                            .eyeTestTag("toggle_head_tracking")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (settings.headTrackingEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle 3DoF head tracking",
                                tint = if (settings.headTrackingEnabled) SpectralTeal else MistGrey,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (hasHardwareGyro) imuStatus.label else "Touch",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                color = if (settings.headTrackingEnabled) SpectralTeal else MistGrey,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Launch CTA + Bluetooth Gamepad Status Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = if (gamepadStatus.connected) SpectralTeal.copy(alpha = 0.16f) else CryptSurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (gamepadStatus.connected) SpectralTeal else BorderSmoke
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .eyeTestTag("gamepad_status_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = "Bluetooth gamepad status",
                                tint = if (gamepadStatus.connected) SpectralTeal else TarnishedBrass,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (gamepadStatus.connected) gamepadStatus.shortLabel else "BT Pad",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (gamepadStatus.connected) SpectralTeal else ParchmentAsh,
                                maxLines = 1
                            )
                        }
                    }

                    val startFocused = focusedItemKey == "start"
                    Button(
                        onClick = onStartGame,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BloodCrimson,
                            contentColor = ParchmentAsh
                        ),
                        border = if (startFocused) BorderStroke(2.dp, TarnishedBrass) else null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .vrControl("start")
                            .eyeTestTag("start_game_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ENTER ${settings.selectedMap.title.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "Gamepad: Stick/D-Pad Nav • A/R2 Select • X Audio • Y Recenter",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 8.5.sp,
                    color = MistGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.eyeTestTag("gamepad_controls_hint")
                )
            }
        }

        // Subtle VR Gaze Reticle centered in each eye when 3DoF head tracking is active
        if (headTrackingEnabled && hasHardwareGyro) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(TarnishedBrass.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun VrMapCard(
    map: GameMap,
    selected: Boolean,
    focused: Boolean,
    bestTimeSeconds: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianVoid),
        border = BorderStroke(
            width = if (focused || selected) 2.dp else 1.dp,
            color = when {
                focused -> TarnishedBrass
                selected -> BloodCrimson
                else -> BorderSmoke
            }
        ),
        modifier = modifier
            .height(78.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = map.previewRes),
                contentDescription = "${map.title} preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.12f),
                                ObsidianVoid.copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected map",
                    tint = BloodCrimson,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(14.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = map.title.uppercase(),
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = ParchmentAsh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (bestTimeSeconds != null) "Best ${formatClock(bestTimeSeconds)}" else "${map.targetBooks} Books",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = if (bestTimeSeconds != null) SpectralTeal else TarnishedBrass,
                    maxLines = 1
                )
            }
        }
    }
}

fun formatClock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
