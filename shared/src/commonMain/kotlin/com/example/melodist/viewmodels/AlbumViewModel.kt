package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.domain.album.*
import com.example.melodist.domain.song.LikeSongUseCase
import com.example.melodist.domain.song.DislikeSongUseCase
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
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

sealed class AlbumState {
    object Loading : AlbumState()
    data class Success(
        val albumPage: AlbumPage,
        val isSaved: Boolean = false,
        val isSaving: Boolean = false,
        val isLoadingForPlay: Boolean = false,
    ) : AlbumState()
    data class Error(val message: String) : AlbumState()
}

class AlbumViewModel(
    private val loadAlbumUseCase: LoadAlbumUseCase,
    private val loadAlbumContinuationUseCase: LoadAlbumContinuationUseCase,
    private val getCachedAlbumUseCase: GetCachedAlbumUseCase,
    private val isAlbumSavedUseCase: IsAlbumSavedUseCase,
    private val saveAlbumUseCase: SaveAlbumUseCase,
    private val removeAlbumUseCase: RemoveAlbumUseCase,
    private val likeSongUseCase: LikeSongUseCase,
    private val dislikeSongUseCase: DislikeSongUseCase
) : ViewModel() {

    private val log = Logger.getLogger("AlbumViewModel")

    private val _uiState = MutableStateFlow<AlbumState>(AlbumState.Loading)
    val uiState: StateFlow<AlbumState> = _uiState.asStateFlow()

    private val _currentBrowseId = MutableStateFlow<String?>(null)

    /** Canciones acumuladas (álbumes normalmente cargan todo de una vez, pero por consistencia) */
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

    /** Actualiza un campo de AlbumState.Success de forma segura; no-op si no es Success */
    private inline fun updateSuccess(transform: AlbumState.Success.() -> AlbumState.Success) {
        _uiState.update { state ->
            if (state is AlbumState.Success) state.transform() else state
        }
    }

    private fun resetLoadState() {
        _uiState.value = AlbumState.Loading
        _songs.value = emptyList()
        _continuation.value = null
        _isLoadingMore.value = false
    }

    private fun buildSuccessState(
        albumPage: AlbumPage,
        isSaved: Boolean,
    ): AlbumState.Success {
        return AlbumState.Success(
            albumPage = albumPage,
            isSaved = isSaved,
        )
    }

    private suspend fun loadCachedAlbum(browseId: String): Boolean {
        val cachedSongs = getCachedAlbumUseCase.getSongs(browseId)
        val cachedAlbum = getCachedAlbumUseCase.getItem(browseId)

        if (cachedSongs == null || cachedAlbum == null) return false

        log.info("Cargando álbum desde caché: $browseId (${cachedSongs.size} canciones)")
        _songs.value = cachedSongs
        _continuation.value = null
        _uiState.value = buildSuccessState(
            albumPage = AlbumPage(
                album = cachedAlbum,
                songs = cachedSongs,
                otherVersions = emptyList()
            ),
            isSaved = true,
        )
        return true
    }

    private suspend fun loadRemoteAlbum(browseId: String) {
        log.info("Álbum no cacheado, cargando desde YouTube: $browseId")
        loadAlbumUseCase(browseId)
            .onSuccess { page ->
                _songs.value = page.songs
                _continuation.value = null
                _uiState.value = buildSuccessState(
                    albumPage = page,
                    isSaved = isAlbumSavedUseCase.once(browseId),
                )
            }
            .onFailure {
                _uiState.value = AlbumState.Error(it.message ?: "Error desconocido")
            }
    }

    fun loadAlbum(browseId: String) {
        if (_currentBrowseId.value == browseId) return
        _currentBrowseId.value = browseId

        viewModelScope.launch {
            resetLoadState()

            if (!loadCachedAlbum(browseId)) {
                loadRemoteAlbum(browseId)
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
                 loadAlbumContinuationUseCase(token)
                 .onSuccess { page ->
                         if (page.songs.isNotEmpty()) _songs.value += page.songs
                         _continuation.value = page.continuation?.takeIf { it.isNotBlank() }
                         success = true
                     }
                     .onFailure { delay((500L * (attempt + 1)).milliseconds) }
             }

            if (!success) log.warning("Falló la carga de más canciones tras 3 intentos")
            _isLoadingMore.value = false
        }
    }

    private suspend fun fetchAllRemainingPages(): List<SongItem> {
        var token = _continuation.value
        while (token != null) {
            var nextToken: String? = null
            var fetched = false

            repeat(3) { attempt ->
                if (fetched) return@repeat
                loadAlbumContinuationUseCase(token)
                    .onSuccess { page ->
                        if (page.songs.isNotEmpty()) _songs.value += page.songs
                        nextToken = page.continuation?.takeIf { it.isNotBlank() }
                        fetched = true
                    }
                    .onFailure {
                        log.warning("Error cargando continuación (intento ${attempt + 1}): ${it.message}")
                        delay((500L * (attempt + 1)).milliseconds)
                    }
            }

            if (!fetched) {
                log.warning("No se pudo cargar una página, abortando")
                break
            }

            token = nextToken
            _continuation.value = token
        }
        return _songs.value
    }

    fun playAllSongs(shuffle: Boolean = false, onReady: (songs: List<SongItem>, startIndex: Int) -> Unit) {
        val state = _uiState.value as? AlbumState.Success ?: return
        if (state.isLoadingForPlay) return

        viewModelScope.launch {
            val allSongs = if (_continuation.value != null) {
                log.info("Cargando todas las páginas antes de reproducir álbum...")
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
        val state = _uiState.value as? AlbumState.Success ?: return
        if (state.isSaving) return

        viewModelScope.launch {
            if (state.isSaved) {
                removeAlbumUseCase(state.albumPage.album.browseId)
                updateSuccess { copy(isSaved = false) }
                log.info("Álbum eliminado de guardados: ${state.albumPage.album.browseId}")
            } else {
                updateSuccess { copy(isSaving = true) }
                try {
                    saveAlbumUseCase(state.albumPage.album, _songs.value)
                    updateSuccess { copy(isSaved = true) }
                    log.info("Álbum guardado con ${_songs.value.size} canciones: ${state.albumPage.album.browseId}")
                } finally {
                    updateSuccess { copy(isSaving = false) }
                }
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
