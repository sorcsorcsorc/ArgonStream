package com.argon.stream.data.remote.wikipedia

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class WikidataCastEntry(val name: String, val character: String, val photoUrl: String?)

/**
 * Full cast list (with Wikimedia Commons portraits) from Wikidata, looked up by IMDb id.
 * Free and keyless; used to fill in actors beyond the few that Cinemeta lists.
 */
@Singleton
class WikidataCastClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val client = okHttpClient.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, List<WikidataCastEntry>>()

    suspend fun castFor(imdbId: String): List<WikidataCastEntry> {
        if (!IMDB_ID.matches(imdbId)) return emptyList()
        cache[imdbId]?.let { return it }
        val query = """
            SELECT ?actor ?actorLabel ?image ?charLabel WHERE {
              ?work wdt:P345 "$imdbId". ?work p:P161 ?st. ?st ps:P161 ?actor.
              OPTIONAL { ?st pq:P453 ?char } OPTIONAL { ?actor wdt:P18 ?image }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            } LIMIT 80
        """.trimIndent()
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("https://query.wikidata.org/sparql?format=json&query=${Uri.encode(query)}")
                    .header("Accept", "application/sparql-results+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    parse(response.body?.string().orEmpty())
                }
            }
        }.getOrDefault(emptyList())
        cache[imdbId] = result
        return result
    }

    private fun parse(body: String): List<WikidataCastEntry> {
        val bindings = json.parseToJsonElement(body).jsonObject["results"]?.jsonObject?.get("bindings")?.jsonArray.orEmpty()
        return bindings.mapNotNull { element ->
            val row = element.jsonObject
            val name = row.value("actorLabel") ?: return@mapNotNull null
            // Unlabelled entities come back as their Q-id; skip those.
            if (Q_ID.matches(name)) return@mapNotNull null
            WikidataCastEntry(
                name = name,
                character = row.value("charLabel")?.takeUnless { Q_ID.matches(it) }.orEmpty(),
                photoUrl = row.value("image")?.let { "${it.replace("http://", "https://")}?width=300" }
            )
        }
            .groupBy { it.name }
            .map { (_, rows) -> rows.first().copy(character = rows.map { it.character }.filter { it.isNotBlank() }.distinct().joinToString(" / ")) }
    }

    private fun JsonObject.value(key: String): String? =
        this[key]?.jsonObject?.get("value")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private companion object {
        val IMDB_ID = Regex("tt[0-9]+")
        val Q_ID = Regex("Q[0-9]+")
    }
}
