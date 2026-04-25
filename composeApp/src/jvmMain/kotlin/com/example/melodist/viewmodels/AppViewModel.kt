package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String
)

class AppViewModel : ViewModel() {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            val controller = SoftwareUpdateController.getInstance() ?: return@launch
            val availability = controller.canTriggerUpdateCheckUI()
            if (availability != SoftwareUpdateController.Availability.AVAILABLE) return@launch

            val current = controller.currentVersion ?: return@launch
            val latest = withContext(Dispatchers.IO) {
                try {
                    controller.currentVersionFromRepository
                } catch (_: SoftwareUpdateController.UpdateCheckException) {
                    null
                }
            } ?: return@launch

            if (latest > current) {
                _updateInfo.value = AppUpdateInfo(
                    currentVersion = current.toString(),
                    latestVersion = latest.toString()
                )
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    fun triggerUpdateUi() {
        dismissUpdate()
        SoftwareUpdateController.getInstance()?.triggerUpdateCheckUI()
    }
}
