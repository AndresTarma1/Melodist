package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.AppLocale
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.data.repository.YouTubeRegion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Aplicación" de Ajustes: idioma, región y comportamiento general. */
class ApplicationSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val locale: StateFlow<AppLocale> = preferencesRepository.locale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLocale.SYSTEM)

    val youtubeRegion: StateFlow<YouTubeRegion> = preferencesRepository.youtubeRegion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), YouTubeRegion.SYSTEM)

    val minimizeToTray: StateFlow<Boolean> = preferencesRepository.minimizeToTray
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val trimMemoryOnTray: StateFlow<Boolean> = preferencesRepository.trimMemoryOnTray
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val launchAtStartup: StateFlow<Boolean> = preferencesRepository.launchAtStartup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val taskbarWidgetEnabled: StateFlow<Boolean> = preferencesRepository.taskbarWidgetEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setLocale(locale: AppLocale) {
        viewModelScope.launch { preferencesRepository.setLocale(locale) }
    }

    fun setYoutubeRegion(region: YouTubeRegion) {
        viewModelScope.launch { preferencesRepository.setYoutubeRegion(region) }
    }

    fun setMinimizeToTray(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMinimizeToTray(enabled) }
    }

    fun setTrimMemoryOnTray(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setTrimMemoryOnTray(enabled) }
    }

    fun setLaunchAtStartup(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLaunchAtStartup(enabled) }
    }

    fun setTaskbarWidgetEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setTaskbarWidgetEnabled(enabled) }
    }
}
