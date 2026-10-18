package com.argon.stream.core.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Grabs small still frames for the seek-bar preview (the picture shown above the bar while skipping).
 *
 * Works for single-file streams (mp4/mkv/webm over http, downloads on the device). Adaptive streams
 * (HLS/DASH) can't be read this way, so [frameAt] returns null and the bar shows only the time.
 * Remote frames need a network seek, so they can take a moment; results are cached per 10 seconds.
 */
class SeekPreviewGenerator(
    private val context: Context,
    private val url: String,
    private val headers: Map<String, String>
) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "argon-seek-preview").apply { isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val cache = LruCache<Long, Bitmap>(60)
    private var retriever: MediaMetadataRetriever? = null
    private var unavailable = isAdaptive(url)
    @Volatile private var released = false

    val isSupported: Boolean get() = !unavailable

    suspend fun frameAt(positionMs: Long): Bitmap? {
        if (unavailable || released) return null
        val bucket = (positionMs / BUCKET_MS) * BUCKET_MS
        cache.get(bucket)?.let { return it }
        return withContext(dispatcher) {
            if (released) return@withContext null
            val r = retriever ?: open() ?: return@withContext null
            val frame = runCatching {
                val timeUs = bucket * 1_000L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
                } else {
                    r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                        val h = (WIDTH * full.height / full.width.coerceAtLeast(1)).coerceAtLeast(1)
                        Bitmap.createScaledBitmap(full, WIDTH, h, true).also { if (it != full) full.recycle() }
                    }
                }
            }.getOrNull()
            frame?.also { cache.put(bucket, it) }
        }
    }

    private fun open(): MediaMetadataRetriever? = runCatching {
        MediaMetadataRetriever().apply {
            val uri = Uri.parse(url)
            when (uri.scheme?.lowercase()) {
                "http", "https" -> setDataSource(url, headers)
                "content", "file" -> setDataSource(context, uri)
                else -> setDataSource(url)
            }
        }
    }.onFailure { unavailable = true }.getOrNull()?.also { retriever = it }

    fun release() {
        released = true
        executor.execute {
            runCatching { retriever?.release() }
            retriever = null
            cache.evictAll()
        }
        executor.shutdown()
    }

    companion object {
        const val BUCKET_MS = 10_000L
        private const val WIDTH = 320
        private const val HEIGHT = 180

        fun isAdaptive(url: String): Boolean {
            val path = url.substringBefore('?').lowercase()
            return path.endsWith(".m3u8") || path.endsWith(".mpd") || "/manifest" in path
        }
    }
}
