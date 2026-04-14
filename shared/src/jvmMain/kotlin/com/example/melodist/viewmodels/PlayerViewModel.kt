package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.melodist.data.repository.AudioQuality
import com.example.melodist.player.AudioStreamResolver
import com.example.melodist.player.DownloadService
import com.example.melodist.player.PlaybackState
import com.example.melodist.player.PlayerService
import com.example.melodist.player.WindowsMediaSession
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.logging.Logger

import com.example.melodist.data.repository.UserPreferencesRepository

class PlayerViewModel(
    private val playerService: PlayerService,
    private val streamResolver: AudioStreamResolver,
    private val mediaSession: WindowsMediaSession,
    userPreferences: UserPreferencesRepository
) : ViewModel() {

    val highResCoverArt = userPreferences.highResCoverArt

    private val log = Logger.getLogger("PlayerViewModel")

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Progreso separado: posición + duración se emiten cada segundo.
     *  Al estar en su propio StateFlow, los composables que NO necesitan
     *  la barra de progreso no se recomponen con cada tick. */
    private val _progressState = MutableStateFlow(PlayerProgressState())
    val progressState: StateFlow<PlayerProgressState> = _progressState.asStateFlow()

    private var resolveJob: Job? = null

    init {
        // Sync equalizer preferences
        viewModelScope.launch {
            userPreferences.equalizerBands.collect { bands ->
                playerService.setEqualizer(bands)
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
                        log.warning(">>> Error de reproducción, el usuario puede reintentar manualmente")
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
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
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
        val session = QueueSession(
            source = QueueSource.Single(song.id),
            items = listOf(song),
            order = listOf(0),
            currentIndex = 0
        )
        _uiState.update {
            it.copy(
                currentSong = song,
                queue = session.queueItems(),
                currentIndex = session.currentIndex,
                queueSource = session.source,
                error = null,
                isShuffled = false,
                queueSession = session
            )
        }
        resolveAndPlay(song)
        fetchRelatedQueue(song, session)
    }

    fun playAlbum(songs: List<SongItem>, startIndex: Int = 0, browseId: String, title: String) {
        if (songs.isEmpty()) return
        val source = QueueSource.Album(browseId, title)
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        val session = QueueSession(
            source = source,
            items = songs,
            order = songs.indices.toList(),
            currentIndex = idx
        )
        _uiState.update {
            it.copy(
                currentSong = songs[idx],
                queue = session.queueItems(),
                currentIndex = idx,
                queueSource = source,
                error = null,
                isShuffled = false,
                queueSession = session
            )
        }
        resolveAndPlay(songs[idx])
    }

    fun playPlaylist(songs: List<SongItem>, startIndex: Int = 0, playlistId: String, title: String) {
        if (songs.isEmpty()) return
        val source = QueueSource.Playlist(playlistId, title)
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        val session = QueueSession(
            source = source,
            items = songs,
            order = songs.indices.toList(),
            currentIndex = idx
        )
        _uiState.update {
            it.copy(
                currentSong = songs[idx],
                queue = session.queueItems(),
                currentIndex = idx,
                queueSource = source,
                error = null,
                isShuffled = false,
                queueSession = session
            )
        }
        resolveAndPlay(songs[idx])
    }

    fun playCustom(songs: List<SongItem>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val idx = startIndex.coerceIn(0, songs.lastIndex)
        val session = QueueSession(
            source = QueueSource.Custom,
            items = songs,
            order = songs.indices.toList(),
            currentIndex = idx
        )
        _uiState.update {
            it.copy(
                currentSong = songs[idx],
                queue = session.queueItems(),
                currentIndex = idx,
                queueSource = QueueSource.Custom,
                error = null,
                isShuffled = false,
                queueSession = session
            )
        }
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
        if (state.queueSession.items.isEmpty()) return

        val nextIndex = when (state.repeatMode) {
            RepeatMode.ONE -> state.currentIndex

            RepeatMode.ALL -> (state.currentIndex + 1) % state.queueSession.order.size

            RepeatMode.OFF -> {
                val n = state.currentIndex + 1
                if (n >= state.queueSession.order.size) {
                    stop()
                    return
                }
                n
            }
        }

        // 1️⃣ Actualizar estado
        _uiState.update {
            val updatedSession = it.queueSession.copy(currentIndex = nextIndex)
            it.copy(
                currentIndex = nextIndex,
                currentSong = updatedSession.order.getOrNull(nextIndex)?.let(updatedSession.items::getOrNull),
                queueSession = updatedSession,
                queue = updatedSession.queueItems()
            )
        }

        // 2️⃣ Ejecutar reproducción FUERA del update
        playAtIndex(nextIndex)
    }

    fun previous() {
        val state = _uiState.value
        if (state.queueSession.items.isEmpty()) return

        if (_progressState.value.positionMs > 3000) {
            seekTo(0)
            return
        }

        val prevIndex = when (state.repeatMode) {
            RepeatMode.ONE -> state.currentIndex
            RepeatMode.ALL -> if (state.currentIndex - 1 < 0) state.queueSession.order.lastIndex else state.currentIndex - 1
            RepeatMode.OFF -> {
                val p = state.currentIndex - 1
                if (p < 0) 0 else p
            }
        }
        playAtIndex(prevIndex)
    }

    fun toggleShuffle() {
        _uiState.update { state ->
            val session = state.queueSession
            if (session.items.isEmpty()) return@update state

            if (state.isShuffled) {
                val naturalOrder = session.naturalOrder()
                val currentId = state.currentSong?.id
                val newIndex = naturalOrder.indexOfFirst { session.items.getOrNull(it)?.id == currentId }.coerceAtLeast(0)
                state.copy(
                    queue = naturalOrder.mapNotNull { session.items.getOrNull(it) },
                    currentIndex = newIndex,
                    isShuffled = false,
                    queueSession = session.copy(order = naturalOrder, currentIndex = newIndex)
                )
            } else {
                val currentItem = state.currentSong ?: return@update state
                val currentBaseIndex = session.items.indexOfFirst { it.id == currentItem.id }.coerceAtLeast(0)
                val rest = session.items.indices.filter { it != currentBaseIndex }.shuffled()
                val shuffledOrder = listOf(currentBaseIndex) + rest
                state.copy(
                    queue = shuffledOrder.mapNotNull { session.items.getOrNull(it) },
                    currentIndex = 0,
                    isShuffled = true,
                    queueSession = session.copy(order = shuffledOrder, currentIndex = 0)
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
        _uiState.update { PlayerUiState() }
        mediaSession.resetToIdle()
    }

    // ─── Queue manipulation ────────────────────────────────

    fun addToQueue(song: SongItem) {
        _uiState.update { state ->
            if (state.queueSession.items.isEmpty()) return@update state.copy(queueSession = QueueSession(source = QueueSource.Custom, items = listOf(song), order = listOf(0), currentIndex = 0), queue = listOf(song))
            val session = state.queueSession
            val items = session.items + song
            val order = session.order + items.lastIndex
            state.copy(
                queueSession = session.copy(items = items, order = order),
                queue = order.mapNotNull { items.getOrNull(it) }
            )
        }
    }

    /** Insert song right after the currently playing song (play next). */
    fun playNext(song: SongItem) {
        _uiState.update { state ->
            if (state.queueSession.items.isEmpty()) return@update state.copy(queueSession = QueueSession(source = QueueSource.Custom, items = listOf(song), order = listOf(0), currentIndex = 0), queue = listOf(song))
            val session = state.queueSession
            val items = session.items + song
            val insertedBaseIndex = items.lastIndex
            val insertAt = (state.currentIndex + 1).coerceAtMost(session.order.size)
            val newOrder = session.order.toMutableList().apply { add(insertAt, insertedBaseIndex) }
            state.copy(
                queueSession = session.copy(items = items, order = newOrder),
                queue = newOrder.mapNotNull { items.getOrNull(it) }
            )
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queueSession.order.size) return

        val session = state.queueSession
        val newOrder = session.order.toMutableList().apply { removeAt(index) }
        val newItems = session.items
        val newIndex = when {
            newOrder.isEmpty() -> {
                stop()
                return
            }
            index < state.currentIndex -> state.currentIndex - 1
            index == state.currentIndex -> {
                val nextIdx = index.coerceAtMost(newOrder.lastIndex)
                val nextSong = newOrder.getOrNull(nextIdx)?.let(newItems::getOrNull)
                _uiState.update { it.copy(queue = newOrder.mapNotNull { idx -> newItems.getOrNull(idx) }, currentIndex = nextIdx, currentSong = nextSong, queueSession = session.copy(order = newOrder, currentIndex = nextIdx)) }
                nextSong?.let { resolveAndPlay(it) }
                return
            }
            else -> state.currentIndex
        }
        _uiState.update { it.copy(queue = newOrder.mapNotNull { idx -> newItems.getOrNull(idx) }, currentIndex = newIndex, queueSession = session.copy(order = newOrder, currentIndex = newIndex)) }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val session = state.queueSession
            if (fromIndex < 0 || fromIndex >= session.order.size || toIndex < 0 || toIndex >= session.order.size) return@update state

            val newOrder = session.order.toMutableList()
            val movedItem = newOrder.removeAt(fromIndex)
            newOrder.add(toIndex, movedItem)

            val curIndex = state.currentIndex
            val newCurrentIndex = when {
                fromIndex == curIndex -> toIndex
                curIndex in (fromIndex + 1)..toIndex -> curIndex - 1
                curIndex in toIndex..<fromIndex -> curIndex + 1
                else -> curIndex
            }

            val newSession = session.copy(order = newOrder, currentIndex = newCurrentIndex)
            state.copy(
                queueSession = newSession,
                queue = newSession.queueItems(),
                currentIndex = newCurrentIndex
            )
        }
    }

    fun playAtIndex(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.queueSession.order.size) return

        val song = state.queueSession.order.getOrNull(index)?.let(state.queueSession.items::getOrNull) ?: return
        _uiState.update { it.copy(currentSong = song, currentIndex = index, error = null, queueSession = state.queueSession.copy(currentIndex = index)) }
        resolveAndPlay(song)
    }

    // ─── Internal ──────────────────────────────────────────

    private fun resolveAndPlay(song: SongItem) {
        resolveJob?.cancel()

        resolveJob = viewModelScope.launch {
            _uiState.update { it.copy(playbackState = PlaybackState.LOADING, error = null) }
            playerService.stopAudioOnly()
            
            try {
                val cachedFile = withContext(Dispatchers.IO) {
                    com.example.melodist.player.DownloadService.getCachedFile(song.id)
                }
                if (cachedFile != null) {
                    playerService.play(cachedFile.absolutePath)
                } else {
                    val streamUrl = withContext(Dispatchers.IO) {
                        streamResolver.resolveAudioStream(song.id)
                    }.streamUrl
                    if (!streamUrl.isEmpty()) {
                        playerService.play(streamUrl)
                    } else {
                        _uiState.update { it.copy(error = "No se pudo obtener el audio para \"${song.title}\"") }
                    }
                }
            } catch (e: com.example.melodist.player.AgeRestrictedException) {
                _uiState.update { it.copy(playbackState = PlaybackState.ERROR, error = e.message) }
            } catch (e: Exception) {
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

    private fun fetchRelatedQueue(song: SongItem, sessionSeed: QueueSession) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = WatchEndpoint(videoId = song.id)
                val result = YouTube.next(endpoint).getOrNull() ?: return@launch
                val originalSongId = song.id

                _uiState.update { state ->
                    if (state.currentSong?.id != originalSongId || sessionSeed.source !is QueueSource.Single) return@update state

                    val suggestedCurrent = result.items.find { it.id == originalSongId }
                    val related = result.items.filter { it.id != originalSongId }
                    val items = listOfNotNull(
                        state.currentSong.let {
                            if (it.duration == null && suggestedCurrent?.duration != null) it.copy(duration = suggestedCurrent.duration) else it
                        }
                    ) + related
                    val order = items.indices.toList()

                    state.copy(
                        currentSong = items.firstOrNull(),
                        queue = items,
                        currentIndex = 0,
                        queueSource = QueueSource.Single(originalSongId),
                        isShuffled = false,
                        queueSession = QueueSession(
                            source = QueueSource.Single(originalSongId),
                            items = items,
                            order = order,
                            currentIndex = 0
                        )
                    )
                }
            } catch (_: Exception) { }
        }
    }

    override fun onCleared() {
        playerService.stopAudioOnly()
        super.onCleared()
    }
}
























