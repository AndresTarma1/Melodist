package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Overlay" de Ajustes: atajo global del overlay de música. */
class OverlaySettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val overlayHotkeyEnabled: StateFlow<Boolean> = preferencesRepository.overlayHotkeyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val overlayHotkeyLabel: StateFlow<String> = preferencesRepository.overlayHotkeyLabel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setOverlayHotkeyEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkeyEnabled(enabled) }
    }

    fun setOverlayHotkey(code: Int, mods: Int, label: String) {
        viewModelScope.launch { preferencesRepository.setOverlayHotkey(code, mods, label) }
    }
}
