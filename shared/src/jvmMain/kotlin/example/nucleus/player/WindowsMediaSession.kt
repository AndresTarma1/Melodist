package example.nucleus.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import io.github.aakira.napier.Napier
import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.logging.Logger

/**
 * Panel de medios de Windows (SMTC) expuesto a `PlayerViewModel`.
 *
 * El SMTC clásico de `dev.toastbits:mediasession` (basado en un `MediaPlayer` sin reproducción)
 * no registra sesión en Windows 11 (26200+): el flyout solo muestra apps que reproducen audio real.
 * En su lugar se usa un helper nativo C++/WinRT (`smtc_bridge.dll`) que crea el SMTC vía
 * `ISystemMediaTransportControlsInterop::GetForWindow(hwnd)` — la vía documentada para apps Win32,
 * que sí registra la sesión sin necesidad de reproducir audio por el propio MediaPlayer.
 *
 * Las operaciones corren en un hilo STA con message pump: el SMTC exige un hilo vivo bombeando
 * mensajes (ahí llegan también los eventos de botón Play/Pausa/Siguiente/Anterior).
 */
class WindowsMediaSession {

    private data class PendingMetadata(
        val title: String,
        val artist: String,
        val album: String,
    )

    private val log = Logger.getLogger("WindowsMediaSession")

    @Volatile private var initialized = false
    @Volatile private var windowHandle = 0L
    private var lastInitError: String? = null

    private var onPlay: (() -> Unit)? = null
    private var onPause: (() -> Unit)? = null
    private var onNext: (() -> Unit)? = null
    private var onPrevious: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null

    private var onPlayCb: VoidCb? = null
    private var onPauseCb: VoidCb? = null
    private var onNextCb: VoidCb? = null
    private var onPreviousCb: VoidCb? = null
    private var onStopCb: VoidCb? = null

    private var pendingMetadata: PendingMetadata? = null
    private var pendingIsPlaying = false
    private var pendingIsPaused = false

    private val bridge: SmtcBridge? by lazy { SmtcBridge.resolve() }

    // ── JNA binding a smtc_bridge.dll ──────────────────────────────────────
    private interface VoidCb : StdCallLibrary.StdCallCallback {
        fun callback()
    }

    private interface SmtcBridge : StdCallLibrary {
        fun smtc_init(hwnd: Pointer): Int
        fun smtc_enable(enabled: Boolean)
        fun smtc_set_playback_state(state: Int)
        fun smtc_set_metadata(title: WString?, artist: WString?, album: WString?)
        fun smtc_set_buttons(play: Boolean, pause: Boolean, next: Boolean, previous: Boolean, stop: Boolean)
        fun smtc_set_callbacks(onPlay: VoidCb?, onPause: VoidCb?, onNext: VoidCb?, onPrevious: VoidCb?, onStop: VoidCb?)
        fun smtc_release()

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

    fun initError(): String? = lastInitError

    fun isInitialized(): Boolean = initialized

    /** HWND de la ventana Tao; necesario para GetForWindow. Lo inyecta App.kt. */
    fun setWindowHandle(hwnd: Long) {
        windowHandle = hwnd
    }

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
        this.onStop = onStop
        if (initialized) attachCallbacks()
    }

    fun setPositionProvider(provider: () -> Long) {
        // La posición del timeline no se expone aún; se conserva la firma para compatibilidad.
    }

    fun initialize() {
        if (initialized) return
        pump.start()
        runOnPump {
            if (initialized) return@runOnPump
            val b = bridge
            if (b == null) {
                lastInitError = "smtc_bridge.dll no disponible"
                log.warning(lastInitError)
                return@runOnPump
            }
            if (windowHandle == 0L) {
                lastInitError = "sin hwnd de ventana"
                log.warning(lastInitError)
                return@runOnPump
            }
            try {
                val rc = b.smtc_init(Pointer(windowHandle))
                if (rc != 0) {
                    lastInitError = "smtc_init hr=0x${rc.toString(16)}"
                    log.warning("Error inicializando MediaSession: $lastInitError")
                    return@runOnPump
                }
                b.smtc_enable(false)
                b.smtc_set_buttons(true, true, true, true, true)
                attachCallbacks()
                initialized = true
                lastInitError = null
                log.info("MediaSession inicializada correctamente")
                Napier.i("[mediasession] inicializada correctamente")
                // La reproducción puede comenzar antes de que termine la inicialización diferida.
                pendingMetadata?.let { applyMetadata(it) }
                applyPlaybackStatus(pendingIsPlaying, pendingIsPaused)
            } catch (error: Throwable) {
                lastInitError = error.message
                log.warning("Error inicializando MediaSession: ${error.message}")
                Napier.e("[mediasession] error inicializando: ${error.message}", error)
            }
        }
    }

    private fun attachCallbacks() {
        val b = bridge ?: return
        onPlayCb = object : VoidCb { override fun callback() { onPlay?.invoke() } }
        onPauseCb = object : VoidCb { override fun callback() { onPause?.invoke() } }
        onNextCb = object : VoidCb { override fun callback() { onNext?.invoke() } }
        onPreviousCb = object : VoidCb { override fun callback() { onPrevious?.invoke() } }
        onStopCb = object : VoidCb { override fun callback() { onStop?.invoke() } }
        b.smtc_set_callbacks(onPlayCb, onPauseCb, onNextCb, onPreviousCb, onStopCb)
    }

