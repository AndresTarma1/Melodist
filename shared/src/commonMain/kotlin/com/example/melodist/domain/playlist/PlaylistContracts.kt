package com.example.melodist.domain.playlist

import com.example.melodist.domain.error.toAppError
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.PlaylistContinuationPage
import com.metrolist.innertube.pages.PlaylistPage
import kotlinx.coroutines.flow.Flow

interface PlaylistRemoteDataSource {
    suspend fun getPlaylist(playlistId: String): Result<PlaylistPage>
    suspend fun getPlaylistContinuation(continuation: String): Result<PlaylistContinuationPage>
}

interface PlaylistRepository {
    fun getSavedPlaylists(): Flow<List<PlaylistItem>>
    fun isPlaylistSaved(id: String): Flow<Boolean>
    suspend fun isPlaylistSavedOnce(id: String): Boolean
    suspend fun savePlaylist(playlist: PlaylistItem)
    suspend fun removePlaylist(id: String)
    suspend fun savePlaylistWithSongs(playlist: PlaylistItem, songs: List<SongItem>)
    suspend fun addSongToPlaylist(playlistId: String, song: SongItem): Unit?
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Unit?
    suspend fun getCachedPlaylistSongs(playlistId: String): List<SongItem>?
    suspend fun getCachedPlaylistItem(playlistId: String): PlaylistItem?
}

class LoadPlaylistUseCase(
    private val remoteDataSource: PlaylistRemoteDataSource,
) {
    suspend operator fun invoke(playlistId: String): Result<PlaylistPage> {
        return remoteDataSource.getPlaylist(playlistId)
            .recoverCatching { throw it.toAppError() }
    }
}

class LoadPlaylistContinuationUseCase(
    private val remoteDataSource: PlaylistRemoteDataSource,
) {
    suspend operator fun invoke(continuation: String): Result<PlaylistContinuationPage> {
        return remoteDataSource.getPlaylistContinuation(continuation)
            .recoverCatching { throw it.toAppError() }
    }
}

class GetSavedPlaylistsUseCase(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(): Flow<List<PlaylistItem>> = repository.getSavedPlaylists()
}

class IsPlaylistSavedUseCase(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(id: String): Flow<Boolean> = repository.isPlaylistSaved(id)
    suspend fun once(id: String): Boolean = repository.isPlaylistSavedOnce(id)
}

class SavePlaylistUseCase(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlist: PlaylistItem, songs: List<SongItem>? = null) {
        if (songs == null) {
            repository.savePlaylist(playlist)
        } else {
            repository.savePlaylistWithSongs(playlist, songs)
        }
    }
}

class RemovePlaylistUseCase(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(id: String) = repository.removePlaylist(id)
}

class AddSongToPlaylistUseCase(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: String, song: SongItem) = repository.addSongToPlaylist(playlistId, song)
}

class RemoveSongFromPlaylistUseCase(
    private val repository: PlaylistRepository,
) {
    suspend operator fun invoke(id: String, songId: String) = repository.removeSongFromPlaylist(id, songId)
}

class GetCachedPlaylistUseCase(
    private val repository: PlaylistRepository,
) {
    suspend fun getSongs(playlistId: String): List<SongItem>? = repository.getCachedPlaylistSongs(playlistId)
    suspend fun getItem(playlistId: String): PlaylistItem? = repository.getCachedPlaylistItem(playlistId)
}
