package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Avanzado" de Ajustes: caché y mantenimiento. */
class AdvancedSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val cacheImages: StateFlow<Boolean> = preferencesRepository.cacheImages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setCacheImages(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCacheImages(enabled) }
    }
}
