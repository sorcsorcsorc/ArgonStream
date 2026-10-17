package com.argon.stream.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.ui.components.EmptyState
import com.argon.stream.ui.components.MediaCard
import com.argon.stream.ui.components.ScreenHeader
import com.argon.stream.ui.screens.home.rememberPosterCardWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenMediaDetails: (MediaItem) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryContent(state = state, onOpenMediaDetails = onOpenMediaDetails)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryContent(
    state: LibraryUiState,
    onOpenMediaDetails: (MediaItem) -> Unit,
    initialTab: Int = 0
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    val cardWidth = rememberPosterCardWidth()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = "Library")

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            listOf("Watchlist", "Favorites", "History").forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (selectedTab) {
                0 -> LibraryGrid(
                    items = state.watchlist.map { it to null },
                    cardWidth = cardWidth,
                    onOpen = onOpenMediaDetails,
                    emptyIcon = Icons.Default.BookmarkBorder,
                    emptyTitle = "Your watchlist is empty",
                    emptyMessage = "Tap the bookmark on any title to save it here."
                )
                1 -> LibraryGrid(
                    items = state.favorites.map { it to null },
                    cardWidth = cardWidth,
                    onOpen = onOpenMediaDetails,
                    emptyIcon = Icons.Default.FavoriteBorder,
                    emptyTitle = "No favorites yet",
                    emptyMessage = "Tap the heart on any title to add it to your favorites."
                )
                else -> LibraryGrid(
                    items = state.history.map { progress ->
                        val badge = if (progress.seasonNumber != null && progress.episodeNumber != null) {
                            "S${progress.seasonNumber} · E${progress.episodeNumber}"
                        } else null
                        progress.mediaItem to (progress.progressFraction to badge)
                    },
                    cardWidth = cardWidth,
                    onOpen = onOpenMediaDetails,
                    emptyIcon = Icons.Default.History,
                    emptyTitle = "Nothing watched yet",
                    emptyMessage = "Titles you start watching show up here so you can resume them."
                )
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    items: List<Pair<MediaItem, Pair<Float, String?>?>>,
    cardWidth: androidx.compose.ui.unit.Dp,
    onOpen: (MediaItem) -> Unit,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptyMessage: String
) {
    if (items.isEmpty()) {
        EmptyState(icon = emptyIcon, title = emptyTitle, message = emptyMessage)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { (item, _) -> "${item.type}_${item.id}" }) { (item, progress) ->
            MediaCard(
                item = item,
                onClick = onOpen,
                progressFraction = progress?.first,
                episodeBadge = progress?.second
            )
        }
    }
}
