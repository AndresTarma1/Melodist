package example.nucleus.player

import dev.toastbits.mediasession.MediaSession
import dev.toastbits.mediasession.MediaSessionMetadata
import dev.toastbits.mediasession.MediaSessionPlaybackStatus
import io.github.aakira.napier.Napier
import java.awt.EventQueue
import java.util.logging.Logger

/**
 * Envoltorio ligero sobre `dev.toastbits:mediasession` para exponer una API sencilla a
 * `PlayerViewModel` y permitir que Windows reconozca a MusicPlayer como reproductor multimedia.
 */
class WindowsMediaSession {

    private data class PendingMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val thumbnailUrl: String?,
    )

    private val log = Logger.getLogger("WindowsMediaSession")
    private var session: MediaSession? = null
    private var positionProvider: () -> Long = { 0L }

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var pendingMetadata: PendingMetadata? = null
    private var pendingIsPlaying = false
    private var pendingIsPaused = false

    /** Último error de [initialize]; se reporta en startup.log si la sesión no llega a crearse. */
    private var lastInitError: String? = null

    fun initError(): String? = lastInitError

    fun setCallbacks(
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onStop: () -> Unit
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onStop = {
            onStop()
        }
        session?.let { attachCallbacks(it) }
    }

    fun setPositionProvider(provider: () -> Long) {
        positionProvider = provider
    }

    fun isInitialized(): Boolean = session != null

    fun initialize() {
        if (session != null) return

        try {
            val created = MediaSession.create(
                getPositionMs = { positionProvider() }
            )
            if (created == null) {
                val reason = "MediaSession.create devolvió null (plataforma no soportada)"
                lastInitError = reason
                log.warning(reason)
                Napier.w("[mediasession] $reason")
                return
            }
            session = created
            attachCallbacks(created)

            created.setIdentity("MusicPlayer")
            created.setDesktopEntry("musicplayer")
            created.setSupportedUriSchemes(listOf("file", "http", "https"))
            created.setSupportedMimeTypes(listOf("audio/mpeg", "audio/x-m4a", "audio/ogg", "audio/webm"))
            created.setEnabled(false)

            // La reproducción puede comenzar antes de que termine la inicialización diferida.
            pendingMetadata?.let { applyMetadata(created, it) }
            applyPlaybackStatus(created, pendingIsPlaying, pendingIsPaused)
            lastInitError = null
            log.info("MediaSession inicializada correctamente")
            Napier.i("[mediasession] inicializada correctamente")
        } catch (error: Throwable) {
            lastInitError = error.message
            log.warning("Error inicializando MediaSession: ${error.message}")
            Napier.e("[mediasession] error inicializando: ${error.message}", error)
        }
    }

    private fun attachCallbacks(session: MediaSession) {
        session.onPlay = { onPlay?.invoke() }
        session.onPause = { onPause?.invoke() }
        session.onNext = { onNext?.invoke() }
        session.onPrevious = { onPrevious?.invoke() }
        session.onStop = { onStop?.invoke() }
    }

    fun updateMetadata(title: String, artist: String, album: String, thumbnailUrl: String? = null) {
        val metadata = PendingMetadata(title, artist, album, thumbnailUrl)
        pendingMetadata = metadata
        val s = session ?: run {
            log.info("MediaSession aún no disponible; metadata encolada")
            return
        }
        applyMetadata(s, metadata)
    }

    private fun applyMetadata(s: MediaSession, metadata: PendingMetadata) {
        log.info("Actualizando MediaSession: title='${metadata.title}', artist='${metadata.artist}', album='${metadata.album}'")
        if (metadata.title.isNotBlank() && metadata.title != "MusicPlayer") {
            nativeCall("setEnabled(true)") { s.setEnabled(true) }
        }

        nativeCall("setMetadata") {
            s.setMetadata(
                MediaSessionMetadata(
                    title = metadata.title.ifBlank { "MusicPlayer" },
                    artist = metadata.artist.ifBlank { "Artista desconocido" },
                    album = metadata.album,
                    art_url = metadata.thumbnailUrl
                )
            )
        }
    }

    fun setPlaybackStatus(isPlaying: Boolean, isPaused: Boolean) {
        pendingIsPlaying = isPlaying
        pendingIsPaused = isPaused
        val s = session ?: run {
            log.info("MediaSession aún no disponible; estado encolado")
            return
        }
        log.info("Actualizando estado MediaSession: playing=$isPlaying, paused=$isPaused")
        applyPlaybackStatus(s, isPlaying, isPaused)
    }

    private fun applyPlaybackStatus(s: MediaSession, isPlaying: Boolean, isPaused: Boolean) {
        if (isPlaying || isPaused) {
            nativeCall("setEnabled(true)") { s.setEnabled(true) }
        }

        val status = when {
            isPlaying -> MediaSessionPlaybackStatus.PLAYING
            isPaused -> MediaSessionPlaybackStatus.PAUSED
            else -> MediaSessionPlaybackStatus.STOPPED
        }
        nativeCall("setPlaybackStatus($status)") { s.setPlaybackStatus(status) }
    }

    private fun nativeCall(operation: String, block: () -> Unit) {
        val invoke = Runnable {
            try {
                block()
            } catch (error: Throwable) {
                log.warning("MediaSession $operation falló: ${error.message}")
            }
        }

        if (EventQueue.isDispatchThread()) {
            invoke.run()
        } else {
            EventQueue.invokeLater(invoke)
        }
    }

    fun resetToIdle() {
        updateMetadata(title = "MusicPlayer", artist = "", album = "", thumbnailUrl = null)
        setPlaybackStatus(isPlaying = false, isPaused = false)
        updateMetadata(title = "", artist = "", album = "")
        session?.let { nativeCall("setEnabled(false)") { it.setEnabled(false) } } // Oculta el panel de Windows
    }

    fun release() {
        session?.let { nativeCall("release") { it.setEnabled(false) } }
    }
}
