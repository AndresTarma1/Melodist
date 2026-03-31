package com.example.melodist.player

import com.example.melodist.data.AppDirs
import com.example.melodist.db.DatabaseDao
import com.example.melodist.db.entities.ArtistEntity
import com.example.melodist.db.entities.FormatEntity
import com.example.melodist.db.entities.SongEntity
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDateTime
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

/**
 * State of a single download task.
 */
sealed class DownloadState {
    data object Queued : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
    data object Cancelled : DownloadState()
}

/**
 * Manages downloading audio streams to local disk cache using HTTP Range requests
 * (chunked/resumable downloads), similar to Metrolist's approach.
 *
 * Strategy:
 *   1. Resolve audio stream URL + metadata (contentLength, itag, mimeType) via AudioStreamResolver
 *   2. Download in 512 KB chunks using HTTP Range headers → resumable on failure
 *   3. Save format metadata in the Format table for future reference
 *   4. Mark song as downloaded in the Song table
 *   5. PlayerViewModel checks local cache before resolving from network
 *
 * Files are stored at `~/.melodist/cache/songs/{videoId}.{ext}` where ext is
 * determined by mimeType (m4a for audio/mp4, webm for audio/webm).
 */
class DownloadService(
    private val streamResolver: AudioStreamResolver,
    private val databaseDao: DatabaseDao
) {
    private val log = Logger.getLogger("DownloadService")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(3) // max 3 concurrent downloads

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    /** Tracks SongItem metadata for songs currently being downloaded (Queued/Downloading).
     *  Cleared once the download completes or fails. */
    private val _pendingSongItems = MutableStateFlow<Map<String, SongItem>>(emptyMap())
    val pendingSongItems: StateFlow<Map<String, SongItem>> = _pendingSongItems.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    init {
        // Scan existing cache on startup so songs already on disk show as Completed
        scanExistingCache()
    }

    /**
     * Scans the cache directory and marks any existing files as Completed
     * so the UI shows the correct download state immediately.
     */
    private fun scanExistingCache() {
        scope.launch {
            try {
                val existing = mutableMapOf<String, DownloadState>()
                for (ext in listOf("m4a", "webm", "ogg")) {
                    cacheDir.listFiles { f -> f.extension == ext && f.length() > 0 }?.forEach { file ->
                        val songId = file.nameWithoutExtension
                        existing[songId] = DownloadState.Completed
                    }
                }
                if (existing.isNotEmpty()) {
                    _downloadStates.update { it + existing }
                }
            } catch (e: Exception) {
                log.warning("Failed to scan cache: ${e.message}")
            }
        }
    }

    companion object {
        /** Chunk size for Range requests: 512 KB */
        private const val CHUNK_SIZE = 512 * 1024L

        /** Max retries per chunk before giving up. */
        private const val MAX_CHUNK_RETRIES = 3

        /** Back-off base delay between retries. */
        private const val RETRY_BASE_DELAY_MS = 500L

        /** Threshold for emitting progress updates (default 100KB) */
        private const val PROGRESS_THRESHOLD_BYTES = 100_000L

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val REFERER = "https://music.youtube.com/"

        private val cacheDir: File by lazy {
            AppDirs.songsDir.also { if (!it.exists()) it.mkdirs() }
        }

        /**
         * Returns the local cached file for a given song ID, or null if not downloaded.
         * Checks both .m4a and .webm extensions.
         */
        fun getCachedFile(songId: String): File? {
            for (ext in listOf("m4a", "webm", "ogg")) {
                val file = File(cacheDir, "$songId.$ext")
                if (file.exists() && file.length() > 0) return file
            }
            return null
        }

        /** Returns the total cache size in bytes. */
        fun getCacheSizeBytes(): Long =
            cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

        /** Formats bytes to human-readable string. */
        fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024L * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }

        /** Determine file extension from mimeType. */
        private fun extensionForMime(mimeType: String): String = when {
            mimeType.contains("audio/mp4") -> "m4a"
            mimeType.contains("audio/webm") -> "webm"
            mimeType.contains("audio/ogg") -> "ogg"
            else -> "m4a"
        }
    }

    // ─── Download a single song ────────────────────────────

    fun downloadSong(song: SongItem) {

        val playbackData = runBlocking(Dispatchers.IO) {
            YTPlayerutils.playerResponseForMetadata(song.id)
        }.getOrNull()

        val newSong = SongItem(
            id = song.id,
            title = song.title,
            artists = song.artists,
            album = song.album,
            duration = playbackData?.videoDetails?.lengthSeconds?.toInt(),
            musicVideoType = song.musicVideoType,
            thumbnail = song.thumbnail,
        )

        scope.launch(Dispatchers.IO) {
            val songId = song.id
            val current = _downloadStates.value[songId]
            if (current is DownloadState.Downloading || current is DownloadState.Queued) return@launch
            if (getCachedFile(songId) != null) {
                _downloadStates.update { it + (songId to DownloadState.Completed) }
                databaseDao.updateSongDownloadStatus(songId, true, System.currentTimeMillis())
                return@launch
            }

            _downloadStates.update { it + (songId to DownloadState.Queued) }
            _pendingSongItems.update { it + (songId to song) }

            semaphore.withPermit {
                if (!isActive) return@withPermit
                _downloadStates.update { it + (songId to DownloadState.Downloading(0f)) }

                try {

                    // 1. Resolve stream URL + format metadata
                    val stream = streamResolver.resolveAudioStream(songId)




                    ensureSongInDb(newSong)


                    // 2. Determine file extension and target path
                    val ext = extensionForMime(stream.format.mimeType)
                    val targetFile = File(cacheDir, "$songId.$ext")
                    val partFile = File(cacheDir, "$songId.$ext.part")

                    // 3. Get total content length (from format metadata or HEAD probe)
                    val totalBytes = stream.format.contentLength ?: probeContentLength(stream.streamUrl)

                    if (totalBytes == null || totalBytes <= 0) {
                        downloadSingleRequest(stream.streamUrl, partFile, songId)
                    } else {
                        // 4. Chunked download with Range headers (resumable)
                        downloadChunked(stream.streamUrl, partFile, songId, totalBytes)
                    }

                    if (!isActive) {
                        partFile.delete()
                        return@withPermit
                    }

                    // 5. Rename .part → final
                    if (partFile.exists() && partFile.length() > 0) {
                        partFile.renameTo(targetFile)

                        val playBackData = ResolvedStream(
                            url = stream.streamUrl,
                            itag = stream.format.itag,
                            mimeType = stream.format.mimeType,
                            bitrate = stream.format.bitrate,
                            contentLength = stream.format.contentLength,
                            sampleRate = 0,
                            loudnessDb = stream.format.loudnessDb,
                            codecs = "",
                            expiresInSeconds = stream.streamExpiresInSeconds.toLong()
                        )
                        // 6. Save format metadata in DB
                        saveFormatMetadata(songId, playBackData)

                        // 7. Mark as downloaded
                        databaseDao.updateSongDownloadStatus(songId, true, System.currentTimeMillis())

                        _downloadStates.update { it + (songId to DownloadState.Completed) }
                        _pendingSongItems.update { it - songId }
                        log.info("Download complete: $songId (${formatSize(targetFile.length())})")
                    } else {
                        _downloadStates.update { it + (songId to DownloadState.Failed("Archivo descargado vacío")) }
                        _pendingSongItems.update { it - songId }
                        partFile.delete()
                    }
                } catch (e: CancellationException) {
                    _downloadStates.update { it + (songId to DownloadState.Cancelled) }
                    _pendingSongItems.update { it - songId }
                    cleanupPartFiles(songId)
                    throw e
                } catch (e: Exception) {
                    log.warning("Download failed: $songId — ${e.message}")
                    _downloadStates.update { it + (songId to DownloadState.Failed(e.message ?: "Error desconocido")) }
                    _pendingSongItems.update { it - songId }
                    // Don't delete .part file — allows resume on retry
                }
            }
        }
    }

    fun downloadAll(songs: List<SongItem>) {
        songs.forEach { downloadSong(it) }
    }

    fun cancelDownload(songId: String) {
        synchronized(activeJobs) {
            activeJobs[songId]?.cancel()
            activeJobs.remove(songId)
        }
        _downloadStates.update { it + (songId to DownloadState.Cancelled) }
        cleanupPartFiles(songId)
    }

    fun removeDownload(songId: String) {
        cancelDownload(songId)
        getCachedFile(songId)?.delete()
        cleanupPartFiles(songId)
        _downloadStates.update { it - songId }
        scope.launch { databaseDao.updateSongDownloadStatus(songId, false, null) }
        log.info("Removed download: $songId")
    }

    fun clearCache() {
        synchronized(activeJobs) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
        }
        // Mark all downloaded songs as not downloaded in DB
        scope.launch {
            cacheDir.listFiles()?.forEach { file ->
                val songId = file.nameWithoutExtension.removeSuffix(".part")
                databaseDao.updateSongDownloadStatus(songId, false, null)
            }
            cacheDir.listFiles()?.forEach { it.delete() }
        }
        _downloadStates.update { emptyMap() }
        _pendingSongItems.update { emptyMap() }
        log.info("Cache cleared")
    }

    fun isDownloaded(songId: String): Boolean = getCachedFile(songId) != null

    fun release() { scope.cancel() }

    // ─── Chunked download with Range headers ───────────────

    /**
     * Downloads a file in chunks using HTTP Range requests.
     * Supports resuming: if [partFile] already has data, continues from where it left off.
     * On HTTP 403, re-resolves the stream URL (YouTube URLs expire) and retries.
     */
    private suspend fun downloadChunked(
        initialUrl: String,
        partFile: File,
        songId: String,
        totalBytes: Long
    ) {
        withContext(Dispatchers.IO) {
            var currentUrl = initialUrl
            var downloadedBytes = if (partFile.exists()) partFile.length() else 0L
            var urlRefreshCount = 0
            var lastProgressUpdate = 0L // throttle UI updates

            if (downloadedBytes >= totalBytes) return@withContext

            RandomAccessFile(partFile, "rw").use { raf ->
                raf.seek(downloadedBytes)

                while (downloadedBytes < totalBytes) {
                    ensureActive()

                    val rangeEnd = minOf(downloadedBytes + CHUNK_SIZE - 1, totalBytes - 1)

                    var chunkSuccess = false
                    for (retry in 1..MAX_CHUNK_RETRIES) {
                        try {
                            val bytesRead = downloadChunk(currentUrl, raf, downloadedBytes, rangeEnd)
                            downloadedBytes += bytesRead
                            chunkSuccess = true

                            // Throttle UI updates: only emit when threshold corresponds
                            if (downloadedBytes - lastProgressUpdate > PROGRESS_THRESHOLD_BYTES || downloadedBytes >= totalBytes) {
                                lastProgressUpdate = downloadedBytes
                                val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                _downloadStates.update { it + (songId to DownloadState.Downloading(progress)) }
                            }
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val is403 = e.message?.contains("403") == true

                            // On 403, the URL likely expired — re-resolve it
                            if (is403 && urlRefreshCount < 3) {
                                val newStream = streamResolver.resolveAudioStream(songId)
                                currentUrl = newStream.streamUrl
                                urlRefreshCount++
                                break
                            }

                            if (retry >= MAX_CHUNK_RETRIES) throw e
                            delay((RETRY_BASE_DELAY_MS * retry).milliseconds)
                        }
                    }

                    // If we broke out of the retry loop due to URL refresh, continue the outer loop
                    // (the while loop will re-attempt the same range with the new URL)
                    if (!chunkSuccess && urlRefreshCount > 0) {
                        // URL was refreshed, retry this chunk range
                        continue
                    }

                    if (!chunkSuccess) {
                        throw Exception("Failed to download chunk after $MAX_CHUNK_RETRIES retries")
                    }
                }
            }

            log.info("Chunked download complete: $songId ($downloadedBytes bytes)")
        }
    }

    /**
     * Downloads a single chunk using HTTP Range header.
     * Returns the number of bytes actually written.
     */
    private fun downloadChunk(
        url: String,
        raf: RandomAccessFile,
        rangeStart: Long,
        rangeEnd: Long
    ): Long {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Referer", REFERER)
            connection.setRequestProperty("Range", "bytes=$rangeStart-$rangeEnd")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.connect()

            val code = connection.responseCode
            if (code !in listOf(200, 206)) {
                throw Exception("HTTP $code for Range bytes=$rangeStart-$rangeEnd")
            }

            var totalRead = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(16_384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    raf.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                }
            }
            return totalRead
        } finally {
            connection.disconnect()
        }
    }

    // ─── Fallback single-request download ──────────────────

    private suspend fun downloadSingleRequest(url: String, targetFile: File, songId: String) {
        withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Referer", REFERER)
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
                }

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L

                connection.inputStream.buffered(16_384).use { input ->
                    targetFile.outputStream().buffered(16_384).use { output ->
                        val buffer = ByteArray(16_384)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                            } else -1f
                            _downloadStates.update { it + (songId to DownloadState.Downloading(progress)) }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    // ─── Helpers ───────────────────────────────────────────

    private fun probeContentLength(url: String): Long? {
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "HEAD"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Referer", REFERER)
                connection.connectTimeout = 10_000
                connection.connect()
                val length = connection.contentLengthLong
                if (length > 0) length else null
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            log.warning("HEAD probe failed: ${e.message}")
            null
        }
    }

    private suspend fun ensureSongInDb(song: SongItem) {
        val existing = databaseDao.songById(song.id).firstOrNull()
        if (existing == null) {

            databaseDao.insertSong(
                SongEntity(
                    id = song.id,
                    title = song.title,
                    duration = song.duration ?: -1,
                    thumbnailUrl = song.thumbnail,
                    albumId = song.album?.id,
                    albumName = song.album?.name,
                    explicit = song.explicit,
                )
            )
        }
        // Always ensure artists and song-artist mappings exist
        song.artists.forEachIndexed { index, artist ->
            val artistId = artist.id ?: "unknown_${artist.name.hashCode()}"
            val existingArtist = databaseDao.artistById(artistId).firstOrNull()
            if (existingArtist == null) {
                databaseDao.insertArtist(
                    ArtistEntity(
                        id = artistId,
                        name = artist.name,
                        thumbnailUrl = null,
                        channelId = artist.id,
                        lastUpdateTime = LocalDateTime.now(),
                        bookmarkedAt = null,
                        isLocal = false
                    )
                )
            }
            try {
                databaseDao.insertSongArtistMap(song.id, artistId, index)
            } catch (_: Exception) {
                // Map already exists — ignore
            }
        }
    }

    private suspend fun saveFormatMetadata(songId: String, stream: ResolvedStream) {
        databaseDao.insertFormat(
            FormatEntity(
                id = songId,
                itag = stream.itag,
                mimeType = stream.mimeType,
                codecs = stream.codecs,
                bitrate = stream.bitrate,
                sampleRate = stream.sampleRate,
                contentLength = stream.contentLength ?: 0L,
                loudnessDb = stream.loudnessDb,
                playbackUrl = null // don't persist URL — it expires
            )
        )
    }

    private fun cleanupPartFiles(songId: String) {
        for (ext in listOf("m4a", "webm", "ogg")) {
            File(cacheDir, "$songId.$ext.part").delete()
        }
    }
}


