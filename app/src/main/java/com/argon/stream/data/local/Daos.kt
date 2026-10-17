package com.argon.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY updatedAtMs DESC LIMIT :limit")
    fun observeContinueWatching(limit: Int = 25): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE mediaId = :mediaId ORDER BY updatedAtMs DESC LIMIT 1")
    suspend fun getLatestForMedia(mediaId: String): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history WHERE playbackKey = :playbackKey LIMIT 1")
    suspend fun getByKey(playbackKey: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchProgress(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE playbackKey = :playbackKey")
    suspend fun removeWatchProgress(playbackKey: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAtMs DESC")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE mediaId = :mediaId LIMIT 1")
    fun observeItem(mediaId: String): Flow<WatchlistEntity?>

    @Query("SELECT * FROM watchlist WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getItem(mediaId: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE mediaId = :mediaId")
    suspend fun remove(mediaId: String)

    @Query("DELETE FROM watchlist")
    suspend fun clearAll()
}

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons ORDER BY priorityOrder ASC")
    fun observeAddons(): Flow<List<AddonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddon(addon: AddonEntity)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteAddon(id: String)

    @Query("DELETE FROM addons")
    suspend fun clearAll()
}