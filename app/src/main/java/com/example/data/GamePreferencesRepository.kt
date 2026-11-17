package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "slendrina_go_prefs")

class GamePreferencesRepository(private val context: Context) {

    private object Keys {
        val RENDER_MODE = stringPreferencesKey("render_mode")
        val REFRESH_RATE = stringPreferencesKey("vr_refresh_rate")
        val HEAD_TRACKING = booleanPreferencesKey("head_tracking")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val LOOK_SENSITIVITY = floatPreferencesKey("look_sensitivity")
        val SELECTED_MAP = stringPreferencesKey("selected_map")
        val SELECTED_DIFFICULTY = stringPreferencesKey("selected_difficulty")
        val TOTAL_ESCAPES = intPreferencesKey("total_escapes")

        fun bestTime(mapId: String) = intPreferencesKey("best_time_$mapId")
        fun completions(mapId: String) = intPreferencesKey("completions_$mapId")
    }

    val settingsFlow: Flow<PlayerSettings> = context.gameDataStore.data.map { prefs ->
        val renderMode = prefs[Keys.RENDER_MODE]
            ?.let { name -> RenderMode.entries.firstOrNull { it.name == name } }
            ?: RenderMode.VR_SBS
        val refreshRate = prefs[Keys.REFRESH_RATE]
            ?.let { name -> VrRefreshRate.entries.firstOrNull { it.name == name } }
            ?: VrRefreshRate.HZ_72
        val selectedMap = prefs[Keys.SELECTED_MAP]
            ?.let { id -> GameMap.entries.firstOrNull { it.id == id } }
            ?: GameMap.HOUSE
        val selectedDifficulty = prefs[Keys.SELECTED_DIFFICULTY]
            ?.let { id -> Difficulty.entries.firstOrNull { it.id == id } }
            ?: Difficulty.MEDIUM

        PlayerSettings(
            renderMode = renderMode,
            refreshRate = refreshRate,
            headTrackingEnabled = prefs[Keys.HEAD_TRACKING] ?: true,
            audioEnabled = prefs[Keys.AUDIO_ENABLED] ?: true,
            lookSensitivity = prefs[Keys.LOOK_SENSITIVITY] ?: 1.0f,
            selectedMap = selectedMap,
            selectedDifficulty = selectedDifficulty
        )
    }

    val recordsFlow: Flow<GameRecords> = context.gameDataStore.data.map { prefs ->
        val mapRecords = GameMap.entries.associateWith { map ->
            MapRecord(
                bestTimeSeconds = prefs[Keys.bestTime(map.id)],
                completions = prefs[Keys.completions(map.id)] ?: 0
            )
        }
        GameRecords(
            byMap = mapRecords,
            totalEscapes = prefs[Keys.TOTAL_ESCAPES] ?: 0
        )
    }

    suspend fun setRenderMode(mode: RenderMode) {
        context.gameDataStore.edit { it[Keys.RENDER_MODE] = mode.name }
    }

    suspend fun setRefreshRate(rate: VrRefreshRate) {
        context.gameDataStore.edit { it[Keys.REFRESH_RATE] = rate.name }
    }

    suspend fun setHeadTrackingEnabled(enabled: Boolean) {
        context.gameDataStore.edit { it[Keys.HEAD_TRACKING] = enabled }
    }

    suspend fun setAudioEnabled(enabled: Boolean) {
        context.gameDataStore.edit { it[Keys.AUDIO_ENABLED] = enabled }
    }

    suspend fun setLookSensitivity(sensitivity: Float) {
        context.gameDataStore.edit { it[Keys.LOOK_SENSITIVITY] = sensitivity.coerceIn(0.4f, 2.2f) }
    }

    suspend fun setSelectedMap(map: GameMap) {
        context.gameDataStore.edit { it[Keys.SELECTED_MAP] = map.id }
    }

    suspend fun setSelectedDifficulty(difficulty: Difficulty) {
        context.gameDataStore.edit { it[Keys.SELECTED_DIFFICULTY] = difficulty.id }
    }

    suspend fun recordEscape(map: GameMap, elapsedSeconds: Int) {
        context.gameDataStore.edit { prefs ->
            val currentBest = prefs[Keys.bestTime(map.id)]
            if (currentBest == null || elapsedSeconds < currentBest) {
                prefs[Keys.bestTime(map.id)] = elapsedSeconds
            }
            prefs[Keys.completions(map.id)] = (prefs[Keys.completions(map.id)] ?: 0) + 1
            prefs[Keys.TOTAL_ESCAPES] = (prefs[Keys.TOTAL_ESCAPES] ?: 0) + 1
        }
    }
}
