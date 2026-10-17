package com.argon.stream.data.remote.wikipedia

import android.net.Uri
import com.argon.stream.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class WikiSummaryDto(
    val type: String? = null,
    val description: String? = null,
    val thumbnail: WikiImageDto? = null
)

@Serializable
private data class WikiImageDto(val source: String? = null)

/**
 * Free actor portraits for when no TMDB key is set (Cinemeta only provides names).
 * Only accepts pages described as a performer, so a same-named non-actor isn't shown.
 */
@Singleton
class WikipediaPhotoClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val cache = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    suspend fun actorPhotoUrl(name: String): String? {
        val key = name.trim()
        if (key.isEmpty()) return null
        cache[key]?.let { return it }
        if (key in misses) return null

        val url = runCatching {
            withContext(Dispatchers.IO) {
                val title = Uri.encode(key.replace(' ', '_'))
                val request = Request.Builder()
                    .url("https://en.wikipedia.org/api/rest_v1/page/summary/$title")
                    .header("User-Agent", "ArgonStream/${BuildConfig.VERSION_NAME} (Android app)")
                    .header("Accept", "application/json")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val dto = json.decodeFromString<WikiSummaryDto>(response.body?.string().orEmpty())
                    val description = dto.description.orEmpty()
                    dto.thumbnail?.source?.takeIf {
                        dto.type == "standard" && PERFORMER_REGEX.containsMatchIn(description)
                    }
                }
            }
        }.getOrNull()

        if (url != null) cache[key] = url else misses += key
        return url
    }

    private companion object {
        val PERFORMER_REGEX = Regex("(?i)\\b(actor|actress|comedian|performer|entertainer|voice|singer|rapper|model|filmmaker|director|presenter|host)\\b")
    }
}
