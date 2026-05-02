package com.example.melodist.domain.library

import com.example.melodist.domain.error.toAppError
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem

data class YtmLibrary(
    val playlists: List<PlaylistItem> = emptyList(),
    val likedSongs: List<SongItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
)

interface LibraryRemoteDataSource {
    suspend fun getLibraryPlaylists(): Result<List<PlaylistItem>>
    suspend fun getLibraryAlbums(): Result<List<AlbumItem>>
    suspend fun getLibraryArtists(): Result<List<ArtistItem>>
    suspend fun getLibraryLikedSongs(): Result<List<SongItem>>
}

class GetYtmLibraryUseCase(
    private val remoteDataSource: LibraryRemoteDataSource,
) {
    suspend operator fun invoke(): Result<YtmLibrary> = runCatching {
        val playlists = remoteDataSource.getLibraryPlaylists().getOrThrow()
        val albums = remoteDataSource.getLibraryAlbums().getOrThrow()
        val artists = remoteDataSource.getLibraryArtists().getOrThrow()
        val likedSongs = remoteDataSource.getLibraryLikedSongs().getOrThrow()
        YtmLibrary(
            playlists = playlists,
            likedSongs = likedSongs,
            albums = albums,
            artists = artists
        )
    }.recoverCatching { throw it.toAppError() }
}
