package com.argon.stream.core.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A part of an episode the viewer may want to jump over, in milliseconds. [endMs] null = until the end. */
data class SkipSegment(val startMs: Long, val endMs: Long?)

data class SkipSegments(
    val intro: SkipSegment? = null,
    val recap: SkipSegment? = null,
    /** Where the end credits start; used to offer the next episode early. */
    val credits: SkipSegment? = null
) {
    val isEmpty: Boolean get() = intro == null && recap == null && credits == null
}

/**
 * Crowd-sourced intro / recap / credits timestamps, looked up by IMDb id (+ season/episode).
 * IntroDB is asked first, TheIntroDB second. Both are free and need no key; titles nobody has
 * submitted simply return nothing, and the player then falls back to its own credits estimate.
 */
@Singleton
class SkipSegmentsClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val client = okHttpClient.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, SkipSegments>()

    suspend fun segmentsFor(imdbId: String, season: Int?, episode: Int?): SkipSegments {
        if (!imdbId.startsWith("tt")) return SkipSegments()
        val key = "$imdbId:$season:$episode"
        cache[key]?.let { return it }
        val episodeQuery = if (season != null && episode != null) "&season=$season&episode=$episode" else ""
        val result = fetch("https://api.introdb.app/segments?imdb_id=$imdbId$episodeQuery")
            ?.let(::parseIntroDb)?.takeUnless { it.isEmpty }
            ?: fetch("https://api.theintrodb.org/v2/media?imdb_id=$imdbId$episodeQuery")
                ?.let(::parseTheIntroDb)
            ?: SkipSegments()
        cache[key] = result
        return result
    }

    private suspend fun fetch(url: String): JsonObject? = runCatching {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
            }
        }
    }.getOrNull()

    /** IntroDB: {"intro": {"start_ms", "end_ms"}, "recap": {...}, "outro": {...}} */
    private fun parseIntroDb(root: JsonObject) = SkipSegments(
        intro = segment(root["intro"]),
        recap = segment(root["recap"]),
        credits = segment(root["outro"])
    )

    /** TheIntroDB: {"intro": [{"start_ms", "end_ms"}], "recap": [...], "credits": [...]} */
    private fun parseTheIntroDb(root: JsonObject) = SkipSegments(
        intro = segment((root["intro"] as? JsonArray)?.firstOrNull()),
        recap = segment((root["recap"] as? JsonArray)?.firstOrNull() ?: (root["recaps"] as? JsonArray)?.firstOrNull()),
        credits = segment((root["credits"] as? JsonArray)?.firstOrNull())
    )

    private fun segment(element: JsonElement?): SkipSegment? {
        val obj = element as? JsonObject ?: return null
        val start = obj.long("start_ms") ?: return null
        val end = obj.long("end_ms")
        if (end != null && end <= start) return null
        return SkipSegment(start.coerceAtLeast(0L), end)
    }

    private fun JsonObject.long(key: String): Long? =
        runCatching { this[key]?.jsonPrimitive?.longOrNull }.getOrNull()
            ?: runCatching { this[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() }.getOrNull()
}
