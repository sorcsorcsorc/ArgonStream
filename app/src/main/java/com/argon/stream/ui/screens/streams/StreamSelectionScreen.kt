package com.argon.stream.ui.screens.streams

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.domain.model.StreamCandidate
import com.argon.stream.ui.components.EmptyState
import com.argon.stream.ui.components.focusHighlight
import com.argon.stream.ui.components.ScreenHeader
import java.util.Locale

@Composable
fun StreamSelectionScreen(
    onBack: () -> Unit,
    onOpenAddons: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchResolvedStream: (ReadyStreamLaunchEvent) -> Unit,
    viewModel: StreamSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.launchPlayerEvent.collect { event -> onLaunchResolvedStream(event) }
    }

    StreamSelectionContent(
        state = state,
        onBack = onBack,
        onOpenAddons = onOpenAddons,
        onOpenSettings = onOpenSettings,
        onRefresh = viewModel::refreshStreams,
        onSelectFilter = viewModel::setResolutionFilter,
        onSelectStream = viewModel::onCandidateClicked,
        onDownloadStream = viewModel::onDownloadClicked,
        onSetSort = viewModel::setSort,
        onDismissStatus = viewModel::dismissStatusDialog,
        onDismissMultiFile = viewModel::dismissMultiFileDialog,
        onSelectMultiFile = viewModel::onMultiFileSelected,
        onDismissToast = viewModel::dismissToast
    )
}

