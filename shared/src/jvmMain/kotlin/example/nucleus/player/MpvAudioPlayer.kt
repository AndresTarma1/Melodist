package example.nucleus.player

import example.nucleus.platform.Platform
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

class MpvAudioPlayer {
    private val log = Logger.getLogger("MpvAudioPlayer")
    private var handle: MemorySegment? = null

    // Ajustes de audio solicitados antes de que exista el handle nativo (init diferido/perezoso
    // de mpv) — se almacenan aquí y se reaplican en init() para no perder nada.
    private var lastEqualizer: List<Float>? = null
    private var lastLoudnessLufs: Int = -1
    private var lastGapless: Boolean? = null
    private var lastSpeed: Float? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    /** mpv pausó por underrun de red/caché (`paused-for-cache`). */
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var openUriJob: Job? = null

    /** True tras el primer PLAYBACK_RESTART de la carga actual (permite distinguir error mid-stream). */
    @Volatile
    private var playbackEverStarted = false

    // Se emite cuando la pista actual termina naturalmente (mpv END_FILE con razón EOF).
    private val _ended = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ended: SharedFlow<Unit> = _ended.asSharedFlow()

    /** Fallo de stream tras haber iniciado (p. ej. 403 a mitad de canción). */
    private val _streamFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val streamFailed: SharedFlow<Unit> = _streamFailed.asSharedFlow()

    // Resultado de la carga más reciente de openUri: completado por el bucle de eventos de mpv —
    // true en el primer PLAYBACK_RESTART (el nuevo archivo se está decodificando/reproduciendo),
    // false en END_FILE/ERROR (por ejemplo, un 403). Basado en eventos en lugar de sondeo, y
    // inmune al estado residual de la pista anterior.
    @Volatile
    private var loadResult: CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().apply { complete(false) }

    @Volatile
    private var eventThread: Thread? = null
    @Volatile
    private var eventLoopRunning = false

    /**
     * Obtiene el valor de una propiedad de mpv como cadena.
     *
     * @param name El nombre de la propiedad de mpv a obtener.
     * @return El valor de la propiedad como cadena, o `null` si el handle no está inicializado o la propiedad no está disponible.
     */
    private fun getMpvPropertyString(name: String): String? {
        val ptr = handle?.let { MpvLib.mpv_get_property_string(it, name) } ?: return null
        return try {
            ptr.reinterpret(1024).getString(0)
        } finally {
            MpvLib.mpv_free(ptr)
        }
    }

