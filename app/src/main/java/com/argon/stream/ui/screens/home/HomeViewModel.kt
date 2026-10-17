package com.argon.stream.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.domain.model.CatalogRow
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.domain.model.WatchProgressItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val filterType: MediaType? = null,
    val featuredItem: MediaItem? = null,
    val continueWatching: List<WatchProgressItem> = emptyList(),
    val catalogRows: List<CatalogRow> = emptyList(),
    val isTmdbConfigured: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedWithTmdb: Boolean? = null

    init {
        viewModelScope.launch {
            catalogRepository.observeContinueWatching().collect { items ->
                _uiState.update { it.copy(continueWatching = items) }
            }
        }
        refreshCatalogs()
    }

    /** Reloads when the catalog source changed (e.g. a TMDB key was added in Settings). */
    fun onScreenVisible() {
        if (loadedWithTmdb != null && loadedWithTmdb != catalogRepository.hasTmdbCredentials()) {
            refreshCatalogs()
        }
    }

    fun setFilter(type: MediaType?) {
        if (_uiState.value.filterType == type) return
        _uiState.update { it.copy(filterType = type) }
        refreshCatalogs()
    }

    fun refreshCatalogs() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val filter = _uiState.value.filterType
            val hasTmdb = catalogRepository.hasTmdbCredentials()
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isTmdbConfigured = hasTmdb) }
            when (val result = catalogRepository.loadHomeCatalogs(filter)) {
                is NetworkResult.Success -> {
                    loadedWithTmdb = hasTmdb
                    val rows = result.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            catalogRows = rows,
                            featuredItem = pickFeatured(rows)
                        )
                    }
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.userMessage)
                }
            }
        }
    }

    private fun pickFeatured(rows: List<CatalogRow>): MediaItem? {
        val candidates = rows.firstOrNull()?.items.orEmpty()
        return candidates.firstOrNull { !it.backdropUrl.isNullOrBlank() && it.overview.isNotBlank() }
            ?: candidates.firstOrNull()
    }
}
