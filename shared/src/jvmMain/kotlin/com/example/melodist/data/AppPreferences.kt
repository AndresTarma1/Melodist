package com.example.melodist.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties
import java.util.logging.Logger

/**
 * Audio quality preference.
 */
enum class AudioQuality(val label: String) {
    LOW("Baja (ahorrar datos)"),
    NORMAL("Normal"),
    HIGH("Alta (mayor consumo)")
}

/**
 * Theme mode.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    DARK("Oscuro"),
    LIGHT("Claro")
}

/**
 * Persistent app preferences stored in ~/.melodist/settings.properties
 */
object AppPreferences {
    private val log = Logger.getLogger("AppPreferences")

    private val file = AppDirs.preferencesFile
    private val props = Properties()

    // ─── Observable state flows ───────────────────────────

    private val _audioQuality = MutableStateFlow(AudioQuality.NORMAL)
    val audioQuality: StateFlow<AudioQuality> = _audioQuality.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColorFromArtwork = MutableStateFlow(false)
    val dynamicColorFromArtwork: StateFlow<Boolean> = _dynamicColorFromArtwork.asStateFlow()

    private val _highResCoverArt = MutableStateFlow(true)
    val highResCoverArt: StateFlow<Boolean> = _highResCoverArt.asStateFlow()

    private val _crossfadeEnabled = MutableStateFlow(false)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _cacheImages = MutableStateFlow(true)
    val cacheImages: StateFlow<Boolean> = _cacheImages.asStateFlow()

    private val _imagesEnabled = MutableStateFlow(true)
    val imagesEnabled: StateFlow<Boolean> = _imagesEnabled.asStateFlow()

    private val _minimizeToTray = MutableStateFlow(true)
    val minimizeToTray: StateFlow<Boolean> = _minimizeToTray.asStateFlow()

    // ─── Window state (not flow, just helper) ───────────
    var windowWidth: Int
        get() = props.getProperty("window.width", "1200").toInt()
        set(v) {
            props.setProperty("window.width", v.toString())
            save()
        }

    var windowHeight: Int
        get() = props.getProperty("window.height", "800").toInt()
        set(v) {
            props.setProperty("window.height", v.toString())
            save()
        }

    var windowMaximized: Boolean
        get() = props.getProperty("window.maximized", "false").toBoolean()
        set(v) {
            props.setProperty("window.maximized", v.toString())
            save()
        }

    init {
        // Garantizar que el directorio existe incluso si se accede antes que main()
        AppDirs.ensureDirectories()
        load()
    }

    // ─── Setters (update + persist) ───────────────────────

    fun setAudioQuality(quality: AudioQuality) {
        _audioQuality.value = quality
        props.setProperty("audio.quality", quality.name)
        save()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        props.setProperty("theme.mode", mode.name)
        save()
    }

    fun setDynamicColorFromArtwork(enabled: Boolean) {
        _dynamicColorFromArtwork.value = enabled
        props.setProperty("theme.dynamicColor", enabled.toString())
        save()
    }

    fun setHighResCoverArt(enabled: Boolean) {
        _highResCoverArt.value = enabled
        props.setProperty("player.highResCover", enabled.toString())
        save()
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        props.setProperty("player.crossfade", enabled.toString())
        save()
    }

    fun setCacheImages(enabled: Boolean) {
        _cacheImages.value = enabled
        props.setProperty("cache.images", enabled.toString())
        save()
    }

    fun setImagesEnabled(enabled: Boolean) {
        _imagesEnabled.value = enabled
        props.setProperty("ui.imagesEnabled", enabled.toString())
        save()
    }

    fun setMinimizeToTray(enabled: Boolean) {
        _minimizeToTray.value = enabled
        props.setProperty("app.minimizeToTray", enabled.toString())
        save()
    }

    // ─── Persistence ──────────────────────────────────────

    private fun load() {
        try {
            if (file.exists()) {
                file.inputStream().use { props.load(it) }
            }
            _audioQuality.value = try { AudioQuality.valueOf(props.getProperty("audio.quality", "NORMAL")) } catch (_: Exception) { AudioQuality.NORMAL }
            _themeMode.value = try { ThemeMode.valueOf(props.getProperty("theme.mode", "DARK")) } catch (_: Exception) { ThemeMode.DARK }
            _dynamicColorFromArtwork.value = props.getProperty("theme.dynamicColor", "false").toBoolean()
            _highResCoverArt.value = props.getProperty("player.highResCover", "true").toBoolean()
            _crossfadeEnabled.value = props.getProperty("player.crossfade", "false").toBoolean()
            _cacheImages.value = props.getProperty("cache.images", "true").toBoolean()
            _imagesEnabled.value = props.getProperty("ui.imagesEnabled", "true").toBoolean()
            _minimizeToTray.value = props.getProperty("app.minimizeToTray", "true").toBoolean()
            log.info("Preferences loaded: quality=${_audioQuality.value}, theme=${_themeMode.value}, dynamicColor=${_dynamicColorFromArtwork.value}")
        } catch (e: Exception) {
            log.warning("Failed to load preferences: ${e.message}")
        }
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "Melodist Preferences") }
        } catch (e: Exception) {
            log.warning("Failed to save preferences: ${e.message}")
        }
    }
}

