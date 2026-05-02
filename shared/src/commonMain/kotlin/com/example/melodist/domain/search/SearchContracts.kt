package com.example.melodist.domain.search

import com.example.melodist.domain.error.toAppError
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.flow.Flow

data class SearchHistoryItem(
    val query: String,
)

enum class SearchFilterOption(val label: String) {
    ALL("Todo"),
    VIDEOS("Videos"),
    SONGS("Canciones"),
    ALBUMS("Álbumes"),
    ARTISTS("Artistas"),
    PLAYLISTS("Playlists"),
}

data class SearchPage(
    val items: List<YTItem>,
    val continuation: String?,
)

interface SearchHistoryRepository {
    fun getSearchHistory(): Flow<List<SearchHistoryItem>>
    suspend fun addSearchQuery(query: String)
    suspend fun deleteSearchQuery(query: String)
    suspend fun clearSearchHistory()
}

interface SearchRemoteDataSource {
    suspend fun search(query: String, filter: SearchFilterOption): Result<SearchPage>
    suspend fun searchSummary(query: String): Result<SearchSummaryPage>
    suspend fun searchSuggestions(query: String): Result<SearchSuggestions>
    suspend fun searchContinuation(token: String): Result<SearchPage>
}

sealed interface SearchResultOutcome {
    data class Summary(val page: SearchSummaryPage) : SearchResultOutcome
    data class Items(val page: SearchPage) : SearchResultOutcome
}

class ObserveSearchHistoryUseCase(
    private val repository: SearchHistoryRepository,
) {
    operator fun invoke(): Flow<List<SearchHistoryItem>> = repository.getSearchHistory()
}

class AddSearchQueryUseCase(
    private val repository: SearchHistoryRepository,
) {
    suspend operator fun invoke(query: String) {
        repository.addSearchQuery(query)
    }
}

class DeleteSearchQueryUseCase(
    private val repository: SearchHistoryRepository,
) {
    suspend operator fun invoke(query: String) {
        repository.deleteSearchQuery(query)
    }
}

class ClearSearchHistoryUseCase(
    private val repository: SearchHistoryRepository,
) {
    suspend operator fun invoke() {
        repository.clearSearchHistory()
    }
}

class LoadSearchSuggestionsUseCase(
    private val remoteDataSource: SearchRemoteDataSource,
) {
    suspend operator fun invoke(query: String): Result<List<String>> =
        remoteDataSource.searchSuggestions(query)
            .map { it.queries }
            .recoverCatching { throw it.toAppError() }
}

class SearchUseCase(
    private val remoteDataSource: SearchRemoteDataSource,
) {
    suspend operator fun invoke(
        query: String,
        filter: SearchFilterOption?,
    ): Result<SearchResultOutcome> = runCatching {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) throw IllegalArgumentException("query cannot be blank")

        if (filter == null || filter == SearchFilterOption.ALL) {
            SearchResultOutcome.Summary(remoteDataSource.searchSummary(normalizedQuery).getOrThrow())
        } else {
            val page = remoteDataSource.search(normalizedQuery, filter).getOrThrow()
            SearchResultOutcome.Items(page)
        }
    }.recoverCatching { throw it.toAppError() }
}

class SearchContinuationUseCase(
    private val remoteDataSource: SearchRemoteDataSource,
) {
    suspend operator fun invoke(token: String): Result<SearchPage> = 
        remoteDataSource.searchContinuation(token)
            .recoverCatching { throw it.toAppError() }
}
