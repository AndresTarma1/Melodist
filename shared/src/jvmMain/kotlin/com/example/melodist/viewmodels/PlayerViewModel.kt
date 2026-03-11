package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.data.AppPreferences
import com.example.melodist.data.AudioQuality
import com.example.melodist.player.AudioStreamResolver
import com.example.melodist.player.DownloadService
import com.example.melodist.player.PlaybackState
import com.example.melodist.player.PlayerService
import com.example.melodist.player.StreamQuality
import com.example.melodist.player.WindowsMediaSession
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.logging.Logger

class PlayerViewModel(
    private val playerService: PlayerService,
    private val streamResolver: AudioStreamResolver,
    private val mediaSession: WindowsMediaSession,
) : ViewModel() {

    private val log = Logger.getLogger("PlayerViewModel")

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Progreso separado: posición + duración se emiten cada segundo.
     *  Al estar en su propio StateFlow, los composables que NO necesitan
     *  la barra de progreso no se recomponen con cada tick. */
    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()

    private var resolveJob: Job? = null
    private var originalQueue: List<SongItem> = emptyList()
    private var currentStreamExpiresAt: Long = 0L

    init {
        // Sync audio quality preference → stream resolver
        viewModelScope.launch {
            AppPreferences.audioQuality.collect { pref ->
                streamResolver.quality = when (pref) {
                    AudioQuality.LOW -> StreamQuality.LOW
                    AudioQuality.NORMAL -> StreamQuality.NORMAL
                    AudioQuality.HIGH -> StreamQuality.HIGH
                }
            }
        }
        viewModelScope.launch {
            playerService.playbackState.collect { state ->
                _uiState.update { it.copy(playbackState = state) }

                // Notificar estado de reproducción al overlay de Windows
                mediaSession.setPlaybackStatus(
                    isPlaying = state == PlaybackState.PLAYING,
                    isPaused  = state == PlaybackState.PAUSED
                )

                when (state) {
                    PlaybackState.ENDED -> onTrackEnded()
                    PlaybackState.ERROR -> {
                        val pos = playerService.position.value
                        val dur = playerService.duration.value
                        // If error early (duration not set or position low), retry
                        if (dur == 0L || pos < dur - 10000) {
                            if (System.currentTimeMillis() >= currentStreamExpiresAt - 60000) {
                                log.info("Refrescando url expirada")
                            } else {
                                log.info("Reintentando resolución de stream por error de reproducción")
                            }
                            _uiState.value.currentSong?.let { resolveAndPlay(it) }
                        }
                    }
                    else -> {}
                }
            }
        }
        // Combine position+duration to reduce state emissions (one update instead of two)
        viewModelScope.launch {
            playerService.position
                .combine(playerService.duration) { pos, dur -> pos to dur }
                .distinctUntilChanged()
                .collect { (pos, dur) ->
                    _progressState.update { it.copy(positionMs = pos, durationMs = dur) }
                }
        }
        viewModelScope.launch {
            playerService.volume.collect { vol ->
                _uiState.update { it.copy(volume = vol) }
            }
        }
        // Actualizar metadatos en el overlay de Windows cuando cambia la canción
        viewModelScope.launch {
            _uiState
                .map { it.currentSong }
                .distinctUntilChanged()
                .collect { song ->
                    if (song != null) {
                        // Descargar thumbnail en IO, luego actualizar SMTC con file:// URI
                        viewModelScope.launch(Dispatchers.IO) {
                            val thumbUri = downloadThumbToTemp(song.thumbnail)
                            mediaSession.updateMetadata(
                                title        = song.title,
                                artist       = song.artists.joinToString(", ") { it.name },
                                album        = song.album?.name ?: "",
                                thumbnailUrl = thumbUri
                            )
                        }
                    } else {
                        mediaSession.resetToIdle()
                    }
                }
        }
    }

    /**
     * Descarga el thumbnail a un archivo temporal y devuelve una URI file:///
     * que Windows SMTC puede leer sin necesidad de headers HTTP especiales.
     * Si falla, devuelve la URL HTTP original como fallback.
     */
    private fun downloadThumbToTemp(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout    = 10_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()

            val tmpFile = java.io.File(System.getProperty("java.io.tmpdir"), "melodist_smtc_thumb.jpg")
            tmpFile.writeBytes(bytes)
            // URI file:/// con barras normales (formato Windows Runtime)
            "file:///${tmpFile.absolutePath.replace('\\', '/')}"
        } catch (e: Exception) {
            log.fine("SMTC thumb download failed: ${e.message}")
            url // fallback a HTTP URL
        }
    }

    // ─── Play actions ──────────────────────────────────────

    fun playSingle(song: SongItem) {
        val source = QueueSource.Single(song.id)
        _uiState.update {
            it.copy(
                currentSong = song,
                queue = listOf(song),
                currentIndex = 0,
                queueSource = source,
                error = null
            )
        }
        originalQueue = listOf(song)
        resolveAndPlay(song)
        fetchRelatedQueue(song)
    }

    fun playAlbum(songs: List<SongItem>, startIndex: Int = 0, browseId: String, title: String) {
        if (songs.isEmpty()) return
        val source = QueueSource.Album(browseId, title)
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        _uiState.update {
            it.copy(
                currentSong = songs[idx],
                queue = songs,
                currentIndex = idx,
                queueSource = source,
                error = null,
                isShuffled = false
            )
        }
        originalQueue = songs
        resolveAndPlay(songs[idx])
    }

    fun playPlaylist(songs: List<SongItem>, startIndex: Int = 0, playlistId: String, title: String) {
        if (songs.isEmpty()) return
        val source = QueueSource.Playlist(playlistId, title)
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        _uiState.update {
            it.copy(
                currentSong = songs[idx],
                queue = songs,
                currentIndex = idx,
                queueSource = source,
                error = null,
                isShuffled = false,
                originalQueue = songs
            )
        }
        resolveAndPlay(songs[idx])
    }

    fun playCustom(songs: List<SongItem>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        _uiState.update {
            it.copy(
                currentSong = songs[idx],
                queue = songs,
                currentIndex = idx,
                queueSource = QueueSource.Custom,
                error = null,
                isShuffled = false
            )
        }
        originalQueue = songs
        resolveAndPlay(songs[idx])
    }

    // ─── Transport controls ────────────────────────────────

    fun togglePlayPause() {
        val state = _uiState.value
        if (state.currentSong == null || state.queue.isEmpty() || state.currentIndex !in state.queue.indices) {
            mediaSession.resetToIdle()
            return
        }
        playerService.togglePlayPause()
    }

    fun seekTo(millis: Long) {
        playerService.seekTo(millis)
    }

    fun setVolume(value: Int) {
        playerService.setVolume(value)
    }

    fun next() {

        val state = _uiState.value
        if (state.queue.isEmpty()) return

        val nextIndex = when (state.repeatMode) {
            RepeatMode.ONE -> state.currentIndex

            RepeatMode.ALL -> (state.currentIndex + 1) % state.queue.size

            RepeatMode.OFF -> {
                val n = state.currentIndex + 1
                if (n >= state.queue.size) {
                    stop()
                    return
                }
                n
            }
        }

        // 1️⃣ Actualizar estado
        _uiState.update {
            it.copy(
                currentIndex = nextIndex,
                currentSong = it.queue[nextIndex]
            )
        }

        // 2️⃣ Ejecutar reproducción FUERA del update
        playAtIndex(nextIndex)
    }

    fun previous() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        if (_progressState.value.positionMs > 3000) {
            seekTo(0)
            return
        }

        val prevIndex = when (state.repeatMode) {
            RepeatMode.ONE -> state.currentIndex
            RepeatMode.ALL -> if (state.currentIndex - 1 < 0) state.queue.lastIndex else state.currentIndex - 1
            RepeatMode.OFF -> {
                val p = state.currentIndex - 1
                if (p < 0) 0 else p
            }
        }
        playAtIndex(prevIndex)
    }

    fun toggleShuffle() {
        _uiState.update { state ->
            if (state.isShuffled) {
                // Restaurar orden original manteniendo la canción actual
                val currentId = state.currentSong?.id
                val newIndex = state.originalQueue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
                state.copy(
                    queue = state.originalQueue,
                    currentIndex = newIndex,
                    isShuffled = false
                )
            } else {
                // Crear orden aleatorio manteniendo la canción actual al inicio
                val rest = state.originalQueue
                    .filter { it.id != state.currentSong?.id }
                    .shuffled()
                val shuffled = listOfNotNull(state.currentSong) + rest
                state.copy(
                    queue = shuffled,
                    currentIndex = 0,
                    isShuffled = true
                )
            }
        }
    }

    fun toggleRepeat() {
        _uiState.update {
            it.copy(
                repeatMode = when (it.repeatMode) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                }
            )
        }
    }

    fun stop() {
        resolveJob?.cancel()
        playerService.stop()
        _progressState.value = PlayerProgressState()
        originalQueue = emptyList()
        _uiState.update { PlayerUiState() }
        mediaSession.resetToIdle()
    }

    // ─── Queue manipulation ────────────────────────────────

    fun addToQueue(song: SongItem) {
        _uiState.update { it.copy(queue = it.queue + song) }
        originalQueue = originalQueue + song
    }

    /** Insert song right after the currently playing song (play next). */
    fun playNext(song: SongItem) {
        _uiState.update { state ->
            val insertAt = (state.currentIndex + 1).coerceAtMost(state.queue.size)
            val newQueue = state.queue.toMutableList().apply { add(insertAt, song) }
            state.copy(queue = newQueue)
        }
        val insertAt = (_uiState.value.currentIndex).coerceAtMost(originalQueue.size)
        originalQueue = originalQueue.toMutableList().apply { add(insertAt, song) }
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queue.size) return

        val newQueue = state.queue.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            newQueue.isEmpty() -> {
                stop()
                return
            }
            index < state.currentIndex -> state.currentIndex - 1
            index == state.currentIndex -> {
                val nextIdx = index.coerceAtMost(newQueue.lastIndex)
                _uiState.update { it.copy(queue = newQueue, currentIndex = nextIdx, currentSong = newQueue[nextIdx]) }
                resolveAndPlay(newQueue[nextIdx])
                return
            }
            else -> state.currentIndex
        }
        _uiState.update { it.copy(queue = newQueue, currentIndex = newIndex) }
    }

    fun playAtIndex(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queue.size) return

        val song = state.queue[index]
        _uiState.update { it.copy(currentSong = song, currentIndex = index, error = null) }
        resolveAndPlay(song)
    }

    // ─── Internal ──────────────────────────────────────────

    private fun resolveAndPlay(song: SongItem) {
        resolveJob?.cancel()

        resolveJob = viewModelScope.launch {
            _uiState.update { it.copy(playbackState = PlaybackState.LOADING) }
            playerService.stopAudioOnly()
            try {
                // Check local cache first
                val cachedFile = DownloadService.getCachedFile(song.id)
                if (cachedFile != null) {
                    currentStreamExpiresAt = Long.MAX_VALUE // cached doesn't expire
                    playerService.play(cachedFile.absolutePath)
                    return@launch
                }

                // No local cache — resolve from network
                val stream = withContext(Dispatchers.IO) {
                    streamResolver.resolveAudioStream(song.id)
                }
                if (stream != null) {
                    currentStreamExpiresAt = System.currentTimeMillis() + (stream.expiresInSeconds ?: 0L) * 1000L
                    playerService.play(stream.url)
                } else {
                    log.warning("No audio stream for ${song.id}")
                    _uiState.update { it.copy(error = "No se pudo obtener el audio para \"${song.title}\"") }
                }
            } catch (e: Exception) {
                log.warning("Resolve failed: ${song.id} — ${e.message}")
                _uiState.update { it.copy(error = e.message) }
            }

        }
    }

    private fun onTrackEnded() {
        val state = _uiState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                state.currentSong?.let { resolveAndPlay(it) }
            }
            RepeatMode.ALL -> {
                next()
            }
            RepeatMode.OFF -> {
                if (state.currentIndex < state.queue.lastIndex) {
                    next()
                }
            }
        }
    }

    private fun fetchRelatedQueue(song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = WatchEndpoint(videoId = song.id)
                val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                val originalSongId = song.id

                _uiState.update { state ->
                    if (state.currentSong?.id == originalSongId) {
                        val newSongs = result.items
                            .filter { it.id != originalSongId }

                        state.copy(
                            queue = state.queue + newSongs,
                            originalQueue = state.originalQueue + newSongs
                        )
                    } else state
                }
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        playerService.stopAudioOnly()
        super.onCleared()
    }
}




















