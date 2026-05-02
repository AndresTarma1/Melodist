package com.example.melodist.data.repository

import com.example.melodist.domain.search.SearchHistoryItem
import com.example.melodist.domain.search.SearchHistoryRepository
import com.example.melodist.db.DatabaseDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for managing search history using SQLDelight.
 */
class SearchRepository(
    private val dao: DatabaseDao
) : SearchHistoryRepository {

    /**
     * Get search history as a reactive Flow.
     */
    override fun getSearchHistory(): Flow<List<SearchHistoryItem>> {
        return dao.searchHistory()
            .map { entries -> entries.map { entry -> SearchHistoryItem(entry.query) } }
    }

    /**
     * Add a search query to history.
     * Uses INSERT OR REPLACE so duplicate queries just update their position.
     */
    override suspend fun addSearchQuery(query: String) {
        if (query.isBlank()) return
        dao.insertSearchHistory(query.trim())
    }

    /**
     * Remove a single query from history.
     */
    override suspend fun deleteSearchQuery(query: String) {
        dao.deleteSearchHistory(query)
    }

    /**
     * Clear all search history.
     */
    override suspend fun clearSearchHistory() {
        dao.clearSearchHistory()
    }
}