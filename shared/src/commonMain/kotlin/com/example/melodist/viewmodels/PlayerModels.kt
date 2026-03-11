package com.example.melodist.viewmodels

import com.example.melodist.player.PlaybackState
import com.metrolist.innertube.models.SongItem

/**
 * Describes how the queue was built.
 */
sealed class QueueSource {
    /** From an album — queue = album songs */
    data class Album(val browseId: String, val title: String) : QueueSource()

    /** From a playlist — queue = playlist songs */
    data class Playlist(val playlistId: String, val title: String) : QueueSource()

    /** From search / home — queue = related songs via YouTube.next() */
    data class Single(val videoId: String) : QueueSource()

    /** Manually assembled (library, etc.) */
    data object Custom : QueueSource()
}

data class PlayerUiState(
    val currentSong: SongItem? = null,
    val queue: List<SongItem> = emptyList(),
    val originalQueue: List<SongItem> = emptyList(),
    val currentIndex: Int = -1,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    // positionMs y durationMs se mantienen aquí para compatibilidad con
    // los composables del player, pero ya NO se actualizan en _uiState cada
    // segundo — se leen desde progressState en los componentes que los necesitan.
    val volume: Int = 100,
    val queueSource: QueueSource? = null,
    val isShuffled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val error: String? = null,
)

/**
 * Estado de progreso separado: se emite cada segundo mientras reproduce.
 * Al tenerlo en un StateFlow distinto, los componentes que NO muestran
 * la barra de progreso (NavigationRail, listados, etc.) NO se recomponen
 * con cada tick del reproductor — esto elimina el principal culpable del
 * alto consumo de CPU/RAM (~800MB en CoroutineScheduler).
 */
data class PlayerProgressState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

enum class RepeatMode { OFF, ALL, ONE }