@Composable
internal fun StreamSelectionContent(
    state: StreamSelectionUiState,
    onBack: () -> Unit,
    onOpenAddons: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFilter: (String?) -> Unit,
    onSelectStream: (StreamCandidate) -> Unit,
    onDownloadStream: (StreamCandidate) -> Unit = {},
    onSetSort: (StreamSort) -> Unit = {},
    onDismissStatus: () -> Unit,
    onDismissMultiFile: () -> Unit,
    onSelectMultiFile: (Int, com.argon.stream.domain.model.TorBoxMediaFile) -> Unit,
    onDismissToast: () -> Unit = {}
) {

    state.multiFilePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = onDismissMultiFile,
            title = { Text(if (prompt.forDownload) "Choose a file to download" else "Choose a file") },
            text = {
                Column {
                    Text(
                        text = prompt.torrentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(prompt.files, key = { it.id }) { file ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectMultiFile(prompt.torrentId, file) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(file.shortName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = formatSize(file.sizeBytes),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissMultiFile) { Text("Cancel") }
            }
        )
    }

    state.statusBanner?.let { banner ->
        AlertDialog(
            onDismissRequest = onDismissStatus,
            icon = if (state.isStatusError) {
                { Icon(Icons.Default.WarningAmber, contentDescription = null) }
            } else null,
            title = { Text(if (state.isStatusError) "Couldn't play this stream" else "Heads up") },
            text = { Text(banner) },
            confirmButton = {
                TextButton(onClick = onDismissStatus) { Text("OK") }
            }
        )
    }

    // Keep the text while the toast fades out after the message is cleared.
    var lastToast by remember { mutableStateOf("") }
    state.toastMessage?.let { lastToast = it }
    LaunchedEffect(state.toastId) {
        if (state.toastMessage != null) {
            delay(2_500)
            onDismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                title = state.title,
                subtitle = if (state.isLoading) "Searching your add-ons…" else "${state.streams.size} streams",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh streams")
                    }
                }
            )

            if (state.isResolving) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Preparing stream…",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            when {
                state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                !state.hasStreamAddons -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Extension,
                        title = "No stream add-ons installed",
                        message = "Argon finds streams through Stremio add-ons. Install one (for example your AIOStreams or Torrentio manifest URL) in Add-ons.",
                        action = {
                            Button(onClick = onOpenAddons) { Text("Open Add-ons") }
                        }
                    )
                }

                state.streams.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No streams found",
                        message = buildString {
                            append("Your add-ons didn't return any streams for this title.")
                            if (state.hiddenByQualityLimit > 0) {
                                append(" ${state.hiddenByQualityLimit} stream(s) above ${state.maxResolution} are hidden by your quality limit in Settings.")
                            }
                            state.providerWarnings.firstOrNull()?.let { append("\n\n$it") }
                        },
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onOpenAddons) { Text("Add-ons") }
                                if (state.hiddenByQualityLimit > 0) {
                                    OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
                                }
                                Button(onClick = onRefresh) { Text("Retry") }
                            }
                        }
                    )
                }

                else -> StreamList(
                    state = state,
                    onSelectFilter = onSelectFilter,
                    onSelectStream = onSelectStream,
                    onDownloadStream = onDownloadStream,
                    onSetSort = onSetSort
                )
            }
        }

        // Short, self-dismissing confirmation (e.g. "Downloading…") instead of a blocking dialog.
        AnimatedVisibility(
            visible = state.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = lastToast,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamList(
    state: StreamSelectionUiState,
    onSelectFilter: (String?) -> Unit,
    onSelectStream: (StreamCandidate) -> Unit,
    onDownloadStream: (StreamCandidate) -> Unit,
    onSetSort: (StreamSort) -> Unit
) {
    var showWarnings by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.streams.size > 1 || state.availableResolutions.size > 1) {
            item(key = "sort") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "Sort",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 14.dp, end = 4.dp)
                        )
                    }
                    items(StreamSort.entries) { sort ->
                        FilterChip(
                            selected = state.sort == sort,
                            onClick = { onSetSort(sort) },
                            label = { Text(sort.label) }
                        )
                    }
                }
            }
        }

        if (state.availableResolutions.size > 1) {
            item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.selectedResolutionFilter == null,
                            onClick = { onSelectFilter(null) },
                            label = { Text("All") }
                        )
                    }
                    items(state.availableResolutions) { res ->
                        FilterChip(
                            selected = state.selectedResolutionFilter == res,
                            onClick = { onSelectFilter(res) },
                            label = { Text(if (res == "2160p") "4K" else res) }
                        )
                    }
                }
            }
        }

        if (state.hiddenByQualityLimit > 0 || state.providerWarnings.isNotEmpty()) {
            item(key = "notes") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.hiddenByQualityLimit > 0) {
                        Text(
                            text = "${state.hiddenByQualityLimit} stream(s) above ${state.maxResolution} hidden (change in Settings).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.providerWarnings.isNotEmpty()) {
                        Text(
                            text = if (showWarnings) state.providerWarnings.joinToString("\n")
                            else "${state.providerWarnings.size} add-on(s) failed to respond. Tap for details.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { showWarnings = !showWarnings }
                        )
                    }
                }
            }
        }

        items(state.streams, key = { it.uniqueKey }) { candidate ->
            StreamCandidateCard(
                candidate = candidate,
                isLastUsed = candidate.uniqueKey == state.lastUsedKey,
                enabled = !state.isResolving,
                onClick = { onSelectStream(candidate) },
                onDownload = { onDownloadStream(candidate) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StreamCandidateCard(
    candidate: StreamCandidate,
    isLastUsed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Badge(
                        text = if (candidate.resolution == "2160p") "4K" else candidate.resolution,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (candidate.isCachedOnDebrid) {
                        Badge(
                            text = "Instant",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            showBolt = true
                        )
                    }
                    if (candidate.compatibilityWarning != null) {
                        Badge(
                            text = "May not play",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (isLastUsed) {
                        Badge(
                            text = "Last used",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    candidate.hdrTag?.let {
                        Badge(
                            text = it,
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                val metadata = listOfNotNull(
                    candidate.sizeDisplay,
                    candidate.codec,
                    candidate.audioTag,
                    candidate.seeders?.let { "$it seeders" },
                    candidate.languages.takeIf { it.isNotEmpty() }?.joinToString("/")
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                candidate.compatibilityWarning?.let { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    text = candidate.addonName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                IconButton(onClick = onDownload, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(
    text: String,
    container: Color,
    content: Color,
    showBolt: Boolean = false
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (showBolt) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun formatSize(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb)
    else String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
}
