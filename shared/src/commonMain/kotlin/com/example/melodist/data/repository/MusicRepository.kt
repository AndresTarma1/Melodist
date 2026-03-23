package com.example.melodist.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.db.SavedAlbum
import com.example.melodist.db.SavedArtist
import com.example.melodist.db.SavedPlaylist
import com.example.melodist.db.SavedSong
import com.example.melodist.db.Song
import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository that bridges local persistence (SQLDelight) with innertube models.
 */
class MusicRepository(
    private val database: MelodistDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ─── Albums ─────────────────────────────────────────────

    fun getSavedAlbums(): Flow<List<SavedAlbum>> {
        return database.savedAlbumQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isAlbumSaved(browseId: String): Flow<Boolean> {
        return database.savedAlbumQueries.exists(browseId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    /** Lectura única (no reactiva) de si el álbum está guardado. */
    suspend fun isAlbumSavedOnce(browseId: String): Boolean = withContext(Dispatchers.IO) {
        database.savedAlbumQueries.exists(browseId).executeAsOneOrNull() ?: false
    }

    suspend fun saveAlbum(album: AlbumItem) = withContext(Dispatchers.IO) {
        database.savedAlbumQueries.insert(
            browseId = album.browseId,
            playlistId = album.playlistId,
            title = album.title,
            artists = json.encodeToString(album.artists?.map { SerializableArtist(it.name, it.id) } ?: emptyList()),
            year = album.year?.toLong(),
            thumbnail = album.thumbnail,
            explicit = if (album.explicit) 1L else 0L,
            savedAt = System.currentTimeMillis()
        )
    }

    /**
     * Guarda el álbum junto con todas sus canciones en la BD local.
     * Usa Album + SongAlbumMap para la relación canción-álbum,
     * y Song + SongArtistMap para los datos completos de cada canción.
     */
    suspend fun saveAlbumWithSongs(album: AlbumItem, songs: List<SongItem>) = withContext(Dispatchers.IO) {
        database.transaction {
            // 1. Guardar metadata en SavedAlbum
            database.savedAlbumQueries.insert(
                browseId = album.browseId,
                playlistId = album.playlistId,
                title = album.title,
                artists = json.encodeToString(album.artists?.map { SerializableArtist(it.name, it.id) } ?: emptyList()),
                year = album.year?.toLong(),
                thumbnail = album.thumbnail,
                explicit = if (album.explicit) 1L else 0L,
                savedAt = System.currentTimeMillis()
            )

            // 2. Guardar en tabla Album (estructura normalizada)
            database.albumQueries.insertAlbum(
                id = album.browseId,
                playlistId = album.playlistId,
                title = album.title,
                year = album.year?.toLong(),
                thumbnailUrl = album.thumbnail,
                themeColor = null,
                songCount = songs.size.toLong(),
                duration = songs.sumOf { it.duration ?: 0 }.toLong(),
                explicit = if (album.explicit) 1L else 0L,
                lastUpdateTime = System.currentTimeMillis(),
                bookmarkedAt = System.currentTimeMillis(),
                likedDate = null,
                inLibrary = System.currentTimeMillis(),
                isLocal = 0L,
                isUploaded = 0L
            )

            // 3. Limpiar mapa anterior y reinsertar canciones
            database.songAlbumMapQueries.deleteSongAlbumMapsByAlbum(album.browseId)

            songs.forEachIndexed { index, song ->
                // Insertar canción
                database.songQueries.insertSongIfNotExists(
                    id = song.id,
                    title = song.title,
                    duration = song.duration?.toLong() ?: -1L,
                    thumbnailUrl = song.thumbnail,
                    albumId = album.browseId,
                    albumName = album.title,
                    explicit = if (song.explicit) 1L else 0L,
                    year = album.year?.toLong(),
                    date = null,
                    dateModified = null,
                    liked = 0L,
                    likedDate = null,
                    totalPlayTime = 0L,
                    inLibrary = null,
                    dateDownload = null,
                    isLocal = 0L,
                    libraryAddToken = null,
                    libraryRemoveToken = null,
                    lyricsOffset = 0L,
                    romanizeLyrics = 1L,
                    isAgeRestricted = 0L,
                    isDownloaded = 0L,
                    isUploaded = 0L,
                    isVideo = 0L
                )

                // Mapeo canción → álbum
                database.songAlbumMapQueries.insertSongAlbumMap(song.id, album.browseId, index.toLong())

                // Artistas de la canción
                song.artists.forEachIndexed { artistIdx, artist ->
                    val artistId = artist.id ?: return@forEachIndexed
                    database.artistQueries.insertArtistIfNotExists(
                        id = artistId,
                        name = artist.name,
                        thumbnailUrl = null,
                        channelId = null,
                        lastUpdateTime = 0L,
                        bookmarkedAt = null,
                        isLocal = 0L
                    )
                    database.songArtistMapQueries.insertSongArtistMap(song.id, artistId, artistIdx.toLong())
                }
            }
        }
    }

    /**
     * Devuelve las canciones cacheadas localmente para un álbum guardado.
     * Retorna null si el álbum no está guardado o no tiene canciones almacenadas.
     */
    suspend fun getCachedAlbumSongs(browseId: String): List<SongItem>? = withContext(Dispatchers.IO) {
        val isSaved = database.savedAlbumQueries.exists(browseId).executeAsOne()
        if (!isSaved) return@withContext null

        val count = database.songAlbumMapQueries.countByAlbum(browseId).executeAsOne()
        if (count == 0L) return@withContext null

        val songs = database.songAlbumMapQueries.songsByAlbum(browseId).executeAsList()

        val artistRows = database.songArtistMapQueries.artistsForAlbumSongs(browseId).executeAsList()
        val artistsBySong: Map<String, List<Artist>> = artistRows
            .groupBy { it.songId }
            .mapValues { (_, rows) -> rows.map { row -> Artist(name = row.name, id = row.id) } }

        songs.map { song -> dbSongToSongItem(song, artistsBySong[song.id] ?: emptyList()) }
    }

    /**
     * Devuelve la metadata del álbum guardado como AlbumItem, o null si no está guardado.
     */
    fun getCachedAlbumItem(browseId: String): AlbumItem? {
        val saved = database.savedAlbumQueries.selectById(browseId).executeAsOneOrNull()
            ?: return null
        return savedAlbumToAlbumItem(saved)
    }

    suspend fun removeAlbum(browseId: String) = withContext(Dispatchers.IO) {
        database.savedAlbumQueries.delete(browseId)
        database.songAlbumMapQueries.deleteSongAlbumMapsByAlbum(browseId)
    }

    // ─── Artists ────────────────────────────────────────────

    fun getSavedArtists(): Flow<List<SavedArtist>> {
        return database.savedArtistQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isArtistSaved(id: String): Flow<Boolean> {
        return database.savedArtistQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    suspend fun saveArtist(artist: ArtistItem, subscriberCount: String? = null) = withContext(Dispatchers.IO) {
        database.savedArtistQueries.insert(
            id = artist.id,
            title = artist.title,
            thumbnail = artist.thumbnail,
            subscriberCount = subscriberCount,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun removeArtist(id: String) = withContext(Dispatchers.IO) {
        database.savedArtistQueries.delete(id)
    }

    // ─── Songs ──────────────────────────────────────────────

    fun getSavedSongs(): Flow<List<SavedSong>> {
        return database.savedSongQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isSongSaved(id: String): Flow<Boolean> {
        return database.savedSongQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    suspend fun saveSong(song: SongItem) = withContext(Dispatchers.IO) {
        database.savedSongQueries.insert(
            id = song.id,
            title = song.title,
            artists = json.encodeToString(song.artists.map { SerializableArtist(it.name, it.id) }),
            albumName = song.album?.name,
            albumId = song.album?.id,
            duration = song.duration?.toLong(),
            thumbnail = song.thumbnail,
            explicit = if (song.explicit) 1L else 0L,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun removeSong(id: String) = withContext(Dispatchers.IO) {
        database.savedSongQueries.delete(id)
    }

    // ─── Playlists ──────────────────────────────────────────

    fun getSavedPlaylists(): Flow<List<SavedPlaylist>> {
        return database.savedPlaylistQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    }

    fun isPlaylistSaved(id: String): Flow<Boolean> {
        return database.savedPlaylistQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    /** Lectura única (no reactiva) de si la playlist está guardada. */
    suspend fun isPlaylistSavedOnce(id: String): Boolean = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.exists(id).executeAsOneOrNull() ?: false
    }

    suspend fun savePlaylist(playlist: PlaylistItem) = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.insert(
            id = playlist.id,
            title = playlist.title,
            authorName = playlist.author?.name,
            authorId = playlist.author?.id,
            songCountText = playlist.songCountText,
            thumbnail = playlist.thumbnail,
            savedAt = System.currentTimeMillis()
        )
    }

    suspend fun removePlaylist(id: String) = withContext(Dispatchers.IO) {
        database.savedPlaylistQueries.delete(id)
        // Borrar también el mapeo de canciones de la playlist
        database.playlistSongMapQueries.deletePlaylistSongMapsByPlaylist(id)
    }

    /**
     * Guarda la playlist junto con todas sus canciones en la BD local.
     * Las canciones se insertan con INSERT OR IGNORE para no sobreescribir
     * datos existentes (p. ej. isDownloaded, liked, etc.).
     */
    suspend fun savePlaylistWithSongs(playlist: PlaylistItem, songs: List<SongItem>) = withContext(Dispatchers.IO) {
        database.transaction {
            // 1. Guardar metadata de la playlist
            database.savedPlaylistQueries.insert(
                id = playlist.id,
                title = playlist.title,
                authorName = playlist.author?.name,
                authorId = playlist.author?.id,
                songCountText = playlist.songCountText,
                thumbnail = playlist.thumbnail,
                savedAt = System.currentTimeMillis()
            )

            // 2. Limpiar mapa anterior (por si era un re-save)
            database.playlistSongMapQueries.deletePlaylistSongMapsByPlaylist(playlist.id)

            // 3. Insertar canciones (sin sobreescribir datos locales), artistas y mapeo
            songs.forEachIndexed { index, song ->
                database.songQueries.insertSongIfNotExists(
                    id = song.id,
                    title = song.title,
                    duration = song.duration?.toLong() ?: -1L,
                    thumbnailUrl = song.thumbnail,
                    albumId = song.album?.id,
                    albumName = song.album?.name,
                    explicit = if (song.explicit) 1L else 0L,
                    year = null,
                    date = null,
                    dateModified = null,
                    liked = 0L,
                    likedDate = null,
                    totalPlayTime = 0L,
                    inLibrary = null,
                    dateDownload = null,
                    isLocal = 0L,
                    libraryAddToken = null,
                    libraryRemoveToken = null,
                    lyricsOffset = 0L,
                    romanizeLyrics = 1L,
                    isAgeRestricted = 0L,
                    isDownloaded = 0L,
                    isUploaded = 0L,
                    isVideo = 0L
                )

                // Insertar artistas de la canción
                song.artists.forEachIndexed { artistIdx, artist ->
                    val artistId = artist.id ?: return@forEachIndexed
                    database.artistQueries.insertArtistIfNotExists(
                        id = artistId,
                        name = artist.name,
                        thumbnailUrl = null,
                        channelId = null,
                        lastUpdateTime = 0L,
                        bookmarkedAt = null,
                        isLocal = 0L
                    )
                    database.songArtistMapQueries.insertSongArtistMap(
                        songId = song.id,
                        artistId = artistId,
                        position = artistIdx.toLong()
                    )
                }

                database.playlistSongMapQueries.insertPlaylistSongMap(
                    playlistId = playlist.id,
                    songId = song.id,
                    position = index.toLong(),
                    setVideoId = null
                )
            }
        }
    }

    /**
     * Devuelve las canciones cacheadas localmente para una playlist guardada.
     * Retorna null si la playlist no está guardada o no tiene canciones almacenadas.
     */
    suspend fun getCachedPlaylistSongs(playlistId: String): List<SongItem>? = withContext(Dispatchers.IO) {
        val isSaved = database.savedPlaylistQueries.exists(playlistId).executeAsOne()
        if (!isSaved) return@withContext null

        val count = database.playlistSongMapQueries.countByPlaylist(playlistId).executeAsOne()
        if (count == 0L) return@withContext null

        // Obtener canciones base
        val songs = database.playlistSongMapQueries.songsByPlaylist(playlistId)
            .executeAsList()

        // Obtener artistas de todas las canciones de la playlist en una sola query
        val artistRows = database.songArtistMapQueries
            .artistsForPlaylistSongs(playlistId)
            .executeAsList()

        // Agrupar artistas por songId
        val artistsBySong: Map<String, List<Artist>> = artistRows
            .groupBy { it.songId }
            .mapValues { (_, rows) ->
                rows.map { row -> Artist(name = row.name, id = row.id) }
            }

        songs.map { song ->
            dbSongToSongItem(song, artistsBySong[song.id] ?: emptyList())
        }
    }

    /**
     * Devuelve la metadata de la playlist guardada como PlaylistItem, o null si no está guardada.
     */
    fun getCachedPlaylistItem(playlistId: String): PlaylistItem? {
        val saved = database.savedPlaylistQueries.selectById(playlistId).executeAsOneOrNull()
            ?: return null
        return savedPlaylistToPlaylistItem(saved)
    }

    /**
     * Returns all downloaded songs as SongItems, enriched with artist info.
     * Used for the special "Descargas" local playlist.
     */
    suspend fun getDownloadedSongs(): List<SongItem> = withContext(Dispatchers.IO) {
        val songs = database.songQueries.downloadedSongs().executeAsList()
        if (songs.isEmpty()) return@withContext emptyList()

        val artistRows = database.songArtistMapQueries.artistsForDownloadedSongs().executeAsList()
        val artistsBySong: Map<String, List<Artist>> = artistRows
            .groupBy { it.songId }
            .mapValues { (_, rows) -> rows.map { row -> Artist(name = row.name, id = row.id) } }

        songs.map { song -> dbSongToSongItem(song, artistsBySong[song.id] ?: emptyList()) }
    }

    // ─── Mappers ────────────────────────────────────────────

    fun savedAlbumToAlbumItem(saved: SavedAlbum): AlbumItem {
        val artists = try {
            json.decodeFromString<List<SerializableArtist>>(saved.artists).map {
                Artist(name = it.name, id = it.id)
            }
        } catch (_: Exception) {
            emptyList()
        }
        return AlbumItem(
            browseId = saved.browseId,
            playlistId = saved.playlistId,
            title = saved.title,
            artists = artists,
            year = saved.year?.toInt(),
            thumbnail = saved.thumbnail,
            explicit = saved.explicit != 0L
        )
    }

    fun savedArtistToArtistItem(saved: SavedArtist): ArtistItem {
        return ArtistItem(
            id = saved.id,
            title = saved.title,
            thumbnail = saved.thumbnail,
            shuffleEndpoint = null,
            radioEndpoint = null
        )
    }

    fun savedSongToSongItem(saved: SavedSong): SongItem {
        val artists = try {
            json.decodeFromString<List<SerializableArtist>>(saved.artists).map {
                Artist(name = it.name, id = it.id)
            }
        } catch (_: Exception) {
            emptyList()
        }
        return SongItem(
            id = saved.id,
            title = saved.title,
            artists = artists,
            album = if (saved.albumName != null && saved.albumId != null) {
                com.metrolist.innertube.models.Album(name = saved.albumName, id = saved.albumId)
            } else null,
            duration = saved.duration?.toInt(),
            thumbnail = saved.thumbnail,
            explicit = saved.explicit != 0L
        )
    }

    fun savedPlaylistToPlaylistItem(saved: SavedPlaylist): PlaylistItem {
        return PlaylistItem(
            id = saved.id,
            title = saved.title,
            author = if (saved.authorName != null) Artist(name = saved.authorName, id = saved.authorId) else null,
            songCountText = saved.songCountText,
            thumbnail = saved.thumbnail,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null
        )
    }

    private fun dbSongToSongItem(song: Song, artists: List<Artist> = emptyList()): SongItem {
        return SongItem(
            id = song.id,
            title = song.title,
            artists = artists,
            album = if (song.albumName != null && song.albumId != null)
                Album(name = song.albumName, id = song.albumId)
            else null,
            duration = song.duration.takeIf { it >= 0 }?.toInt(),
            thumbnail = song.thumbnailUrl?: "",
            explicit = song.explicit != 0L,
            // isAgeRestricted ya no existe en innertube.SongItem, se omite
        )
    }
}

@kotlinx.serialization.Serializable
data class SerializableArtist(
    val name: String,
    val id: String?
)

