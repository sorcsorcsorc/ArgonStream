package com.argon.stream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        WatchHistoryEntity::class,
        WatchlistEntity::class,
        WatchedEntity::class,
        StreamChoiceEntity::class,
        AddonEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class ArgonDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun watchedDao(): WatchedDao
    abstract fun streamChoiceDao(): StreamChoiceDao
    abstract fun addonDao(): AddonDao

    companion object {
        const val DEFAULT_PROFILE_ID = 1L

        /**
         * v1 → v2: adds profiles. Existing history and watchlist move to a default "Me" profile;
         * every old watchlist row stays in the watchlist and keeps its favorite flag.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(CREATE_PROFILES)
                db.execSQL(
                    "INSERT INTO `profiles` (`id`, `name`, `colorIndex`, `pinHash`, `createdAtMs`) " +
                        "VALUES ($DEFAULT_PROFILE_ID, 'Me', 0, NULL, ${System.currentTimeMillis()})"
                )

                db.execSQL(CREATE_WATCH_HISTORY.replace("`watch_history`", "`watch_history_new`"))
                db.execSQL(
                    "INSERT INTO `watch_history_new` (`profileId`, `playbackKey`, `mediaId`, `tmdbId`, `imdbId`, `title`, `mediaType`, " +
                        "`posterUrl`, `backdropUrl`, `seasonNumber`, `episodeNumber`, `episodeTitle`, `positionMs`, `durationMs`, " +
                        "`lastStreamUrlHash`, `updatedAtMs`) SELECT $DEFAULT_PROFILE_ID, `playbackKey`, `mediaId`, `tmdbId`, `imdbId`, " +
                        "`title`, `mediaType`, `posterUrl`, `backdropUrl`, `seasonNumber`, `episodeNumber`, `episodeTitle`, `positionMs`, " +
                        "`durationMs`, `lastStreamUrlHash`, `updatedAtMs` FROM `watch_history`"
                )
                db.execSQL("DROP TABLE `watch_history`")
                db.execSQL("ALTER TABLE `watch_history_new` RENAME TO `watch_history`")

                db.execSQL(CREATE_WATCHLIST.replace("`watchlist`", "`watchlist_new`"))
                db.execSQL(
                    "INSERT INTO `watchlist_new` (`profileId`, `mediaId`, `tmdbId`, `imdbId`, `title`, `mediaType`, `posterUrl`, " +
                        "`backdropUrl`, `releaseYear`, `rating`, `isInWatchlist`, `isFavorite`, `addedAtMs`) SELECT $DEFAULT_PROFILE_ID, " +
                        "`mediaId`, `tmdbId`, `imdbId`, `title`, `mediaType`, `posterUrl`, `backdropUrl`, `releaseYear`, `rating`, 1, " +
                        "`isFavorite`, `addedAtMs` FROM `watchlist`"
                )
                db.execSQL("DROP TABLE `watchlist`")
                db.execSQL("ALTER TABLE `watchlist_new` RENAME TO `watchlist`")

                db.execSQL(CREATE_WATCHED)
                db.execSQL(CREATE_STREAM_CHOICES)
            }
        }

        // Must match what Room generates for the entities exactly (checked by ArgonDatabaseMigrationTest).
        private const val CREATE_PROFILES =
            "CREATE TABLE IF NOT EXISTS `profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`colorIndex` INTEGER NOT NULL, `pinHash` TEXT, `createdAtMs` INTEGER NOT NULL)"
        private const val CREATE_WATCH_HISTORY =
            "CREATE TABLE IF NOT EXISTS `watch_history` (`profileId` INTEGER NOT NULL, `playbackKey` TEXT NOT NULL, `mediaId` TEXT NOT NULL, " +
                "`tmdbId` INTEGER, `imdbId` TEXT, `title` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `posterUrl` TEXT, `backdropUrl` TEXT, " +
                "`seasonNumber` INTEGER, `episodeNumber` INTEGER, `episodeTitle` TEXT, `positionMs` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL, `lastStreamUrlHash` TEXT, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `playbackKey`))"
        private const val CREATE_WATCHLIST =
            "CREATE TABLE IF NOT EXISTS `watchlist` (`profileId` INTEGER NOT NULL, `mediaId` TEXT NOT NULL, `tmdbId` INTEGER, `imdbId` TEXT, " +
                "`title` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `posterUrl` TEXT, `backdropUrl` TEXT, `releaseYear` TEXT, `rating` REAL, " +
                "`isInWatchlist` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `mediaId`))"
        private const val CREATE_WATCHED =
            "CREATE TABLE IF NOT EXISTS `watched_items` (`profileId` INTEGER NOT NULL, `mediaId` TEXT NOT NULL, `seasonNumber` INTEGER NOT NULL, " +
                "`episodeNumber` INTEGER NOT NULL, `watchedAtMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `mediaId`, `seasonNumber`, `episodeNumber`))"
        private const val CREATE_STREAM_CHOICES =
            "CREATE TABLE IF NOT EXISTS `stream_choices` (`profileId` INTEGER NOT NULL, `mediaId` TEXT NOT NULL, `addonId` TEXT NOT NULL, " +
                "`bingeGroup` TEXT, `resolution` TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `mediaId`))"
    }
}
