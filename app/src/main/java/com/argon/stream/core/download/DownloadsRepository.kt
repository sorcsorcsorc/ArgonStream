package com.argon.stream.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }

data class DownloadItem(
    val id: Long,
    val title: String,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Lists Argon's downloads straight from Android's DownloadManager (it only returns this app's own
 * downloads), so every file started from the stream page shows up without separate bookkeeping.
 */
@Singleton
class DownloadsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun list(): List<DownloadItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DownloadItem>()
        runCatching {
            manager.query(DownloadManager.Query()).use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val titleCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                val descCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_DESCRIPTION)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    if (c.getString(descCol) != StreamDownloader.DESCRIPTION) continue
                    items += DownloadItem(
                        id = c.getLong(idCol),
                        title = c.getString(titleCol).orEmpty(),
                        status = when (c.getInt(statusCol)) {
                            DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
                            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                            else -> DownloadStatus.QUEUED
                        },
                        downloadedBytes = c.getLong(soFarCol).coerceAtLeast(0),
                        totalBytes = c.getLong(totalCol).coerceAtLeast(0)
                    )
                }
            }
        }
        items.sortedByDescending { it.id }
    }

    /** A content:// URI the built-in player can open, or null if the file is gone. */
    fun playableUri(id: Long): Uri? = runCatching { manager.getUriForDownloadedFile(id) }.getOrNull()

    /** Cancels a running download or deletes a finished file. */
    fun remove(id: Long) {
        runCatching { manager.remove(id) }
    }
}
