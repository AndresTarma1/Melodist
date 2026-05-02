package com.example.melodist.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.example.melodist.db.MelodistDatabase
import com.example.melodist.domain.artist.ArtistRepository
import com.metrolist.innertube.models.ArtistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ArtistRepositoryImpl(private val database: MelodistDatabase) : ArtistRepository {
    override fun getSavedArtists(): Flow<List<ArtistItem>> {
        return database.savedArtistQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { list -> list.map { savedArtistToArtistItem(it) } }
    }

    override fun isArtistSaved(id: String): Flow<Boolean> {
        return database.savedArtistQueries.exists(id)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it ?: false }
    }

    override suspend fun saveArtist(artist: ArtistItem) = withContext(Dispatchers.IO) {
        database.savedArtistQueries.insert(
            id = artist.id,
            title = artist.title,
            thumbnail = artist.thumbnail,
            subscriberCount = null, // Or get it from artist if available
            savedAt = System.currentTimeMillis()
        )
    }

    override suspend fun removeArtist(id: String) = withContext(Dispatchers.IO) {
        database.savedArtistQueries.delete(id)
    }
}
