package com.example.melodist.player

import com.example.melodist.domain.player.PlaybackMetadataService
import com.example.melodist.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.logging.Logger

/**
 * Implementación de [PlaybackMetadataService] para JVM/Desktop.
 * Maneja la descarga de miniaturas y sincronización de metadatos con Windows MediaSession.
 */
class PlaybackMetadataServiceImpl(
    private val mediaSession: WindowsMediaSession
) : PlaybackMetadataService {

    private val log = Logger.getLogger("PlaybackMetadataService")

    override suspend fun updateMetadata(song: MediaMetadata): String? = withContext(Dispatchers.IO) {
        val thumbUri = downloadThumbToTemp(song.id, song.thumbnailUrl)
        mediaSession.updateMetadata(
            title = song.title,
            artist = song.artists.joinToString(", ") { it.name },
            album = song.album?.title ?: "",
            thumbnailUrl = thumbUri
        )
        thumbUri
    }

    override suspend fun resetMetadata() {
        mediaSession.resetToIdle()
    }

    private fun downloadThumbToTemp(songId: String, url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val smtcDir = File(System.getProperty("java.io.tmpdir"), "melodist_smtc").also { it.mkdirs() }
            val tmpFile = File(smtcDir, "thumb_${songId}_${UUID.randomUUID()}.jpg")

            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connect()
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()

            tmpFile.writeBytes(bytes)

            // Conserva un buffer pequeño y elimina miniaturas antiguas.
            smtcDir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("thumb_") && it.name != tmpFile.name }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(8)
                ?.forEach { it.delete() }

            "file:///${tmpFile.absolutePath.replace('\\', '/')}"
        } catch (e: Exception) {
            log.fine("SMTC thumb download failed: ${e.message}")
            url
        }
    }
}

