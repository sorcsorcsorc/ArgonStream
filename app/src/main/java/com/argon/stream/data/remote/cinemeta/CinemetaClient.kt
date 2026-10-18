package com.argon.stream.data.remote.cinemeta

import android.net.Uri
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.safeApiCall
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
data class CinemetaCatalogResponse(
    val metas: List<CinemetaMetaDto> = emptyList()
)

@Serializable
data class CinemetaMetaResponse(
    val meta: CinemetaMetaDto? = null
)

@Serializable
data class CinemetaMetaDto(
    val id: String,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("moviedb_id") val tmdbId: Int? = null,
    val type: String = "movie",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val year: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val status: String? = null,
    val genres: List<String>? = null,
    val genre: List<String>? = null,
    val cast: List<String>? = null,
    val videos: List<CinemetaVideoDto>? = null,
    val trailers: List<CinemetaTrailerDto>? = null,
    /** Only on cinemeta-live: the billed cast with TMDB portraits. */
    @SerialName("credits_cast") val creditsCast: List<CinemetaCreditDto>? = null
)

@Serializable
data class CinemetaCreditDto(
    val name: String = "",
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val id: Int? = null
)

@Serializable
data class CinemetaTrailerDto(
    val source: String? = null,
    val type: String? = null
)

@Serializable
data class CinemetaVideoDto(
    val id: String = "",
    val name: String? = null,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val number: Int? = null,
    val overview: String? = null,
    val description: String? = null,
    val thumbnail: String? = null,
    val released: String? = null,
    val firstAired: String? = null,
    val rating: String? = null
)

/**
 * Client for Cinemeta, Stremio's free public catalog/metadata add-on.
 * Used as the default catalog source so the app works without a TMDB key.
 */
@Singleton
class CinemetaClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun fetchCatalog(
        type: String,
        catalogId: String,
        search: String? = null,
        genre: String? = null,
        skip: Int = 0
    ): NetworkResult<List<CinemetaMetaDto>> {
        val extras = listOfNotNull(
            search?.let { "search=${Uri.encode(it)}" },
            genre?.let { "genre=${Uri.encode(it)}" },
            skip.takeIf { it > 0 }?.let { "skip=$it" }
        )
        val extra = if (extras.isEmpty()) "" else "/" + extras.joinToString("&")
        return when (val res = getJson<CinemetaCatalogResponse>("$BASE_URL/catalog/$type/$catalogId$extra.json")) {
            is NetworkResult.Success -> NetworkResult.Success(res.data.metas)
            is NetworkResult.Error -> res
        }
    }

    suspend fun fetchMeta(type: String, imdbId: String): NetworkResult<CinemetaMetaDto> {
        return when (val res = getJson<CinemetaMetaResponse>("$BASE_URL/meta/$type/${Uri.encode(imdbId)}.json")) {
            is NetworkResult.Success -> res.data.meta?.let { NetworkResult.Success(it) }
                ?: NetworkResult.Error(
                    userMessage = "No metadata found for this title.",
                    diagnosticReason = "CINEMETA_EMPTY_META",
                    isRetryable = false
                )
            is NetworkResult.Error -> res
        }
    }

    /** Billed cast with photos from Cinemeta's live endpoint (the v3 meta only lists ~3 names). */
    suspend fun fetchCredits(type: String, imdbId: String): List<CinemetaCreditDto> =
        when (val res = getJson<CinemetaMetaResponse>("$LIVE_URL/meta/$type/${Uri.encode(imdbId)}.json")) {
            is NetworkResult.Success -> res.data.meta?.creditsCast.orEmpty().filter { it.name.isNotBlank() }
            is NetworkResult.Error -> emptyList()
        }

    private suspend inline fun <reified T> getJson(url: String): NetworkResult<T> = safeApiCall {
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} from Cinemeta")
                }
                json.decodeFromString<T>(response.body?.string().orEmpty())
            }
        }
    }

    companion object {
        const val BASE_URL = "https://v3-cinemeta.strem.io"
        const val LIVE_URL = "https://cinemeta-live.strem.io"

        /** Cinemeta catalog posters default to the "small" size; "medium" looks much sharper on phones. */
        fun upgradePosterUrl(url: String?): String? =
            url?.replace("/poster/small/", "/poster/medium/")
    }
}
