package com.argon.stream.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.argon.stream.domain.model.MediaType

/** A "Who's watching?" profile. Every personal row below is scoped to a profile. */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int,
    val pinHash: String?,
    val createdAtMs: Long
)

@Entity(tableName = "watch_history", primaryKeys = ["profileId", "playbackKey"])
data class WatchHistoryEntity(
    val profileId: Long,
    val playbackKey: String, // e.g. "movie:550" or "series:tt0903747"
    val mediaId: String,
    val tmdbId: Int?,
    val imdbId: String?,
    val title: String,
    val mediaType: MediaType,
    val posterUrl: String?,
    val backdropUrl: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    val lastStreamUrlHash: String?,
    val updatedAtMs: Long
)

/** One row per saved title; "in watchlist" and "favorite" are independent flags. */
@Entity(tableName = "watchlist", primaryKeys = ["profileId", "mediaId"])
data class WatchlistEntity(
    val profileId: Long,
    val mediaId: String,
    val tmdbId: Int?,
    val imdbId: String?,
    val title: String,
    val mediaType: MediaType,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val rating: Double?,
    val isInWatchlist: Boolean,
    val isFavorite: Boolean,
    val addedAtMs: Long
)

/** Watched marks. Movies use season 0 / episode 0. */
@Entity(tableName = "watched_items", primaryKeys = ["profileId", "mediaId", "seasonNumber", "episodeNumber"])
data class WatchedEntity(
    val profileId: Long,
    val mediaId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val watchedAtMs: Long
)

/** The stream source last picked for a title, used to auto-pick the next episode. */
@Entity(tableName = "stream_choices", primaryKeys = ["profileId", "mediaId"])
data class StreamChoiceEntity(
    val profileId: Long,
    val mediaId: String,
    val addonId: String,
    val bingeGroup: String?,
    val resolution: String,
    val updatedAtMs: Long
)

@Entity(tableName = "addons")
data class AddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val manifestUrl: String,
    val baseUrl: String,
    val resourcesCsv: String,
    val typesCsv: String,
    val idPrefixesCsv: String,
    val isEnabled: Boolean,
    val priorityOrder: Int,
    val lastHealthStatus: String,
    val updatedAtMs: Long
)
