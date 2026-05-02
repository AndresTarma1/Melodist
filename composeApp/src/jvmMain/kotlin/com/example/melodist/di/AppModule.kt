package com.example.melodist.di

import app.cash.sqldelight.db.SqlDriver
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.local.DatabaseDriverFactory
import com.example.melodist.domain.home.*
import com.example.melodist.domain.playlist.*
import com.example.melodist.domain.song.*
import com.example.melodist.domain.album.*
import com.example.melodist.domain.artist.*
import com.example.melodist.domain.search.*
import com.example.melodist.domain.player.MusicPlayer
import com.example.melodist.domain.player.GetRelatedSongsUseCase
import com.example.melodist.domain.library.*
import com.example.melodist.data.remote.ApiService
import com.example.melodist.data.repository.*
import com.example.melodist.viewmodels.*
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.MusicDatabase
import com.example.melodist.player.AudioStreamResolver
import com.example.melodist.player.DownloadService
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.player.YouTubeSongLikeServiceImpl
import com.example.melodist.player.PlaybackMetadataServiceImpl
import com.example.melodist.domain.player.PlaybackMetadataService
import com.example.melodist.domain.player.UpdatePlaybackMetadataUseCase
import com.example.melodist.utils.SyncUtils
import org.koin.dsl.module

val appModule = module {
    // Database
    single<SqlDriver> { DatabaseDriverFactory.createDriver() }
    single<MelodistDatabase> { MelodistDatabase(get<SqlDriver>()) }

    single<MusicDatabase> { MusicDatabase(get<MelodistDatabase>()) }
    single<DatabaseDao> { get<MusicDatabase>().dao }

    // Data layer
    single<ApiService> { ApiService() }
    single<HomeRemoteDataSource> { get<ApiService>() }
    single<PlaylistRemoteDataSource> { get<ApiService>() }
    single<SearchRemoteDataSource> { get<ApiService>() }
    single<AlbumRemoteDataSource> { get<ApiService>() }
    single<ArtistRemoteDataSource> { get<ApiService>() }
    single<LibraryRemoteDataSource> { get<ApiService>() }

    single<AlbumRepository> { AlbumRepositoryImpl(get()) }
    single<ArtistRepository> { ArtistRepositoryImpl(get()) }
    single<SongRepository> { SongRepositoryImpl(get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get()) }
    single<SearchHistoryRepository> { SearchRepository(get()) }
    single<SyncUtils> { SyncUtils(get(), get(), get(), get(), get()) }

    // Use cases (Clean Architecture)
    factory { LoadHomeUseCase(get()) }
    factory { LoadPlaylistUseCase(get()) }
    factory { LoadPlaylistContinuationUseCase(get()) }
    factory { GetSavedPlaylistsUseCase(get()) }
    factory { LoadAlbumContinuationUseCase(get()) }
    factory { IsPlaylistSavedUseCase(get()) }
    factory { SavePlaylistUseCase(get()) }
    factory { RemovePlaylistUseCase(get()) }
    factory { AddSongToPlaylistUseCase(get()) }
    factory { RemoveSongFromPlaylistUseCase(get()) }
    factory { GetCachedPlaylistUseCase(get()) }
    
    factory { GetSavedSongsUseCase(get()) }
    factory { IsSongSavedUseCase(get()) }
    factory { SaveSongUseCase(get()) }
    factory { RemoveSongUseCase(get()) }
    factory { GetDownloadedSongsUseCase(get()) }

    // Song like/dislike service and use cases
    single<SongLikeService> { YouTubeSongLikeServiceImpl() }
    factory { LikeSongUseCase(get()) }
    factory { DislikeSongUseCase(get()) }
    factory { RemoveLikeSongUseCase(get()) }

    factory { GetSavedAlbumsUseCase(get()) }
    factory { IsAlbumSavedUseCase(get()) }
    factory { SaveAlbumUseCase(get()) }
    factory { RemoveAlbumUseCase(get()) }
    factory { GetCachedAlbumUseCase(get()) }

    factory { GetSavedArtistsUseCase(get()) }
    factory { IsArtistSavedUseCase(get()) }
    factory { SaveArtistUseCase(get()) }
    factory { RemoveArtistUseCase(get()) }
    factory { LoadArtistUseCase(get()) }
    factory { LoadAlbumUseCase(get()) }

    factory { GetYtmLibraryUseCase(get()) }

    factory { GetRelatedSongsUseCase() }

    factory { ObserveSearchHistoryUseCase(get()) }
    factory { AddSearchQueryUseCase(get()) }
    factory { DeleteSearchQueryUseCase(get()) }
    factory { ClearSearchHistoryUseCase(get()) }
    factory { LoadSearchSuggestionsUseCase(get()) }
    factory { SearchUseCase(get()) }
    factory { SearchContinuationUseCase(get()) }

    // Player (singletons — shared across entire app)
    single<PlayerService> { PlayerService() }
    single<MusicPlayer> { get<PlayerService>() }
    single<AudioStreamResolver> { AudioStreamResolver() }
    single<WindowsMediaSession> { WindowsMediaSession() }
    single<DownloadService> { DownloadService(get(), get()) }
    single<PlaybackMetadataService> { PlaybackMetadataServiceImpl(get<WindowsMediaSession>()) }
    factory { UpdatePlaybackMetadataUseCase(get<PlaybackMetadataService>()) }
    single<AppViewModel> { AppViewModel() }
    single<PlayerViewModel> { PlayerViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    single<DownloadViewModel> { DownloadViewModel(get(), get(), get()) }

    // ViewModels — loginState de AccountManager para reaccionar a cambios de sesión
    factory { AccountViewModel() }
    factory { HomeViewModel(get(), loginState = AccountManager.loginState) }
    factory { SearchViewModel(get(), get(), get(), get(), get(), get(), get()) }
    factory { LibraryViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), loginState = AccountManager.loginState) }
    factory { LibrarySongsViewModel(get()) }
    factory { LibraryAlbumsViewModel(get()) }
    factory { LibraryArtistsViewModel(get()) }
    factory { LibraryPlaylistsViewModel(get(), get(), get(), get()) }
     factory { LibraryMixedViewModel(get()) }
     factory { AlbumViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
     factory { PlaylistViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ArtistViewModel(get(), get(), get(), get()) }
    factory { SettingsViewModel(get()) }
}
