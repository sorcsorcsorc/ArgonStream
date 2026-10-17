package com.argon.stream.ui.screens.downloads

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.core.download.DownloadItem
import com.argon.stream.core.download.DownloadStatus
import com.argon.stream.ui.components.EmptyState
import com.argon.stream.ui.components.ScreenHeader
import java.util.Locale

@Composable
fun DownloadsScreen(
    onPlayFile: (uri: Uri, title: String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var missingFile by remember { mutableStateOf(false) }

    if (missingFile) {
        AlertDialog(
            onDismissRequest = { missingFile = false },
            title = { Text("File not found") },
            text = { Text("This file was moved or deleted outside Argon. Remove it from the list and download it again.") },
            confirmButton = { TextButton(onClick = { missingFile = false }) { Text("OK") } }
        )
    }

    DownloadsContent(
        state = state,
        onPlay = { item ->
            val uri = viewModel.playableUri(item)
            if (uri != null) onPlayFile(uri, item.title.substringBeforeLast('.')) else missingFile = true
        },
        onRemove = viewModel::remove
    )
}

@Composable
internal fun DownloadsContent(
    state: DownloadsUiState,
    onPlay: (DownloadItem) -> Unit,
    onRemove: (DownloadItem) -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<DownloadItem?>(null) }

    pendingRemoval?.let { item ->
        val finished = item.status == DownloadStatus.COMPLETED
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(if (finished) "Delete download?" else "Cancel download?") },
            text = {
                Text(if (finished) "\"${item.title}\" will be deleted from your phone." else "\"${item.title}\" will stop downloading and the partial file is removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(item)
                    pendingRemoval = null
                }) { Text(if (finished) "Delete" else "Cancel download") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Keep") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Downloads",
            subtitle = "Saved to Downloads/Argon on your phone"
        )

        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Download,
                    title = "No downloads yet",
                    message = "Tap the download icon next to a stream to save it here and watch it offline."
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    DownloadRow(
                        item = item,
                        onPlay = { onPlay(item) },
                        onRemove = { pendingRemoval = item }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val isDone = item.status == DownloadStatus.COMPLETED
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isDone, onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (item.status) {
                    DownloadStatus.COMPLETED -> Icons.Default.PlayCircle
                    DownloadStatus.FAILED -> Icons.Default.ErrorOutline
                    else -> Icons.Default.Download
                },
                contentDescription = null,
                tint = when (item.status) {
                    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusLine(item),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.status == DownloadStatus.RUNNING || item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.QUEUED) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (item.totalBytes > 0) {
                        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = if (isDone || item.status == DownloadStatus.FAILED) Icons.Default.Delete else Icons.Default.Close,
                    contentDescription = if (isDone) "Delete" else "Cancel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun statusLine(item: DownloadItem): String = when (item.status) {
    DownloadStatus.COMPLETED -> "${formatBytes(item.totalBytes.takeIf { it > 0 } ?: item.downloadedBytes)} · Tap to play"
    DownloadStatus.RUNNING -> if (item.totalBytes > 0) {
        "${(item.progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
    } else "Downloading · ${formatBytes(item.downloadedBytes)}"
    DownloadStatus.PAUSED -> "Paused (waiting for network) · ${formatBytes(item.downloadedBytes)}"
    DownloadStatus.QUEUED -> "Waiting to start"
    DownloadStatus.FAILED -> "Download failed. The link may have expired; download it again from the stream page."
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 1.0) String.format(Locale.US, "%.2f GB", gb)
    else String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
}
