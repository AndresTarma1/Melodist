package example.nucleus.db.entities

/** Entrada del top de canciones por reproducciones (PlayCount agregado). */
data class TopSongEntry(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val playCount: Long,
)

/** Entrada del top de álbumes por reproducciones. */
data class TopAlbumEntry(
    val albumName: String,
    val playCount: Long,
)

/** Entrada del top de artistas por reproducciones. */
data class TopArtistEntry(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val playCount: Long,
)
