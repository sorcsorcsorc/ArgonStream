package com.argon.stream

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.argon.stream.data.local.ArgonDatabase
import com.argon.stream.data.local.WatchedEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a database exactly as version 1.4.x created it, fills it with data, then opens it with the
 * current Room schema. Room validates every table after migrating, so a schema mistake fails here
 * instead of wiping a user's watchlist on update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ArgonDatabaseMigrationTest {

    @Test
    fun `v1 data moves into the default profile and survives the upgrade`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }

        val v1Schema = javaClass.classLoader!!.getResource("argon_db_v1_schema.sql")!!.readText()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1Schema.lines().filter { it.isNotBlank() }.forEach { db.execSQL(it) }
            db.execSQL(
                "INSERT INTO watch_history VALUES ('series:tt0903747','tt0903747',1396,'tt0903747','Breaking Bad','SERIES'," +
                    "'https://p/poster.jpg',NULL,2,5,'S2E5',1200000,2700000,'123',1700000000000)"
            )
            db.execSQL(
                "INSERT INTO watchlist VALUES ('tt0133093',603,'tt0133093','The Matrix','MOVIE',NULL,NULL,'1999',8.7,1,1700000000000)"
            )
            db.execSQL(
                "INSERT INTO watchlist VALUES ('tt0816692',NULL,'tt0816692','Interstellar','MOVIE',NULL,NULL,'2014',8.7,0,1700000000001)"
            )
            db.execSQL(
                "INSERT INTO addons VALUES ('com.linvo.cinemeta','Cinemeta','3.0.14',NULL,'https://v3-cinemeta.strem.io/manifest.json'," +
                    "'https://v3-cinemeta.strem.io','catalog,meta','movie,series','tt',1,0,'VERIFIED',1700000000000)"
            )
            db.version = 1
        }

        val room = Room.databaseBuilder(context, ArgonDatabase::class.java, name)
            .addMigrations(ArgonDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val profiles = room.profileDao().getAll()
            assertEquals(1, profiles.size)
            assertEquals(ArgonDatabase.DEFAULT_PROFILE_ID, profiles.first().id)
            assertEquals("Me", profiles.first().name)

            val history = room.watchHistoryDao().getByKey(ArgonDatabase.DEFAULT_PROFILE_ID, "series:tt0903747")
            assertNotNull(history)
            assertEquals(1_200_000L, history!!.positionMs)
            assertEquals(5, history.episodeNumber)

            // Old favorites were also on the watchlist; both flags must be kept.
            val matrix = room.watchlistDao().getItem(ArgonDatabase.DEFAULT_PROFILE_ID, "tt0133093")!!
            assertTrue(matrix.isInWatchlist)
            assertTrue(matrix.isFavorite)
            val interstellar = room.watchlistDao().getItem(ArgonDatabase.DEFAULT_PROFILE_ID, "tt0816692")!!
            assertTrue(interstellar.isInWatchlist)
            assertEquals(false, interstellar.isFavorite)

            assertEquals(2, room.watchlistDao().observeWatchlist(ArgonDatabase.DEFAULT_PROFILE_ID).first().size)
            assertEquals(1, room.watchlistDao().observeFavorites(ArgonDatabase.DEFAULT_PROFILE_ID).first().size)
            assertEquals(1, room.addonDao().observeAddons().first().size)

            // New tables are usable.
            room.watchedDao().upsert(WatchedEntity(ArgonDatabase.DEFAULT_PROFILE_ID, "tt0903747", 2, 5, 1L))
            assertEquals(1, room.watchedDao().observeForMedia(ArgonDatabase.DEFAULT_PROFILE_ID, "tt0903747").first().size)
        }
        room.close()
    }
}
