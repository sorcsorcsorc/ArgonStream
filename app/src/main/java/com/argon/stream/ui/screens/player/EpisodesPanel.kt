package com.argon.stream.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.argon.stream.domain.model.EpisodeItem

/** Side panel in the player listing every episode of the show, so you can jump to any of them. */
@Composable
internal fun EpisodesPanel(
    state: PlayerUiState,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onClose: () -> Unit
) {
    val panel = state.episodesPanel
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClose)
    ) {
        Surface(
            color = Color(0xF20D1117),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .then(if (wide) Modifier.width(420.dp) else Modifier.fillMaxWidth(0.62f))
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.systemBars))
                    .padding(top = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Episodes", style = MaterialTheme.typography.titleLarge)
                        Text(state.title, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
                }
                if (panel.seasons.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        items(panel.seasons, key = { it }) { season ->
                            FilterChip(
                                selected = season == panel.selectedSeason,
                                onClick = { onSelectSeason(season) },
                                label = { Text("Season $season") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    labelColor = Color.White,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
                when {
                    panel.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    panel.error != null -> Text(panel.error, modifier = Modifier.padding(20.dp), color = Color.White.copy(alpha = 0.8f))
                    else -> {
                        val listState = rememberLazyListState()
                        val currentIndex = panel.episodes.indexOfFirst {
                            it.seasonNumber == state.seasonNumber && it.episodeNumber == state.episodeNumber
                        }
                        LaunchedEffect(panel.selectedSeason, panel.episodes.size) {
                            if (currentIndex > 0) listState.scrollToItem(currentIndex)
                        }
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp)
                        ) {
                            items(panel.episodes, key = { "${it.seasonNumber}_${it.episodeNumber}" }) { episode ->
                                EpisodeRow(
                                    episode = episode,
                                    isCurrent = episode.seasonNumber == state.seasonNumber && episode.episodeNumber == state.episodeNumber,
                                    isWatched = (episode.seasonNumber to episode.episodeNumber) in state.watchedEpisodes,
                                    onClick = { onPlayEpisode(episode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeItem, isCurrent: Boolean, isWatched: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val highlight = when {
        focused -> Color.White.copy(alpha = 0.16f)
        isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(highlight)
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (episode.stillUrl != null) {
                AsyncImage(model = episode.stillUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (isCurrent) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Playing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${episode.episodeNumber}. ${episode.title}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(episode.runtimeMinutes?.let { "${it}m" }, episode.airDate?.take(10)).joinToString("  ·  ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
            }
            if (episode.overview.isNotBlank()) {
                Text(
                    episode.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isWatched) {
            Icon(Icons.Default.CheckCircle, contentDescription = "Watched", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 6.dp).size(18.dp))
        }
    }
}
