package com.example.melodist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AudioQuality(val label: String) {
    LOW("Baja (ahorrar datos)"),
    NORMAL("Normal"),
    HIGH("Alta (mayor consumo)")
}

enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    DARK("Oscuro"),
    LIGHT("Claro")
}

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private object PreferencesKeys {
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HIGH_RES_COVER = booleanPreferencesKey("high_res_cover")
        val CROSSFADE = booleanPreferencesKey("crossfade")
        val CACHE_IMAGES = booleanPreferencesKey("cache_images")
        val IMAGES_ENABLED = booleanPreferencesKey("images_enabled")
        val MINIMIZE_TO_TRAY = booleanPreferencesKey("minimize_to_tray")
        val WINDOW_WIDTH = intPreferencesKey("window_width")
        val WINDOW_HEIGHT = intPreferencesKey("window_height")
        val WINDOW_MAXIMIZED = booleanPreferencesKey("window_maximized")
        val QUEUE_LOCKED = booleanPreferencesKey("queue_locked")
        val EQUALIZER_BANDS = stringPreferencesKey("equalizer_bands")
    }

    val audioQuality: Flow<AudioQuality> = dataStore.data.map { preferences ->
        try {
            AudioQuality.valueOf(preferences[PreferencesKeys.AUDIO_QUALITY] ?: AudioQuality.NORMAL.name)
        } catch (e: Exception) {
            AudioQuality.NORMAL
        }
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        try {
            ThemeMode.valueOf(preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.DARK.name)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    }

    val dynamicColorFromArtwork: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.DYNAMIC_COLOR] ?: false }
    val highResCoverArt: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.HIGH_RES_COVER] ?: true }
    val crossfadeEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.CROSSFADE] ?: false }
    val cacheImages: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.CACHE_IMAGES] ?: true }
    val imagesEnabled: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.IMAGES_ENABLED] ?: true }
    val minimizeToTray: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.MINIMIZE_TO_TRAY] ?: true }

    val windowWidth: Flow<Int> = dataStore.data.map { it[PreferencesKeys.WINDOW_WIDTH] ?: 1200 }
    val windowHeight: Flow<Int> = dataStore.data.map { it[PreferencesKeys.WINDOW_HEIGHT] ?: 800 }
    val windowMaximized: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.WINDOW_MAXIMIZED] ?: false }
    val queueLocked: Flow<Boolean> = dataStore.data.map { it[PreferencesKeys.QUEUE_LOCKED] ?: false }

    // Almacenamos 10 bandas separadas por coma, default a 0.0
    val equalizerBands: Flow<List<Float>> = dataStore.data.map { pref ->
        val str = pref[PreferencesKeys.EQUALIZER_BANDS] ?: "0,0,0,0,0,0,0,0,0,0"
        try {
            str.split(",").map { it.toFloat() }
        } catch(e: Exception) {
            List(10) { 0f }
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { it[PreferencesKeys.AUDIO_QUALITY] = quality.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColorFromArtwork(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setHighResCoverArt(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HIGH_RES_COVER] = enabled }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CROSSFADE] = enabled }
    }

    suspend fun setCacheImages(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CACHE_IMAGES] = enabled }
    }

    suspend fun setImagesEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.IMAGES_ENABLED] = enabled }
    }

    suspend fun setMinimizeToTray(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MINIMIZE_TO_TRAY] = enabled }
    }

    suspend fun setWindowSize(width: Int, height: Int) {
        dataStore.edit {
            it[PreferencesKeys.WINDOW_WIDTH] = width
            it[PreferencesKeys.WINDOW_HEIGHT] = height
        }
    }

    suspend fun setWindowMaximized(maximized: Boolean) {
        dataStore.edit { it[PreferencesKeys.WINDOW_MAXIMIZED] = maximized }
    }

    suspend fun setQueueLocked(locked: Boolean) {
        dataStore.edit { it[PreferencesKeys.QUEUE_LOCKED] = locked }
    }

    suspend fun setEqualizerBands(bands: List<Float>) {
        dataStore.edit { it[PreferencesKeys.EQUALIZER_BANDS] = bands.joinToString(",") }
    }
}