    /**
     * Inicializa la instancia de reproducción de audio de mpv con configuración solo de audio
     * (salida nativa WASAPI en Windows; el backend autodetectado por mpv en otros sistemas).
     *
     * Si la instancia de mpv ya está inicializada, este método retorna inmediatamente. De lo
     * contrario, crea una nueva instancia de mpv y la configura para reproducción de audio estéreo
     * con renderizado de video desactivado y parámetros de búfer optimizados.
     *
     * @throws Exception si la inicialización de mpv falla.
     */
    fun init() {
        if (handle != null) return
        try {
            handle = MpvLib.mpv_create()
            handle?.let {
                // Siempre pasamos URLs de flujo directo completamente resueltas; no dejamos que mpv
                // invoque yt-dlp (su fallback ytdl_hook lanza yt-dlp al fallar la apertura,
                // añadiendo latencia/ruido).
                MpvLib.mpv_set_property_string(it, "ytdl", "no")

                // Configuraciones críticas para estabilidad
                MpvLib.mpv_set_property_string(it, "video", "no")
                MpvLib.mpv_set_property_string(it, "audio-display", "no")
                MpvLib.mpv_set_property_string(it, "audio-channels", "stereo")
                // WASAPI es solo para Windows — forzarlo en Linux/macOS falla silenciosamente al
                // abrir un dispositivo de audio (mpv_initialize tiene éxito, pero la reproducción
                // se bloquea esperando uno que nunca se abre, sin error aparente). En otros
                // sistemas, dejar "ao" sin configurar: la autodetección de mpv
                // (pipewire/pulse/alsa, o coreaudio en macOS) es más robusta que adivinar un backend.
                if (Platform.isWindows) {
                    MpvLib.mpv_set_property_string(it, "ao", "wasapi")
                }

                MpvLib.mpv_set_property_string(it, "initial-audio-sync", "no")
                MpvLib.mpv_set_property_string(it, "hr-seek", "no")

                MpvLib.mpv_initialize(it)

// 3. Límites de memoria y control de búfer optimizado
                MpvLib.mpv_set_property_string(it, "cache", "yes")
                // Margen generoso: redes flojas / googlevideo a mitad de pista sin stall eterno.
                MpvLib.mpv_set_property_string(it, "cache-secs", "45")
                MpvLib.mpv_set_property_string(it, "demuxer-max-bytes", "52428800") // 50 MiB
                MpvLib.mpv_set_property_string(it, "demuxer-max-back-bytes", "10485760") // 10 MiB
                MpvLib.mpv_set_property_string(it, "cache-pause", "yes")
                MpvLib.mpv_set_property_string(it, "cache-pause-initial", "yes")
                MpvLib.mpv_set_property_string(it, "cache-pause-wait", "1.0")

                startEventLoop(it)
            }
            // Reaplicar ajustes solicitados antes de que existiera el handle (init diferido/perezoso).
            lastEqualizer?.let { bands -> setEqualizer(bands) }
            if (lastLoudnessLufs > 0) setLoudness(lastLoudnessLufs)
            lastGapless?.let { enabled -> setGaplessAudio(enabled) }
            lastSpeed?.let { speed -> setSpeed(speed) }
        } catch (e: Exception) {
            log.severe("MpvAudioPlayer init failed: ${e.message}")
            throw e
        }
    }

    /**
     * Hilo en segundo plano que drena la cola de eventos de mpv. Los eventos principales
     * (END_FILE, PLAYBACK_RESTART) se entregan sin observar ninguna propiedad, por lo que no
     * se necesita mpv_observe_property. Controla:
     *  - éxito/fallo de la carga ([loadResult]) — reemplaza la detección basada en sondeo,
     *  - la señal de fin natural de la pista ([ended]) — reemplaza la heurística pos≈dur,
     *  - el estado real de reproducción ([_isPlaying]).
     *  - fallos mid-stream ([streamFailed]) cuando ya hubo PLAYBACK_RESTART.
     */
    private fun startEventLoop(h: MemorySegment) {
        if (eventThread != null) return
        eventLoopRunning = true
        eventThread = Thread({
            while (eventLoopRunning) {
                val evPtr = try {
                    MpvLib.mpv_wait_event(h, -1.0)
                } catch (e: Throwable) {
                    null
                } ?: continue
                val ev = evPtr.reinterpret(64) // mpv_event: event_id(4)+error(4)+reply_userdata(8)+data(8)
                when (ev.get(ValueLayout.JAVA_INT, 0L)) { // event_id @ offset 0
                    MpvLib.MPV_EVENT_SHUTDOWN -> eventLoopRunning = false

                    MpvLib.MPV_EVENT_PLAYBACK_RESTART -> {
                        // El nuevo archivo se está decodificando/reproduciendo (también se dispara
                        // después de búsquedas — inofensivo).
                        playbackEverStarted = true
                        _isBuffering.value = false
                        _isPlaying.value = true
                        loadResult.takeIf { !it.isCompleted }?.complete(true)
                    }

                    MpvLib.MPV_EVENT_END_FILE -> {
                        // datos en offset 16 -> mpv_event_end_file; la razón es su primer int.
                        val dataPtr = ev.get(ValueLayout.ADDRESS, 16L)
                        val reason = if (dataPtr.address() != 0L) {
                            dataPtr.reinterpret(16).get(ValueLayout.JAVA_INT, 0L)
                        } else {
                            -1
                        }
                        when (reason) {
                            MpvLib.MPV_END_FILE_REASON_ERROR -> {
                                val midStream = playbackEverStarted
                                _isPlaying.value = false
                                _isBuffering.value = false
                                loadResult.takeIf { !it.isCompleted }?.complete(false)
                                if (midStream) {
                                    log.warning("mpv END_FILE error mid-stream")
                                    _streamFailed.tryEmit(Unit)
                                }
                            }
                            MpvLib.MPV_END_FILE_REASON_EOF -> {
                                _isPlaying.value = false
                                _isBuffering.value = false
                                loadResult.takeIf { !it.isCompleted }?.complete(true)
                                _ended.tryEmit(Unit)
                            }
                            // STOP/REDIRECT/QUIT: el archivo fue reemplazado o la app se está cerrando — ignorar.
                        }
                    }
                }
            }
        }, "mpv-events").apply { isDaemon = true; start() }
    }
    /**
     * Inicia la reproducción de la URI dada.
     *
     * Cancela cualquier operación de carga pendiente anterior. El resultado está disponible
     * mediante `awaitPlaybackStarted()`.
     *
     * @param uri La URI a cargar.
     */
    fun openUri(uri: String) {
        handle?.let { h ->
            openUriJob?.cancel()
            // Desbloquear cualquier await pendiente de una carga reemplazada, y preparar un nuevo
            // resultado que el bucle de eventos completará (PLAYBACK_RESTART => true,
            // END_FILE/ERROR => false).
            loadResult.takeIf { !it.isCompleted }?.complete(false)
            loadResult = CompletableDeferred()
            playbackEverStarted = false
            // Hasta que el nuevo archivo realmente inicie, reportar que no se reproduce para que la
            // UI se mantenga en LOADING en lugar de cambiar a PLAYING con el estado residual de la
            // pista anterior.
            _isPlaying.value = false
            _isBuffering.value = false
            openUriJob = scope.launch {
                withContext(Dispatchers.IO) {
                    MpvLib.mpv_set_property_string(h, "user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0")
                    MpvLib.mpv_set_property_string(h, "referrer", "https://music.youtube.com")
                    MpvLib.mpv_command(h, arrayOf("loadfile", uri, "replace", null))
                    MpvLib.mpv_set_property_string(h, "pause", "no")
                }
            }
        }
    }

