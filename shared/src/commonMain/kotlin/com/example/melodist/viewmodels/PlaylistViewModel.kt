package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.repository.MusicRepository
import com.metrolist.innertube.YouTube
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
    private val repository: MusicRepository,
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
        .map { it != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = _continuation.value != null
        )

    /** Actualiza un campo de PlaylistState.Success de forma segura; no-op si el estado no es Success */
    private inline fun updateSuccess(transform: PlaylistState.Success.() -> PlaylistState.Success) {
        _uiState.update { state ->
            if (state is PlaylistState.Success) state.transform() else state
        }
    }

    fun loadPlaylist(playlistId: String) {
        if (_currentPlaylistId.value == playlistId && playlistId != "LOCAL_DOWNLOADS") return
        _currentPlaylistId.value = playlistId

        viewModelScope.launch {
            _uiState.value = PlaylistState.Loading
            _songs.value = emptyList()
            _continuation.value = null

            // Special case: local downloads playlist
            if (playlistId == "LOCAL_DOWNLOADS") {
                val downloadedSongs = repository.getDownloadedSongs()
                _songs.value = downloadedSongs
                _uiState.value = PlaylistState.Success(
                    playlistPage = PlaylistPage(
                        playlist = PlaylistItem(
                            id = "LOCAL_DOWNLOADS",
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
                return@launch
            }

            // 1. Intentar cargar desde caché local
            val cachedSongs = repository.getCachedPlaylistSongs(playlistId)
            val cachedPlaylist = repository.getCachedPlaylistItem(playlistId)

            if (cachedSongs != null && cachedPlaylist != null) {
                log.info("Cargando playlist desde caché local: $playlistId (${cachedSongs.size} canciones)")
                _songs.value = cachedSongs
                _continuation.value = null
                val saved = repository.isPlaylistSavedOnce(playlistId)
                _uiState.value = PlaylistState.Success(
                    playlistPage = PlaylistPage(
                        playlist = cachedPlaylist,
                        songs = cachedSongs,
                        songsContinuation = null,
                        continuation = null
                    ),
                    isFromCache = true,
                    isSaved = saved,
                )
                return@launch
            }

            // 2. No está en caché → buscar en YouTube
            log.info("Playlist no cacheada, cargando desde YouTube: $playlistId")
            YouTube.playlist(playlistId)
                .onSuccess { page ->
                    _songs.value = page.songs
                    _continuation.value = page.songsContinuation ?: page.continuation
                    val saved = repository.isPlaylistSavedOnce(playlistId)
                    _uiState.value = PlaylistState.Success(
                        playlistPage = page,
                        isFromCache = false,
                        isSaved = saved,
                    )
                }
                .onFailure {
                    _uiState.value = PlaylistState.Error(it.message ?: "Error desconocido")
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
                YouTube.playlistContinuation(token)
                    .onSuccess { page ->
                        if (page.songs.isNotEmpty()) {
                            _songs.value += page.songs
                        }
                        _continuation.value = page.continuation
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
        // Usamos una lista local para no disparar recomposiciones constantes del StateFlow
        val accumulatedSongs = _songs.value.toMutableList()

        while (token != null) {
            var fetched = false
            repeat(3) { attempt ->
                if (fetched) return@repeat

                YouTube.playlistContinuation(token!!)
                    .onSuccess { page ->
                        if (page.songs.isNotEmpty()) {
                            accumulatedSongs.addAll(page.songs)
                            // Actualizamos el flujo de canciones para que el usuario vea progreso
                            _songs.value = accumulatedSongs.toList()
                        }
                        token = page.continuation
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
                repository.removePlaylist(playlistId)
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

                    repository.savePlaylistWithSongs(
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
}
