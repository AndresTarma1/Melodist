package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.AppLocale
import example.nucleus.data.repository.AudioQuality
import example.nucleus.data.repository.DarkLevel
import example.nucleus.data.repository.IslandStyle
import example.nucleus.data.repository.LayoutMode
import example.nucleus.data.repository.NavigationRailStyle
import example.nucleus.data.repository.BackgroundStyle
import example.nucleus.data.repository.SeekBarStyle
import example.nucleus.data.repository.ThemeMode
import example.nucleus.data.repository.ThemePalette
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.data.repository.YouTubeRegion
import example.nucleus.utils.SyncUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    val audioQuality: StateFlow<AudioQuality> = preferencesRepository.audioQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AudioQuality.NORMAL)

    val themeMode: StateFlow<ThemeMode> = preferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val darkLevel: StateFlow<DarkLevel> = preferencesRepository.darkLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DarkLevel.DIM)

    val layoutMode: StateFlow<LayoutMode> = preferencesRepository.layoutMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LayoutMode.ATTACHED)

    val islandStyle: StateFlow<IslandStyle> = preferencesRepository.islandStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IslandStyle.COMFORTABLE)

    val dynamicColorFromArtwork: StateFlow<Boolean> = preferencesRepository.dynamicColorFromArtwork
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val highResCoverArt: StateFlow<Boolean> = preferencesRepository.highResCoverArt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val crossfadeEnabled: StateFlow<Boolean> = preferencesRepository.crossfadeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val seekBarStyle: StateFlow<SeekBarStyle> = preferencesRepository.seekBarStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeekBarStyle.WAVY)

    val cacheImages: StateFlow<Boolean> = preferencesRepository.cacheImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val imagesEnabled: StateFlow<Boolean> = preferencesRepository.imagesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val minimizeToTray: StateFlow<Boolean> = preferencesRepository.minimizeToTray
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val trimMemoryOnTray: StateFlow<Boolean> = preferencesRepository.trimMemoryOnTray
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val launchAtStartup: StateFlow<Boolean> = preferencesRepository.launchAtStartup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val queueLocked: StateFlow<Boolean> = preferencesRepository.queueLocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val equalizerBands: StateFlow<List<Float>> = preferencesRepository.equalizerBands
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(5) { 0f })

    val themePalette: StateFlow<ThemePalette> = preferencesRepository.themePalette
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePalette.DEFAULT)

    val locale: StateFlow<AppLocale> = preferencesRepository.locale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLocale.SYSTEM)

    val youtubeRegion: StateFlow<YouTubeRegion> = preferencesRepository.youtubeRegion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YouTubeRegion.SYSTEM)

    val ytmSyncEnabled: StateFlow<Boolean> = preferencesRepository.ytmSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val offlineModeEnabled: StateFlow<Boolean> = preferencesRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Progreso de la sincronización para el botón manual "Sincronizar ahora" — ignora el enfriamiento de inicio de sesión. */
    val syncState = syncUtils.syncState

    val overlayHotkeyEnabled: StateFlow<Boolean> = preferencesRepository.overlayHotkeyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val overlayHotkeyLabel: StateFlow<String> = preferencesRepository.overlayHotkeyLabel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val nowPlayingBackground: StateFlow<BackgroundStyle> = preferencesRepository.nowPlayingBackground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackgroundStyle.GRADIENT)

    val navigationRailStyle: StateFlow<NavigationRailStyle> = preferencesRepository.navigationRailStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NavigationRailStyle.DEFAULT)

    val fullScreenPlayer: StateFlow<Boolean> = preferencesRepository.fullScreenPlayer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val appBackgroundStyle: StateFlow<BackgroundStyle> = preferencesRepository.appBackgroundStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackgroundStyle.BLURRED_COVER)

    val uiScale: StateFlow<Float> = preferencesRepository.uiScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val selectedFont: StateFlow<String> = preferencesRepository.selectedFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")


    fun setAudioQuality(quality: AudioQuality) {
        viewModelScope.launch { preferencesRepository.setAudioQuality(quality) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDarkLevel(level: DarkLevel) {
        viewModelScope.launch { preferencesRepository.setDarkLevel(level) }
    }

    fun setLayoutMode(mode: LayoutMode) {
        viewModelScope.launch { preferencesRepository.setLayoutMode(mode) }
    }

    fun setIslandStyle(style: IslandStyle) {
        viewModelScope.launch { preferencesRepository.setIslandStyle(style) }
    }

    fun setDynamicColorFromArtwork(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDynamicColorFromArtwork(enabled) }
    }

    fun setHighResCoverArt(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHighResCoverArt(enabled) }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCrossfadeEnabled(enabled) }
    }

    fun setSeekBarStyle(style: SeekBarStyle) {
        viewModelScope.launch { preferencesRepository.setSeekBarStyle(style) }
    }

    fun setCacheImages(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCacheImages(enabled) }
    }

    fun setImagesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setImagesEnabled(enabled) }
    }

    fun setMinimizeToTray(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMinimizeToTray(enabled) }
    }

    fun setFullScreenPlayer(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setFullScreenPlayer(enabled) }
    }

    fun setTrimMemoryOnTray(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setTrimMemoryOnTray(enabled) }
    }

    fun setLaunchAtStartup(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLaunchAtStartup(enabled) }
    }

    fun setQueueLocked(locked: Boolean) {
        viewModelScope.launch { preferencesRepository.setQueueLocked(locked) }
    }

    fun setEqualizerBands(bands: List<Float>) {
        viewModelScope.launch { preferencesRepository.setEqualizerBands(bands) }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { preferencesRepository.setThemePalette(palette) }
    }

    fun setLocale(locale: AppLocale) {
        viewModelScope.launch { preferencesRepository.setLocale(locale) }
    }

    fun setYoutubeRegion(region: YouTubeRegion) {
        viewModelScope.launch { preferencesRepository.setYoutubeRegion(region) }
    }

    fun setYtmSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setYtmSyncEnabled(enabled)
            // Dar retroalimentación inmediata de que activar esto hace algo: reconciliar las playlists
            // vinculadas a YouTube de inmediato en lugar de esperar al siguiente inicio de sesión/restauración de sesión.
            if (enabled) syncUtils.syncAutoSyncPlaylists()
        }
    }

    // Sin usar hasta previo aviso