    /**
     * Se suspende hasta que se conozca que la carga más reciente de [openUri] ha iniciado
     * (true) o fallado (false). Resuelto por el bucle de eventos de mpv; [timeoutMs] protege
     * contra una carga que nunca produce un evento (por ejemplo, una conexión bloqueada).
     */
    suspend fun awaitPlaybackStarted(timeoutMs: Long = 10000): Boolean {
        val r = loadResult
        return withTimeoutOrNull(timeoutMs.milliseconds) { r.await() } ?: false
    }

    /**
     * Reanuda la reproducción.
     */
    fun play() {
        handle?.let {
            MpvLib.mpv_set_property_string(it, "pause", "no")
            // El ticker / paused-for-cache afinarán PLAYING vs BUFFERING.
            if (!_isBuffering.value) {
                _isPlaying.value = true
            }
        }
    }

    fun pause() {
        handle?.let {
            MpvLib.mpv_set_property_string(it, "pause", "yes")
            _isPlaying.value = false
            _isBuffering.value = false
        }
    }

    fun stop() {
        handle?.let {
            MpvLib.mpv_command(it, arrayOf("stop", null))
            _isPlaying.value = false
            _isBuffering.value = false
            playbackEverStarted = false
        }
    }

    fun seekTo(percent: Float) {
        handle?.let {
            val position = (percent * 100).coerceIn(0f, 100f)
            MpvLib.mpv_command(it, arrayOf("seek", "$position", "absolute-percent", null))
        }
    }

    /** Seek absoluto en milisegundos (recovery mid-track). */
    fun seekToMs(millis: Long) {
        handle?.let {
            val seconds = (millis.coerceAtLeast(0L) / 1000.0)
            MpvLib.mpv_command(it, arrayOf("seek", "$seconds", "absolute", null))
            MpvLib.mpv_set_property_string(it, "pause", "no")
        }
    }

