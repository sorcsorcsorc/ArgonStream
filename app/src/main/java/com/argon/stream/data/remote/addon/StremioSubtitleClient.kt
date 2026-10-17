package com.argon.stream.data.remote.addon

import android.net.Uri
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.safeApiCall
import com.argon.stream.data.local.AddonDao
import com.argon.stream.domain.model.ExternalSubtitleTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class StremioSubtitleResponseDto(
    val subtitles: List<StremioSubtitleItemDto> = emptyList()
)

@Serializable
data class StremioSubtitleItemDto(
    val id: String? = null,
    val url: String? = null,
    val lang: String? = null
)

@Singleton
class StremioSubtitleClient @Inject constructor(
    private val addonDao: AddonDao,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun querySubtitlesForVideo(stremioVideoId: String): List<ExternalSubtitleTrack> = coroutineScope {
        if (stremioVideoId.isBlank()) return@coroutineScope emptyList()
        val mediaType = if (stremioVideoId.contains(":")) "series" else "movie"

        val subtitleAddons = addonDao.observeAddons().first().filter { entity ->
            entity.isEnabled && entity.resourcesCsv.contains("subtitles", ignoreCase = true)
        }

        val deferred = subtitleAddons.map { addon ->
            async {
                fetchFromAddon(
                    baseUrl = addon.baseUrl,
                    addonName = addon.name,
                    mediaType = mediaType,
                    videoId = stremioVideoId
                )
            }
        }

        deferred.awaitAll().flatten()
    }

    private suspend fun fetchFromAddon(
        baseUrl: String,
        addonName: String,
        mediaType: String,
        videoId: String
    ): List<ExternalSubtitleTrack> {
        val encodedId = Uri.encode(videoId)
        val endpoint = "${baseUrl.trimEnd('/')}/subtitles/$mediaType/$encodedId.json"

        val res = safeApiCall {
            withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                okHttpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) return@use StremioSubtitleResponseDto()
                    val body = response.body?.string() ?: return@use StremioSubtitleResponseDto()
                    json.decodeFromString<StremioSubtitleResponseDto>(body)
                }
            }
        }

        if (res !is NetworkResult.Success) return emptyList()

        return res.data.subtitles.mapIndexedNotNull { idx, dto ->
            val subUrl = dto.url?.takeIf { it.startsWith("http") } ?: return@mapIndexedNotNull null
            val lang = (dto.lang ?: "und").lowercase(Locale.US)
            val mimeType = detectSubtitleMimeType(subUrl)
            ExternalSubtitleTrack(
                id = dto.id ?: "${addonName}_${lang}_$idx",
                url = subUrl,
                languageCode = lang,
                label = "${lang.uppercase(Locale.US)} • $addonName",
                addonName = addonName,
                mimeType = mimeType
            )
        }
    }

    companion object {
        fun detectSubtitleMimeType(url: String): String {
            val cleanPath = url.substringBefore('?').lowercase(Locale.US)
            return when {
                cleanPath.endsWith(".vtt") -> "text/vtt"
                else -> "application/x-subrip" // Default SRT
            }
        }
    }
}