//    fun setNowPlayingBackground(style: BackgroundStyle) {
//        viewModelScope.launch { preferencesRepository.setNowPlayingBackground(style) }
//    }

    fun setNavigationRailStyle(style: NavigationRailStyle){
        viewModelScope.launch { preferencesRepository.setNavigationRailStyle(style) }
    }

    fun setOfflineModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOfflineModeEnabled(enabled) }
    }

    /**
     * "Sincronizar ahora" manual — omite el enfriamiento de 30 minutos de AccountViewModel, para
     * pruebas o cuando el usuario quiere una sincronización completa ahora mismo (canciones/álbumes/artistas/playlists favoritas).
     */
    fun syncNow() {
        viewModelScope.launch {
            preferencesRepository.setLastFullSyncAt(System.currentTimeMillis())
            syncUtils.performFullSync()
            if (preferencesRepository.ytmSyncEnabled.first()) {
                syncUtils.syncAutoSyncPlaylists()
            }
        }
    }

    fun setOverlayHotkeyEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkeyEnabled(enabled) }
    }

    fun setOverlayHotkey(code: Int, mods: Int, label: String) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkey(code, mods, label) }
    }

    fun setAppBackgroundStyle(style: BackgroundStyle) {
        viewModelScope.launch { preferencesRepository.setAppBackgroundStyle(style) }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch { preferencesRepository.setUiScale(scale) }
    }

    fun setSelectedFont(name: String) {
        viewModelScope.launch { preferencesRepository.setSelectedFont(name) }
    }
}
