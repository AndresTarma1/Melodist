package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.repository.MusicRepository
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

enum class LibraryTab {
    SONGS, ALBUMS, ARTISTS, PLAYLISTS, DOWNLOADS
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
    private val repository: MusicRepository,
    loginState: StateFlow<Boolean>? = null
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.SONGS)
    val selectedTab = _selectedTab.asStateFlow()

    // ── Local DB ────────────────────────────────────────────

    val songs: StateFlow<List<SongItem>> = repository.getSavedSongs()
        .map { list -> list.map { repository.savedSongToSongItem(it) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val albums: StateFlow<List<AlbumItem>> = repository.getSavedAlbums()
        .map { list -> list.map { repository.savedAlbumToAlbumItem(it) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<ArtistItem>> = repository.getSavedArtists()
        .map { list -> list.map { repository.savedArtistToArtistItem(it) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playlists: StateFlow<List<PlaylistItem>> = repository.getSavedPlaylists()
        .map { list -> list.map { repository.savedPlaylistToPlaylistItem(it) } }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
            try {
                // Playlists
                val playlists = YouTube.library("FEmusic_liked_playlists")
                    .getOrNull()?.items?.filterIsInstance<PlaylistItem>() ?: emptyList()

                // Canciones con "Me gusta" (liked songs — tabIndex 0)
                val likedSongs = YouTube.library("FEmusic_liked_videos", tabIndex = 0)
                    .getOrNull()?.items?.filterIsInstance<SongItem>() ?: emptyList()

                // Álbumes guardados (tabIndex 1)
                val ytmAlbums = YouTube.library("FEmusic_liked_albums", tabIndex = 0)
                    .getOrNull()?.items?.filterIsInstance<AlbumItem>() ?: emptyList()

                // Artistas suscritos (tabIndex 2)
                val ytmArtists = YouTube.library("FEmusic_library_corpus_artists", tabIndex = 0)
                    .getOrNull()?.items?.filterIsInstance<ArtistItem>() ?: emptyList()

                _ytmState.value = YtmLibraryState.Success(
                    playlists = playlists,
                    likedSongs = likedSongs,
                    albums = ytmAlbums,
                    artists = ytmArtists,
                )
            } catch (e: Exception) {
                _ytmState.value = YtmLibraryState.Error(e.message ?: "Error al cargar biblioteca")
            }
        }
    }

    // ── Tabs / local actions ────────────────────────────────

    fun selectTab(tab: LibraryTab) { _selectedTab.value = tab }

    fun removeSong(id: String) { viewModelScope.launch { repository.removeSong(id) } }
    fun removeAlbum(browseId: String) { viewModelScope.launch { repository.removeAlbum(browseId) } }
    fun removeArtist(id: String) { viewModelScope.launch { repository.removeArtist(id) } }
    fun removePlaylist(id: String) { viewModelScope.launch { repository.removePlaylist(id) } }

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
                songCountText = "0 canciones",
                thumbnail = null,
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null
            )
            repository.savePlaylist(playlist)
        }
    }

    fun refreshYtmLibrary() = loadYtmLibrary()
}
