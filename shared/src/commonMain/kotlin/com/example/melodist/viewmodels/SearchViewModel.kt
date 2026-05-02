package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.melodist.domain.search.AddSearchQueryUseCase
import com.example.melodist.domain.search.ClearSearchHistoryUseCase
import com.example.melodist.domain.search.DeleteSearchQueryUseCase
import com.example.melodist.domain.search.LoadSearchSuggestionsUseCase
import com.example.melodist.domain.search.ObserveSearchHistoryUseCase
import com.example.melodist.domain.search.SearchContinuationUseCase
import com.example.melodist.domain.search.SearchFilterOption
import com.example.melodist.domain.search.SearchResultOutcome
import com.example.melodist.domain.search.SearchUseCase
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.logging.Logger
import kotlin.time.Duration.Companion.milliseconds

sealed class SearchState {
    data class Success(val items: List<YTItem>, val continuation: String? = null, val isLoadingMore: Boolean = false) : SearchState()
    data class SummarySuccess(val summary: SearchSummaryPage) : SearchState()
    data class Error(val message: String) : SearchState()
    object Loading : SearchState()
    object Idle : SearchState()
}

class SearchViewModel(
    observeSearchHistory: ObserveSearchHistoryUseCase,
    private val addSearchQuery: AddSearchQueryUseCase,
    private val deleteSearchQuery: DeleteSearchQueryUseCase,
    private val clearSearchHistory: ClearSearchHistoryUseCase,
    private val loadSearchSuggestions: LoadSearchSuggestionsUseCase,
    private val searchUseCase: SearchUseCase,
    private val searchContinuationUseCase: SearchContinuationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchState>(SearchState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    /**
     * Search history as a reactive StateFlow.
     */
    val searchHistory = observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var suggestionsJob: Job? = null
    private fun fetchSuggestions(query: String) {
        suggestionsJob?.cancel()

        suggestionsJob = viewModelScope.launch {
            delay(300.milliseconds)
            loadSearchSuggestions(query)
                .onSuccess { _suggestions.value = it }
                .onFailure { _suggestions.value = emptyList() }
        }
    }

    private val _filter = MutableStateFlow<SearchFilterOption?>(null)
    val filter = _filter.asStateFlow()

    fun onQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
        if (newQuery.isNotEmpty()) {
            fetchSuggestions(newQuery)
        } else {
            _suggestions.value = emptyList()
        }
    }

    fun onFilterChange(newFilter: SearchFilterOption?) {
        _filter.value = newFilter
        search()
    }

    private var searchJob: Job? = null
    fun search() {
        searchJob?.cancel()

        if (_searchQuery.value.isBlank()) {
            _uiState.value = SearchState.Idle
            return
        }

        viewModelScope.launch {
            addSearchQuery(_searchQuery.value)
        }

        searchJob = viewModelScope.launch {
            _uiState.value = SearchState.Loading
            searchUseCase(_searchQuery.value, _filter.value)
                .onSuccess { result ->
                    when (result) {
                        is SearchResultOutcome.Summary -> _uiState.value = SearchState.SummarySuccess(result.page)
                        is SearchResultOutcome.Items -> _uiState.value = SearchState.Success(result.page.items, result.page.continuation)
                    }
                }
                .onFailure {
                    _uiState.value = SearchState.Error(it.message ?: "Unknown error")
                }
        }
    }

    fun searchContinuation() {
        val current = _uiState.value

        if (current !is SearchState.Success) return
        if (current.isLoadingMore) return
        if (current.continuation == null) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoadingMore = true)

            searchContinuationUseCase(current.continuation)
                .onSuccess { response ->
                    _uiState.value = current.copy(
                        items = (current.items + response.items).distinctBy { it.id },
                        continuation = response.continuation,
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    Logger.getGlobal().warning("Failed to load continuation: ${it.message}")
                    _uiState.value = current.copy(isLoadingMore = false)
                }
        }
    }

    /**
     * Delete a single entry from search history.
     */
    fun deleteHistoryEntry(query: String) {
        viewModelScope.launch {
            deleteSearchQuery(query)
        }
    }

    /**
     * Clear all search history.
     */
    fun clearHistory() {
        viewModelScope.launch {
            clearSearchHistory()
        }
    }
}