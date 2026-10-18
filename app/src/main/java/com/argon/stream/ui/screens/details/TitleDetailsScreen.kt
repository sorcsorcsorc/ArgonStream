package com.argon.stream.ui.screens.details

import com.argon.stream.ui.components.ArgonFilterChip
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.argon.stream.domain.model.CastMember
import com.argon.stream.domain.model.EpisodeItem
import com.argon.stream.domain.model.MediaDetails
import com.argon.stream.domain.model.MediaType
import com.argon.stream.ui.components.EmptyState
import com.argon.stream.ui.components.focusHighlight
import java.time.LocalDate
import java.util.Locale

@Composable
fun TitleDetailsScreen(
    onBack: () -> Unit,
    onSelectStreams: (stremioVideoId: String, title: String) -> Unit,
    viewModel: TitleDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val details = state.details

    when {
        state.isLoading && details == null -> Box(modifier = Modifier.fillMaxSize()) {
            BackButton(onBack, Modifier.statusBarsPadding().padding(8.dp))
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        details == null -> Box(modifier = Modifier.fillMaxSize()) {
            BackButton(onBack, Modifier.statusBarsPadding().padding(8.dp))
            EmptyState(
                icon = Icons.Default.ErrorOutline,
                title = "Couldn't open this title",
                message = state.errorMessage ?: "Metadata is unavailable right now.",
                modifier = Modifier.align(Alignment.Center),
                action = {
                    Button(onClick = viewModel::loadDetails) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Try again")
                    }
                }
            )
        }

        else -> DetailsContent(
            details = details,
            state = state,
            onBack = onBack,
            onSelectStreams = onSelectStreams,
            onToggleWatchlist = viewModel::toggleWatchlist,
            onToggleFavorite = viewModel::toggleFavorite,
            onSelectSeason = viewModel::selectSeason,
            onToggleWatched = viewModel::toggleWatched
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    FilledIconButton(
        onClick = onBack,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White
        ),
        modifier = modifier
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsContent(
    details: MediaDetails,
    state: TitleDetailsUiState,
    onBack: () -> Unit,
    onSelectStreams: (String, String) -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onToggleWatched: (season: Int, episode: Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val item = details.item
    val imdbId = item.imdbId ?: item.id.takeIf { it.startsWith("tt") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val backdropHeight = (maxWidth * 0.62f).coerceIn(220.dp, 360.dp)
                val posterWidth = if (maxWidth < 360.dp) 96.dp else 112.dp
                val posterOverlap = 64.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(backdropHeight)
                ) {
                    AsyncImage(
                        model = item.backdropUrl ?: item.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.5f),
                                    0.3f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.background
                                )
                            )
                    )
                    BackButton(onBack, Modifier.statusBarsPadding().padding(8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = backdropHeight - posterOverlap),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .width(posterWidth)
                            .aspectRatio(2f / 3f)
                    ) {
                        if (item.posterUrl != null) {
                            AsyncImage(
                                model = item.posterUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Movie, contentDescription = null)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        MetaLine(details)
                    }
                }
            }
        }

        item(key = "actions") {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (item.type == MediaType.MOVIE) {
                    Button(
                        onClick = { imdbId?.let { onSelectStreams(it, item.title) } },
                        enabled = imdbId != null,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Find streams")
                    }
                }

                // Watchlist and Favorite are separate: either, both or neither can be on.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    DetailAction(
                        label = "Watchlist",
                        icon = if (state.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        active = state.isInWatchlist,
                        onClick = onToggleWatchlist
                    )
                    DetailAction(
                        label = "Favorite",
                        icon = if (state.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        active = state.isFavorite,
                        activeTint = MaterialTheme.colorScheme.error,
                        onClick = onToggleFavorite
                    )
                    if (item.type == MediaType.MOVIE) {
                        val watched = (0 to 0) in state.watched
                        DetailAction(
                            label = if (watched) "Watched" else "Mark watched",
                            icon = if (watched) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                            active = watched,
                            activeTint = MaterialTheme.colorScheme.tertiary,
                            onClick = { onToggleWatched(0, 0) }
                        )
                    }
                    details.trailerYoutubeId?.let { videoId ->
                        DetailAction(
                            label = "Trailer",
                            icon = Icons.Default.Movie,
                            active = false,
                            onClick = { openTrailer(context, videoId) }
                        )
                    }
                }

                if (imdbId == null) {
                    Text(
                        text = "Streams can't be searched for this title because it has no IMDb ID.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                details.tagline?.let {
                    Text(
                        text = "“$it”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (item.genres.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item.genres.forEach { genre ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                ExpandableText(item.overview.ifBlank { "No synopsis available." })
            }
        }

        if (details.cast.isNotEmpty()) {
            item(key = "cast") {
                Column {
                    Text(
                        text = "Cast",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(details.cast, key = { "${it.id}_${it.name}" }) { member ->
                            CastCard(member)
                        }
                    }
                }
            }
        }

        if (item.type == MediaType.SERIES) {
            item(key = "seasons") {
                Column {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (details.seasons.isEmpty()) {
                        Text(
                            text = "No season information available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(details.seasons, key = { it.seasonNumber }) { season ->
                                ArgonFilterChip(
                                    selected = state.selectedSeasonNumber == season.seasonNumber,
                                    onClick = { onSelectSeason(season.seasonNumber) },
                                    label = { Text(season.name) }
                                )
                            }
                        }
                    }
                }
            }

            when {
                state.isLoadingEpisodes -> item(key = "episodes_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                state.episodesError != null -> item(key = "episodes_error") {
                    Text(
                        text = state.episodesError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                else -> items(state.episodes, key = { "ep_${it.seasonNumber}_${it.episodeNumber}_${it.id}" }) { ep ->
                    EpisodeRowItem(
                        episode = ep,
                        enabled = imdbId != null,
                        watched = (ep.seasonNumber to ep.episodeNumber) in state.watched,
                        onToggleWatched = { onToggleWatched(ep.seasonNumber, ep.episodeNumber) },
                        onClick = {
                            imdbId?.let {
                                onSelectStreams(
                                    ep.buildStremioVideoId(it),
                                    "${item.title} S${ep.seasonNumber}E${ep.episodeNumber}"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaLine(details: MediaDetails) {
    val item = details.item
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
                style = MaterialTheme.typography.labelMedium
            )
        }
        val runtime = details.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
            if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
        }
        val seasons = details.seasons.size.takeIf { item.type == MediaType.SERIES && it > 0 }
            ?.let { if (it == 1) "1 season" else "$it seasons" }
        Text(
            text = listOfNotNull(item.releaseYear, runtime, seasons).joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExpandableText(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .animateContentSize()
            .clickable { expanded = !expanded }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (!expanded && text.length > 220) {
            Text(
                text = "More",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CastCard(member: CastMember) {
    Column(
        modifier = Modifier.width(84.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp)
        ) {
            if (member.profileUrl != null) {
                AsyncImage(
                    model = member.profileUrl,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = member.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (member.character.isNotBlank()) {
            Text(
                text = member.character,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EpisodeRowItem(
    episode: EpisodeItem,
    enabled: Boolean,
    watched: Boolean,
    onToggleWatched: () -> Unit,
    onClick: () -> Unit
) {
    val isUpcoming = episode.airDate?.let { date ->
        runCatching { LocalDate.parse(date.take(10)).isAfter(LocalDate.now()) }.getOrDefault(false)
    } ?: false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (episode.stillUrl != null) {
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNumber}. ${episode.title}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                episode.airDate?.take(10),
                episode.runtimeMinutes?.takeIf { it > 0 }?.let { "${it}m" },
                "Upcoming".takeIf { isUpcoming }
            ).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUpcoming) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (episode.overview.isNotBlank()) {
                Text(
                    text = episode.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onToggleWatched) {
            Icon(
                imageVector = if (watched) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                contentDescription = if (watched) "Mark as unwatched" else "Mark as watched",
                tint = if (watched) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Icon over a short label, used for the Watchlist / Favorite / Watched / Trailer actions. */
@Composable
private fun DetailAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    activeTint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .focusHighlight()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Round tonal icon "buttons", tinted when on.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (active) activeTint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Icon(icon, contentDescription = null, tint = if (active) activeTint else MaterialTheme.colorScheme.onSurface)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) activeTint else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Opens the trailer in the YouTube app (phone or TV) or the browser. */
private fun openTrailer(context: Context, youtubeId: String) {
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$youtubeId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$youtubeId")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(app) }.recoverCatching { context.startActivity(web) }
}
