package com.argon.stream.data.remote.kitsu

import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.safeApiCall
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class KitsuCatalogResponse(val metas: List<KitsuMetaDto> = emptyList())

@Serializable
private data class KitsuMetaDto(
    val id: String = "",
    @SerialName("imdb_id") val imdbId: String? = null,
    val name: String = "",
    val animeType: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null,
    val poster: String? = null,
    val background: String? = null
)

/**
 * Anime catalogs from the public Kitsu Stremio add-on (anime-kitsu.strem.fun). Only titles that carry an
 * IMDb id are kept, so details, episodes and streams use the same Cinemeta/IMDb flow as everything else.
 * Kitsu lists each season separately; they share one IMDb id and are merged into one card.
 */
@Singleton
class KitsuAnimeClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchCatalog(catalogId: String, skip: Int = 0): NetworkResult<List<MediaItem>> = safeApiCall {
        withContext(Dispatchers.IO) {
            val extra = if (skip > 0) "/skip=$skip" else ""
            val request = Request.Builder()
                .url("$BASE_URL/catalog/anime/$catalogId$extra.json")
                .header("Accept", "application/json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} from Kitsu")
                json.decodeFromString<KitsuCatalogResponse>(response.body?.string().orEmpty())
                    .metas
                    .mapNotNull { it.toMediaItem() }
                    .distinctBy { it.id }
            }
        }
    }

    private fun KitsuMetaDto.toMediaItem(): MediaItem? {
        val imdb = imdbId?.takeIf { it.startsWith("tt") } ?: return null
        if (name.isBlank()) return null
        return MediaItem(
            id = imdb,
            tmdbId = null,
            imdbId = imdb,
            title = name,
            type = if (animeType.equals("movie", ignoreCase = true)) MediaType.MOVIE else MediaType.SERIES,
            posterUrl = poster,
            backdropUrl = background,
            overview = description.orEmpty(),
            releaseYear = releaseInfo?.take(4),
            rating = imdbRating?.toDoubleOrNull(),
            genres = genres.orEmpty()
        )
    }

    companion object {
        const val BASE_URL = "https://anime-kitsu.strem.fun"

        /** Home rows for the Anime filter: catalog id to row title. */
        val HOME_ROWS = listOf(
            "kitsu-anime-trending" to "Trending Anime",
            "kitsu-anime-airing" to "Airing Now",
            "kitsu-anime-popular" to "Most Popular Anime",
            "kitsu-anime-rating" to "Top Rated Anime"
        )
    }
}
