package com.argon.stream.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.argon.stream.data.repository.Profile
import com.argon.stream.domain.model.CatalogGenres
import com.argon.stream.domain.model.CatalogKind
import com.argon.stream.domain.model.CatalogQuery
import com.argon.stream.domain.model.CatalogRow
import com.argon.stream.domain.model.WatchProgressItem
import com.argon.stream.ui.screens.profiles.ProfileAvatar
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import com.argon.stream.ui.components.EmptyState
import com.argon.stream.ui.components.MediaCard
import com.argon.stream.ui.components.ShimmerPlaceholder
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenMediaDetails: (MediaItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCatalog: (query: CatalogQuery, title: String) -> Unit = { _, _ -> },
    onOpenLibrary: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onScreenVisible() }

    HomeContent(
        state = state,
        onOpenMediaDetails = onOpenMediaDetails,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onSetFilter = viewModel::setFilter,
        onRetry = viewModel::refreshCatalogs,
        onSwitchProfile = viewModel::switchProfile,
        onOpenCatalog = onOpenCatalog,
        onOpenLibrary = onOpenLibrary,
        onRemoveContinueWatching = viewModel::removeFromContinueWatching
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    onOpenMediaDetails: (MediaItem) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onSetFilter: (MediaType?) -> Unit,
    onRetry: () -> Unit,
    onSwitchProfile: () -> Unit = {},
    onOpenCatalog: (query: CatalogQuery, title: String) -> Unit = { _, _ -> },
    onOpenLibrary: () -> Unit = {},
    onRemoveContinueWatching: (WatchProgressItem) -> Unit = {}
) {
    val cardWidth = rememberPosterCardWidth()
    var tipDismissed by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<WatchProgressItem?>(null) }

    pendingRemoval?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove from Continue Watching?") },
            text = { Text("\"${item.mediaItem.title}\" will be removed from this row. Your watched marks stay.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveContinueWatching(item)
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val hero = state.featuredItem
        if (hero != null && !state.isLoading && state.errorMessage == null) {
            item(key = "hero_banner") {
                FeaturedHeroBanner(
                    item = hero,
                    onOpen = { onOpenMediaDetails(hero) },
                    onSearch = onOpenSearch,
                    profile = state.activeProfile,
                    onSwitchProfile = onSwitchProfile
                )
            }
        } else {
            item(key = "header") {
                HomeTopBar(onSearch = onOpenSearch, profile = state.activeProfile, onSwitchProfile = onSwitchProfile)
            }
        }

        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(null to "All", MediaType.MOVIE to "Movies", MediaType.SERIES to "Series")
                items(filters, key = { it.second }) { (type, label) ->
                    FilterChip(
                        selected = state.filterType == type,
                        onClick = { onSetFilter(type) },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (!state.isTmdbConfigured && !tipDismissed && !state.isLoading) {
            item(key = "tmdb_tip") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(
                            text = "Browsing the free Cinemeta catalog. Add a TMDB key in Settings for TMDB trending lists and cast photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(modifier = Modifier.align(Alignment.End)) {
                            TextButton(onClick = { tipDismissed = true }) { Text("Dismiss") }
                            TextButton(onClick = onOpenSettings) { Text("Settings") }
                        }
                    }
                }
            }
        }

        when {
            state.isLoading -> item(key = "skeleton") { HomeSkeleton(cardWidth) }

            state.errorMessage != null -> item(key = "error") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.CloudOff,
                        title = "Couldn't load catalogs",
                        message = state.errorMessage.orEmpty(),
                        action = {
                            Button(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Try again")
                            }
                        }
                    )
                }
            }

            else -> {
                if (state.continueWatching.isNotEmpty()) {
                    item(key = "continue_watching") {
                        PosterRow(title = "Continue Watching") {
                            items(
                                items = state.continueWatching,
                                key = { "${it.mediaItem.type}_${it.mediaItem.id}" }
                            ) { progressItem ->
                                val badge = if (progressItem.seasonNumber != null && progressItem.episodeNumber != null) {
                                    "S${progressItem.seasonNumber} · E${progressItem.episodeNumber}"
                                } else null
                                MediaCard(
                                    item = progressItem.mediaItem,
                                    progressFraction = progressItem.progressFraction,
                                    episodeBadge = badge,
                                    onClick = onOpenMediaDetails,
                                    onLongClick = { pendingRemoval = progressItem },
                                    modifier = Modifier.width(cardWidth)
                                )
                            }
                        }
                    }
                }

                val myList = state.myList.filter { state.filterType == null || it.type == state.filterType }
                if (myList.isNotEmpty()) {
                    item(key = "my_list") {
                        PosterRow(title = "My List", onSeeAll = onOpenLibrary) {
                            items(myList, key = { "my_${it.type}_${it.id}" }) { media ->
                                MediaCard(item = media, onClick = onOpenMediaDetails, modifier = Modifier.width(cardWidth))
                            }
                        }
                    }
                }

                state.catalogRows.forEachIndexed { index, row ->
                    item(key = row.id) {
                        CatalogRowSection(
                            row = row,
                            cardWidth = cardWidth,
                            onItemClick = onOpenMediaDetails,
                            onSeeAll = row.query?.let { query -> { onOpenCatalog(query, row.title) } }
                        )
                    }
                    if (index == 0) {
                        item(key = "genres") {
                            GenreSection(
                                type = state.filterType ?: MediaType.MOVIE,
                                onOpenGenre = { genre, type ->
                                    onOpenCatalog(CatalogQuery(CatalogKind.GENRE, type, genre), genre)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Poster width that fits ~2.6 cards on small phones and more on larger screens. */
@Composable
fun rememberPosterCardWidth(): Dp {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidth < 360 -> 112.dp
        screenWidth < 600 -> 128.dp
        else -> 148.dp
    }
}

@Composable
private fun HomeTopBar(onSearch: () -> Unit, profile: Profile?, onSwitchProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Argon",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = "Search")
        }
        ProfileButton(profile, onSwitchProfile)
    }
}

@Composable
private fun FeaturedHeroBanner(
    item: MediaItem,
    onOpen: () -> Unit,
    onSearch: () -> Unit,
    profile: Profile?,
    onSwitchProfile: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isWide = maxWidth > 600.dp
        val heroHeight = if (isWide) 320.dp else (maxWidth * 1.05f).coerceIn(320.dp, 460.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight)
        ) {
            AsyncImage(
                model = if (isWide) item.backdropUrl ?: item.posterUrl else item.posterUrl ?: item.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.25f to Color.Transparent,
                            0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Argon",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onSearch,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                ProfileButton(profile, onSwitchProfile)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                HeroMetaLine(item)
                if (item.overview.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpen) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Watch now")
                }
            }
        }
    }
}

