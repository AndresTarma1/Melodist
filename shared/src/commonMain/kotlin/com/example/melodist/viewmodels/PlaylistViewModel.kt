package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.domain.playlist.GetCachedPlaylistUseCase
import com.example.melodist.domain.playlist.IsPlaylistSavedUseCase
import com.example.melodist.domain.playlist.LoadPlaylistContinuationUseCase
import com.example.melodist.domain.playlist.LoadPlaylistUseCase
import com.example.melodist.domain.playlist.RemovePlaylistUseCase
import com.example.melodist.domain.playlist.RemoveSongFromPlaylistUseCase
import com.example.melodist.domain.playlist.SavePlaylistUseCase
import com.example.melodist.domain.song.GetDownloadedSongsUseCase
import com.example.melodist.domain.song.LikeSongUseCase
import com.example.melodist.domain.song.DislikeSongUseCase
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.pages.PlaylistPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers

sealed class PlaylistState {
    object Loading : PlaylistState()
    data class Success(
        val playlistPage: PlaylistPage,
        val isFromCache: Boolean = false,
        val isSaved: Boolean = false,
        val isSaving: Boolean = false,
        val isLoadingForPlay: Boolean = false,
    ) : PlaylistState()
    data class Error(val message: String) : PlaylistState()
}

