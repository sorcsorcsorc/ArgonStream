package com.argon.stream.ui.screens.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.domain.model.EpisodeItem
import com.argon.stream.domain.model.MediaDetails
import com.argon.stream.domain.model.MediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TitleDetailsUiState(
    val isLoading: Boolean = true,
    val details: MediaDetails? = null,
    val selectedSeasonNumber: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val isLoadingEpisodes: Boolean = false,
    val episodesError: String? = null,
    val isInWatchlist: Boolean = false,
    val isFavorite: Boolean = false,
    /** Watched marks as (season, episode); a movie uses (0, 0). */
    val watched: Set<Pair<Int, Int>> = emptySet(),
    val errorMessage: String? = null
)

@HiltViewModel
class TitleDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val typeArg: String = savedStateHandle["type"] ?: MediaType.MOVIE.name
    val mediaId: String = savedStateHandle["mediaId"] ?: ""
    val mediaType: MediaType = runCatching { MediaType.valueOf(typeArg) }.getOrDefault(MediaType.MOVIE)

    private val _uiState = MutableStateFlow(TitleDetailsUiState())
    val uiState: StateFlow<TitleDetailsUiState> = _uiState.asStateFlow()

    private var episodesJob: Job? = null

    init {
        viewModelScope.launch {
            catalogRepository.observeWatchlistEntry(mediaId).collectLatest { entry ->
                _uiState.update {
                    it.copy(isInWatchlist = entry?.isInWatchlist == true, isFavorite = entry?.isFavorite == true)
                }
            }
        }
        viewModelScope.launch {
            catalogRepository.observeWatched(mediaId).collectLatest { marks ->
                _uiState.update { it.copy(watched = marks) }
            }
        }
        loadDetails()
    }

    fun loadDetails() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = catalogRepository.getMediaDetails(mediaType, mediaId)) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(isLoading = false, details = res.data) }
                    if (mediaType == MediaType.SERIES) {
                        val firstSeason = res.data.seasons.firstOrNull()?.seasonNumber ?: 1
                        selectSeason(firstSeason)
                    }
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.userMessage)
                }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        episodesJob?.cancel()
        _uiState.update {
            it.copy(
                selectedSeasonNumber = seasonNumber,
                isLoadingEpisodes = true,
                episodesError = null,
                episodes = emptyList()
            )
        }
        episodesJob = viewModelScope.launch {
            when (val res = catalogRepository.getSeasonEpisodes(mediaId, seasonNumber)) {
                is NetworkResult.Success -> _uiState.update {
                    it.copy(isLoadingEpisodes = false, episodes = res.data)
                }
                is NetworkResult.Error -> _uiState.update {
                    it.copy(isLoadingEpisodes = false, episodesError = res.userMessage)
                }
            }
        }
    }

    fun toggleWatchlist() {
        val item = _uiState.value.details?.item ?: return
        viewModelScope.launch { catalogRepository.toggleWatchlist(item) }
    }

    fun toggleFavorite() {
        val item = _uiState.value.details?.item ?: return
        viewModelScope.launch { catalogRepository.toggleFavorite(item) }
    }

    fun toggleWatched(season: Int, episode: Int) {
        val isWatched = (season to episode) in _uiState.value.watched
        viewModelScope.launch { catalogRepository.setWatched(mediaId, season, episode, !isWatched) }
    }
}
