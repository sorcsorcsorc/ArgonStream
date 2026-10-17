package com.argon.stream.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class TorBoxPlanTier(val displayName: String) {
    FREE("Free (Plan 0)"),
    ESSENTIAL("Essential (Plan 1)"),
    PRO("Pro (Plan 2)"),
    STANDARD("Standard (Plan 3)"),
    UNKNOWN("Unknown Plan");

    companion object {
        fun fromCode(code: Int?): TorBoxPlanTier = when (code) {
            0 -> FREE
            1 -> ESSENTIAL
            2 -> PRO
            3 -> STANDARD
            else -> UNKNOWN
        }
    }
}

@Serializable
data class TorBoxAccountInfo(
    val userId: Int,
    val email: String,
    val planTier: TorBoxPlanTier,
    val isSubscribed: Boolean,
    val premiumExpiresAt: String?,
    val totalBytesDownloaded: Long
)

@Serializable
enum class TorBoxContentState {
    CACHED_INSTANT,
    PROCESSING_DOWNLOAD,
    UNAVAILABLE,
    UNSUPPORTED
}

@Serializable
data class TorBoxMediaFile(
    val id: Int,
    val name: String,
    val shortName: String,
    val sizeBytes: Long
)

@Serializable
sealed interface TorBoxResolutionOutcome {
    data class ReadyToPlay(
        val resolvedStreamUrl: String,
        val fileName: String,
        val torrentId: Int,
        val fileId: Int
    ) : TorBoxResolutionOutcome

    data class MultiFileSelectionRequired(
        val torrentId: Int,
        val torrentName: String,
        val playableFiles: List<TorBoxMediaFile>
    ) : TorBoxResolutionOutcome

    data class QueuedProcessing(
        val torrentId: Int,
        val statusText: String,
        val progressPercent: Double
    ) : TorBoxResolutionOutcome

    data class Failed(
        val state: TorBoxContentState,
        val userMessage: String
    ) : TorBoxResolutionOutcome
}