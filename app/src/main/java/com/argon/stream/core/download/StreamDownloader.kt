package com.argon.stream.core.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.URLUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadStartResult {
    data class Started(val fileName: String, val locationLabel: String) : DownloadStartResult
    data class NotSupported(val reason: String) : DownloadStartResult
}

/**
 * Saves a resolved stream file with Android's DownloadManager, which shows progress in the
 * notification shade and keeps going if the app is closed. Files land in Downloads/Argon.
 */
@Singleton
class StreamDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueue(
        url: String,
        suggestedName: String,
        headers: Map<String, String> = emptyMap()
    ): DownloadStartResult {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null || (uri.scheme != "https" && uri.scheme != "http")) {
            return DownloadStartResult.NotSupported("This stream isn't a downloadable web link.")
        }
        val path = uri.path.orEmpty().lowercase(Locale.US)
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) {
            return DownloadStartResult.NotSupported(
                "This is an adaptive stream (HLS/DASH) made of many small pieces, so it can't be saved as one file. Pick a stream that is a single file (e.g. .mkv or .mp4)."
            )
        }

        val fileName = buildFileName(url, suggestedName)
        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setDescription(DESCRIPTION)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
        headers.forEach { (name, value) -> request.addRequestHeader(name, value) }

        val locationLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$FOLDER/$fileName")
            "Downloads/$FOLDER"
        } else {
            // Android 8–9 would need a storage permission for the public folder; use the app's own folder instead.
            request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            "Android/data/${context.packageName}/files/Download"
        }

        return runCatching {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            DownloadStartResult.Started(fileName, locationLabel)
        }.getOrElse {
            DownloadStartResult.NotSupported("Android's download manager refused the download (${it.javaClass.simpleName}).")
        }
    }

    companion object {
        const val FOLDER = "Argon"
        const val DESCRIPTION = "Argon download"
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts", "m2ts")
        private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        fun buildFileName(url: String, suggestedName: String): String {
            val guessed = URLUtil.guessFileName(url, null, null)
            val guessedExt = guessed.substringAfterLast('.', "").lowercase(Locale.US)
            val base = suggestedName.ifBlank { guessed }.replace(ILLEGAL_CHARS, " ").replace(Regex("\\s+"), " ").trim().take(150)
            val baseExt = base.substringAfterLast('.', "").lowercase(Locale.US)
            return when {
                baseExt in VIDEO_EXTENSIONS -> base
                guessedExt in VIDEO_EXTENSIONS -> "$base.$guessedExt"
                else -> "$base.mkv"
            }
        }
    }
}
