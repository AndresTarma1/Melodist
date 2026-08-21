package example.nucleus.listentogether

import example.nucleus.models.MediaMetadata
import example.nucleus.player.PlaybackState
import example.nucleus.viewmodels.PlayerViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Conecta [ListenTogetherClient] con el [PlayerViewModel] de MusicPlayer.
 *
 * ANFITRIÓN: observa el estado del reproductor (pista / pausa-reanudar / seek) y transmite acciones.
 * INVITADO: aplica las acciones de reproducción entrantes al reproductor local.
 *
 * Adaptado del `ListenTogetherManager` de Metrolist (que se conecta al `Player.Listener` de ExoPlayer);
 * aquí observamos los StateFlows de MusicPlayer en su lugar, ya que la reproducción con mpv es impulsada
 * por eventos/flows.
 */
class ListenTogetherManager(
    private val client: ListenTogetherClient,
) {
    companion object {
        private const val TAG = "[LT-Manager]"
        private const val POSITION_TOLERANCE_MS = 2_000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var player: PlayerViewModel? = null
    private var hostObserverJob: Job? = null

    private var lastSentTrackId: String? = null
    private var lastSentPlaying: Boolean? = null

    @Volatile
    private var isApplyingRemote = false

    // Exponer el estado del cliente para la UI.
    val connectionState = client.connectionState
    val roomState = client.roomState
    val role = client.role
    val userId = client.userId
    val pendingJoinRequests = client.pendingJoinRequests
    val events = client.events

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost

    /** Recopilar eventos del cliente una vez al inicio de la app. */
    fun initialize() {
        scope.launch {
            client.events.collect { event -> runCatching { handleEvent(event) }.onFailure { Napier.e("$TAG handleEvent", it) } }
        }
        scope.launch {
            client.role.collect { refreshObservation() }
        }
    }

    /** Proporcionar (o limpiar) el reproductor. Se llama cuando PlayerViewModel está disponible. */
    fun setPlayer(playerViewModel: PlayerViewModel?) {
        player = playerViewModel
        refreshObservation()
    }

    // ---- Passthrough de la API de sala (usado por la UI) ----
    fun createRoom(username: String) = client.createRoom(username)
    fun joinRoom(code: String, username: String) = client.joinRoom(code, username)
    fun leaveRoom() = client.leaveRoom()
    fun approveJoin(userId: String) = client.approveJoin(userId)
    fun rejectJoin(userId: String) = client.rejectJoin(userId)
    fun kickUser(userId: String) = client.kickUser(userId)
    fun transferHost(userId: String) = client.transferHost(userId)
    fun requestSync() = client.requestSync()

    // ---- Observación del anfitrión ----

    private fun refreshObservation() {
        val pvm = player
        // Los invitados no pueden controlar la reproducción compartida; su pausa/reanudar se convierte en silenciar/activar sonido.
        pvm?.listenTogetherGuestMode = isInRoom && !isHost
        if (pvm != null && isInRoom && isHost) startHostObservation(pvm) else stopHostObservation()
    }

    private fun startHostObservation(pvm: PlayerViewModel) {
        if (hostObserverJob?.isActive == true) return
        Napier.i("$TAG Start host observation")
        lastSentTrackId = pvm.uiState.value.currentSong?.id
        lastSentPlaying = pvm.uiState.value.playbackState == PlaybackState.PLAYING

        hostObserverJob = scope.launch {
            // Cambios de pista
            launch {
                pvm.uiState.map { it.currentSong?.id }.distinctUntilChanged().collect { trackId ->
                    if (!isHost || isApplyingRemote) return@collect
                    if (trackId != null && trackId != lastSentTrackId) {
                        val state = pvm.uiState.value
                        val song = state.currentSong ?: return@collect
                        lastSentTrackId = trackId
                        lastSentPlaying = false
                        Napier.i("$TAG Host track change -> ${song.title}")
                        client.sendPlaybackAction(
                            action = PlaybackActions.CHANGE_TRACK,
                            trackId = trackId,
                            trackInfo = song.toTrackInfo(),
                            queue = state.queue.map { it.toTrackInfo() },
                            queueTitle = "Listen Together",
                        )
                    }
                }
            }
            // Reproducir / pausar
            launch {
                pvm.uiState.map { it.playbackState }.distinctUntilChanged().collect { pbState ->
                    if (!isHost || isApplyingRemote) return@collect
                    val position = pvm.progressState.value.positionMs
                    val trackId = pvm.uiState.value.currentSong?.id
                    when (pbState) {
                        PlaybackState.PLAYING -> if (lastSentPlaying != true) {
                            lastSentPlaying = true
                            client.sendPlaybackAction(PlaybackActions.PLAY, trackId = trackId, position = position)
                        }
                        PlaybackState.PAUSED -> if (lastSentPlaying == true) {
                            lastSentPlaying = false
                            client.sendPlaybackAction(PlaybackActions.PAUSE, trackId = trackId, position = position)
                        }
                        else -> Unit
                    }
                }
            }
            // Búsquedas de posición (seek)
            launch {
                pvm.seekEvents.collect { positionMs ->
                    if (!isHost || isApplyingRemote) return@collect
                    val trackId = pvm.uiState.value.currentSong?.id
                    client.sendPlaybackAction(PlaybackActions.SEEK, trackId = trackId, position = positionMs)
                }
            }
            // Contenido de la cola (agregados/eliminados). Saltar dentro de la misma cola no cambia
            // la lista de ids, así que esto solo se activa en ediciones reales de la cola.
            launch {
                pvm.uiState.map { state -> state.queue.map { it.id } }.distinctUntilChanged().collect { ids ->
                    if (!isHost || isApplyingRemote || ids.isEmpty()) return@collect
                    client.sendPlaybackAction(
                        action = PlaybackActions.SYNC_QUEUE,
                        queue = pvm.uiState.value.queue.map { it.toTrackInfo() },
                        queueTitle = "Listen Together",
                    )
                }
            }
            // Latido del anfitrión: reenvía PLAY con la posición cada pocos segundos mientras
            // reproduce. Es el ancla que permite a un invitado desviado volver a la pista del
            // host (junto con la reconciliación por trackId), aunque no haya ningún cambio de estado.
            launch {
                while (isHost && isActive) {
                    delay(4_000)
                    val state = pvm.uiState.value
                    val song = state.currentSong
                    if (state.playbackState == PlaybackState.PLAYING && song != null) {
                        client.sendPlaybackAction(
                            action = PlaybackActions.PLAY,
                            trackId = song.id,
                            position = pvm.progressState.value.positionMs,
                        )
                    }
                }
            }
        }
    }

    private fun stopHostObservation() {
        hostObserverJob?.cancel()
        hostObserverJob = null
    }

    // ---- Manejo de eventos ----

    private fun handleEvent(event: ListenTogetherEvent) {
        when (event) {
            is ListenTogetherEvent.RoomCreated -> refreshObservation()
            is ListenTogetherEvent.JoinApproved -> applyFullState(
                event.state.currentTrack, event.state.isPlaying, event.state.position, event.state.queue,
            )
            is ListenTogetherEvent.Reconnected -> if (!event.isHost) {
                applyFullState(event.state.currentTrack, event.state.isPlaying, event.state.position, event.state.queue)
            }
            is ListenTogetherEvent.HostChanged -> refreshObservation()
            is ListenTogetherEvent.SyncStateReceived -> if (!isHost) {
                applyFullState(event.state.currentTrack, event.state.isPlaying, event.state.position, event.state.queue)
            }
            is ListenTogetherEvent.UserJoined -> if (isHost) {
                // Enviar la pista actual al nuevo usuario.
                val pvm = player ?: return
                val state = pvm.uiState.value
                val song = state.currentSong ?: return
                client.sendPlaybackAction(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = song.id,
                    trackInfo = song.toTrackInfo(),
                    queue = state.queue.map { it.toTrackInfo() },
                    queueTitle = "Listen Together",
                )
                if (state.playbackState == PlaybackState.PLAYING) {
                    client.sendPlaybackAction(PlaybackActions.PLAY, trackId = song.id, position = pvm.progressState.value.positionMs)
                }
            }
            is ListenTogetherEvent.PlaybackSync -> if (!isHost) handlePlaybackSync(event.action)
            is ListenTogetherEvent.Kicked -> stopHostObservation()
            else -> Unit
        }
    }

    private fun handlePlaybackSync(action: PlaybackActionPayload) {
        val pvm = player ?: return
        when (action.action) {
            PlaybackActions.CHANGE_TRACK -> action.trackInfo?.let { loadTrack(it, action.queue, action.position, autoplay = true) }
            PlaybackActions.PLAY -> applyPlay(action.trackId, action.position)
            PlaybackActions.PAUSE -> applyPause(action.trackId, action.position)
            PlaybackActions.SEEK -> applySeek(action.trackId, action.position)
            PlaybackActions.SKIP_NEXT -> withRemote { pvm.next() }
            PlaybackActions.SKIP_PREV -> withRemote { pvm.previous() }
            PlaybackActions.SYNC_QUEUE -> applyQueueSync(action.queue)
            else -> Unit
        }
    }

    private fun applyFullState(track: TrackInfo?, isPlaying: Boolean, position: Long, queue: List<TrackInfo>) {
        if (isHost) return
        track ?: return
        loadTrack(track, queue, position, autoplay = isPlaying)
    }

    /** Invitado: cargar la cola completa (para que skip/next funcione) posicionado en [currentTrack]. */
    private fun loadTrack(currentTrack: TrackInfo, queue: List<TrackInfo>, position: Long, autoplay: Boolean) {
        val pvm = player ?: return
        Napier.i("$TAG Guest load ${currentTrack.title} @ $position (queue=${queue.size})")
        if (queue.isNotEmpty()) {
            val items = queue.map { it.toMediaMetadata() }
            val index = items.indexOfFirst { it.id == currentTrack.id }
            if (index >= 0) {
                withRemote { pvm.playCustom(items, index) }
            } else {
                // La pista actual no está en la cola recibida (p. ej. host Metrolist manda
                // solo las próximas). Usar Custom con la pista al frente para no disparar Single fetch.
                Napier.w("$TAG Guest: current track not in received queue, playing custom single+queue")
                val single = listOf(currentTrack.toMediaMetadata()) + items
                withRemote { pvm.playCustom(single, 0) }
            }
        } else {
            withRemote { pvm.playCustom(listOf(currentTrack.toMediaMetadata()), 0) }
        }
        // El stream se resuelve de forma asíncrona; hacer seek (y pausar opcionalmente) una vez que esté reproduciendo.
        scope.launch {
            delay(1200)
            if (position > POSITION_TOLERANCE_MS) withRemote { pvm.seekTo(position) }
            if (!autoplay && pvm.uiState.value.playbackState == PlaybackState.PLAYING) {
                withRemote { pvm.togglePlayPause() }
            }
        }
    }

    /** Invitado: sincronizar cola completa con el host sin reiniciar la reproducción.
     *  Metrolist manda cola UPCOMING (sin la actual). Si current no esta en hostIds, anteponerla. */
    private fun applyQueueSync(queue: List<TrackInfo>) {
        if (isHost) return
        val pvm = player ?: return
        if (queue.isEmpty()) return
        val state = pvm.uiState.value
        val current = state.currentSong
        val currentId = current?.id
        val hostIds = queue.map { it.id }
        val currentIds = state.queue.map { it.id }
        if (currentIds == hostIds) return
        if (currentIds.isEmpty()) return // aún no se cargó — CHANGE_TRACK lo llenará

        val hostItems = queue.map { it.toMediaMetadata() }
        // Host manda UPCOMING (sin current) -> anteponer current para no perderla y no adelantar 1
        val fullItems: List<example.nucleus.models.MediaMetadata>
        val newIndex: Int
        if (current != null && currentId != null && currentId !in hostIds) {
            fullItems = listOf(current) + hostItems
            newIndex = 0
        } else {
            fullItems = hostItems
            newIndex = hostIds.indexOf(currentId).let { if (it >= 0) it else state.currentIndex.coerceIn(0, hostIds.lastIndex) }
        }

        // Append puro (hostIds empieza con currentIds)
        if (hostIds.size > currentIds.size && hostIds.subList(0, currentIds.size) == currentIds && fullItems.size == hostIds.size) {
            val newTracks = queue.filter { it.id !in currentIds.toSet() }
            Napier.i("$TAG Guest queue sync: appending ${newTracks.size} track(s)")
            newTracks.forEach { t -> withRemote { pvm.addToQueue(t.toMediaMetadata()) } }
            return
        }

        Napier.i("$TAG Guest queue sync: replacing queue ${currentIds.size} -> ${fullItems.size} (keep index $newIndex)")
        withRemote { pvm.syncQueueFromHost(fullItems, newIndex) }
    }

    private fun applyPlay(trackId: String?, position: Long) {
        val pvm = player ?: return
        // Si el host dice "reproducir" pero localmente estamos en otra pista (p. ej. nos
        // adelantamos por un fallo/fin de pista), volver a la pista del host antes de nada.
        if (guestNeedsTrackReconcile(trackId, pvm.uiState.value.currentSong?.id)) {
            reconcileGuestToHostTrack(trackId ?: return, wantPlaying = true, positionMs = position)
            return
        }
        val cur = pvm.progressState.value.positionMs
        if (kotlin.math.abs(cur - position) > POSITION_TOLERANCE_MS) withRemote { pvm.seekTo(position) }
        if (pvm.uiState.value.playbackState != PlaybackState.PLAYING) withRemote { pvm.togglePlayPause() }
    }

    private fun applyPause(trackId: String?, position: Long) {
        val pvm = player ?: return
        if (guestNeedsTrackReconcile(trackId, pvm.uiState.value.currentSong?.id)) {
            reconcileGuestToHostTrack(trackId ?: return, wantPlaying = false, positionMs = position)
            return
        }
        if (pvm.uiState.value.playbackState == PlaybackState.PLAYING) withRemote { pvm.togglePlayPause() }
        val cur = pvm.progressState.value.positionMs
        if (kotlin.math.abs(cur - position) > POSITION_TOLERANCE_MS) withRemote { pvm.seekTo(position) }
    }

    private fun applySeek(trackId: String?, position: Long) {
        val pvm = player ?: return
        if (guestNeedsTrackReconcile(trackId, pvm.uiState.value.currentSong?.id)) {
            reconcileGuestToHostTrack(trackId ?: return, wantPlaying = pvm.uiState.value.playbackState == PlaybackState.PLAYING, positionMs = position)
            return
        }
        if (kotlin.math.abs(pvm.progressState.value.positionMs - position) > POSITION_TOLERANCE_MS) {
            withRemote { pvm.seekTo(position) }
        }
    }

    /**
     * Verdadero cuando el host adjuntó un [actionTrackId] y el reproductor local no está
     * en esa pista (o no tiene ninguna). Evita aplicar PLAY/PAUSE/SEEK a la canción equivocada.
     */
    private fun guestNeedsTrackReconcile(actionTrackId: String?, localMediaId: String?): Boolean {
        val expected = actionTrackId?.takeIf { it.isNotEmpty() } ?: return false
        return localMediaId == null || localMediaId != expected
    }

    /** Recarga la reproducción del invitado desde el [client.roomState] cuando coincide con la pista esperada. */
    private fun reconcileGuestToHostTrack(expectedTrackId: String, wantPlaying: Boolean, positionMs: Long) {
        val snapshot = client.roomState.value
        val serverTrack = snapshot?.currentTrack
        val queue = snapshot?.queue?.takeIf { it.isNotEmpty() }
        if (serverTrack?.id == expectedTrackId) {
            Napier.w("$TAG Guest: wrong local track — reloading ${serverTrack.title} from room state")
            applyFullState(serverTrack, wantPlaying, positionMs, queue.orEmpty())
        } else {
            Napier.w("$TAG Guest: track mismatch, requesting sync")
            client.requestSync()
        }
    }

    /** Ejecutar [block] con la bandera de aplicación remota activada para que los observadores del anfitrión no lo repliquen. */
    private inline fun withRemote(block: () -> Unit) {
        val pvm = player
        isApplyingRemote = true
        pvm?.allowInternalSync = true
        try {
            block()
        } finally {
            isApplyingRemote = false
            pvm?.allowInternalSync = false
        }
    }
}

private fun MediaMetadata.toTrackInfo(): TrackInfo = TrackInfo(
    id = id,
    title = title,
    artist = artists.joinToString(", ") { it.name },
    album = album?.title ?: "",
    duration = duration * 1000L,
    thumbnail = thumbnailUrl ?: "",
)

private fun TrackInfo.toMediaMetadata(): MediaMetadata = MediaMetadata(
    id = id,
    title = title,
    artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
    duration = (duration / 1000L).toInt(),
    thumbnailUrl = thumbnail.ifEmpty { null },
    album = album.ifEmpty { null }?.let { MediaMetadata.Album(id = "", title = it) },
)
