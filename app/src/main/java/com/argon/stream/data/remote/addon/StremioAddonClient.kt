package com.argon.stream.data.remote.addon

import android.net.Uri
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StremioAddonClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    /**
     * Normalizes stremio:// or base URLs into valid HTTPS manifest URLs.
     */
    fun normalizeManifestUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
            .replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")
        return when {
            trimmed.endsWith("/manifest.json", ignoreCase = true) -> trimmed
            trimmed.contains("/manifest.json?") -> trimmed
            else -> "${trimmed.trimEnd('/')}/manifest.json"
        }
    }

    fun deriveBaseUrl(manifestUrl: String): String {
        val index = manifestUrl.indexOf("/manifest.json", ignoreCase = true)
        return if (index != -1) manifestUrl.substring(0, index) else manifestUrl.trimEnd('/')
    }

    suspend fun fetchManifest(rawUrl: String): NetworkResult<Pair<String, StremioManifestDto>> {
        val manifestUrl = normalizeManifestUrl(rawUrl)
        if (!manifestUrl.startsWith("https://") && !manifestUrl.startsWith("http://")) {
            return NetworkResult.Error(
                userMessage = "Unsupported URL protocol. Use https:// or stremio://.",
                diagnosticReason = "INVALID_SCHEME",
                isRetryable = false
            )
        }

        return safeApiCall {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(manifestUrl)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code} fetching manifest")
                    }
                    val body = response.body?.string()
                        ?: throw IllegalStateException("Empty manifest body")
                    val dto = json.decodeFromString<StremioManifestDto>(body)
                    manifestUrl to dto
                }
            }
        }
    }

    suspend fun fetchStreams(
        baseUrl: String,
        type: String,
        videoId: String
    ): NetworkResult<StremioStreamResponseDto> {
        val encodedVideoId = Uri.encode(videoId)
        val endpoint = "${baseUrl.trimEnd('/')}/stream/$type/$encodedVideoId.json"

        return safeApiCall {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code} from stream endpoint")
                    }
                    val body = response.body?.string()
                        ?: return@use StremioStreamResponseDto()
                    json.decodeFromString<StremioStreamResponseDto>(body)
                }
            }
        }
    }
}