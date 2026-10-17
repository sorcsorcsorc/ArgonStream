package com.argon.stream.di

import android.content.Context
import com.argon.stream.BuildConfig
import androidx.room.Room
import com.argon.stream.core.network.RedactingLoggingInterceptor
import com.argon.stream.core.network.TmdbAuthInterceptor
import com.argon.stream.data.local.AddonDao
import com.argon.stream.data.local.ArgonDatabase
import com.argon.stream.data.local.WatchHistoryDao
import com.argon.stream.data.local.WatchlistDao
import com.argon.stream.data.remote.tmdb.TmdbApiService
import com.argon.stream.data.remote.torbox.TorBoxApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        redactingLoggingInterceptor: RedactingLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                // Some image hosts (e.g. Wikimedia) reject OkHttp's generic user agent.
                val request = chain.request()
                chain.proceed(
                    if (request.header("User-Agent") == null) {
                        request.newBuilder().header("User-Agent", "ArgonStream/${BuildConfig.VERSION_NAME} (Android app)").build()
                    } else request
                )
            }
            .addInterceptor(redactingLoggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideTmdbApiService(
        okHttpClient: OkHttpClient,
        json: Json
    ): TmdbApiService {
        val tmdbClient = okHttpClient.newBuilder()
            .addInterceptor(TmdbAuthInterceptor())
            .build()
        return Retrofit.Builder()
            .baseUrl(TmdbApiService.BASE_URL)
            .client(tmdbClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideTorBoxApiService(
        okHttpClient: OkHttpClient,
        json: Json
    ): TorBoxApiService {
        return Retrofit.Builder()
            .baseUrl(TorBoxApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TorBoxApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ArgonDatabase {
        return Room.databaseBuilder(
            context,
            ArgonDatabase::class.java,
            "argon_stream.db"
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    @Provides
    fun provideWatchHistoryDao(db: ArgonDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideWatchlistDao(db: ArgonDatabase): WatchlistDao = db.watchlistDao()

    @Provides
    fun provideAddonDao(db: ArgonDatabase): AddonDao = db.addonDao()
}