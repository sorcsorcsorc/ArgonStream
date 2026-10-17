package com.argon.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    suspend fun getAll(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM profiles")
    suspend fun clearAll()
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history WHERE profileId = :profileId ORDER BY updatedAtMs DESC LIMIT :limit")
    fun observeContinueWatching(profileId: Long, limit: Int = 25): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND playbackKey = :playbackKey LIMIT 1")
    suspend fun getByKey(profileId: Long, playbackKey: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchProgress(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId AND playbackKey = :playbackKey")
    suspend fun removeWatchProgress(profileId: Long, playbackKey: String)

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist WHERE profileId = :profileId AND isInWatchlist = 1 ORDER BY addedAtMs DESC")
    fun observeWatchlist(profileId: Long): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId AND isFavorite = 1 ORDER BY addedAtMs DESC")
    fun observeFavorites(profileId: Long): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId AND mediaId = :mediaId LIMIT 1")
    fun observeItem(profileId: Long, mediaId: String): Flow<WatchlistEntity?>

    @Query("SELECT * FROM watchlist WHERE profileId = :profileId AND mediaId = :mediaId LIMIT 1")
    suspend fun getItem(profileId: Long, mediaId: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId AND mediaId = :mediaId")
    suspend fun remove(profileId: Long, mediaId: String)

    @Query("DELETE FROM watchlist WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)

    @Query("DELETE FROM watchlist")
    suspend fun clearAll()
}

@Dao
interface WatchedDao {
    @Query("SELECT * FROM watched_items WHERE profileId = :profileId AND mediaId = :mediaId")
    fun observeForMedia(profileId: Long, mediaId: String): Flow<List<WatchedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchedEntity)

    @Query("DELETE FROM watched_items WHERE profileId = :profileId AND mediaId = :mediaId AND seasonNumber = :season AND episodeNumber = :episode")
    suspend fun remove(profileId: Long, mediaId: String, season: Int, episode: Int)

    @Query("DELETE FROM watched_items WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)

    @Query("DELETE FROM watched_items")
    suspend fun clearAll()
}

@Dao
interface StreamChoiceDao {
    @Query("SELECT * FROM stream_choices WHERE profileId = :profileId AND mediaId = :mediaId LIMIT 1")
    suspend fun get(profileId: Long, mediaId: String): StreamChoiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StreamChoiceEntity)

    @Query("DELETE FROM stream_choices WHERE profileId = :profileId")
    suspend fun clearForProfile(profileId: Long)

    @Query("DELETE FROM stream_choices")
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
