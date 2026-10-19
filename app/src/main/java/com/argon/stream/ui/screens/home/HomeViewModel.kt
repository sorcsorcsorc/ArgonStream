package com.argon.stream.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.data.repository.Profile
import com.argon.stream.data.repository.ProfileRepository
import com.argon.stream.domain.model.CatalogRow
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.domain.model.WatchProgressItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Home's top filter chips. Anime is its own catalog (Kitsu), not a media type. */
enum class HomeFilter(val label: String, val mediaType: MediaType?) {
    ALL("All", null), MOVIES("Movies", MediaType.MOVIE), SERIES("Series", MediaType.SERIES), ANIME("Anime", null)
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val filter: HomeFilter = HomeFilter.ALL,
    val featuredItem: MediaItem? = null,
    val continueWatching: List<WatchProgressItem> = emptyList(),
    val myList: List<MediaItem> = emptyList(),
    val catalogRows: List<CatalogRow> = emptyList(),
    val isTmdbConfigured: Boolean = false,
    val activeProfile: Profile? = null,
    val errorMessage: String? = null
) {
    val filterType: MediaType? get() = filter.mediaType
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedWithTmdb: Boolean? = null
    /** Rows per filter, so switching All / Movies / Series / Anime is instant after the first load. */
    private val rowsCache = mutableMapOf<HomeFilter, List<CatalogRow>>()
    private var prefetched = false

    init {
        viewModelScope.launch {
            catalogRepository.observeContinueWatching().collect { items ->
                _uiState.update { it.copy(continueWatching = items) }
            }
        }
        viewModelScope.launch {
            catalogRepository.observeWatchlistItems().collect { items ->
                _uiState.update { it.copy(myList = items) }
            }
        }
        viewModelScope.launch {
            combine(profileRepository.profiles, profileRepository.activeProfileId) { profiles, id ->
                profiles.firstOrNull { it.id == id }
            }.collect { profile -> _uiState.update { it.copy(activeProfile = profile) } }
        }
        refreshCatalogs()
    }

    fun switchProfile() = profileRepository.requestSwitch()

    fun removeFromContinueWatching(item: WatchProgressItem) {
        viewModelScope.launch { catalogRepository.removeFromContinueWatching(item.mediaItem) }
    }

    /** Reloads when the catalog source changed (e.g. a TMDB key was added in Settings). */
    fun onScreenVisible() {
        if (loadedWithTmdb != null && loadedWithTmdb != catalogRepository.hasTmdbCredentials()) {
            refreshCatalogs()
        }
    }

    fun setFilter(filter: HomeFilter) {
        if (_uiState.value.filter == filter) return
        loadJob?.cancel()
        val cached = rowsCache[filter]
        _uiState.update {
            it.copy(
                filter = filter,
                catalogRows = cached ?: emptyList(),
                featuredItem = cached?.let(::pickFeatured) ?: it.featuredItem,
                isLoading = cached == null,
                errorMessage = null
            )
        }
        if (cached == null) refreshCatalogs()
    }

    fun refreshCatalogs() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val filter = _uiState.value.filter
            val hasTmdb = catalogRepository.hasTmdbCredentials()
            if (loadedWithTmdb != null && loadedWithTmdb != hasTmdb) rowsCache.clear()
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isTmdbConfigured = hasTmdb) }
            when (val result = load(filter)) {
                is NetworkResult.Success -> {
                    loadedWithTmdb = hasTmdb
                    val rows = result.data
                    rowsCache[filter] = rows
                    _uiState.update {
                        if (it.filter != filter) it
                        else it.copy(isLoading = false, catalogRows = rows, featuredItem = pickFeatured(rows))
                    }
                    prefetchOtherFilters()
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.userMessage)
                }
            }
        }
    }

    private suspend fun load(filter: HomeFilter) =
        if (filter == HomeFilter.ANIME) catalogRepository.loadAnimeRows() else catalogRepository.loadHomeCatalogs(filter.mediaType)

    /** Loads the other filters quietly in the background once the first one is shown. */
    private fun prefetchOtherFilters() {
        if (prefetched) return
        prefetched = true
        viewModelScope.launch {
            HomeFilter.entries.filter { it !in rowsCache }.forEach { filter ->
                (load(filter) as? NetworkResult.Success)?.data?.let { rowsCache[filter] = it }
            }
        }
    }

    private fun pickFeatured(rows: List<CatalogRow>): MediaItem? {
        val candidates = rows.firstOrNull()?.items.orEmpty()
        return candidates.firstOrNull { !it.backdropUrl.isNullOrBlank() && it.overview.isNotBlank() }
            ?: candidates.firstOrNull()
    }
}