    /**
     * Lee flags de caché/idle de mpv. Llamar desde el ticker del [PlayerService].
     * @return true si el núcleo está en underrun de buffer (debería mapearse a BUFFERING).
     *
     * [userWantsPlay] solo es true cuando el usuario no pausó manualmente; así
     * `pause`+`core-idle` se interpreta como stall/cache y no como PAUSED.
     */
    fun refreshBufferingState(userWantsPlay: Boolean): Boolean {
        if (handle == null || !userWantsPlay) {
            if (_isBuffering.value) _isBuffering.value = false
            return false
        }
        if (mpvFlagTrue("eof-reached")) {
            _isBuffering.value = false
            return false
        }
        val pausedForCache = mpvFlagTrue("paused-for-cache")
        val coreIdle = mpvFlagTrue("core-idle")
        val pause = mpvFlagTrue("pause")
        // Preferir paused-for-cache; si no, idle+pause con intención de play ≈ stall AO/red.
        val isBuf = pausedForCache ||
            (playbackEverStarted && coreIdle && pause)
        _isBuffering.value = isBuf
        if (isBuf) {
            _isPlaying.value = false
        } else if (!pause && playbackEverStarted) {
            _isPlaying.value = true
        }
        return isBuf
    }

    private fun mpvFlagTrue(name: String): Boolean {
        val v = getMpvPropertyString(name) ?: return false
        return v == "yes" || v == "true" || v == "1"
    }

    var volume: Float
        get() {
            return getMpvPropertyString("volume")?.toFloatOrNull() ?: 100f
        }
        set(value) {
            handle?.let {
                val vol = (value * 100).toInt().coerceIn(0, 100)
                MpvLib.mpv_set_property_string(it, "volume", "$vol")
            }
        }

    fun setSpeed(value: Float) {
        val clamped = value.coerceIn(0.25f, 3f)
        lastSpeed = clamped
        handle?.let {
            MpvLib.mpv_set_property_string(it, "speed", clamped.toString())
        }
    }

    fun setGaplessAudio(enabled: Boolean) {
        lastGapless = enabled
        handle?.let {
            MpvLib.mpv_set_property_string(it, "gapless-audio", if (enabled) "yes" else "no")
        }
    }

    fun setEqualizer(bands: List<Float>) {
        if (bands.size != 5) return
        lastEqualizer = bands
        applyAudioChain()
    }

    /** Nivel LUFS objetivo (-1 = desactivado) para el filtro loudnorm. */
    fun setLoudness(lufs: Int) {
        lastLoudnessLufs = lufs
        applyAudioChain()
    }

    /**
     * Compone la cadena `af` de mpv con el ecualizador (firequalizer) y la normalización
     * (loudnorm). Un filtro no debe pisar al otro: ambos viven en la misma lista separada
     * por comas, con loudnorm primero y el ecualizador después.
     */
    private fun applyAudioChain() {
        handle?.let { mpv ->
            val filters = mutableListOf<String>()

            val lufs = lastLoudnessLufs
            if (lufs > 0) {
                filters += "lavfi=[loudnorm=I=$lufs:TP=-1.5:LRA=11]"
            }

            val bands = lastEqualizer
            if (bands != null && bands.any { it != 0f }) {
                val freqs = listOf(60, 250, 1000, 4000, 12000)
                val entries = bands.mapIndexed { index, gain ->
                    "entry(${freqs[index]},$gain)"
                }.joinToString(";")
                filters += "lavfi=[firequalizer=gain='cubic_interpolate(f)':gain_entry='$entries']"
            }

            MpvLib.mpv_set_property_string(mpv, "af", filters.joinToString(","))
        }
    }

    fun getDuration(): Long {
        val durStr = getMpvPropertyString("duration") ?: "0"
        return ((durStr.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    fun getCurrentPosition(): Long {
        val posStr = getMpvPropertyString("time-pos") ?: "0"
        return ((posStr.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    fun dispose() {
        openUriJob?.cancel()
        eventLoopRunning = false
        handle?.let {
            MpvLib.mpv_wakeup(it) // desbloquear el mpv_wait_event del hilo de eventos
            eventThread = null
            MpvLib.mpv_terminate_destroy(it)
            handle = null
        }
        scope.cancel()
    }
}
