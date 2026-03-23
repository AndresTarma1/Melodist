package com.example.melodist.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Data access object wrapping SQLDelight generated queries.
 * Desktop-compatible replacement for the Android Room @Dao.
 */
class DatabaseDao(private val database: MelodistDatabase) {

    // ─── Helpers: epoch millis <-> LocalDateTime ────────────

    private fun Long?.toLocalDateTime(): LocalDateTime? =
        this?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC) }

    private fun LocalDateTime?.toEpochMillis(): Long? =
        this?.atZone(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    // ─── Row mappers (work with raw generated types) ────────

    private fun Song.toEntity() = SongEntity(
        id = id, title = title, duration = duration.toInt(),
        thumbnailUrl = thumbnailUrl, albumId = albumId, albumName = albumName,
        explicit = explicit != 0L, year = year?.toInt(),
        date = date.toLocalDateTime(), dateModified = dateModified.toLocalDateTime(),
        liked = liked != 0L, likedDate = likedDate.toLocalDateTime(),
        totalPlayTime = totalPlayTime, inLibrary = inLibrary.toLocalDateTime(),
        dateDownload = dateDownload.toLocalDateTime(), isLocal = isLocal != 0L,
        libraryAddToken = libraryAddToken, libraryRemoveToken = libraryRemoveToken,
        lyricsOffset = lyricsOffset.toInt(), romanizeLyrics = romanizeLyrics != 0L,
        isAgeRestricted = isAgeRestricted != 0L,
        isDownloaded = isDownloaded != 0L, isUploaded = isUploaded != 0L, isVideo = isVideo != 0L
    )

    private fun buildSongEntity(
        id: String, title: String, duration: Long, thumbnailUrl: String?, albumId: String?,
        albumName: String?, explicit: Long, year: Long?, date: Long?, dateModified: Long?,
        liked: Long, likedDate: Long?, totalPlayTime: Long, inLibrary: Long?, dateDownload: Long?,
        isLocal: Long, libraryAddToken: String?, libraryRemoveToken: String?,
        lyricsOffset: Long, romanizeLyrics: Long, isAgeRestricted: Long,
        isDownloaded: Long, isUploaded: Long, isVideo: Long
    ) = SongEntity(
        id = id, title = title, duration = duration.toInt(),
        thumbnailUrl = thumbnailUrl, albumId = albumId, albumName = albumName,
        explicit = explicit != 0L, year = year?.toInt(),
        date = date.toLocalDateTime(), dateModified = dateModified.toLocalDateTime(),
        liked = liked != 0L, likedDate = likedDate.toLocalDateTime(),
        totalPlayTime = totalPlayTime, inLibrary = inLibrary.toLocalDateTime(),
        dateDownload = dateDownload.toLocalDateTime(), isLocal = isLocal != 0L,
        libraryAddToken = libraryAddToken, libraryRemoveToken = libraryRemoveToken,
        lyricsOffset = lyricsOffset.toInt(), romanizeLyrics = romanizeLyrics != 0L,
        isAgeRestricted = isAgeRestricted != 0L,
        isDownloaded = isDownloaded != 0L, isUploaded = isUploaded != 0L, isVideo = isVideo != 0L
    )

    private fun mapArtist(
        id: String, name: String, thumbnailUrl: String?, channelId: String?,
        lastUpdateTime: Long, bookmarkedAt: Long?, isLocal: Long
    ) = ArtistEntity(
        id = id, name = name, thumbnailUrl = thumbnailUrl, channelId = channelId,
        lastUpdateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdateTime), ZoneOffset.UTC),
        bookmarkedAt = bookmarkedAt.toLocalDateTime(), isLocal = isLocal != 0L
    )

    private fun mapAlbum(
        id: String, playlistId: String?, title: String, year: Long?, thumbnailUrl: String?,
        themeColor: Long?, songCount: Long, duration: Long, explicit: Long, lastUpdateTime: Long,
        bookmarkedAt: Long?, likedDate: Long?, inLibrary: Long?, isLocal: Long, isUploaded: Long
    ) = AlbumEntity(
        id = id, playlistId = playlistId, title = title, year = year?.toInt(),
        thumbnailUrl = thumbnailUrl, themeColor = themeColor?.toInt(),
        songCount = songCount.toInt(), duration = duration.toInt(), explicit = explicit != 0L,
        lastUpdateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastUpdateTime), ZoneOffset.UTC),
        bookmarkedAt = bookmarkedAt.toLocalDateTime(), likedDate = likedDate.toLocalDateTime(),
        inLibrary = inLibrary.toLocalDateTime(), isLocal = isLocal != 0L, isUploaded = isUploaded != 0L
    )

    private fun mapPlaylist(
        id: String, name: String, browseId: String?, createdAt: Long?, lastUpdateTime: Long?,
        isEditable: Long, bookmarkedAt: Long?, remoteSongCount: Long?, playEndpointParams: String?,
        thumbnailUrl: String?, shuffleEndpointParams: String?, radioEndpointParams: String?,
        isLocal: Long, isAutoSync: Long
    ) = PlaylistEntity(
        id = id, name = name, browseId = browseId,
        createdAt = createdAt.toLocalDateTime(), lastUpdateTime = lastUpdateTime.toLocalDateTime(),
        isEditable = isEditable != 0L, bookmarkedAt = bookmarkedAt.toLocalDateTime(),
        remoteSongCount = remoteSongCount?.toInt(), playEndpointParams = playEndpointParams,
        thumbnailUrl = thumbnailUrl, shuffleEndpointParams = shuffleEndpointParams,
        radioEndpointParams = radioEndpointParams, isLocal = isLocal != 0L, isAutoSync = isAutoSync != 0L
    )

    // ─── Internal: fetch relations (blocking) ───────────────

    private fun artistsForSong(songId: String): List<ArtistEntity> {
        val maps = database.songArtistMapQueries.selectBySong(songId).executeAsList()
        if (maps.isEmpty()) return emptyList()
        return maps.mapNotNull { map ->
            database.artistQueries.artistById(map.artistId, ::mapArtist).executeAsOneOrNull()
        }
    }

    private fun albumForSong(songId: String): AlbumEntity? {
        val map = database.songAlbumMapQueries.selectBySong(songId).executeAsOneOrNull() ?: return null
        return database.albumQueries.albumById(map.albumId, ::mapAlbum).executeAsOneOrNull()
    }

    private fun artistsForAlbum(albumId: String): List<ArtistEntity> {
        val maps = database.albumArtistMapQueries.selectByAlbum(albumId).executeAsList()
        if (maps.isEmpty()) return emptyList()
        return maps.mapNotNull { map ->
            database.artistQueries.artistById(map.artistId, ::mapArtist).executeAsOneOrNull()
        }
    }

    // ─── Songs ──────────────────────────────────────────────

    fun allSongs(): Flow<List<SongEntity>> =
        database.songQueries.selectAll(::buildSongEntity)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun songsInLibrary(): Flow<List<SongEntity>> =
        database.songQueries.songsInLibrary { id, title, duration, thumbnailUrl, albumId,
                                               albumName, explicit, year, date, dateModified,
                                               liked, likedDate, totalPlayTime, inLibrary,
                                               dateDownload, isLocal, libraryAddToken,
                                               libraryRemoveToken, lyricsOffset, romanizeLyrics,
                                               isAgeRestricted, isDownloaded, isUploaded, isVideo ->
            buildSongEntity(id, title, duration, thumbnailUrl, albumId, albumName, explicit, year,
                date, dateModified, liked, likedDate, totalPlayTime, inLibrary, dateDownload,
                isLocal, libraryAddToken, libraryRemoveToken, lyricsOffset, romanizeLyrics,
                isAgeRestricted, isDownloaded, isUploaded, isVideo)
        }.asFlow().mapToList(Dispatchers.IO)

    fun songById(id: String): Flow<SongEntity?> =
        database.songQueries.songById(id, ::buildSongEntity)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun likedSongs(): Flow<List<SongEntity>> =
        database.songQueries.likedSongs { id, title, duration, thumbnailUrl, albumId,
                                           albumName, explicit, year, date, dateModified,
                                           liked, likedDate, totalPlayTime, inLibrary,
                                           dateDownload, isLocal, libraryAddToken,
                                           libraryRemoveToken, lyricsOffset, romanizeLyrics,
                                           isAgeRestricted, isDownloaded, isUploaded, isVideo ->
            buildSongEntity(id, title, duration, thumbnailUrl, albumId, albumName, explicit, year,
                date, dateModified, liked, likedDate, totalPlayTime, inLibrary, dateDownload,
                isLocal, libraryAddToken, libraryRemoveToken, lyricsOffset, romanizeLyrics,
                isAgeRestricted, isDownloaded, isUploaded, isVideo)
        }.asFlow().mapToList(Dispatchers.IO)

    fun songWithRelations(songId: String): Flow<SongWithRelations?> =
        songById(songId).map { songEntity ->
            songEntity?.let { song ->
                SongWithRelations(
                    song = song,
                    artists = artistsForSong(songId),
                    album = albumForSong(songId)
                )
            }
        }

    suspend fun insertSong(song: SongEntity) = withContext(Dispatchers.IO) {
        database.songQueries.insertSong(
            id = song.id, title = song.title, duration = song.duration.toLong(),
            thumbnailUrl = song.thumbnailUrl, albumId = song.albumId, albumName = song.albumName,
            explicit = if (song.explicit) 1L else 0L, year = song.year?.toLong(),
            date = song.date.toEpochMillis(), dateModified = song.dateModified.toEpochMillis(),
            liked = if (song.liked) 1L else 0L, likedDate = song.likedDate.toEpochMillis(),
            totalPlayTime = song.totalPlayTime, inLibrary = song.inLibrary.toEpochMillis(),
            dateDownload = song.dateDownload.toEpochMillis(),
            isLocal = if (song.isLocal) 1L else 0L,
            libraryAddToken = song.libraryAddToken, libraryRemoveToken = song.libraryRemoveToken,
            lyricsOffset = song.lyricsOffset.toLong(),
            romanizeLyrics = if (song.romanizeLyrics) 1L else 0L,
            isAgeRestricted = if (song.isAgeRestricted) 1L else 0L,
            isDownloaded = if (song.isDownloaded) 1L else 0L,
            isUploaded = if (song.isUploaded) 1L else 0L,
            isVideo = if (song.isVideo) 1L else 0L
        )
    }

    suspend fun deleteSong(id: String) = withContext(Dispatchers.IO) {
        database.songQueries.deleteSong(id)
    }

    // ─── Artists ────────────────────────────────────────────

    fun allArtists(): Flow<List<ArtistEntity>> =
        database.artistQueries.selectAllArtists(::mapArtist)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun artistById(id: String): Flow<ArtistEntity?> =
        database.artistQueries.artistById(id, ::mapArtist)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun bookmarkedArtists(): Flow<List<ArtistEntity>> =
        database.artistQueries.bookmarkedArtists { id, name, thumbnailUrl, channelId,
                                                    lastUpdateTime, bookmarkedAt, isLocal ->
            mapArtist(id, name, thumbnailUrl, channelId, lastUpdateTime, bookmarkedAt, isLocal)
        }.asFlow().mapToList(Dispatchers.IO)

    suspend fun insertArtist(artist: ArtistEntity) = withContext(Dispatchers.IO) {
        database.artistQueries.insertArtist(
            id = artist.id, name = artist.name, thumbnailUrl = artist.thumbnailUrl,
            channelId = artist.channelId,
            lastUpdateTime = artist.lastUpdateTime.toEpochMillis() ?: System.currentTimeMillis(),
            bookmarkedAt = artist.bookmarkedAt.toEpochMillis(),
            isLocal = if (artist.isLocal) 1L else 0L
        )
    }

    suspend fun updateArtistBookmark(id: String, bookmarkedAt: LocalDateTime?) = withContext(Dispatchers.IO) {
        database.artistQueries.updateArtistBookmark(
            bookmarkedAt = bookmarkedAt.toEpochMillis(), id = id
        )
    }

    suspend fun deleteArtist(id: String) = withContext(Dispatchers.IO) {
        database.artistQueries.deleteArtist(id)
    }

    // ─── Albums ─────────────────────────────────────────────

    fun allAlbums(): Flow<List<AlbumEntity>> =
        database.albumQueries.selectAllAlbums(::mapAlbum)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun albumById(id: String): Flow<AlbumEntity?> =
        database.albumQueries.albumById(id, ::mapAlbum)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    fun bookmarkedAlbums(): Flow<List<AlbumEntity>> =
        database.albumQueries.bookmarkedAlbums { id, playlistId, title, year, thumbnailUrl,
                                                  themeColor, songCount, duration, explicit,
                                                  lastUpdateTime, bookmarkedAt, likedDate,
                                                  inLibrary, isLocal, isUploaded ->
            mapAlbum(id, playlistId, title, year, thumbnailUrl, themeColor, songCount, duration,
                explicit, lastUpdateTime, bookmarkedAt, likedDate, inLibrary, isLocal, isUploaded)
        }.asFlow().mapToList(Dispatchers.IO)

    suspend fun insertAlbum(album: AlbumEntity) = withContext(Dispatchers.IO) {
        database.albumQueries.insertAlbum(
            id = album.id, playlistId = album.playlistId, title = album.title,
            year = album.year?.toLong(), thumbnailUrl = album.thumbnailUrl,
            themeColor = album.themeColor?.toLong(), songCount = album.songCount.toLong(),
            duration = album.duration.toLong(), explicit = if (album.explicit) 1L else 0L,
            lastUpdateTime = album.lastUpdateTime.toEpochMillis() ?: System.currentTimeMillis(),
            bookmarkedAt = album.bookmarkedAt.toEpochMillis(),
            likedDate = album.likedDate.toEpochMillis(),
            inLibrary = album.inLibrary.toEpochMillis(),
            isLocal = if (album.isLocal) 1L else 0L,
            isUploaded = if (album.isUploaded) 1L else 0L
        )
    }

    suspend fun updateAlbumBookmark(id: String, bookmarkedAt: LocalDateTime?) = withContext(Dispatchers.IO) {
        database.albumQueries.updateAlbumBookmark(
            bookmarkedAt = bookmarkedAt.toEpochMillis(), id = id
        )
    }

    suspend fun deleteAlbum(id: String) = withContext(Dispatchers.IO) {
        database.albumQueries.deleteAlbum(id)
    }

    fun albumWithArtists(albumId: String): Flow<AlbumWithArtists?> =
        albumById(albumId).map { albumEntity ->
            albumEntity?.let { album ->
                AlbumWithArtists(album = album, artists = artistsForAlbum(albumId))
            }
        }

    fun albumWithSongs(albumId: String): Flow<AlbumWithSongs?> =
        albumById(albumId).map { albumEntity ->
            albumEntity?.let { album ->
                val artists = artistsForAlbum(albumId)
                val songMaps = database.songAlbumMapQueries.selectByAlbum(albumId).executeAsList()
                val songs = songMaps.mapNotNull { map ->
                    database.songQueries.songById(map.songId, ::buildSongEntity).executeAsOneOrNull()?.let { songEntity ->
                        SongWithRelations(
                            song = songEntity,
                            artists = artistsForSong(songEntity.id),
                            album = album
                        )
                    }
                }
                AlbumWithSongs(album = album, artists = artists, songs = songs)
            }
        }

    // ─── Playlists ──────────────────────────────────────────

    fun allPlaylists(): Flow<List<PlaylistEntity>> =
        database.playlistQueries.selectAllPlaylists(::mapPlaylist)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun playlistById(id: String): Flow<PlaylistEntity?> =
        database.playlistQueries.playlistById(id, ::mapPlaylist)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)

    suspend fun insertPlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        database.playlistQueries.insertPlaylist(
            id = playlist.id, name = playlist.name, browseId = playlist.browseId,
            createdAt = playlist.createdAt.toEpochMillis(),
            lastUpdateTime = playlist.lastUpdateTime.toEpochMillis(),
            isEditable = if (playlist.isEditable) 1L else 0L,
            bookmarkedAt = playlist.bookmarkedAt.toEpochMillis(),
            remoteSongCount = playlist.remoteSongCount?.toLong(),
            playEndpointParams = playlist.playEndpointParams,
            thumbnailUrl = playlist.thumbnailUrl,
            shuffleEndpointParams = playlist.shuffleEndpointParams,
            radioEndpointParams = playlist.radioEndpointParams,
            isLocal = if (playlist.isLocal) 1L else 0L,
            isAutoSync = if (playlist.isAutoSync) 1L else 0L
        )
    }

    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
        database.playlistQueries.deletePlaylist(id)
    }

    // ─── Mapping tables ─────────────────────────────────────

    suspend fun insertSongArtistMap(songId: String, artistId: String, position: Int) = withContext(Dispatchers.IO) {
        database.songArtistMapQueries.insertSongArtistMap(songId, artistId, position.toLong())
    }

    suspend fun insertSongAlbumMap(songId: String, albumId: String, index: Int) = withContext(Dispatchers.IO) {
        database.songAlbumMapQueries.insertSongAlbumMap(songId, albumId, index.toLong())
    }

    suspend fun insertAlbumArtistMap(albumId: String, artistId: String, order: Int) = withContext(Dispatchers.IO) {
        database.albumArtistMapQueries.insertAlbumArtistMap(albumId, artistId, order.toLong())
    }

    suspend fun insertPlaylistSongMap(playlistId: String, songId: String, position: Int, setVideoId: String? = null) = withContext(Dispatchers.IO) {
        database.playlistSongMapQueries.insertPlaylistSongMap(playlistId, songId, position.toLong(), setVideoId)
    }

    /** Returns songIds in a playlist (ordered by position). */
    fun songIdsInPlaylist(playlistId: String): List<String> =
        database.playlistSongMapQueries.selectByPlaylist(playlistId).executeAsList().map { it.songId }

    fun countByPlaylist(playlistId: String): Long =
        database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()

    fun countDownloadedByPlaylist(playlistId: String): Long =
        database.playlistSongMapQueries.countDownloadedByPlaylist(playlistId).executeAsOne()

    // ─── Events ─────────────────────────────────────────────

    suspend fun insertEvent(songId: String, timestamp: LocalDateTime, playTime: Long) = withContext(Dispatchers.IO) {
        database.eventQueries.insertEvent(
            songId = songId,
            timestamp = timestamp.toEpochMillis() ?: System.currentTimeMillis(),
            playTime = playTime
        )
    }

    // ─── Search History ─────────────────────────────────────

    fun searchHistory(): Flow<List<SearchHistoryEntry>> =
        database.searchHistoryQueries.selectAll { id, query, timestamp ->
            SearchHistoryEntry(id = id, query = query)
        }.asFlow().mapToList(Dispatchers.IO)

    suspend fun insertSearchHistory(query: String) = withContext(Dispatchers.IO) {
        database.searchHistoryQueries.insertQuery(query, System.currentTimeMillis())
    }

    suspend fun deleteSearchHistory(query: String) = withContext(Dispatchers.IO) {
        database.searchHistoryQueries.deleteQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        database.searchHistoryQueries.deleteAll()
    }

    // ─── Lyrics ─────────────────────────────────────────────

    suspend fun insertLyrics(id: String, lyrics: String, provider: String = "Unknown") = withContext(Dispatchers.IO) {
        database.lyricsQueries.insertLyrics(id, lyrics, provider)
    }

    // ─── Downloads ──────────────────────────────────────────

    fun downloadedSongs(): Flow<List<SongEntity>> =
        database.songQueries.downloadedSongs(::buildSongEntity)
            .asFlow()
            .mapToList(Dispatchers.IO)

    fun downloadedSongsCount(): Flow<Long> =
        database.songQueries.downloadedSongsCount()
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: 0L }

    fun countDownloadedByAlbum(albumId: String): Long =
        database.songQueries.countDownloadedByAlbum(albumId).executeAsOne()

    /**
     * Returns downloaded songs enriched with artist names from the song-artist map.
     */
    fun downloadedSongsWithRelations(): Flow<List<SongWithRelations>> =
        downloadedSongs().map { songs ->
            songs.map { song ->
                SongWithRelations(
                    song = song,
                    artists = artistsForSong(song.id),
                    album = albumForSong(song.id)
                )
            }
        }

    suspend fun updateSongDownloadStatus(songId: String, isDownloaded: Boolean, dateDownload: Long?) =
        withContext(Dispatchers.IO) {
            database.songQueries.updateSongDownloadStatus(
                isDownloaded = if (isDownloaded) 1L else 0L,
                dateDownload = dateDownload,
                id = songId
            )
        }

    // ─── Format ─────────────────────────────────────────────

    suspend fun insertFormat(format: FormatEntity) = withContext(Dispatchers.IO) {
        database.formatQueries.insertFormat(
            id = format.id, itag = format.itag.toLong(), mimeType = format.mimeType,
            codecs = format.codecs, bitrate = format.bitrate.toLong(),
            sampleRate = format.sampleRate?.toLong(), contentLength = format.contentLength,
            loudnessDb = format.loudnessDb, perceptualLoudnessDb = format.perceptualLoudnessDb,
            playbackUrl = format.playbackUrl
        )
    }

    // ─── Transaction support ────────────────────────────────

    fun <T> transaction(block: () -> T): T {
        var result: T? = null
        database.transaction {
            result = block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

