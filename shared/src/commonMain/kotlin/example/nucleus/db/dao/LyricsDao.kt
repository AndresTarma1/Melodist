package example.nucleus.db.dao

import example.nucleus.db.MusicPlayerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LyricsDao(private val database: MusicPlayerDatabase) {

    suspend fun insertLyrics(id: String, lyrics: String, provider: String = "Unknown") = withContext(Dispatchers.IO) {
        database.lyricsQueries.insertLyrics(id, lyrics, provider)
    }
}
