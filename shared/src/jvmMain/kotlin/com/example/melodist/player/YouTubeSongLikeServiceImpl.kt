package com.example.melodist.player

import com.example.melodist.domain.song.SongLikeService
import java.util.logging.Logger

/**
 * Implementación de [SongLikeService] para JVM/Desktop.
 * Procesa like/dislike de canciones a través de YouTube Music.
 *
 * Nota: La funcionalidad de like/dislike requiere tokens de feedback específicos
 * que se obtienen del endpoint de reproducción de YouTube Music.
 * Esta implementación logguea las acciones para integración futura.
 */
class YouTubeSongLikeServiceImpl : SongLikeService {

    private val log = Logger.getLogger("YouTubeSongLikeService")

    override suspend fun likeSong(songId: String): Result<Unit> = runCatching {
        // TODO: Implementar con sistema de feedback tokens cuando esté disponible en innertube
        log.info("[LIKE] Song liked (local state only): $songId")
        log.info("Pending full YouTube Music API integration for like/dislike")
    }

    override suspend fun dislikeSong(songId: String): Result<Unit> = runCatching {
        // TODO: Implementar con sistema de feedback tokens cuando esté disponible en innertube
        log.info("[DISLIKE] Song disliked (local state only): $songId")
        log.info("Pending full YouTube Music API integration for like/dislike")
    }

    override suspend fun removeLike(songId: String): Result<Unit> = runCatching {
        // TODO: Implementar con sistema de feedback tokens cuando esté disponible en innertube
        log.info("[REMOVE_LIKE] Like removed from song (local state only): $songId")
        log.info("Pending full YouTube Music API integration for like/dislike")
    }
}


