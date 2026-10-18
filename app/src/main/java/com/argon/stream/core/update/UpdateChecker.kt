package com.argon.stream.core.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import com.argon.stream.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GithubAssetDto> = emptyList()
)

@Serializable
private data class GithubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0
)

data class AppUpdate(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progress: Float?) : UpdateDownloadState
    data class Ready(val downloadId: Long) : UpdateDownloadState
    data class Failed(val message: String) : UpdateDownloadState
}

/**
 * Looks for a newer release of the app on GitHub (latest release of [BuildConfig.UPDATE_REPO] with an
 * .apk attached), downloads it and hands it to Android's installer. The repository must be public:
 * a private one answers 404 without a token, and the check then quietly finds nothing.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val client = okHttpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val prefs = context.getSharedPreferences("argon_updates", Context.MODE_PRIVATE)
    private val downloadManager get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    suspend fun findUpdate(): AppUpdate? = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val release = json.decodeFromString<GithubReleaseDto>(response.body?.string().orEmpty())
                if (release.draft || release.prerelease) return@use null
                val version = release.tagName.removePrefix("v").trim()
                val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return@use null
                if (!isNewer(version, BuildConfig.VERSION_NAME)) return@use null
                AppUpdate(version, release.body.orEmpty().trim(), apk.downloadUrl, apk.size)
            }
        }
    }.getOrNull()

    /** "Not now" hides the prompt for this version until the next app start; "Skip" hides it for good. */
    fun isSkipped(version: String): Boolean = prefs.getString(KEY_SKIPPED, null) == version

    fun skip(version: String) = prefs.edit().putString(KEY_SKIPPED, version).apply()

    fun startDownload(update: AppUpdate): Long {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Argon ${update.version}")
            .setDescription("Argon update")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "Argon-${update.version}.apk")
        return downloadManager.enqueue(request)
    }

    fun downloadState(id: Long): UpdateDownloadState {
        downloadManager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return UpdateDownloadState.Failed("The download was cancelled.")
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> UpdateDownloadState.Ready(id)
                DownloadManager.STATUS_FAILED -> UpdateDownloadState.Failed("The download failed. Check your connection and try again.")
                else -> UpdateDownloadState.Downloading(if (total > 0) done.toFloat() / total else null)
            }
        }
        return UpdateDownloadState.Failed("The download was cancelled.")
    }

    /** True when Android still needs the user to allow installs from Argon. */
    fun needsInstallPermission(): Boolean = !context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(downloadId: Long): Intent? {
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        private const val KEY_SKIPPED = "skipped_version"
        private const val APK_MIME = "application/vnd.android.package-archive"

        /** Compares dotted versions numerically ("1.10.0" > "1.9.2"); extra suffixes are ignored. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.substringBefore('-').split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
