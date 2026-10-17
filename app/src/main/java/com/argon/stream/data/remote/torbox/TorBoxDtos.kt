package com.argon.stream.data.remote.torbox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TorBoxEnvelope<T>(
    val success: Boolean = false,
    val error: String? = null,
    val detail: String? = null,
    val data: T? = null
)

@Serializable
data class TorBoxUserDto(
    val id: Int = 0,
    val email: String = "",
    val plan: Int = 0,
    @SerialName("is_subscribed") val isSubscribed: Boolean = false,
    @SerialName("premium_expires_at") val premiumExpiresAt: String? = null,
    @SerialName("total_downloaded") val totalDownloaded: Long = 0L
)

@Serializable
data class TorBoxCreatedTorrentDto(
    @SerialName("torrent_id") val torrentId: Int? = null,
    val id: Int? = null,
    val hash: String? = null,
    val name: String? = null
) {
    val resolvedId: Int?
        get() = torrentId ?: id
}

@Serializable
data class TorBoxTorrentItemDto(
    val id: Int,
    val hash: String = "",
    val name: String = "",
    val size: Long = 0L,
    val progress: Double = 0.0,
    @SerialName("download_state") val downloadState: String = "",
    @SerialName("download_present") val downloadPresent: Boolean = false,
    @SerialName("download_finished") val downloadFinished: Boolean = false,
    val files: List<TorBoxTorrentFileDto> = emptyList()
)

@Serializable
data class TorBoxTorrentFileDto(
    val id: Int,
    val name: String = "",
    @SerialName("short_name") val shortName: String? = null,
    val size: Long = 0L
)

typealias TorBoxRawEnvelope = TorBoxEnvelope<JsonElement>