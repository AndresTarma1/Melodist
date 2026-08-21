package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Avanzado" de Ajustes: caché, logs y mantenimiento. */
class AdvancedSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val cacheImages: StateFlow<Boolean> = preferencesRepository.cacheImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val logToFile: StateFlow<Boolean> = preferencesRepository.logToFile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val logVerbose: StateFlow<Boolean> = preferencesRepository.logVerbose
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setCacheImages(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCacheImages(enabled) }
    }

    fun setLogToFile(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLogToFile(enabled) }
    }

    fun setLogVerbose(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setLogVerbose(enabled) }
    }
}
