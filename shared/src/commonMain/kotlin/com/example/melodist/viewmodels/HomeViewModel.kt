package com.example.melodist.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.ChartsPage
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed class HomeState {
    data class Success(
        val page: HomePage,
        val selectedParams: String? = null,
        val isLoadingMore: Boolean = false,
    ) : HomeState()
    data class Error(val message: String) : HomeState()
    object Loading : HomeState()
}

sealed class HomeUiEvent {
    data class ChipSelected(val params: String?) : HomeUiEvent()
    object LoadMore : HomeUiEvent()
    object Retry : HomeUiEvent()
}

class HomeViewModel(
    loginState: StateFlow<Boolean>? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeState>(HomeState.Loading)
    val uiState: StateFlow<HomeState> = _uiState.asStateFlow()

    private val _charts = MutableStateFlow<ChartsPage?>(null)
    val charts: StateFlow<ChartsPage?> = _charts.asStateFlow()

    // Para disparar efectos de una sola vez hacia la UI (snackbars, navegar, etc.)
    val events = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        loadHome()
        loadCharts()

        loginState
            ?.drop(1)
            ?.onEach { forceReload() }
            ?.launchIn(viewModelScope)
    }

    private fun loadCharts() {
        viewModelScope.launch {
            YouTube.getChartsPage()
                .onSuccess { _charts.value = it }
        }
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.ChipSelected -> {
                val params = event.params
                val currentSuccess = _uiState.value as? HomeState.Success
                // Deseleccionar chip si ya está seleccionado
                val newParams = if (currentSuccess?.selectedParams == params) null else params
                loadHome(newParams)
            }
            HomeUiEvent.LoadMore -> loadMore()
            HomeUiEvent.Retry -> forceReload()
        }
    }

    fun forceReload(params: String? = currentParams()) {
        _uiState.value = HomeState.Loading
        fetchHome(params)
    }

    private fun loadHome(params: String? = null) {
        // Evitar recargar si ya estamos en el mismo estado
        val current = _uiState.value
        if (current is HomeState.Success && current.selectedParams == params) return

        _uiState.value = HomeState.Loading
        fetchHome(params)
    }

    private fun fetchHome(params: String?) {
        viewModelScope.launch {
            YouTube.home(params = params)
                .onSuccess { page ->
                    _uiState.value = HomeState.Success(page = page, selectedParams = params)
                }
                .onFailure { error ->
                    _uiState.value = HomeState.Error(
                        error.message ?: "Error al cargar el home"
                    )
                }
        }
    }

    private fun loadMore() {
        val current = _uiState.value as? HomeState.Success ?: return
        if (current.isLoadingMore) return
        val continuation = current.page.continuation ?: return

        _uiState.value = current.copy(isLoadingMore = true)

        viewModelScope.launch {
            YouTube.home(continuation = continuation)
                .onSuccess { newPage ->
                    _uiState.value = current.copy(
                        page = newPage.copy(
                            sections = current.page.sections + newPage.sections,
                            chips = current.page.chips
                        ),
                        isLoadingMore = false
                    )
                }
                .onFailure {
                    _uiState.value = current.copy(isLoadingMore = false)
                }
        }
    }

    private fun currentParams(): String? =
        (_uiState.value as? HomeState.Success)?.selectedParams
}
