package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.domain.album.*
import com.example.melodist.domain.artist.*
import com.example.melodist.domain.playlist.*
import com.example.melodist.domain.song.*
import com.example.melodist.domain.library.*
import com.example.melodist.data.repository.dbSongToSongItem
import com.example.melodist.data.repository.savedAlbumToAlbumItem
import com.example.melodist.data.repository.savedArtistToArtistItem
import com.example.melodist.data.repository.savedPlaylistToPlaylistItem
import com.example.melodist.data.repository.savedSongToSongItem
import com.example.melodist.db.MusicDatabase
import com.example.melodist.utils.withMissingMetadataResolved
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi

enum class LibraryTab {
    LIBRARY, ALBUMS, ARTISTS, PLAYLISTS
}

// Estado para el contenido remoto de YTM
sealed class YtmLibraryState {
    data object Idle : YtmLibraryState()
    data object Loading : YtmLibraryState()
    data class Success(
        val playlists: List<PlaylistItem> = emptyList(),
        val likedSongs: List<SongItem> = emptyList(),
        val albums: List<AlbumItem> = emptyList(),
        val artists: List<ArtistItem> = emptyList(),
    ) : YtmLibraryState()
    data class Error(val message: String) : YtmLibraryState()
}

class LibraryViewModel(
    private val getYtmLibraryUseCase: GetYtmLibraryUseCase,
    private val getSavedSongsUseCase: GetSavedSongsUseCase,
    private val getSavedAlbumsUseCase: GetSavedAlbumsUseCase,
    private val getSavedArtistsUseCase: GetSavedArtistsUseCase,
    private val getSavedPlaylistsUseCase: GetSavedPlaylistsUseCase,
    private val removeSongUseCase: RemoveSongUseCase,
    private val removeAlbumUseCase: RemoveAlbumUseCase,
    private val removeArtistUseCase: RemoveArtistUseCase,
    private val removePlaylistUseCase: RemovePlaylistUseCase,
    private val savePlaylistUseCase: SavePlaylistUseCase,
    private val loadAlbumUseCase: LoadAlbumUseCase,
    private val loadPlaylistUseCase: LoadPlaylistUseCase,
    loginState: StateFlow<Boolean>? = null
) : ViewModel() {

    private val _selectedTab = MutableStateFlow<LibraryTab?>(LibraryTab.LIBRARY)
    val selectedTab = _selectedTab.asStateFlow()

    val savedSongs = getSavedSongsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedAlbums = getSavedAlbumsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedArtists = getSavedArtistsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val savedPlaylists = getSavedPlaylistsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val continuation = MutableStateFlow<String?>(null)

    // ── Local DB ────────────────────────────────────────────

    // ── Remote YTM (cuenta) ─────────────────────────────────

    private val _ytmState = MutableStateFlow<YtmLibraryState>(YtmLibraryState.Idle)
    val ytmState: StateFlow<YtmLibraryState> = _ytmState.asStateFlow()

    init {
        // Cargar al inicio si ya hay sesión activa
        loginState?.onEach { isLoggedIn ->
                if (isLoggedIn) loadYtmLibrary()
                else _ytmState.value = YtmLibraryState.Idle
            }?.launchIn(viewModelScope)
    }

    /** Carga la biblioteca remota de YouTube Music:
     *  - Playlists propias (FEmusic_liked_playlists)
     *  - Canciones que le gustan (FEmusic_liked_videos → tabIndex 0)
     *  - Álbumes guardados (FEmusic_library_corpus_track_artists → tabIndex 1)
     *  - Artistas suscritos (FEmusic_library_corpus_track_artists → tabIndex 2)
     */
    fun loadYtmLibrary() {
        _ytmState.value = YtmLibraryState.Loading
        viewModelScope.launch {
            getYtmLibraryUseCase()
                .onSuccess { library ->
                    _ytmState.value = YtmLibraryState.Success(
                        playlists = library.playlists,
                        likedSongs = library.likedSongs,
                        albums = library.albums,
                        artists = library.artists,
                    )
                }
                .onFailure {
                    _ytmState.value = YtmLibraryState.Error(it.message ?: "Error al cargar biblioteca")
                }
        }
    }

    // ── Tabs / local actions ────────────────────────────────

    fun selectTab(tab: LibraryTab) { _selectedTab.value = tab }

    fun selectMixedTab() { _selectedTab.value = LibraryTab.LIBRARY }

    fun removeSong(id: String) { viewModelScope.launch { removeSongUseCase(id) } }
    fun removeAlbum(browseId: String) { viewModelScope.launch { removeAlbumUseCase(browseId) } }
    fun removeArtist(id: String) { viewModelScope.launch { removeArtistUseCase(id) } }
    fun removePlaylist(id: String) { viewModelScope.launch { removePlaylistUseCase(id) } }

    /**
     * Creates a new local playlist
     */
    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            savePlaylistUseCase(playlist)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylistWithSong(name: String, song: SongItem) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = song.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            savePlaylistUseCase(playlist, listOf(song))
        }
    }

    fun refreshYtmLibrary() = loadYtmLibrary()

    fun resolveAlbumSongsForPlayback(
        browseId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            loadAlbumUseCase(browseId).onSuccess { page ->
                val songs = page.songs
                if (songs.isNotEmpty()) onResolved(songs) else onFallback()
            }.onFailure {
                onFallback()
            }
        }
    }

    fun resolvePlaylistSongsForPlayback(
        playlistId: String,
        onResolved: (List<SongItem>) -> Unit,
        onFallback: () -> Unit = {}
    ) {
        viewModelScope.launch {
            loadPlaylistUseCase(playlistId).onSuccess { page ->
                val songs = page.songs
                if (songs.isNotEmpty()) onResolved(songs) else onFallback()
            }.onFailure {
                onFallback()
            }
        }
    }
}


