package example.nucleus.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import dev.nucleusframework.media.control.MediaControlEvent
import dev.nucleusframework.media.control.MediaControlService
import dev.nucleusframework.media.control.MediaMetadata
import dev.nucleusframework.media.control.MediaPlaybackState
import dev.nucleusframework.media.control.MediaPlaybackStatus
import io.github.aakira.napier.Napier
import java.io.File
import java.util.logging.Logger

/**
 * Media controls del sistema (Now Playing) expuestos a `PlayerViewModel`.
 *
 * Delega en el `nucleus.media-control` de Nucleus (`MediaControlService`), que envuelve:
 *  - Windows: SMTC (System Media Transport Controls)
 *  - Linux:   MPRIS (D-Bus; `playerctl`, indicadores de escritorio)
 *  - macOS:   Now Playing / MPRemoteCommandCenter
 *
 * Sustituye a la implementación custom C++/WinRT (`smtc_bridge.dll`) que solo cubría Windows SMTC.
 * Se conserva `smtc_bridge.dll` únicamente para `smtc_fix_shortcut_aumid`: pone el AppUserModelID
 * del acceso directo del menú Inicio para que el panel de medios muestre "PaltaSound" y no
 * "Aplicación Desconocida" (no es parte de MediaControlService).
 */
class WindowsMediaSession {

    private val log = Logger.getLogger("WindowsMediaSession")

    @Volatile private var configured = false

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var onSeek: ((Long) -> Unit)? = null

    private var positionProvider: (() -> Long)? = null

    // ── JNA binding a smtc_bridge.dll (solo el fix del acceso directo) ──────
    private interface SmtcBridge : StdCallLibrary {
        fun smtc_fix_shortcut_aumid(aumid: WString?): Int

        companion object {
            fun resolve(): SmtcBridge? = runCatching {
                Native.load(findLibrary().absolutePath, SmtcBridge::class.java)
            }.onFailure { Napier.w("[mediasession] smtc_bridge.dll: ${it.message}") }.getOrNull()

            private fun findLibrary(): File {
                val userDir = File(System.getProperty("user.dir"))
                val rootDir = userDir.parentFile
                val possibleDirs = listOf(
                    userDir,
                    File(userDir, "resources"),
                    File(userDir, "app/resources"),
                    File(userDir, "app/app/resources"),
                    File(userDir, "mpv-resources/windows"),
                    File(userDir, "mpv-resources"),
                    File(rootDir, "mpv-resources/windows"),
                    File(rootDir, "mpv-resources"),
                    File(rootDir, "resources"),
                ) + listOfNotNull(System.getProperty("smtc.resources")).flatMap { listOf(File(it), File(it, "windows")) }
                return possibleDirs.map { File(it, "smtc_bridge.dll") }.firstOrNull { it.exists() }
                    ?: error("smtc_bridge.dll no encontrada")
            }
        }
    }

    fun initError(): String? =
        if (MediaControlService.isAvailable()) null else "nucleus.media-control no disponible"

    fun isInitialized(): Boolean = configured

    fun setCallbacks(
        onPlay: () -> Unit,
        onPause: () -> Unit,
        onNext: () -> Unit,
        onPrevious: () -> Unit,
        onStop: () -> Unit,
    ) {
        this.onPlay = onPlay
        this.onPause = onPause
        this.onNext = onNext
        this.onPrevious = onPrevious
        this.onStop = onStop
        attachCallbacks()
    }

    fun setPositionProvider(provider: () -> Long) {
        this.positionProvider = provider
    }

    /** Recibe seek desde el sistema (SeekBy / SetPosition). */
    fun setSeekHandler(handler: (Long) -> Unit) {
        this.onSeek = handler
    }

    /** Configura el backend nativo, repara el acceso directo y registra el listener de eventos. */
    fun initialize() {
        if (configured) return
        configured = true
        if (!MediaControlService.isAvailable()) {
            log.warning("MediaControlService no disponible; media controls desactivados")
            return
        }
        MediaControlService.configure()
        // Asegura el nombre "PaltaSound" en el panel de medios: AppUserModelID del acceso directo.
        runCatching { SmtcBridge.resolve()?.smtc_fix_shortcut_aumid(WString("Tarma.MusicPlayer")) }
        Napier.i("[mediasession] media-control inicializado (${platformLabel()})")
        attachCallbacks()
    }

    private fun platformLabel(): String = when {
        System.getProperty("os.name").lowercase().contains("win") -> "SMTC"
        System.getProperty("os.name").lowercase().contains("linux") -> "MPRIS"
        else -> "Now Playing"
    }

    private fun attachCallbacks() {
        if (!configured || !MediaControlService.isAvailable()) return
        MediaControlService.attach { event ->
            when (event) {
                MediaControlEvent.Play -> onPlay?.invoke()
                MediaControlEvent.Pause -> onPause?.invoke()
                // En esta app onPlay/onPause se cablean a togglePlayPause.
                MediaControlEvent.Toggle -> onPlay?.invoke()
                MediaControlEvent.Next -> onNext?.invoke()
                MediaControlEvent.Previous -> onPrevious?.invoke()
                MediaControlEvent.Stop -> onStop?.invoke()
                is MediaControlEvent.SeekBy -> onSeek?.invoke((positionProvider?.invoke() ?: 0L) + event.offsetMs)
                is MediaControlEvent.SetPosition -> onSeek?.invoke(event.positionMs)
                else -> {}
            }
        }
    }

    fun updateMetadata(title: String, artist: String, album: String, thumbnailUrl: String? = null, durationMs: Long? = null) {
        if (!configured || !MediaControlService.isAvailable()) return
        val coverUrl = thumbnailUrl?.let {
            runCatching { File(it).toURI().toString() }.getOrNull()
        }
        MediaControlService.setMetadata(
            MediaMetadata(
                title = title,
                artist = artist,
                album = album,
                coverUrl = coverUrl,
                duration = durationMs,
            ),
        )
    }

    fun setPlaybackStatus(isPlaying: Boolean, isPaused: Boolean) {
        if (!configured || !MediaControlService.isAvailable()) return
        val status = when {
            isPlaying -> MediaPlaybackStatus.PLAYING
            isPaused -> MediaPlaybackStatus.PAUSED
            else -> MediaPlaybackStatus.STOPPED
        }
        MediaControlService.setPlaybackState(MediaPlaybackState(status, positionProvider?.invoke()))
    }

    fun setVolume(volume: Float) {
        if (!configured || !MediaControlService.isAvailable()) return
        MediaControlService.setVolume(volume.toDouble().coerceIn(0.0, 1.0))
    }

    fun resetToIdle() {
        if (!configured || !MediaControlService.isAvailable()) return
        MediaControlService.setMetadata(MediaMetadata(title = "MusicPlayer", artist = "", album = "", coverUrl = null, duration = null))
        MediaControlService.setPlaybackState(MediaPlaybackState(MediaPlaybackStatus.STOPPED, null))
    }

    fun release() {
        runCatching { MediaControlService.detach() }
        configured = false
    }
}
