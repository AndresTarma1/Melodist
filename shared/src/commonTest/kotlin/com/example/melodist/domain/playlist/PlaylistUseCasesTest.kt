package com.example.melodist.domain.playlist

import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.pages.PlaylistContinuationPage
import com.metrolist.innertube.pages.PlaylistPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PlaylistUseCasesTest {

    private class FakeRemote : PlaylistRemoteDataSource {
        var lastPlaylistId: String? = null
        var lastContinuation: String? = null

        override suspend fun getPlaylist(playlistId: String): Result<PlaylistPage> {
            lastPlaylistId = playlistId
            return Result.success(
                PlaylistPage(
                    playlist = PlaylistItem(
                        id = playlistId,
                        title = "Mix",
                        author = null,
                        songCountText = "0 canciones",
                        thumbnail = null,
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    ),
                    songs = emptyList(),
                    songsContinuation = null,
                    continuation = null,
                )
            )
        }

        override suspend fun getPlaylistContinuation(continuation: String): Result<PlaylistContinuationPage> {
            lastContinuation = continuation
            return Result.success(PlaylistContinuationPage(songs = emptyList(), continuation = null))
        }
    }

    @Test
    fun loadPlaylistUseCase_callsRemoteSource() = runBlocking {
        val remote = FakeRemote()
        val useCase = LoadPlaylistUseCase(remote)

        val result = useCase("PL123").getOrThrow()

        assertEquals("PL123", remote.lastPlaylistId)
        assertEquals("PL123", result.playlist.id)
    }

    @Test
    fun loadPlaylistContinuationUseCase_callsRemoteSource() = runBlocking {
        val remote = FakeRemote()
        val useCase = LoadPlaylistContinuationUseCase(remote)

        val result = useCase("next-token").getOrThrow()

        assertEquals("next-token", remote.lastContinuation)
        assertTrue(result.songs.isEmpty())
    }
}
