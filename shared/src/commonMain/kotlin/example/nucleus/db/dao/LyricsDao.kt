package example.nucleus.db.dao

import example.nucleus.db.MusicPlayerDatabase
import example.nucleus.db.entities.LyricsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsDao(private val database: MusicPlayerDatabase) {

    suspend fun insertLyrics(id: String, lyrics: String, provider: String = "Unknown") = withContext(Dispatchers.IO) {
        database.lyricsQueries.insertLyrics(id, lyrics, provider)
    }

    suspend fun getLyrics(id: String): LyricsEntity? = withContext(Dispatchers.IO) {
        database.lyricsQueries.selectById(id).executeAsOneOrNull()?.let { row ->
            LyricsEntity(id = row.id, lyrics = row.lyrics, provider = row.provider)
        }
    }

    suspend fun deleteLyrics(id: String) = withContext(Dispatchers.IO) {
        database.lyricsQueries.deleteLyrics(id)
    }
}
