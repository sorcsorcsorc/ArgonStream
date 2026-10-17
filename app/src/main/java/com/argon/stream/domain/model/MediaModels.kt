package com.argon.stream.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    MOVIE,
    SERIES
}

@Serializable
data class MediaItem(
    val id: String,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val title: String,
    val type: MediaType,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String,
    val releaseYear: String?,
    val rating: Double?,
    val genres: List<String> = emptyList()
)

@Serializable
data class CastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?
)

@Serializable
data class SeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val posterUrl: String?,
    val airDate: String?
)

@Serializable
data class EpisodeItem(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String,
    val stillUrl: String?,
    val airDate: String?,
    val runtimeMinutes: Int?,
    val rating: Double?
) {
    fun buildStremioVideoId(imdbId: String): String = "$imdbId:$seasonNumber:$episodeNumber"
}

@Serializable
data class MediaDetails(
    val item: MediaItem,
    val runtimeMinutes: Int?,
    val status: String?,
    val tagline: String?,
    val cast: List<CastMember>,
    val seasons: List<SeasonSummary>,
    val isInWatchlist: Boolean = false,
    val isFavorite: Boolean = false
)

@Serializable
data class WatchProgressItem(
    val mediaItem: MediaItem,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

@Serializable
data class CatalogRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val items: List<MediaItem>
)