    fun updateMetadata(title: String, artist: String, album: String, thumbnailUrl: String? = null) {
        val metadata = PendingMetadata(title, artist, album)
        pendingMetadata = metadata
        if (!initialized) {
            log.info("MediaSession aún no disponible; metadata encolada")
            return
        }
        applyMetadata(metadata)
    }

    private fun applyMetadata(m: PendingMetadata) {
        log.info("Actualizando MediaSession: title='${m.title}', artist='${m.artist}', album='${m.album}'")
        nativeCall("setMetadata") {
            val b = bridge ?: return@nativeCall
            if (m.title.isNotBlank() && m.title != "MusicPlayer") b.smtc_enable(true)
            b.smtc_set_metadata(
                WString(m.title.ifBlank { "MusicPlayer" }),
                WString(m.artist.ifBlank { "Artista desconocido" }),
                WString(m.album ?: ""),
            )
        }
    }

    fun setPlaybackStatus(isPlaying: Boolean, isPaused: Boolean) {
        pendingIsPlaying = isPlaying
        pendingIsPaused = isPaused
        if (!initialized) {
            log.info("MediaSession aún no disponible; estado encolado")
            return
        }
        applyPlaybackStatus(isPlaying, isPaused)
    }

    private fun applyPlaybackStatus(isPlaying: Boolean, isPaused: Boolean) {
        log.info("Actualizando estado MediaSession: playing=$isPlaying, paused=$isPaused")
        nativeCall("setPlaybackStatus") {
            val b = bridge ?: return@nativeCall
            if (isPlaying || isPaused) b.smtc_enable(true)
            val status = when {
                isPlaying -> 3
                isPaused -> 1
                else -> 2
            }
            b.smtc_set_playback_state(status)
        }
    }

    fun resetToIdle() {
        updateMetadata(title = "MusicPlayer", artist = "", album = "")
        setPlaybackStatus(isPlaying = false, isPaused = false)
        nativeCall("setEnabled(false)") { bridge?.smtc_enable(false) } // Oculta el panel de Windows
    }

    fun release() {
        nativeCall("release") { bridge?.smtc_release() }
        pump.stop()
    }

    // ── Hilo STA con message pump ──────────────────────────────────────────
    private fun nativeCall(operation: String, block: () -> Unit) {
        pump.dispatch {
            try {
                block()
            } catch (error: Throwable) {
                log.warning("MediaSession $operation falló: ${error.message}")
            }
        }
    }

    /** Ejecuta [block] en el hilo del message pump y espera a que termine (para initialize). */
    private fun runOnPump(block: () -> Unit) {
        if (pump.onPumpThread()) {
            block()
        } else {
            val done = CountDownLatch(1)
            pump.dispatch {
                try { block() } finally { done.countDown() }
            }
            try { done.await() } catch (_: InterruptedException) {}
        }
    }

    private class MessagePumpThread {
        private val arena = Arena.ofShared()
        private val linker = Linker.nativeLinker()

        private val user32 = SymbolLookup.libraryLookup("user32", arena)
        private val kernel32 = SymbolLookup.libraryLookup("kernel32", arena)

        private val peekMessage = linker.downcallHandle(user32.find("PeekMessageW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        private val translateMessage = linker.downcallHandle(user32.find("TranslateMessage").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
        private val dispatchMessage = linker.downcallHandle(user32.find("DispatchMessageW").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS))
        private val msgWaitForMultipleObjects = linker.downcallHandle(user32.find("MsgWaitForMultipleObjects").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
        private val createEvent = linker.downcallHandle(kernel32.find("CreateEventW").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
        private val setEvent = linker.downcallHandle(kernel32.find("SetEvent").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))

        private val tasks = ConcurrentLinkedQueue<Runnable>()
        private val quitEvent: MemorySegment = createEvent.invokeWithArguments(MemorySegment.NULL, 0, 0, MemorySegment.NULL) as MemorySegment
        @Volatile private var running = false
        private var thread: Thread? = null

        fun onPumpThread(): Boolean = Thread.currentThread() === thread

        fun start() {
            if (running) return
            synchronized(this) {
                if (running) return
                running = true
                thread = Thread {
                    val msg = arena.allocate(48)
                    try {
                        while (running) {
                            while (true) {
                                val task = tasks.poll() ?: break
                                runCatching { task.run() }
                            }
                            val eventRef = arena.allocate(ValueLayout.ADDRESS).also { it.set(ValueLayout.ADDRESS, 0, quitEvent) }
                            msgWaitForMultipleObjects.invokeWithArguments(1, eventRef, 0, 0xFFFFFFFF.toInt(), 0xFF)
                            while ((peekMessage.invokeWithArguments(msg, MemorySegment.NULL, 0, 0, 1) as Int) != 0) {
                                translateMessage.invokeWithArguments(msg)
                                dispatchMessage.invokeWithArguments(msg)
                            }
                        }
                    } finally {
                        running = false
                    }
                }.apply { name = "smtc-message-pump"; isDaemon = true }
                thread?.start()
            }
        }

        fun dispatch(task: Runnable) {
            if (onPumpThread()) {
                task.run()
                return
            }
            if (!running) start()
            tasks.add(task)
            setEvent.invokeWithArguments(quitEvent)
        }

        fun stop() {
            running = false
            if (quitEvent != MemorySegment.NULL) {
                runCatching { setEvent.invokeWithArguments(quitEvent) }
            }
        }
    }

    private val pump = MessagePumpThread()
}
