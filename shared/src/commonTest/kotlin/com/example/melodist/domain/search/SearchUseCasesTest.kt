package com.example.melodist.domain.search

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SearchSuggestions
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.SearchSummary
import com.metrolist.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun sampleSong(id: String = "song-1", title: String = "Song 1") = SongItem(
    id = id,
    title = title,
    artists = listOf(Artist(name = "Artist", id = "artist-1")),
    album = Album(name = "Album", id = "album-1"),
    thumbnail = "https://example.com/thumb.jpg",
)

class SearchUseCasesTest {

    private class FakeRemote : SearchRemoteDataSource {
        var lastQuery: String? = null
        var lastFilter: SearchFilterOption? = null

        override suspend fun search(query: String, filter: SearchFilterOption): Result<SearchPage> {
            lastQuery = query
            lastFilter = filter
            return Result.success(SearchPage(items = listOf(sampleSong()), continuation = "next-token"))
        }

        override suspend fun searchSummary(query: String): Result<SearchSummaryPage> {
            lastQuery = query
            return Result.success(
                SearchSummaryPage(
                    summaries = listOf(
                        SearchSummary(title = "Trending", items = listOf(sampleSong()))
                    )
                )
            )
        }

        override suspend fun searchSuggestions(query: String): Result<SearchSuggestions> {
            lastQuery = query
            return Result.success(SearchSuggestions(queries = listOf("$query one", "$query two"), recommendedItems = emptyList()))
        }

        override suspend fun searchContinuation(token: String): Result<SearchPage> {
            return Result.success(SearchPage(items = listOf(sampleSong(id = "song-2", title = "Song 2")), continuation = null))
        }
    }

    private class FakeHistoryRepository : SearchHistoryRepository {
        private val state = MutableStateFlow(listOf(SearchHistoryItem("initial")))

        override fun getSearchHistory(): Flow<List<SearchHistoryItem>> = state

        override suspend fun addSearchQuery(query: String) {
            state.value = (state.value + SearchHistoryItem(query.trim())).distinctBy { it.query }
        }

        override suspend fun deleteSearchQuery(query: String) {
            state.value = state.value.filterNot { it.query == query }
        }

        override suspend fun clearSearchHistory() {
            state.value = emptyList()
        }
    }

    @Test
    fun searchUseCase_returnsSummaryWhenFilterIsNull() = runBlocking {
        val remote = FakeRemote()
        val useCase = SearchUseCase(remote)

        val result = useCase("  hello  ", null).getOrThrow()

        assertTrue(result is SearchResultOutcome.Summary)
        val summary = result.page
        assertEquals("hello", remote.lastQuery)
        assertEquals("Trending", summary.summaries.first().title)
    }

    @Test
    fun searchUseCase_returnsItemsWhenFilterIsProvided() = runBlocking {
        val remote = FakeRemote()
        val useCase = SearchUseCase(remote)

        val result = useCase("beat", SearchFilterOption.SONGS).getOrThrow()

        assertTrue(result is SearchResultOutcome.Items)
        val items = result.page
        assertEquals("beat", remote.lastQuery)
        assertEquals(SearchFilterOption.SONGS, remote.lastFilter)
        assertEquals(1, items.items.size)
        assertEquals("next-token", items.continuation)
    }

    @Test
    fun searchHistoryUseCases_delegateToRepository() = runBlocking {
        val repository = FakeHistoryRepository()
        val observe = ObserveSearchHistoryUseCase(repository)
        val add = AddSearchQueryUseCase(repository)
        val delete = DeleteSearchQueryUseCase(repository)
        val clear = ClearSearchHistoryUseCase(repository)

        assertEquals(listOf(SearchHistoryItem("initial")), observe().first())

        add("  new query  ")
        assertEquals(listOf(SearchHistoryItem("initial"), SearchHistoryItem("new query")), observe().first())

        delete("initial")
        assertEquals(listOf(SearchHistoryItem("new query")), observe().first())

        clear()
        assertTrue(observe().first().isEmpty())
    }
}



