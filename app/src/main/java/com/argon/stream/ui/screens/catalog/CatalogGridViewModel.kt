package com.argon.stream.ui.screens.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.domain.model.CatalogGenres
import com.argon.stream.domain.model.CatalogKind
import com.argon.stream.domain.model.CatalogQuery
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogGridUiState(
    val title: String = "",
    val query: CatalogQuery? = null,
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
    /** Movies/Series switch shown for genres that exist for both. */
    val switchableTypes: List<MediaType> = emptyList()
)

@HiltViewModel
class CatalogGridViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val initialQuery = CatalogQuery(
        kind = runCatching { CatalogKind.valueOf(savedStateHandle["kind"] ?: "") }.getOrDefault(CatalogKind.POPULAR),
        type = savedStateHandle.get<String>("type")?.let { runCatching { MediaType.valueOf(it) }.getOrNull() },
        genre = savedStateHandle.get<String>("genre")?.takeIf { it.isNotBlank() }
    )

    private val _uiState = MutableStateFlow(
        CatalogGridUiState(title = savedStateHandle.get<String>("title").orEmpty())
    )
    val uiState: StateFlow<CatalogGridUiState> = _uiState.asStateFlow()

    private var nextCursor: Int? = 0
    private var loadJob: Job? = null

    init {
        start(initialQuery)
    }

    private fun start(query: CatalogQuery) {
        loadJob?.cancel()
        nextCursor = 0
        val switchable = query.genre?.let { genre ->
            MediaType.entries.filter { genre in CatalogGenres.forType(it) }
        }.orEmpty().takeIf { it.size > 1 }.orEmpty()
        _uiState.update {
            it.copy(query = query, items = emptyList(), isLoading = true, endReached = false, errorMessage = null, switchableTypes = switchable)
        }
        loadNextPage()
    }

    fun setType(type: MediaType) {
        val query = _uiState.value.query ?: return
        if (query.type != type) start(query.copy(type = type))
    }

    /** Called when the user scrolls near the end; does nothing while a page is loading or at the end. */
    fun loadNextPage() {
        val query = _uiState.value.query ?: return
        val cursor = nextCursor ?: return
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = it.items.isNotEmpty(), errorMessage = null) }
            when (val res = catalogRepository.loadCatalogPage(query, cursor)) {
                is NetworkResult.Success -> {
                    nextCursor = res.data.nextCursor
                    _uiState.update { state ->
                        val merged = (state.items + res.data.items).distinctBy { "${it.type}_${it.id}" }
                        state.copy(
                            items = merged,
                            isLoading = false,
                            isLoadingMore = false,
                            // A page that adds nothing new also ends the list, so it can't loop forever.
                            endReached = res.data.nextCursor == null || merged.size == state.items.size
                        )
                    }
                    if (_uiState.value.endReached) nextCursor = null
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, isLoadingMore = false, errorMessage = res.userMessage)
                }
            }
        }
    }
}
