@file:Suppress("unused")

package com.example.melodist.data.remote

import com.example.melodist.domain.search.SearchFilterOption
import com.example.melodist.domain.search.SearchPage
import com.example.melodist.domain.search.SearchRemoteDataSource
import com.example.melodist.domain.home.HomeRemoteDataSource
import com.example.melodist.domain.playlist.PlaylistRemoteDataSource
import com.example.melodist.domain.album.AlbumRemoteDataSource
import com.example.melodist.domain.artist.ArtistRemoteDataSource
import com.example.melodist.domain.library.LibraryRemoteDataSource
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.AlbumPage
import com.metrolist.innertube.pages.ArtistPage
import com.metrolist.innertube.pages.HomePage
import com.metrolist.innertube.pages.PlaylistPage
import com.metrolist.innertube.pages.SearchSummaryPage
import com.metrolist.innertube.pages.PlaylistContinuationPage

/**
 * Wrapper around the YouTube (innertube) API.
 * Acts as the single remote data source for the app.
 */
class ApiService : SearchRemoteDataSource, HomeRemoteDataSource, PlaylistRemoteDataSource, AlbumRemoteDataSource, ArtistRemoteDataSource, LibraryRemoteDataSource {

    override suspend fun search(query: String, filter: SearchFilterOption): Result<SearchPage> {
        return YouTube.search(query, filter.toYouTubeFilter()).map { result ->
            SearchPage(items = result.items, continuation = result.continuation)
        }
    }

    override suspend fun searchSummary(query: String): Result<SearchSummaryPage> {
        return YouTube.searchSummary(query)
    }

    override suspend fun searchSuggestions(query: String): Result<com.metrolist.innertube.models.SearchSuggestions> {
        return YouTube.searchSuggestions(query)
    }

    override suspend fun searchContinuation(token: String): Result<SearchPage> {
        return YouTube.searchContinuation(token).map { result ->
            SearchPage(items = result.items, continuation = result.continuation)
        }
    }

    override suspend fun getHome(params: String?, continuation: String?): Result<HomePage> {
        return if (continuation != null) {
            YouTube.home(continuation = continuation)
        } else {
            YouTube.home(params = params)
        }
    }

    override suspend fun getAlbum(browseId: String): Result<AlbumPage> {
        return YouTube.album(browseId)
    }

    override suspend fun getArtist(browseId: String): Result<ArtistPage> {
        return YouTube.artist(browseId)
    }

    override suspend fun getPlaylist(playlistId: String): Result<PlaylistPage> {
        return YouTube.playlist(playlistId)
    }

    override suspend fun getPlaylistContinuation(continuation: String) =
        YouTube.playlistContinuation(continuation)

    override suspend fun getAlbumContinuation(continuation: String): Result<PlaylistContinuationPage> =
        YouTube.playlistContinuation(continuation)

    override suspend fun getLibraryPlaylists(): Result<List<PlaylistItem>> {
        return YouTube.library("FEmusic_liked_playlists").map { page ->
            page.items.filterIsInstance<PlaylistItem>()
        }
    }

    override suspend fun getLibraryAlbums(): Result<List<AlbumItem>> {
        return YouTube.library("FEmusic_liked_albums", tabIndex = 0).map { page ->
            page.items.filterIsInstance<AlbumItem>()
        }
    }

    override suspend fun getLibraryArtists(): Result<List<ArtistItem>> {
        return YouTube.library("FEmusic_library_corpus_artists", tabIndex = 0).map { page ->
            page.items.filterIsInstance<ArtistItem>()
        }
    }

    override suspend fun getLibraryLikedSongs(): Result<List<SongItem>> {
        return YouTube.library("FEmusic_liked_videos").map { page ->
            page.items.filterIsInstance<SongItem>()
        }
    }

    private fun SearchFilterOption.toYouTubeFilter(): YouTube.SearchFilter = when (this) {
        SearchFilterOption.VIDEOS -> YouTube.SearchFilter.FILTER_VIDEO
        SearchFilterOption.SONGS -> YouTube.SearchFilter.FILTER_SONG
        SearchFilterOption.ALBUMS -> YouTube.SearchFilter.FILTER_ALBUM
        SearchFilterOption.ARTISTS -> YouTube.SearchFilter.FILTER_ARTIST
        SearchFilterOption.PLAYLISTS -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
        SearchFilterOption.ALL -> YouTube.SearchFilter.FILTER_VIDEO
    }
}