@Composable
private fun ProfileButton(profile: Profile?, onClick: () -> Unit) {
    if (profile == null) return
    IconButton(onClick = onClick) {
        ProfileAvatar(name = profile.name, colorIndex = profile.colorIndex, size = 30.dp)
    }
}

@Composable
private fun HeroMetaLine(item: MediaItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item.rating?.takeIf { it > 0 }?.let { rating ->
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        val parts = listOfNotNull(
            item.releaseYear,
            if (item.type == MediaType.SERIES) "Series" else "Movie",
            item.genres.take(2).joinToString(", ").takeIf { it.isNotBlank() }
        )
        Text(
            text = parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PosterRow(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp)
                .heightIn(min = 40.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) { Text("See all") }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun CatalogRowSection(
    row: CatalogRow,
    cardWidth: Dp,
    onItemClick: (MediaItem) -> Unit,
    onSeeAll: (() -> Unit)? = null
) {
    PosterRow(title = row.title, onSeeAll = onSeeAll) {
        items(items = row.items, key = { "${it.type}_${it.id}" }) { media ->
            MediaCard(
                item = media,
                onClick = onItemClick,
                modifier = Modifier.width(cardWidth)
            )
        }
    }
}

/** "Browse by genre" chips; each opens an endlessly scrolling genre grid. */
@Composable
private fun GenreSection(type: MediaType, onOpenGenre: (genre: String, type: MediaType) -> Unit) {
    Column {
        Text(
            text = "Browse by genre",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CatalogGenres.forType(type), key = { it }) { genre ->
                AssistChip(onClick = { onOpenGenre(genre, type) }, label = { Text(genre) })
            }
        }
    }
}

@Composable
private fun HomeSkeleton(cardWidth: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(3) {
            ShimmerPlaceholder(
                modifier = Modifier
                    .width(160.dp)
                    .height(22.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .width(cardWidth)
                            .height(cardWidth * 1.5f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}
