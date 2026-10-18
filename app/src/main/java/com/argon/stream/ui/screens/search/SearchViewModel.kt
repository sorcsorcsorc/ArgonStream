package com.argon.stream.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.data.repository.RecentSearchesRepository
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SortOrder(val label: String) {
    RELEVANCE("Relevance"),
    RATING_DESC("Highest Rated"),
    YEAR_DESC("Newest First")
}

data class SearchUiState(
    val query: String = "",
    val selectedType: MediaType? = null,
    val minRating: Double = 0.0,
    val sortOrder: SortOrder = SortOrder.RELEVANCE,
    val isLoading: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val errorMessage: String? = null,
    val recentSearches: List<String> = emptyList()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val recentSearches: RecentSearchesRepository
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")
    private val typeFilterFlow = MutableStateFlow<MediaType?>(null)
    private val minRatingFlow = MutableStateFlow(0.0)
    private val sortOrderFlow = MutableStateFlow(SortOrder.RELEVANCE)

    private val searchResultsFlow = combine(
        queryFlow.debounce(350).map { it.trim() }.distinctUntilChanged(),
        typeFilterFlow
    ) { q, type ->
        q to type
    }.flatMapLatest { (q, type) ->
        flow {
            if (q.length < 2) {
                emit(Triple(false, emptyList<MediaItem>(), null as String?))
                return@flow
            }
            emit(Triple(true, emptyList<MediaItem>(), null as String?))
            when (val res = catalogRepository.searchCatalog(q, type)) {
                is NetworkResult.Success -> emit(Triple(false, res.data, null))
                is NetworkResult.Error -> emit(Triple(false, emptyList(), res.userMessage))
            }
        }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        queryFlow,
        typeFilterFlow,
        minRatingFlow,
        sortOrderFlow,
        searchResultsFlow
    ) { query, type, rating, sort, (loading, rawResults, error) ->
        val rated = if (rating > 0.0) rawResults.filter { (it.rating ?: 0.0) >= rating } else rawResults
        val sorted = when (sort) {
            SortOrder.RELEVANCE -> rated
            SortOrder.RATING_DESC -> rated.sortedByDescending { it.rating ?: 0.0 }
            SortOrder.YEAR_DESC -> rated.sortedByDescending { it.releaseYear ?: "" }
        }
        SearchUiState(
            query = query,
            selectedType = type,
            minRating = rating,
            sortOrder = sort,
            isLoading = loading,
            results = sorted,
            errorMessage = error
        )
    }.combine(recentSearches.observe()) { state, recents ->
        state.copy(recentSearches = recents)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        queryFlow.value = newQuery
    }

    fun onTypeFilterChange(type: MediaType?) {
        typeFilterFlow.value = type
    }

    fun onMinRatingChange(rating: Double) {
        minRatingFlow.value = rating
    }

    fun onSortOrderChange(sortOrder: SortOrder) {
        sortOrderFlow.value = sortOrder
    }

    /** Remembers the current query (called when a result is opened or Search is pressed). */
    fun rememberCurrentQuery() = recentSearches.add(queryFlow.value)

    fun removeRecentSearch(query: String) = recentSearches.remove(query)

    fun clearRecentSearches() = recentSearches.clear()
}