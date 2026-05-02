package com.example.melodist.domain.album

import com.example.melodist.domain.error.toAppError
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.PlaylistContinuationPage
import kotlinx.coroutines.flow.Flow

interface AlbumRemoteDataSource {
    suspend fun getAlbum(browseId: String): Result<AlbumPage>
    suspend fun getAlbumContinuation(continuation: String): Result<PlaylistContinuationPage>
}

interface AlbumRepository {
    fun getSavedAlbums(): Flow<List<AlbumItem>>
    fun isAlbumSaved(id: String): Flow<Boolean>
    suspend fun isAlbumSavedOnce(id: String): Boolean
    suspend fun saveAlbum(album: AlbumItem)
    suspend fun saveAlbumWithSongs(album: AlbumItem, songs: List<SongItem>)
    suspend fun removeAlbum(id: String)
    suspend fun getCachedAlbumSongs(browseId: String): List<SongItem>?
    suspend fun getCachedAlbumItem(browseId: String): AlbumItem?
}

class LoadAlbumUseCase(
    private val remoteDataSource: AlbumRemoteDataSource,
) {
    suspend operator fun invoke(browseId: String): Result<AlbumPage> {
        return remoteDataSource.getAlbum(browseId)
            .recoverCatching { throw it.toAppError() }
    }
}

class LoadAlbumContinuationUseCase(
    private val remoteDataSource: AlbumRemoteDataSource,
) {
    suspend operator fun invoke(continuation: String): Result<PlaylistContinuationPage> {
        return remoteDataSource.getAlbumContinuation(continuation)
            .recoverCatching { throw it.toAppError() }
    }
}

class GetSavedAlbumsUseCase(
    private val repository: AlbumRepository,
) {
    operator fun invoke(): Flow<List<AlbumItem>> = repository.getSavedAlbums()
}

class IsAlbumSavedUseCase(
    private val repository: AlbumRepository,
) {
    operator fun invoke(id: String): Flow<Boolean> = repository.isAlbumSaved(id)
    suspend fun once(id: String): Boolean = repository.isAlbumSavedOnce(id)
}

class SaveAlbumUseCase(
    private val repository: AlbumRepository,
) {
    suspend operator fun invoke(album: AlbumItem, songs: List<SongItem>? = null) {
        if (songs == null) {
            repository.saveAlbum(album)
        } else {
            repository.saveAlbumWithSongs(album, songs)
        }
    }
}

class RemoveAlbumUseCase(
    private val repository: AlbumRepository,
) {
    suspend operator fun invoke(id: String) = repository.removeAlbum(id)
}

class GetCachedAlbumUseCase(
    private val repository: AlbumRepository,
) {
    suspend fun getSongs(browseId: String): List<SongItem>? = repository.getCachedAlbumSongs(browseId)
    suspend fun getItem(browseId: String): AlbumItem? = repository.getCachedAlbumItem(browseId)
}
