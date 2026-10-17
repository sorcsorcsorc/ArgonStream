package com.argon.stream.data.remote.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("trending/all/week")
    suspend fun getTrendingAll(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("tv/popular")
    suspend fun getPopularSeries(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("tv/top_rated")
    suspend fun getTopRatedSeries(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbPagedResponse

    @GET("movie/{movieId}")
    suspend fun getMovieDetails(
        @Path("movieId") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "external_ids,credits,videos"
    ): TmdbDetailsDto

    @GET("tv/{tvId}")
    suspend fun getSeriesDetails(
        @Path("tvId") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "external_ids,credits,videos"
    ): TmdbDetailsDto

    @GET("tv/{tvId}/season/{seasonNumber}")
    suspend fun getSeasonEpisodes(
        @Path("tvId") tvId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbSeasonDetailsDto

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_W185 = "https://image.tmdb.org/t/p/w185"
        const val IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
        const val IMAGE_W780 = "https://image.tmdb.org/t/p/w780"
        const val IMAGE_W1280 = "https://image.tmdb.org/t/p/w1280"
    }
}