package com.argon.stream.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.argon.stream.domain.model.MediaType

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val playbackKey: String, // e.g. "movie:550" or "series:1399"
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

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val mediaId: String,
    val tmdbId: Int?,
    val imdbId: String?,
    val title: String,
    val mediaType: MediaType,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val rating: Double?,
    val isFavorite: Boolean,
    val addedAtMs: Long
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