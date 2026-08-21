package example.nucleus.player

import example.nucleus.data.repository.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

class PlayerService(
    private val userPreferences: UserPreferencesRepository,
) {
    private val log = Logger.getLogger("PlayerService")
    private val mpvPlayer = MpvAudioPlayer()
    private val isMpvDisabled = false

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    /**
     * Emite la posición (ms) en la que se detectó un stall / stream muerto para que el
     * ViewModel re-resuelva la URL y haga seek de vuelta.
     */
    private val _recoveryRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val recoveryRequests: SharedFlow<Long> = _recoveryRequests.asSharedFlow()

    private var _previousVolume = 100

    private var initAttempted = false
    private val _mpvError = MutableStateFlow<String?>(null)
    val mpvError: StateFlow<String?> = _mpvError.asStateFlow()
    fun clearMpvError() { _mpvError.value = null }

    /** ¿Hay un medio cargado en mpv? Falso tras arrancar o tras [stop]. */
    @Volatile
    private var hasMedia = false

    fun hasLoadedMedia(): Boolean = hasMedia

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null

    @Volatile
    private var isTransitioning = false

    @Volatile
    private var endNotified = false

    /** El usuario quiere audio (play/resume/load); false solo tras pause/stop manual. */
    @Volatile
    private var userWantsPlay = false

    private var lastPolledPos = -1L
    private var frozenTicks = 0
    private var lastRecoveryAtMs = 0L

    private companion object {
        const val TICK_MS = 400L
        /** ~4.8 s de posición congelada con intención de play → recovery (evita re-resolve temprano). */
        const val STALL_TICKS = 12
        const val RECOVERY_COOLDOWN_MS = 15_000L
        const val MIN_POS_FOR_STALL_MS = 1_500L
    }

    fun init() {
        if (initAttempted) return
        initAttempted = true
        if (isMpvDisabled) {
            log.warning("mpv disabled via -Dmusicplayer.disableMpv=true")
            return
        }
        if (!MpvLib.isAvailable) {
            val msg = MpvLib.getLoadError()?.message ?: "libmpv no disponible"
            _mpvError.value = "No se pudo cargar libmpv-2.dll. $msg — Reinstala la aplicación y verifica tu antivirus."
            log.severe("mpv init skipped: $msg")
            return
        }
        try {
            mpvPlayer.init()
        } catch (e: Throwable) {
            val msg = e.message ?: "desconocido"
            _mpvError.value = "Error al inicializar audio (libmpv): $msg — Reinstala la aplicación."
            log.severe("mpv init failed: $msg")
            return
        }
        startPositionTicker()

        loadSavedVolume()

        scope.launch {
            mpvPlayer.ended.collect {
                if (!endNotified) {
                    endNotified = true
                    userWantsPlay = false
                    frozenTicks = 0
                    _playbackState.value = PlaybackState.ENDED
                }
            }
        }

        scope.launch {
            mpvPlayer.streamFailed.collect {
                if (!hasMedia || isTransitioning) return@collect
                val pos = _position.value.coerceAtLeast(0L)
                log.warning("streamFailed mid-track @${pos}ms — requesting recovery")
                requestRecovery(pos)
            }
        }
    }

    /**
     * Hidrata el volumen guardado en la UI SIN inicializar mpv.
     */
    fun primeVolume() {
        loadSavedVolume()
    }

    private fun loadSavedVolume() {
        val savedVolume = runBlocking {
            userPreferences.readSavedVolume()
        }
        _volume.value = savedVolume
        _previousVolume = savedVolume

        if (!isMpvDisabled) {
            mpvPlayer.volume = savedVolume.toFloat() / 100f
        }
    }

    /**
     * Carga e inicia la reproducción de la URL de audio especificada.
     */
    fun play(url: String) {
        init()
        if (isMpvDisabled || !MpvLib.isAvailable) {
            _playbackState.value = PlaybackState.ERROR
            _position.value = 0L
            _duration.value = 0L
            if (!MpvLib.isAvailable && _mpvError.value == null) {
                _mpvError.value = "libmpv no disponible — reinstala la aplicación."
            }
            return
        }
        try {
            _playbackState.value = PlaybackState.LOADING
            isTransitioning = false
            endNotified = false
            userWantsPlay = true
            frozenTicks = 0
            lastPolledPos = -1L
            _position.value = 0L
            _duration.value = 0L
            hasMedia = true
            mpvPlayer.openUri(url)
        } catch (e: Exception) {
            _playbackState.value = PlaybackState.ERROR
            log.severe("Error al reproducir: ${e.message}")
        }
    }

    /**
     * Reanuda un stream re-resuelto en [seekMs] (anti-stall mid-track).
     */
    fun playFrom(url: String, seekMs: Long) {
        play(url)
        if (seekMs > 500L) {
            scope.launch {
                val started = awaitPlaybackStarted(timeoutMs = 12_000)
                if (started && hasMedia && userWantsPlay) {
                    delay(80)
                    mpvPlayer.seekToMs(seekMs)
                    _position.value = seekMs
                }
            }
        }
    }

    suspend fun awaitPlaybackStarted(timeoutMs: Long = 6000): Boolean {
        return !isMpvDisabled && mpvPlayer.awaitPlaybackStarted(timeoutMs)
    }

    fun pause() {
        isTransitioning = false
        endNotified = false
        userWantsPlay = false
        frozenTicks = 0
        _playbackState.value = PlaybackState.PAUSED
        if (isMpvDisabled) return
        mpvPlayer.pause()
    }

    fun resume() {
        isTransitioning = false
        endNotified = false
        userWantsPlay = true
        frozenTicks = 0
        _playbackState.value = PlaybackState.PLAYING
        if (isMpvDisabled) return
        mpvPlayer.play()
    }

    fun togglePlayPause() {
        when (_playbackState.value) {
            PlaybackState.PLAYING, PlaybackState.BUFFERING, PlaybackState.LOADING -> pause()
            else -> resume()
        }
    }

    fun stop() {
        isTransitioning = false
        endNotified = false
        userWantsPlay = false
        frozenTicks = 0
        lastPolledPos = -1L
        hasMedia = false
        _playbackState.value = PlaybackState.IDLE
        _position.value = 0L
        _duration.value = 0L
        if (isMpvDisabled) return
        mpvPlayer.stop()
    }

    fun seekTo(millis: Long) {
        if (isMpvDisabled) return
        val dur = _duration.value
        if (dur > 0) {
            val endThresholdMs = 1000L
            if (millis < dur - endThresholdMs) {
                endNotified = false
            }
            frozenTicks = 0
            lastPolledPos = millis
            mpvPlayer.seekTo(millis.toFloat() / dur.toFloat())
        } else if (millis >= 0) {
            frozenTicks = 0
            mpvPlayer.seekToMs(millis)
        }
    }

    fun setVolume(value: Int) {
        _volume.value = value
        if (isMpvDisabled) return
        mpvPlayer.volume = value.toFloat() / 100f
    }

    fun toggleMute() {
        if (_volume.value > 0) {
            _previousVolume = _volume.value
            setVolume(0)
        } else {
            setVolume(_previousVolume)
        }
    }

    fun setEqualizer(bands: List<Float>) {
        if (isMpvDisabled) return
        mpvPlayer.setEqualizer(bands)
    }

    /** Normalización de volumen LUFS (-1 = desactivado). */
    fun setLoudness(lufs: Int) {
        if (isMpvDisabled) return
        mpvPlayer.setLoudness(lufs)
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        if (isMpvDisabled) return
        mpvPlayer.setGaplessAudio(enabled)
    }

    fun setPlaybackSpeed(value: Float) {
        if (isMpvDisabled) return
        mpvPlayer.setSpeed(value)
    }

    fun release() {
        tickJob?.cancel()
        if (!isMpvDisabled) {
            mpvPlayer.dispose()
        }
        scope.cancel()
    }

    private fun requestRecovery(atMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastRecoveryAtMs < RECOVERY_COOLDOWN_MS) return
        lastRecoveryAtMs = now
        frozenTicks = 0
        if (_playbackState.value != PlaybackState.LOADING) {
            _playbackState.value = PlaybackState.BUFFERING
        }
        _recoveryRequests.tryEmit(atMs.coerceAtLeast(0L))
    }

    private fun startPositionTicker() {
        if (isMpvDisabled) return
        tickJob = scope.launch {
            while (isActive) {
                try {
                    val state = _playbackState.value
                    val shouldPoll = state == PlaybackState.PLAYING ||
                        state == PlaybackState.LOADING ||
                        state == PlaybackState.BUFFERING ||
                        (userWantsPlay && hasMedia && state != PlaybackState.ENDED)

                    if (shouldPoll) {
                        _duration.value = mpvPlayer.getDuration()
                        val pos = mpvPlayer.getCurrentPosition()
                        _position.value = pos

                        if (!isTransitioning && hasMedia) {
                            val buffering = mpvPlayer.refreshBufferingState(userWantsPlay)
                            val playing = mpvPlayer.isPlaying.value

                            when {
                                !userWantsPlay -> {
                                    // pause/stop manual ya fijaron PAUSED/IDLE
                                }
                                buffering -> {
                                    endNotified = false
                                    if (_playbackState.value != PlaybackState.LOADING) {
                                        _playbackState.value = PlaybackState.BUFFERING
                                    }
                                }
                                playing -> {
                                    endNotified = false
                                    if (_playbackState.value != PlaybackState.PLAYING) {
                                        _playbackState.value = PlaybackState.PLAYING
                                    }
                                }
                                _playbackState.value == PlaybackState.PLAYING ||
                                    _playbackState.value == PlaybackState.BUFFERING -> {
                                    // Sin playing ni buffer claro: no flip a PAUSED si el usuario
                                    // quiere play (evita el “pause fantasma” del stall).
                                    if (_playbackState.value != PlaybackState.BUFFERING) {
                                        _playbackState.value = PlaybackState.BUFFERING
                                    }
                                }
                            }

                            // Watchdog: posición congelada con intención de reproducir.
                            if (userWantsPlay &&
                                pos >= MIN_POS_FOR_STALL_MS &&
                                state != PlaybackState.LOADING &&
                                state != PlaybackState.ENDED
                            ) {
                                if (lastPolledPos >= 0 && kotlin.math.abs(pos - lastPolledPos) < 200L) {
                                    frozenTicks++
                                    if (frozenTicks >= STALL_TICKS) {
                                        log.warning("position stall @${pos}ms (${frozenTicks} ticks) — recovery")
                                        requestRecovery(pos)
                                    }
                                } else {
                                    frozenTicks = 0
                                }
                                lastPolledPos = pos
                            } else if (!userWantsPlay) {
                                frozenTicks = 0
                                lastPolledPos = pos
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // ticker en segundo plano
                }
                delay(TICK_MS.milliseconds)
            }
        }
    }

    fun stopAudioOnly() {
        isTransitioning = true
        endNotified = false
        userWantsPlay = true // vamos a cargar otra URL enseguida
        frozenTicks = 0
        lastPolledPos = -1L
        _position.value = 0L
        _duration.value = 0L
        if (isMpvDisabled) return
        scope.launch {
            try {
                mpvPlayer.pause()
            } catch (_: Throwable) { /* ignorar */ }
        }
    }
}
