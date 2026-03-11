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
                log.info("INICIALIZANDO CON VLC EMBEBIDO: $bundledPath")
                System.setProperty("jna.library.path", bundledPath)
                val pluginsDir = File(bundledPath, "plugins")
                if (pluginsDir.isDirectory) {
                    System.setProperty("VLC_PLUGIN_PATH", pluginsDir.absolutePath)
                }
            } else {
                log.info("No se encontró VLC embebido, usando fallback de NativeDiscovery...")
                NativeDiscovery().discover()
            }

            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            factory = MediaPlayerFactory(
                "--no-video",
                "--quiet",
                "--no-lua",
                "--http-user-agent=$userAgent"
            )
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
            candidates.add(File(current, "composeApp/vlc-resources/windows"))
            current = current.parentFile
        }

        for (dir in candidates) {
            if (!dir.exists()) continue
            val libVlc = dir.resolve("libvlc.dll")
            if (libVlc.exists()) return dir.absolutePath
        }
        return null
    }

    fun play(url: String) {
        init()
        if (!vlcAvailable || mediaPlayer == null) return
        _playbackState.value = PlaybackState.LOADING
        isTransitioning = false
        scope.launch {
            try {
                if (mediaPlayer!!.status().isPlaying) mediaPlayer!!.controls().stop()
                mediaPlayer!!.media().play(url)
            } catch (e: Exception) { _playbackState.value = PlaybackState.ERROR }
        }
    }

    fun pause() { scope.launch { mediaPlayer?.controls()?.pause() } }
    fun resume() { scope.launch { mediaPlayer?.controls()?.play() } }
    fun togglePlayPause() { if (_playbackState.value == PlaybackState.PLAYING) pause() else resume() }
    
    fun stop() {
        isTransitioning = false
        _playbackState.value = PlaybackState.IDLE
        scope.launch { mediaPlayer?.controls()?.stop() }
    }

    fun stopAudioOnly() {
        isTransitioning = true
        scope.launch { 
            try {
                if (mediaPlayer?.status()?.isPlaying == true) mediaPlayer?.controls()?.stop()
            } catch (_: Exception) {}
        }
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
        tickJob?.cancel(); scope.cancel(); vlcDispatcher.close()
        mediaPlayer?.release(); factory?.release()
    }

    private fun attachListeners() {
        mediaPlayer?.events()?.addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) { _playbackState.value = PlaybackState.PLAYING }
            override fun paused(mediaPlayer: MediaPlayer) { _playbackState.value = PlaybackState.PAUSED }
            override fun stopped(mediaPlayer: MediaPlayer) { 
                if (!isTransitioning) _playbackState.value = PlaybackState.IDLE 
            }
            override fun finished(mediaPlayer: MediaPlayer) { _playbackState.value = PlaybackState.ENDED }
            override fun error(mediaPlayer: MediaPlayer) { _playbackState.value = PlaybackState.ERROR }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) { _duration.value = newLength }
        })
    }

    private fun startPositionTicker() {
        tickJob = scope.launch {
            while (isActive) {
                val mp = mediaPlayer
                if (mp != null && vlcAvailable && (_playbackState.value == PlaybackState.PLAYING)) {
                    try {
                        val dur = mp.status().length()
                        _duration.value = dur
                        _position.value = (mp.status().position() * dur).toLong()
                    } catch (_: Exception) {}
                }
                delay(1000)
            }
        }
    }
}
