package com.example.data

import androidx.annotation.DrawableRes
import com.example.R

enum class GameMap(
    val id: String,
    val title: String,
    val subtitle: String,
    val objectiveLabel: String,
    val targetBooks: Int,
    @DrawableRes val previewRes: Int,
    val fogDensity: Float,
    val fogColorR: Float,
    val fogColorG: Float,
    val fogColorB: Float
) {
    HOUSE(
        id = "house",
        title = "The House",
        subtitle = "Creaking floorboards, locked studies & cellars",
        objectiveLabel = "Find 6 Occult Books",
        targetBooks = 6,
        previewRes = R.drawable.img_map_house_1794927942910,
        fogDensity = 0.088f,
        fogColorR = 0.04f,
        fogColorG = 0.025f,
        fogColorB = 0.02f
    ),
    YARD(
        id = "yard",
        title = "The Yard",
        subtitle = "Moonlit graveyard, stone fountain & sheds",
        objectiveLabel = "Find 6 Ritual Books",
        targetBooks = 6,
        previewRes = R.drawable.img_map_yard_1794927943472,
        fogDensity = 0.072f,
        fogColorR = 0.02f,
        fogColorG = 0.035f,
        fogColorB = 0.055f
    ),
    HOTEL(
        id = "hotel",
        title = "The Hotel",
        subtitle = "Looping haunted corridors & locked suites",
        objectiveLabel = "Find 6 Guest Ledgers",
        targetBooks = 6,
        previewRes = R.drawable.img_map_hotel_1794927945901,
        fogDensity = 0.085f,
        fogColorR = 0.045f,
        fogColorG = 0.02f,
        fogColorB = 0.025f
    )
}

enum class Difficulty(
    val id: String,
    val label: String,
    val stareDrainPerSecond: Float,
    val stalkIntervalBaseSec: Float,
    val slendrinaMinDist: Float,
    val sanityRegenPerSecond: Float
) {
    EASY(
        id = "easy",
        label = "Easy",
        stareDrainPerSecond = 28f,
        stalkIntervalBaseSec = 10.5f,
        slendrinaMinDist = 5.5f,
        sanityRegenPerSecond = 6.5f
    ),
    MEDIUM(
        id = "medium",
        label = "Medium",
        stareDrainPerSecond = 44f,
        stalkIntervalBaseSec = 7.5f,
        slendrinaMinDist = 4.2f,
        sanityRegenPerSecond = 4.0f
    ),
    HARD(
        id = "hard",
        label = "Hard",
        stareDrainPerSecond = 65f,
        stalkIntervalBaseSec = 5.2f,
        slendrinaMinDist = 3.2f,
        sanityRegenPerSecond = 2.0f
    )
}

enum class RenderMode(val label: String, val shortLabel: String) {
    VR_SBS("Oculus Go VR (SBS)", "VR SBS"),
    FLAT_2D("2D / Oculus TV", "2D FLAT")
}

enum class VrRefreshRate(val hz: Int, val label: String) {
    HZ_72(72, "72 Hz"),
    HZ_60(60, "60 Hz")
}

data class PlayerSettings(
    val renderMode: RenderMode = RenderMode.VR_SBS,
    val refreshRate: VrRefreshRate = VrRefreshRate.HZ_72,
    val headTrackingEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
    val lookSensitivity: Float = 1.0f,
    val selectedMap: GameMap = GameMap.HOUSE,
    val selectedDifficulty: Difficulty = Difficulty.MEDIUM
)

data class MapRecord(
    val bestTimeSeconds: Int? = null,
    val completions: Int = 0
)

data class GameRecords(
    val byMap: Map<GameMap, MapRecord> = emptyMap(),
    val totalEscapes: Int = 0
)