class PlaylistViewModel(
    private val loadPlaylistUseCase: LoadPlaylistUseCase,
    private val loadPlaylistContinuationUseCase: LoadPlaylistContinuationUseCase,
    private val getCachedPlaylistUseCase: GetCachedPlaylistUseCase,
    private val isPlaylistSavedUseCase: IsPlaylistSavedUseCase,
    private val savePlaylistUseCase: SavePlaylistUseCase,
    private val removePlaylistUseCase: RemovePlaylistUseCase,
    private val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase,
    private val getDownloadedSongsUseCase: GetDownloadedSongsUseCase,
    private val likeSongUseCase: LikeSongUseCase,
    private val dislikeSongUseCase: DislikeSongUseCase
) : ViewModel() {

    private val log = Logger.getLogger("PlaylistViewModel")

    private val _uiState = MutableStateFlow<PlaylistState>(PlaylistState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _currentPlaylistId = MutableStateFlow<String?>(null)

    /** Canciones acumuladas de todas las páginas cargadas */
    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()

    private val _continuation = MutableStateFlow<String?>(null)

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val hasMoreSongs: StateFlow<Boolean> = _continuation
        .map { token -> token != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /** Actualiza un campo de PlaylistState.Success de forma segura; no-op si el estado no es Success */
    private inline fun updateSuccess(transform: PlaylistState.Success.() -> PlaylistState.Success) {
        _uiState.update { state ->
            if (state is PlaylistState.Success) state.transform() else state
        }
    }

    private fun resetLoadState() {
        _uiState.value = PlaylistState.Loading
        _songs.value = emptyList()
        _continuation.value = null
        _isLoadingMore.value = false
    }

    private fun buildSuccessState(
        playlistPage: PlaylistPage,
        isFromCache: Boolean,
        isSaved: Boolean,
    ): PlaylistState.Success {
        return PlaylistState.Success(
            playlistPage = playlistPage,
            isFromCache = isFromCache,
            isSaved = isSaved,
        )
    }

    private suspend fun loadDownloadsPlaylist(playlistId: String): Boolean {
        if (playlistId != "LOCAL_DOWNLOADS") return false

        val downloadedSongs = getDownloadedSongsUseCase()
        _songs.value = downloadedSongs
        _continuation.value = null
        _uiState.value = buildSuccessState(
            playlistPage = PlaylistPage(
                playlist = PlaylistItem(
                    id = playlistId,
                    title = "Descargas",
                    author = null,
                    songCountText = "${downloadedSongs.size} canciones",
                    thumbnail = downloadedSongs.firstOrNull()?.thumbnail,
                    playEndpoint = null,
                    shuffleEndpoint = null,
                    radioEndpoint = null
                ),
                songs = downloadedSongs,
                songsContinuation = null,
                continuation = null
            ),
            isFromCache = true,
            isSaved = true,
        )
        return true
    }

    private suspend fun loadLocalPlaylist(playlistId: String): Boolean {
        if (!playlistId.startsWith("LOCAL_")) return false

        val localPlaylist = getCachedPlaylistUseCase.getItem(playlistId)
        val localSongs = getCachedPlaylistUseCase.getSongs(playlistId) ?: emptyList()

        if (localPlaylist == null) return false

        _songs.value = localSongs
        _continuation.value = null
        _uiState.value = buildSuccessState(
            playlistPage = PlaylistPage(
                playlist = localPlaylist,
                songs = localSongs,
                songsContinuation = null,
                continuation = null
            ),
            isFromCache = true,
            isSaved = true,
        )
        return true
    }

    private suspend fun loadCachedPlaylist(playlistId: String): Boolean {
        val cachedSongs = getCachedPlaylistUseCase.getSongs(playlistId)
        val cachedPlaylist = getCachedPlaylistUseCase.getItem(playlistId)

        if (cachedSongs == null || cachedPlaylist == null) return false

        log.info("Cargando playlist desde caché local: $playlistId (${cachedSongs.size} canciones)")
        _songs.value = cachedSongs
        _continuation.value = null
        _uiState.value = buildSuccessState(
            playlistPage = PlaylistPage(
                playlist = cachedPlaylist,
                songs = cachedSongs,
                songsContinuation = null,
                continuation = null
            ),
            isFromCache = true,
            isSaved = isPlaylistSavedUseCase.once(playlistId),
        )
        return true
    }

    private suspend fun loadRemotePlaylist(playlistId: String) {
        log.info("Playlist no cacheada, cargando desde YouTube: $playlistId")
        loadPlaylistUseCase(playlistId)
            .onSuccess { page ->
                _songs.value = page.songs
                _continuation.value = page.songsContinuation?.takeIf { it.isNotBlank() }
                _uiState.value = buildSuccessState(
                    playlistPage = page,
                    isFromCache = false,
                    isSaved = isPlaylistSavedUseCase.once(playlistId),
                )
            }
            .onFailure {
                _uiState.value = PlaylistState.Error(it.message ?: "Error desconocido")
            }
    }

    fun loadPlaylist(playlistId: String) {
        if (_currentPlaylistId.value == playlistId && playlistId != "LOCAL_DOWNLOADS") return
        _currentPlaylistId.value = playlistId

        viewModelScope.launch {
            resetLoadState()

            when {
                loadDownloadsPlaylist(playlistId) -> Unit
                loadLocalPlaylist(playlistId) -> Unit
                loadCachedPlaylist(playlistId) -> Unit
                else -> loadRemotePlaylist(playlistId)
            }
        }
    }

    fun loadMoreSongs() {
        val token = _continuation.value ?: return
        if (_isLoadingMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            var success = false

            repeat(3) { attempt ->
                if (success) return@repeat
                loadPlaylistContinuationUseCase(token)
                .onSuccess { page ->
                        if (page.songs.isNotEmpty()) {
                            _songs.value += page.songs
                        }
                        _continuation.value = page.continuation?.takeIf { it.isNotBlank() }
                        success = true
                    }
                    .onFailure {
                        delay((500L * (attempt + 1)).milliseconds)
                    }
            }

            if (!success) log.warning("Falló la carga de más canciones tras 3 intentos")
            _isLoadingMore.value = false
        }
    }

    suspend fun fetchAllRemainingPages(): List<SongItem> {
        var token = _continuation.value
        val accumulatedSongs = _songs.value.toMutableList()

        while (token != null) {
            var fetched = false
            repeat(3) { attempt ->
                if (fetched) return@repeat

                loadPlaylistContinuationUseCase(token!!)
                    .onSuccess { page ->
                        if (page.songs.isNotEmpty()) {
                            accumulatedSongs.addAll(page.songs)
                            // Actualizamos el flujo de canciones para que el usuario vea progreso
                            _songs.value = accumulatedSongs.toList()
                        }
                        token = page.continuation?.takeIf { it.isNotBlank() }
                        _continuation.value = token
                        fetched = true
                    }
                    .onFailure {
                        delay((500L * (attempt + 1)).milliseconds)
                    }
            }
            if (!fetched) break
        }
        return _songs.value
    }

    fun playAllSongs(shuffle: Boolean = false, onReady: (songs: List<SongItem>, startIndex: Int) -> Unit) {
        val state = _uiState.value as? PlaylistState.Success ?: return
        if (state.isLoadingForPlay) return

        viewModelScope.launch {
            val allSongs = if (_continuation.value != null) {
                log.info("Cargando todas las páginas antes de reproducir...")
                updateSuccess { copy(isLoadingForPlay = true) }
                try {
                    fetchAllRemainingPages()
                } finally {
                    updateSuccess { copy(isLoadingForPlay = false) }
                }
            } else {
                _songs.value
            }

            if (allSongs.isEmpty()) return@launch
            val finalList = if (shuffle) allSongs.shuffled() else allSongs
            onReady(finalList, 0)
        }
    }

    fun toggleSave() {
        val playlistId = _currentPlaylistId.value ?: return
        val state = _uiState.value as? PlaylistState.Success ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            if (state.isSaved) {
                removePlaylistUseCase(playlistId)
                updateSuccess { copy(isSaved = false) }
                log.info("Playlist eliminada de guardados: $playlistId")
            } else {
                updateSuccess { copy(isSaving = true) }
                try {
                    val allSongs = if (_continuation.value != null) {
                        log.info("Cargando todas las páginas antes de guardar...")
                        fetchAllRemainingPages()
                    } else {
                        _songs.value
                    }

                    savePlaylistUseCase(
                        playlist = state.playlistPage.playlist,
                        songs = allSongs
                    )
                    updateSuccess { copy(isSaved = true) }
                    log.info("Playlist guardada localmente: $playlistId (${allSongs.size} canciones)")
                } finally {
                    updateSuccess { copy(isSaving = false) }
                }
            }
        }
    }


    fun downloadPlaylist(onDownloadExecute: (List<SongItem>) -> Unit ) {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = fetchAllRemainingPages()
            onDownloadExecute(songs) // Ejecutamos la función que nos pasaron
        }
    }

    fun removeSongFromPlaylist(songId: String) {
        val playlistId = _currentPlaylistId.value ?: return
        val state = _uiState.value as? PlaylistState.Success ?: return
        if (!playlistId.startsWith("LOCAL_")) return

        viewModelScope.launch {
            removeSongFromPlaylistUseCase(playlistId, songId)
            val updatedSongs = _songs.value.filterNot { it.id == songId }
            _songs.value = updatedSongs
            val updatedPlaylist = state.playlistPage.playlist.copy(
                songCountText = "${updatedSongs.size} canciones"
            )
            updateSuccess {
                copy(
                    playlistPage = state.playlistPage.copy(
                        playlist = updatedPlaylist,
                        songs = updatedSongs,
                    ),
                )
            }
        }
    }

    fun likeSong(songId: String) {
        viewModelScope.launch {
            likeSongUseCase(songId)
                .onSuccess { log.info("Song liked: $songId") }
                .onFailure { log.warning("Failed to like song: ${it.message}") }
        }
    }

    fun dislikeSong(songId: String) {
        viewModelScope.launch {
            dislikeSongUseCase(songId)
                .onSuccess { log.info("Song disliked: $songId") }
                .onFailure { log.warning("Failed to dislike song: ${it.message}") }
        }
    }
}
