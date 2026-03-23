package com.example.melodist.viewmodels


import androidx.lifecycle.ViewModel

import androidx.lifecycle.viewModelScope

import com.example.melodist.db.DatabaseDao

import com.example.melodist.db.entities.SongEntity

import com.example.melodist.db.entities.SongWithRelations

import com.example.melodist.player.DownloadService

import com.example.melodist.player.DownloadState

import com.metrolist.innertube.models.Album

import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.SharingStarted

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.firstOrNull

import kotlinx.coroutines.flow.map

import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.flow.Flow

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.delay

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**

 * ViewModel that exposes download state to the UI.

 * Registered as a singleton in Koin so download state is shared app-wide.

 */

class DownloadViewModel(

    private val downloadService: DownloadService,

    private val databaseDao: DatabaseDao? = null

) : ViewModel() {


    /** Per-song download states (songId → DownloadState). */

    val downloadStates: StateFlow<Map<String, DownloadState>> =

        downloadService.downloadStates


    /** Total cache size as human-readable string. */

    private val _cacheSizeText = MutableStateFlow("Calculando...")

    val cacheSizeText: StateFlow<String> = _cacheSizeText.asStateFlow()


    /** Downloaded songs from DB as SongItems (for the Library Downloads tab). */

    val downloadedSongs: StateFlow<List<SongItem>> = databaseDao?.downloadedSongsWithRelations()

        ?.map { relations -> relations.map { it.toSongItem() } }

        ?.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        ?: MutableStateFlow<List<SongItem>>(emptyList())


    /** Initialization state for the downloads tab content. */

    private val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()


    init {

        viewModelScope.launch {

// Wait for the first actual query result (or empty if DB is actually empty) before turning off loading state.

            delay(300) // Small buffer to allow DB to initialize

            downloadedSongs.firstOrNull()

            _isLoading.value = false

        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {

            refreshCacheSize()

        }

    }


    /** Total downloaded song count. */

    val downloadedCount: StateFlow<Int> = downloadedSongs

        .map { it.size }

        .stateIn(viewModelScope, SharingStarted.Lazily, 0)


    /** Songs currently being downloaded (metadata for UI display). */

    val pendingSongItems: StateFlow<Map<String, SongItem>> =

        downloadService.pendingSongItems

            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())


    /**

     * Albums that are fully downloaded (all songs present).

     * Groups downloaded songs by albumId, then checks if the count matches the album's songCount in DB.

     * Only includes albums that exist in the DB with a valid songCount > 0.

     */

    data class DownloadedAlbumInfo(

        val albumId: String,

        val albumName: String,

        val thumbnail: String?,

        val songs: List<SongItem>,

        val totalSongCount: Int

    )

    val fullyDownloadedAlbums: StateFlow<List<DownloadedAlbumInfo>> = downloadedSongs

        .map { songs ->

            withContext(kotlinx.coroutines.Dispatchers.IO) {

                val albumIds = songs.mapNotNull { it.album?.id }.distinct()

                albumIds.mapNotNull { albumId ->

                    val albumEntity = databaseDao?.albumById(albumId)?.firstOrNull() ?: return@mapNotNull null

                    val totalCount = albumEntity.songCount

                    if (totalCount <= 0) return@mapNotNull null


                    val downloadedCount = databaseDao?.countDownloadedByAlbum(albumId) ?: 0L

                    if (downloadedCount >= totalCount.toLong()) {

                        DownloadedAlbumInfo(

                            albumId = albumId,

                            albumName = albumEntity.title,

                            thumbnail = albumEntity.thumbnailUrl
                                ?: songs.firstOrNull { it.album?.id == albumId }?.thumbnail,

                            songs = songs.filter { it.album?.id == albumId },

                            totalSongCount = totalCount

                        )

                    } else null

                }.sortedBy { it.albumName }

            }

        }

        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    /**

     * Playlists that are fully downloaded (all mapped songs downloaded).

     */

    data class DownloadedPlaylistInfo(

        val playlistId: String,

        val playlistName: String,

        val thumbnail: String?,

        val downloadedSongCount: Int,

        val totalSongCount: Int

    )


    val fullyDownloadedPlaylists: StateFlow<List<DownloadedPlaylistInfo>> = downloadedSongs

        .map { dlSongs ->

            withContext(kotlinx.coroutines.Dispatchers.IO) {

                val playlists = databaseDao?.allPlaylists()?.firstOrNull() ?: emptyList()

                playlists.mapNotNull { playlist ->

                    val total = databaseDao?.countByPlaylist(playlist.id) ?: 0L

                    if (total == 0L) return@mapNotNull null

                    val downloaded = databaseDao?.countDownloadedByPlaylist(playlist.id) ?: 0L



                    if (total == downloaded) {

                        DownloadedPlaylistInfo(

                            playlistId = playlist.browseId ?: playlist.id,

                            playlistName = playlist.name,

                            thumbnail = playlist.thumbnailUrl,

                            downloadedSongCount = downloaded.toInt(),

                            totalSongCount = playlist.remoteSongCount ?: total.toInt()

                        )

                    } else null

                }

            }

        }

        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


// ─── Actions ───────────────────────────────────────────


    fun downloadSong(song: SongItem) {

        downloadService.downloadSong(song)

    }


    fun downloadAll(songs: List<SongItem>) {

        downloadService.downloadAll(songs)

    }


    fun cancelDownload(songId: String) {

        downloadService.cancelDownload(songId)

    }


    fun removeDownload(songId: String) {

        downloadService.removeDownload(songId)

        refreshCacheSize()

    }


    fun clearCache() {

        downloadService.clearCache()

        refreshCacheSize()

    }


    fun isDownloaded(songId: String): Boolean {

        return downloadService.isDownloaded(songId)

    }


    fun getState(songId: String): DownloadState? {

        return downloadStates.value[songId]

    }


    fun refreshCacheSize() {

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {

            val size = DownloadService.getCacheSizeBytes()

            _cacheSizeText.value = DownloadService.formatSize(size)

        }

    }


    /** Returns a flow for a single song's download state, distinct until changed. */

    fun downloadStateFlow(songId: String): Flow<DownloadState?> =

        downloadStates.map { it[songId] }.distinctUntilChanged()


    fun isAnyDownloadingFlow(songIds: List<String>): Flow<Boolean> =

        downloadStates.map { states ->

            songIds.any { id ->

                val state = states[id]

                state is DownloadState.Queued || state is DownloadState.Downloading

            }

        }.distinctUntilChanged()


    /** Returns a flow that emits true if ALL of the provided songs are completely downloaded. */

    fun isFullyDownloadedFlow(songIds: List<String>): Flow<Boolean> =

        downloadStates.map { states ->

            if (songIds.isEmpty()) false
            else songIds.all { id -> states[id] is DownloadState.Completed }

        }.distinctUntilChanged()

}


/** Converts a [SongWithRelations] to a [SongItem] for UI display, including artists. */

private fun SongWithRelations.toSongItem(): SongItem = SongItem(

    id = song.id,

    title = song.title,

    artists = artists.map { Artist(name = it.name, id = it.id) },

    album = if (song.albumName != null && song.albumId != null) Album(
        name = song.albumName, id = song.albumId
    ) else null,

    duration = if (song.duration > 0) song.duration else null,

    thumbnail = song.thumbnailUrl ?: "",

    explicit = song.explicit

)


/** Converts a [SongEntity] to a [SongItem] (simplified, no artists). */

private fun SongEntity.toSongItem(): SongItem = SongItem(

    id = id,

    title = title,

    artists = emptyList(),

    album = if (albumName != null && albumId != null) Album(name = albumName, id = albumId) else null,

    duration = if (duration > 0) duration else null,

    thumbnail = thumbnailUrl ?: "",

    explicit = explicit

)

