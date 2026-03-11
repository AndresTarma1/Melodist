package com.example.melodist.di

import app.cash.sqldelight.db.SqlDriver
import com.example.melodist.data.account.AccountManager
import com.example.melodist.data.local.DatabaseDriverFactory
import com.example.melodist.data.remote.ApiService
import com.example.melodist.data.repository.MusicRepository
import com.example.melodist.data.repository.SearchRepository
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.MusicDatabase
import com.example.melodist.player.AudioStreamResolver
import com.example.melodist.player.DownloadService
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.example.melodist.viewmodels.AccountViewModel
import com.example.melodist.viewmodels.AlbumViewModel
import com.example.melodist.viewmodels.ArtistViewModel
import com.example.melodist.viewmodels.DownloadViewModel
import com.example.melodist.viewmodels.HomeViewModel
import com.example.melodist.viewmodels.LibraryViewModel
import com.example.melodist.viewmodels.PlayerViewModel
import com.example.melodist.viewmodels.PlaylistViewModel
import com.example.melodist.viewmodels.SearchViewModel
import org.koin.dsl.module
import java.util.logging.Logger

val appModule = module {
    val log = Logger.getLogger("AppModule")

    // Database
    single<SqlDriver> { DatabaseDriverFactory.createDriver() }
    single<MelodistDatabase> { MelodistDatabase(get<SqlDriver>()) }

    single<MusicDatabase> { MusicDatabase(get<MelodistDatabase>()) }
    single<DatabaseDao> { get<MusicDatabase>().dao }

    // Data layer
    single<ApiService> { ApiService() }
    single<MusicRepository> { MusicRepository(get()) }
    single<SearchRepository> { SearchRepository(get()) }

    // Player (singletons — shared across entire app)
    single<PlayerService> { PlayerService() }
    single<AudioStreamResolver> { AudioStreamResolver() }
    single<WindowsMediaSession> { WindowsMediaSession() }
    single<DownloadService> { DownloadService(get(), get()) }
    single<PlayerViewModel> { PlayerViewModel(get(), get(), get()) }
    single<DownloadViewModel> { DownloadViewModel(get(), get()) }

    // ViewModels — loginState de AccountManager para reaccionar a cambios de sesión
    factory { AccountViewModel() }
    factory { HomeViewModel(loginState = AccountManager.loginState) }
    factory { SearchViewModel(get()) }
    factory { LibraryViewModel(get(), loginState = AccountManager.loginState) }
    factory { AlbumViewModel(get()) }
    factory { PlaylistViewModel(get()) }
    factory { ArtistViewModel(get()) }
}