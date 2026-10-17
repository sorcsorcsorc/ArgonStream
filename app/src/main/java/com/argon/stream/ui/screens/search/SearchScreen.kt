package com.argon.stream.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
        onOpenMediaDetails = onOpenMediaDetails
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onTypeFilterChange: (MediaType?) -> Unit,
    onMinRatingChange: (Double) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onOpenMediaDetails: (MediaItem) -> Unit
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
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
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
                FilterChip(
                    selected = state.selectedType == type,
                    onClick = { onTypeFilterChange(type) },
                    label = { Text(label) }
                )
            }
            item(key = "rating") {
                FilterChip(
                    selected = state.minRating >= 7.0,
                    onClick = { onMinRatingChange(if (state.minRating >= 7.0) 0.0 else 7.0) },
                    label = { Text("Rated 7+") }
                )
            }
            items(SortOrder.entries.filter { it != SortOrder.RELEVANCE }, key = { it.name }) { order ->
                FilterChip(
                    selected = state.sortOrder == order,
                    onClick = {
                        onSortOrderChange(if (state.sortOrder == order) SortOrder.RELEVANCE else order)
                    },
                    label = { Text(order.label) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.errorMessage != null -> EmptyState(
                    icon = Icons.Default.CloudOff,
                    title = "Search failed",
                    message = state.errorMessage.orEmpty()
                )

                state.query.trim().length < 2 -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "Find something to watch",
                    message = "Type at least 2 characters to search movies and series."
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
