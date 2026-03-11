package com.example.melodist.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

class PlayerService {

    private val log = Logger.getLogger("PlayerService")

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private var factory: MediaPlayerFactory? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vlcAvailable = false
    private var initAttempted = false

    private val vlcDispatcher = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "vlc-native").also { it.isDaemon = true }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(vlcDispatcher + SupervisorJob())
    private var tickJob: Job? = null

    @Volatile
    private var isTransitioning = false

    fun init() {
        if (initAttempted) return
        initAttempted = true

        try {
            val bundledPath = findBundledVlc()
            if (bundledPath != null) {
                System.setProperty("jna.library.path", bundledPath)
                val pluginsDir = File(bundledPath, "plugins")
                if (pluginsDir.isDirectory) System.setProperty("VLC_PLUGIN_PATH", pluginsDir.absolutePath)
            } else {
                NativeDiscovery().discover()
            }

            factory = MediaPlayerFactory("--no-video", "--quiet", "--no-lua")
            mediaPlayer = factory!!.mediaPlayers().newMediaPlayer()
            attachListeners()
            startPositionTicker()
            vlcAvailable = true
        } catch (e: Exception) {
            log.log(Level.SEVERE, "Error crítico iniciando VLC", e)
            vlcAvailable = false
        }
    }

    private fun findBundledVlc(): String? {
        val userDir = File(System.getProperty("user.dir"))
        val resourcesDir = System.getProperty("compose.application.resources.dir")
        val candidates = mutableListOf<File>()

        if (!resourcesDir.isNullOrBlank()) {
            candidates.add(File(resourcesDir))
            candidates.add(File(resourcesDir, "windows"))
        }

        var current: File? = userDir
        while (current != null) {
            candidates.add(File(current, "vlc-resources/windows"))
            current = current.parentFile
        }

        for (dir in candidates) {
            if (dir.exists() && dir.resolve("libvlc.dll").exists()) return dir.absolutePath
        }
        return null
    }

    fun play(url: String) {
        init()
        // No hagas release() aquí, solo detén la media actual
        scope.launch {
            try {
                mediaPlayer?.controls()?.stop()
                _playbackState.value = PlaybackState.LOADING
                isTransitioning = false
                mediaPlayer?.media()?.play(url)
            } catch (e: Exception) {
                _playbackState.value = PlaybackState.ERROR
            }
        }
    }

    fun pause() { scope.launch { mediaPlayer?.controls()?.pause() } }
    fun resume() { scope.launch { mediaPlayer?.controls()?.play() } }
    fun togglePlayPause() { if (_playbackState.value == PlaybackState.PLAYING) pause() else resume() }

    fun stop() {
        isTransitioning = false
        _playbackState.value = PlaybackState.IDLE
        _position.value = 0L
        _duration.value = 0L
        scope.launch { mediaPlayer?.controls()?.stop() }
    }

    fun seekTo(millis: Long) {
        val dur = _duration.value
        if (dur > 0) scope.launch { mediaPlayer?.controls()?.setPosition(millis.toFloat() / dur.toFloat()) }
    }

    fun setVolume(value: Int) {
        _volume.value = value
        scope.launch { mediaPlayer?.audio()?.setVolume(value) }
    }

    fun release() {
        tickJob?.cancel()
        // Liberamos solo al final, al cerrar la app
        mediaPlayer?.release()
        factory?.release()
        mediaPlayer = null
        factory = null
        scope.cancel()
        vlcDispatcher.close()
    }

    private fun attachListeners() {
        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mp: MediaPlayer) { _playbackState.value = PlaybackState.PLAYING }
            override fun paused(mp: MediaPlayer) { _playbackState.value = PlaybackState.PAUSED }
            override fun stopped(mp: MediaPlayer) { if (!isTransitioning) _playbackState.value = PlaybackState.IDLE }
            override fun finished(mp: MediaPlayer) { _playbackState.value = PlaybackState.ENDED }
            override fun error(mp: MediaPlayer) { _playbackState.value = PlaybackState.ERROR }
            override fun lengthChanged(mp: MediaPlayer, newLength: Long) { _duration.value = newLength }
        })
    }

    private fun startPositionTicker() {
        tickJob = scope.launch {
            while (isActive) {
                // Acceso seguro: no hacemos nada si el media player es null o está siendo liberado
                val mp = mediaPlayer
                if (mp != null && _playbackState.value == PlaybackState.PLAYING) {
                    try {
                        val dur = mp.status().length()
                        _duration.value = dur
                        _position.value = (mp.status().position() * dur).toLong()
                    } catch (e: Throwable) { /* Capturamos cualquier error nativo */ }
                }
                delay(1000)
            }
        }
    }

    fun stopAudioOnly() {
        isTransitioning = true
        scope.launch {
            try {
                // Verificación de seguridad antes de llamar a métodos nativos
                mediaPlayer?.let { mp ->
                    if (mp.status().isPlaying) {
                        mp.controls().stop()
                    }
                }
            } catch (e: Throwable) {
                log.warning("Intento de detener audio falló de forma segura: ${e.message}")
            }
        }
    }
}