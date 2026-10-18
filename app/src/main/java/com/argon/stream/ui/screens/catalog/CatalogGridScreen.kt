package com.argon.stream.ui.screens.catalog

import com.argon.stream.ui.components.ArgonFilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.ui.components.EmptyState
import com.argon.stream.ui.components.MediaCard
import com.argon.stream.ui.components.ScreenHeader

@Composable
fun CatalogGridScreen(
    onBack: () -> Unit,
    onOpenMediaDetails: (MediaItem) -> Unit,
    viewModel: CatalogGridViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CatalogGridContent(
        state = state,
        onBack = onBack,
        onOpenMediaDetails = onOpenMediaDetails,
        onSetType = viewModel::setType,
        onLoadMore = viewModel::loadNextPage
    )
}

@Composable
internal fun CatalogGridContent(
    state: CatalogGridUiState,
    onBack: () -> Unit,
    onOpenMediaDetails: (MediaItem) -> Unit,
    onSetType: (MediaType) -> Unit,
    onLoadMore: () -> Unit
) {
    val gridState = rememberLazyGridState()

    // Load the next page when the user is within a couple of rows of the end.
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 10
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd && !state.endReached && !state.isLoading) onLoadMore()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = state.title,
            navigation = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        )

        if (state.switchableTypes.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                state.switchableTypes.forEach { type ->
                    ArgonFilterChip(
                        selected = state.query?.type == type,
                        onClick = { onSetType(type) },
                        label = { Text(if (type == MediaType.MOVIE) "Movies" else "Series") }
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.isLoading && state.items.isEmpty() -> CircularProgressIndicator()

                state.items.isEmpty() && state.errorMessage != null -> EmptyState(
                    icon = Icons.Default.CloudOff,
                    title = "Couldn't load this list",
                    message = state.errorMessage,
                    action = { Button(onClick = onLoadMore) { Text("Try again") } }
                )

                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Default.SearchOff,
                    title = "Nothing here yet",
                    message = "This list is empty right now."
                )

                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { "${it.type}_${it.id}" }) { item ->
                        MediaCard(item = item, onClick = onOpenMediaDetails)
                    }
                    if (state.isLoadingMore || state.errorMessage != null) {
                        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (state.errorMessage != null) {
                                    TextButton(onClick = onLoadMore) {
                                        Text("Couldn't load more. Tap to retry", color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
