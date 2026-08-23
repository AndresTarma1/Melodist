package example.nucleus.utils

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.SnackbarHostState
import example.nucleus.data.repository.UserPreferencesRepository
import example.nucleus.viewmodels.DownloadViewModel
import example.nucleus.viewmodels.LibraryPlaylistsViewModel
import example.nucleus.viewmodels.PlayerViewModel
import kotlinx.coroutines.CoroutineScope

val LocalPlayerViewModel = staticCompositionLocalOf<PlayerViewModel> { error("No PlayerViewModel provided") }

val LocalPlaylistsViewModel = staticCompositionLocalOf<LibraryPlaylistsViewModel> {
    error("No se ha proporcionado un LibraryPlaylistsViewModel")
}

val LocalDownloadViewModel = staticCompositionLocalOf<DownloadViewModel> {
    error("No se ha proporcionado un DownloadViewModel")
}

val LocalUserPreferences = staticCompositionLocalOf<UserPreferencesRepository> {
    error("No se ha proporcionado un UserPreferencesRepository")
}

val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No se ha proporcionado un SnackbarHostState")
}

val LocalSnackbarScope = staticCompositionLocalOf<CoroutineScope> {
    error("No se ha proporcionado un SnackbarScope")
}

val LocalAnimationsEnabled = staticCompositionLocalOf<Boolean> {
    error("No se ha proporcionado animationsEnabled")
}

val LocalAppFullscreen = staticCompositionLocalOf<MutableState<Boolean>> {
    error("No se ha proporcionado un estado de pantalla completa de la app")
}
