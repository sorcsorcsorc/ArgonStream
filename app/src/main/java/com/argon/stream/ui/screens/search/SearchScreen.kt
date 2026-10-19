package com.argon.stream.ui.screens.search

import com.argon.stream.ui.components.ArgonFilterChip
import androidx.compose.foundation.layout.Arrangement
import com.argon.stream.ui.components.focusHighlight
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.ui.components.EmptyState
import com.argon.stream.ui.components.MediaCard

@Composable
fun SearchScreen(
    onOpenMediaDetails: (MediaItem) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onTypeFilterChange = viewModel::onTypeFilterChange,
        onMinRatingChange = viewModel::onMinRatingChange,
        onSortOrderChange = viewModel::onSortOrderChange,
        onOpenMediaDetails = { item ->
            viewModel.rememberCurrentQuery()
            onOpenMediaDetails(item)
        },
        onSubmitSearch = viewModel::rememberCurrentQuery,
        onRemoveRecent = viewModel::removeRecentSearch,
        onClearRecents = viewModel::clearRecentSearches
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onTypeFilterChange: (MediaType?) -> Unit,
    onMinRatingChange: (Double) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onOpenMediaDetails: (MediaItem) -> Unit,
    onSubmitSearch: () -> Unit = {},
    onRemoveRecent: (String) -> Unit = {},
    onClearRecents: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search movies and series") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSubmitSearch()
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            val typeFilters = listOf(null to "All", MediaType.MOVIE to "Movies", MediaType.SERIES to "Series")
            items(typeFilters, key = { it.second }) { (type, label) ->
                ArgonFilterChip(
                    selected = state.selectedType == type,
                    onClick = { onTypeFilterChange(type) },
                    label = { Text(label) }
                )
            }
            item(key = "rating") {
                ArgonFilterChip(
                    selected = state.minRating >= 7.0,
                    onClick = { onMinRatingChange(if (state.minRating >= 7.0) 0.0 else 7.0) },
                    label = { Text("Rated 7+") }
                )
            }
            items(SortOrder.entries.filter { it != SortOrder.RELEVANCE }, key = { it.name }) { order ->
                ArgonFilterChip(
                    selected = state.sortOrder == order,
                    onClick = {
                        onSortOrderChange(if (state.sortOrder == order) SortOrder.RELEVANCE else order)
                    },
                    label = { Text(order.label) }
                )
            }
        }

        // Fast matches stay on screen while the full list is still loading.
        if (state.isLoading && state.results.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.isLoading && state.results.isEmpty() -> CircularProgressIndicator()

                state.errorMessage != null -> EmptyState(
                    icon = Icons.Default.CloudOff,
                    title = "Search failed",
                    message = state.errorMessage.orEmpty()
                )

                state.query.trim().length < 2 -> SearchSuggestions(
                    state = state,
                    onPickRecent = onQueryChange,
                    onRemoveRecent = onRemoveRecent,
                    onClearRecents = onClearRecents,
                    onOpen = onOpenMediaDetails
                )

                state.results.isEmpty() -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "No results",
                    message = "Nothing matched \"${state.query.trim()}\". Try a different title or remove filters."
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(items = state.results, key = { "${it.type}_${it.id}" }) { item ->
                        MediaCard(item = item, onClick = onOpenMediaDetails)
                    }
                }
            }
        }
    }
}

/** Before typing: recent searches and popular titles to browse. */
@Composable
private fun SearchSuggestions(
    state: SearchUiState,
    onPickRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpen: (MediaItem) -> Unit
) {
    if (state.suggestions.isEmpty() && state.recentSearches.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Search,
            title = "Find something to watch",
            message = "Type at least 2 characters to search movies and series."
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (state.recentSearches.isNotEmpty()) {
            item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                RecentSearchesList(
                    recents = state.recentSearches.take(5),
                    onPick = onPickRecent,
                    onRemove = onRemoveRecent,
                    onClear = onClearRecents
                )
            }
        }
        if (state.suggestions.isNotEmpty()) {
            item(key = "popular_title", span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Popular right now",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(state.suggestions, key = { "s_${it.type}_${it.id}" }) { item ->
                MediaCard(item = item, onClick = onOpen)
            }
        }
    }
}

@Composable
private fun RecentSearchesList(
    recents: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        run {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Recent searches", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        recents.forEach { query ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusHighlight()
                    .clickable { onPick(query) }
                    .padding(start = 4.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(16.dp))
                Text(query, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { onRemove(query) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove $query", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
