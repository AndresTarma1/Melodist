package com.example.melodist.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.logging.Logger

class PlayerService {

    private val log = Logger.getLogger("PlayerService")
    private val mpvPlayer = MpvAudioPlayer()

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private var initAttempted = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null

    @Volatile
    private var isTransitioning = false

    @Volatile
    private var endNotified = false

    fun init() {
        if (initAttempted) return
        initAttempted = true
        mpvPlayer.init()
        startPositionTicker()
    }

    fun play(url: String) {
        init()
        scope.launch {
            try {
                _playbackState.value = PlaybackState.LOADING
                isTransitioning = false
                endNotified = false
                mpvPlayer.openUri(url)
            } catch (e: Exception) {
                _playbackState.value = PlaybackState.ERROR
            }
        }
    }

    fun pause() {
        mpvPlayer.pause()
    }

    fun resume() {
        mpvPlayer.play()
    }

    fun togglePlayPause() {
        if (_playbackState.value == PlaybackState.PLAYING) pause() else resume()
    }

    fun stop() {
        isTransitioning = false
        endNotified = false
        _playbackState.value = PlaybackState.IDLE
        _position.value = 0L
        _duration.value = 0L
        mpvPlayer.stop()
    }

    fun seekTo(millis: Long) {
        val dur = _duration.value
        if (dur > 0) {
            val endThresholdMs = 1000L
            if (millis < dur - endThresholdMs) {
                endNotified = false
            }
            mpvPlayer.seekTo(millis.toFloat() / dur.toFloat())
        }
    }

    fun setVolume(value: Int) {
        _volume.value = value
        mpvPlayer.volume = value.toFloat() / 100f
    }

    fun setEqualizer(bands: List<Float>) {
        // Send values to mpv
        mpvPlayer.setEqualizer(bands)
    }

    fun release() {
        tickJob?.cancel()
        mpvPlayer.dispose()
        scope.cancel()
    }

    private fun startPositionTicker() {
        tickJob = scope.launch {
            while (isActive) {
                try {
                    val dur = mpvPlayer.getDuration()
                    _duration.value = dur
                    
                    val pos = mpvPlayer.getCurrentPosition()
                    _position.value = pos

                    val playing = mpvPlayer.isPlaying.value
                    val endThresholdMs = 1000L
                    val looksEnded =
                        !endNotified &&
                        dur > endThresholdMs &&
                        pos >= (dur - endThresholdMs) &&
                        _playbackState.value != PlaybackState.LOADING

                    if (looksEnded) {
                        endNotified = true
                        _playbackState.value = PlaybackState.ENDED
                        delay(500)
                        continue
                    }

                    if (!isTransitioning) {
                        if (playing && _playbackState.value != PlaybackState.PLAYING) {
                            endNotified = false
                            _playbackState.value = PlaybackState.PLAYING
                        } else if (!playing && _playbackState.value == PlaybackState.PLAYING) {
                            _playbackState.value = PlaybackState.PAUSED
                        }
                    }
                } catch (e: Throwable) {
                    // silent catch for background ticker
                }
                delay(500)
            }
        }
    }

    fun stopAudioOnly() {
        isTransitioning = true
        scope.launch {
            try {
                mpvPlayer.pause()
            } catch (e: Throwable) { /* ignore */ }
        }
    }
}
