package com.argon.stream.data.repository

import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.safeApiCall
import com.argon.stream.core.security.SecretVault
import com.argon.stream.data.remote.torbox.TorBoxApiService
import com.argon.stream.data.remote.torbox.TorBoxTorrentFileDto
import com.argon.stream.data.remote.torbox.TorBoxTorrentItemDto
import com.argon.stream.domain.model.StreamCandidate
import com.argon.stream.domain.model.TorBoxAccountInfo
import com.argon.stream.domain.model.TorBoxContentState
import com.argon.stream.domain.model.TorBoxMediaFile
import com.argon.stream.domain.model.TorBoxPlanTier
import com.argon.stream.domain.model.TorBoxResolutionOutcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorBoxRepository @Inject constructor(
    private val torBoxApi: TorBoxApiService,
    private val secretVault: SecretVault
) {
    fun hasApiKey(): Boolean = !getApiKeyOrNull().isNullOrBlank()

    fun saveApiKey(rawKey: String) {
        secretVault.putSecret(SecretVault.KEY_TORBOX_API_KEY, rawKey.trim())
    }

    fun disconnectTorBox() {
        secretVault.removeSecret(SecretVault.KEY_TORBOX_API_KEY)
    }

    private fun getApiKeyOrNull(): String? =
        secretVault.getSecret(SecretVault.KEY_TORBOX_API_KEY)?.trim()?.takeIf { it.isNotEmpty() }

    private fun bearerHeader(apiKey: String): String = "Bearer $apiKey"

    suspend fun verifyAccount(): NetworkResult<TorBoxAccountInfo> {
        val key = getApiKeyOrNull() ?: return NetworkResult.Error(
            userMessage = "No TorBox API key configured.",
            diagnosticReason = "MISSING_TORBOX_KEY",
            isRetryable = false
        )

        return when (val res = safeApiCall { torBoxApi.getAccountMe(bearerHeader(key)) }) {
            is NetworkResult.Success -> {
                val env = res.data
                val user = env.data
                if (!env.success || user == null) {
                    NetworkResult.Error(
                        userMessage = env.detail ?: env.error ?: "TorBox rejected the API token.",
                        diagnosticReason = "TORBOX_AUTH_FAILED",
                        isRetryable = false
                    )
                } else {
                    NetworkResult.Success(
                        TorBoxAccountInfo(
                            userId = user.id,
                            email = user.email,
                            planTier = TorBoxPlanTier.fromCode(user.plan),
                            isSubscribed = user.isSubscribed,
                            premiumExpiresAt = user.premiumExpiresAt,
                            totalBytesDownloaded = user.totalDownloaded
                        )
                    )
                }
            }
            is NetworkResult.Error -> res
        }
    }

    /**
     * Queries GET /torrents/checkcached in chunks of up to 40 hashes and returns the set of cached lowercase hashes.
     */
    suspend fun checkCachedHashes(hashes: List<String>): Set<String> {
        val key = getApiKeyOrNull() ?: return emptySet()
        val normalized = hashes.map { it.trim().lowercase(Locale.US) }.filter { it.length == 40 }.distinct()
        if (normalized.isEmpty()) return emptySet()

        val cachedSet = mutableSetOf<String>()
        normalized.chunked(40).forEach { batch ->
            val csv = batch.joinToString(",")
            val result = safeApiCall {
                torBoxApi.checkCachedHashes(
                    bearerToken = bearerHeader(key),
                    hashesCsv = csv,
                    format = "object",
                    listFiles = false
                )
            }
            if (result is NetworkResult.Success && result.data.success) {
                cachedSet.addAll(parseCachedHashesFromDataElement(result.data.data))
            }
        }
        return cachedSet
    }

    /**
     * Resolves a StreamCandidate into an immediately playable HTTP URL via TorBox,
     * or returns MultiFileSelectionRequired when a multi-video pack requires explicit user choice.
     */
    suspend fun resolveStreamCandidate(
        candidate: StreamCandidate,
        stremioVideoId: String,
        explicitFileId: Int? = null
    ): TorBoxResolutionOutcome {
        // Direct HTTP/HLS stream from addon that doesn't require TorBox infoHash resolution
        if (!candidate.directUrl.isNullOrBlank() && candidate.infoHash.isNullOrBlank()) {
            return TorBoxResolutionOutcome.ReadyToPlay(
                resolvedStreamUrl = candidate.directUrl,
                fileName = candidate.filenameHint ?: candidate.title,
                torrentId = -1,
                fileId = -1
            )
        }

        val infoHash = candidate.infoHash?.lowercase(Locale.US)
            ?: return TorBoxResolutionOutcome.Failed(
                state = TorBoxContentState.UNSUPPORTED,
                userMessage = "Stream has neither a direct HTTP URL nor a valid 40-character infoHash."
            )

        val key = getApiKeyOrNull()
            ?: return TorBoxResolutionOutcome.Failed(
                state = TorBoxContentState.UNAVAILABLE,
                userMessage = "This stream requires TorBox to resolve magnet/infoHash ($infoHash). Add your TorBox API key in Settings."
            )

        // Step 1: Check if user already has this torrent in their TorBox list or create it from magnet
        val existingTorrent = findExistingTorrentByHash(key, infoHash)
        val torrentId = existingTorrent?.id ?: run {
            val magnetUri = "magnet:?xt=urn:btih:$infoHash"
            val magnetPart = magnetUri.toRequestBody("text/plain".toMediaType())
            when (val createRes = safeApiCall { torBoxApi.createTorrentFromMagnet(bearerHeader(key), magnetPart) }) {
                is NetworkResult.Success -> {
                    val createdId = createRes.data.data?.resolvedId
                    if (!createRes.data.success || createdId == null) {
                        return TorBoxResolutionOutcome.Failed(
                            state = TorBoxContentState.UNAVAILABLE,
                            userMessage = createRes.data.detail ?: createRes.data.error ?: "TorBox could not add torrent."
                        )
                    }
                    createdId
                }
                is NetworkResult.Error -> {
                    return TorBoxResolutionOutcome.Failed(
                        state = TorBoxContentState.UNAVAILABLE,
                        userMessage = createRes.userMessage
                    )
                }
            }
        }

        // Step 2: Fetch full torrent details & file list from /torrents/mylist?id=...
        val torrentInfo = fetchTorrentDetails(key, torrentId)
            ?: return TorBoxResolutionOutcome.Failed(
                state = TorBoxContentState.UNAVAILABLE,
                userMessage = "TorBox added torrent #$torrentId, but metadata is still initializing. Try again in a moment."
            )

        val isReadyToDownload = torrentInfo.downloadPresent ||
            torrentInfo.downloadFinished ||
            torrentInfo.progress >= 1.0 ||
            torrentInfo.downloadState.contains("cached", ignoreCase = true) ||
            torrentInfo.downloadState.contains("seeding", ignoreCase = true) ||
            torrentInfo.downloadState.contains("completed", ignoreCase = true)

        if (!isReadyToDownload || torrentInfo.files.isEmpty()) {
            return TorBoxResolutionOutcome.QueuedProcessing(
                torrentId = torrentInfo.id,
                statusText = torrentInfo.downloadState.ifBlank { "Downloading to TorBox Cloud" },
                progressPercent = (torrentInfo.progress * 100.0).coerceIn(0.0, 100.0)
            )
        }

        // Step 3: Filter playable video files
        val videoFiles = torrentInfo.files.filter { isPlayableVideoFile(it.name) }
            .ifEmpty { torrentInfo.files }

        val chosenFile: TorBoxTorrentFileDto = when {
            explicitFileId != null -> videoFiles.firstOrNull { it.id == explicitFileId } ?: videoFiles.first()
            videoFiles.size == 1 -> videoFiles.first()
            else -> {
                val autoMatched = matchEpisodeOrFileIdx(videoFiles, stremioVideoId, candidate.fileIdx)
                if (autoMatched != null) {
                    autoMatched
                } else {
                    return TorBoxResolutionOutcome.MultiFileSelectionRequired(
                        torrentId = torrentInfo.id,
                        torrentName = torrentInfo.name,
                        playableFiles = videoFiles.sortedByDescending { it.size }.map {
                            TorBoxMediaFile(
                                id = it.id,
                                name = it.name,
                                shortName = it.shortName ?: it.name.substringAfterLast('/'),
                                sizeBytes = it.size
                            )
                        }
                    )
                }
            }
        }

        return requestDirectLinkForFile(
            torrentId = torrentInfo.id,
            fileId = chosenFile.id,
            fileName = chosenFile.shortName ?: chosenFile.name
        )
    }

    suspend fun requestDirectLinkForFile(
        torrentId: Int,
        fileId: Int,
        fileName: String
    ): TorBoxResolutionOutcome {
        val key = getApiKeyOrNull() ?: return TorBoxResolutionOutcome.Failed(
            state = TorBoxContentState.UNAVAILABLE,
            userMessage = "TorBox API key missing."
        )

        return when (val dlRes = safeApiCall {
            torBoxApi.requestDownloadLink(
                apiKey = key,
                torrentId = torrentId,
                fileId = fileId,
                zipLink = false
            )
        }) {
            is NetworkResult.Success -> {
                val url = dlRes.data.data
                if (dlRes.data.success && !url.isNullOrBlank()) {
                    TorBoxResolutionOutcome.ReadyToPlay(
                        resolvedStreamUrl = url,
                        fileName = fileName,
                        torrentId = torrentId,
                        fileId = fileId
                    )
                } else {
                    TorBoxResolutionOutcome.Failed(
                        state = TorBoxContentState.UNAVAILABLE,
                        userMessage = dlRes.data.detail ?: dlRes.data.error ?: "Failed to generate TorBox download link."
                    )
                }
            }
            is NetworkResult.Error -> TorBoxResolutionOutcome.Failed(
                state = TorBoxContentState.UNAVAILABLE,
                userMessage = dlRes.userMessage
            )
        }
    }

    private suspend fun findExistingTorrentByHash(apiKey: String, hash: String): TorBoxTorrentItemDto? {
        val listRes = safeApiCall { torBoxApi.getUserTorrentList(bearerHeader(apiKey)) }
        if (listRes is NetworkResult.Success && listRes.data.success) {
            return listRes.data.data?.firstOrNull { it.hash.equals(hash, ignoreCase = true) }
        }
        return null
    }

    private suspend fun fetchTorrentDetails(apiKey: String, torrentId: Int): TorBoxTorrentItemDto? {
        val singleRes = safeApiCall { torBoxApi.getTorrentById(bearerHeader(apiKey), torrentId) }
        if (singleRes is NetworkResult.Success && singleRes.data.success && singleRes.data.data != null) {
            return singleRes.data.data
        }
        val listRes = safeApiCall { torBoxApi.getUserTorrentList(bearerHeader(apiKey)) }
        if (listRes is NetworkResult.Success && listRes.data.success) {
            return listRes.data.data?.firstOrNull { it.id == torrentId }
        }
        return null
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "avi", "webm", "mov", "m4v", "ts", "m2ts")

        fun isPlayableVideoFile(fileName: String): Boolean {
            val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
            return ext in VIDEO_EXTENSIONS && !fileName.contains("sample", ignoreCase = true)
        }

        fun matchEpisodeOrFileIdx(
            videoFiles: List<TorBoxTorrentFileDto>,
            stremioVideoId: String,
            fileIdx: Int?
        ): TorBoxTorrentFileDto? {
            // Series IDs are formatted as "tt1234567:season:episode"
            val parts = stremioVideoId.split(":")
            if (parts.size == 3) {
                val season = parts[1].toIntOrNull()
                val episode = parts[2].toIntOrNull()
                if (season != null && episode != null) {
                    val sePattern = Regex(
                        String.format(Locale.US, "(?i)s0?%d[._\\-\\s]*e0?%d\\b|%dx0?%d\\b", season, episode, season, episode)
                    )
                    val matched = videoFiles.firstOrNull { sePattern.containsMatchIn(it.name) }
                    if (matched != null) return matched
                }
            }
            if (fileIdx != null && fileIdx in videoFiles.indices) {
                return videoFiles[fileIdx]
            }
            return null
        }

        /**
         * Parses TorBox `checkcached` response payload whether formatted as JSON object map or JSON array.
         */
        fun parseCachedHashesFromDataElement(dataElement: JsonElement?): Set<String> {
            if (dataElement == null || dataElement is JsonNull) return emptySet()
            val found = mutableSetOf<String>()
            when (dataElement) {
                is JsonObject -> {
                    dataElement.forEach { (hashKey, value) ->
                        if (value !is JsonNull && value.toString() != "false") {
                            found.add(hashKey.lowercase(Locale.US))
                        }
                    }
                }
                is JsonArray -> {
                    dataElement.forEach { item ->
                        when (item) {
                            is JsonPrimitive -> item.contentOrNull?.let { found.add(it.lowercase(Locale.US)) }
                            is JsonObject -> (item["hash"] as? JsonPrimitive)?.contentOrNull?.let {
                                found.add(it.lowercase(Locale.US))
                            }
                            else -> Unit
                        }
                    }
                }
                else -> Unit
            }
            return found
        }
    }
}