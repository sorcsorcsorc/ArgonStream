package com.argon.stream.data.remote.imdb

import android.net.Uri
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.safeApiCall
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SuggestResponse(val d: List<SuggestItem> = emptyList())

@Serializable
private data class SuggestItem(
    val id: String = "",
    val l: String? = null,
    val qid: String? = null,
    val y: Int? = null,
    val i: SuggestImage? = null
)

@Serializable
private data class SuggestImage(val imageUrl: String? = null)

/**
 * Title search through IMDb's public type-ahead endpoint (the one imdb.com's search box uses).
 * It answers in well under a second, where Cinemeta's search often takes 3–6 seconds.
 * Results carry IMDb ids, so they open through the usual Cinemeta details flow.
 */
@Singleton
class ImdbSuggestClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val client = okHttpClient.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun search(query: String): NetworkResult<List<MediaItem>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val q = query.trim().lowercase(Locale.US)
            val first = q.firstOrNull()?.takeIf { it.isLetterOrDigit() } ?: 'x'
            val request = Request.Builder()
                .url("https://v3.sg.media-imdb.com/suggestion/$first/${Uri.encode(q)}.json?includeVideos=0")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} from IMDb search")
                json.decodeFromString<SuggestResponse>(response.body?.string().orEmpty()).d.mapNotNull { it.toMediaItem() }
            }
        }
    }

    private fun SuggestItem.toMediaItem(): MediaItem? {
        if (!id.startsWith("tt")) return null // people, companies…
        val type = when (qid) {
            "movie", "tvMovie", "short", "tvSpecial", "video" -> MediaType.MOVIE
            "tvSeries", "tvMiniSeries" -> MediaType.SERIES
            else -> return null
        }
        val title = l?.takeIf { it.isNotBlank() } ?: return null
        return MediaItem(
            id = id,
            tmdbId = null,
            imdbId = id,
            title = title,
            type = type,
            posterUrl = i?.imageUrl?.let(::smallPoster),
            backdropUrl = null,
            overview = "",
            releaseYear = y?.toString(),
            rating = null
        )
    }

    companion object {
        /** IMDb image URLs accept size hints; ask for a card-sized poster instead of the full original. */
        fun smallPoster(url: String): String {
            val dot = url.lastIndexOf("._V1_")
            return if (dot >= 0) url.substring(0, dot) + "._V1_QL75_UX380_.jpg"
            else url.replace(Regex("\\.jpg$"), "._V1_QL75_UX380_.jpg")
        }
    }
}
