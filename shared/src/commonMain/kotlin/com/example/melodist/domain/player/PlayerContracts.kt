package com.example.melodist.domain.player

import com.example.melodist.domain.error.toAppError
import com.example.melodist.models.MediaMetadata
import com.example.melodist.models.toMediaMetadata
import com.example.melodist.player.PlaybackState
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.flow.StateFlow

interface MusicPlayer {
    val playbackState: StateFlow<PlaybackState>
    val position: StateFlow<Long>
    val duration: StateFlow<Long>
    val volume: StateFlow<Int>

    fun play(url: String)
    fun pause()
    fun resume()
    fun togglePlayPause()
    fun stop()
    fun seekTo(millis: Long)
    fun setVolume(value: Int)
    fun setEqualizer(bands: List<Float>)
    fun stopAudioOnly()
}

class GetRelatedSongsUseCase {
    suspend operator fun invoke(songId: String): Result<List<MediaMetadata>> = runCatching {
        val endpoint = WatchEndpoint(videoId = songId)
        val result = YouTube.next(endpoint).getOrThrow()
        result.items.map { it.toMediaMetadata() }
    }.recoverCatching { throw it.toAppError() }
}

interface PlaybackMetadataService {
    suspend fun updateMetadata(song: MediaMetadata): String?
    suspend fun resetMetadata()
}

class UpdatePlaybackMetadataUseCase(
    private val metadataService: PlaybackMetadataService,
) {
    suspend operator fun invoke(song: MediaMetadata): String? {
        return metadataService.updateMetadata(song)
    }

    suspend fun reset() = metadataService.resetMetadata()
}

