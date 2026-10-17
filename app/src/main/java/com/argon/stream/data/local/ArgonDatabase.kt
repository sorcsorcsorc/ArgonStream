package com.argon.stream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        WatchlistEntity::class,
        AddonEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ArgonDatabase : RoomDatabase() {
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun addonDao(): AddonDao
}