package example.nucleus.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.utils.SyncUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado de la sección "Sincronización" de Ajustes: modo sin conexión y sync con YouTube Music. */
class SyncSettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    val offlineModeEnabled: StateFlow<Boolean> = preferencesRepository.offlineModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val ytmSyncEnabled: StateFlow<Boolean> = preferencesRepository.ytmSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Progreso de la sincronización para el botón manual "Sincronizar ahora" — ignora el enfriamiento de inicio de sesión. */
    val syncState = syncUtils.syncState

    fun setOfflineModeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOfflineModeEnabled(enabled) }
    }

    fun setYtmSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setYtmSyncEnabled(enabled)
            // Dar retroalimentación inmediata de que activar esto hace algo: reconciliar las playlists
            // vinculadas a YouTube de inmediato en lugar de esperar al siguiente inicio de sesión/restauración de sesión.
            if (enabled) syncUtils.syncAutoSyncPlaylists()
        }
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
}