class LibrarySongsViewModel(
    private val getSavedSongsUseCase: GetSavedSongsUseCase,
) : ViewModel() {
    val savedSongs = getSavedSongsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class LibraryAlbumsViewModel(
    private val getSavedAlbumsUseCase: GetSavedAlbumsUseCase,
) : ViewModel() {
    val savedAlbums = getSavedAlbumsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class LibraryArtistsViewModel(
    private val getSavedArtistsUseCase: GetSavedArtistsUseCase,
) : ViewModel() {
    val savedArtists = getSavedArtistsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

class LibraryPlaylistsViewModel(
    private val getSavedPlaylistsUseCase: GetSavedPlaylistsUseCase,
    private val savePlaylistUseCase: SavePlaylistUseCase,
    private val addSongToPlaylistUseCase: AddSongToPlaylistUseCase,
    private val removeSongFromPlaylistUseCase: RemoveSongFromPlaylistUseCase,
) : ViewModel() {
    val savedPlaylists = getSavedPlaylistsUseCase()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val localPlaylists = savedPlaylists.map { playlists ->
        playlists.filter { it.id.startsWith("LOCAL_") }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(ExperimentalUuidApi::class)
    fun createLocalPlaylist(name: String, song: SongItem? = null) {
        viewModelScope.launch {
            val id = "LOCAL_${kotlin.uuid.Uuid.random()}"
            val playlist = PlaylistItem(
                id = id,
                title = name,
                author = Artist(name = "Local", id = null),
                songCountText = null,
                thumbnail = song?.thumbnail,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            savePlaylistUseCase(playlist, song?.let { listOf(it) })
        }
    }

    fun addSongToLocalPlaylist(playlistId: String, song: SongItem) {
        viewModelScope.launch {
            val resolvedSong = withContext(Dispatchers.IO){ song.withMissingMetadataResolved()}
            addSongToPlaylistUseCase(playlistId, resolvedSong)
        }
    }

    fun removeSongFromLocalPlaylist(playlistId: String, songId: String) {
        viewModelScope.launch {
            removeSongFromPlaylistUseCase(playlistId, songId)
        }
    }
}

class LibraryMixedViewModel(
    musicDatabase: MusicDatabase
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val database = musicDatabase.database

    var albums = database.savedAlbumQueries.selectAll()
    var playlists = database.playlistQueries.playlistsByNameAsc()
}
