package com.argon.stream.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.data.local.WatchlistEntity
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.WatchProgressItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LibraryUiState(
    val watchlist: List<MediaItem> = emptyList(),
    val favorites: List<MediaItem> = emptyList(),
    val history: List<WatchProgressItem> = emptyList()
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    catalogRepository: CatalogRepository
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        catalogRepository.observeWatchlist(),
        catalogRepository.observeFavorites(),
        catalogRepository.observeContinueWatching()
    ) { watchlist, favorites, historyItems ->
        LibraryUiState(
            watchlist = watchlist.map { it.toMediaItem() },
            favorites = favorites.map { it.toMediaItem() },
            history = historyItems
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    private fun WatchlistEntity.toMediaItem() = MediaItem(
        id = mediaId,
        tmdbId = tmdbId,
        imdbId = imdbId,
        title = title,
        type = mediaType,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        overview = "",
        releaseYear = releaseYear,
        rating = rating
    )
}