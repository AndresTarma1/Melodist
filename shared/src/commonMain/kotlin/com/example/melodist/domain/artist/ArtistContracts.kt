package com.example.melodist.domain.artist

import com.example.melodist.domain.error.toAppError
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.pages.ArtistPage
import kotlinx.coroutines.flow.Flow

interface ArtistRemoteDataSource {
    suspend fun getArtist(browseId: String): Result<ArtistPage>
}

interface ArtistRepository {
    fun getSavedArtists(): Flow<List<ArtistItem>>
    fun isArtistSaved(id: String): Flow<Boolean>
    suspend fun saveArtist(artist: ArtistItem)
    suspend fun removeArtist(id: String)
}

class LoadArtistUseCase(
    private val remoteDataSource: ArtistRemoteDataSource,
) {
    suspend operator fun invoke(browseId: String): Result<ArtistPage> {
        return remoteDataSource.getArtist(browseId)
            .recoverCatching { throw it.toAppError() }
    }
}

class GetSavedArtistsUseCase(
    private val repository: ArtistRepository,
) {
    operator fun invoke(): Flow<List<ArtistItem>> = repository.getSavedArtists()
}

class IsArtistSavedUseCase(
    private val repository: ArtistRepository,
) {
    operator fun invoke(id: String): Flow<Boolean> = repository.isArtistSaved(id)
}

class SaveArtistUseCase(
    private val repository: ArtistRepository,
) {
    suspend operator fun invoke(artist: ArtistItem) = repository.saveArtist(artist)
}

class RemoveArtistUseCase(
    private val repository: ArtistRepository,
) {
    suspend operator fun invoke(id: String) = repository.removeArtist(id)
}
