package com.argon.stream.data.remote.torbox

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Verified against Official TorBox API Docs (https://api-docs.torbox.app/).
 * Base URL: https://api.torbox.app/v1/api/
 */
interface TorBoxApiService {

    @GET("user/me")
    suspend fun getAccountMe(
        @Header("Authorization") bearerToken: String,
        @Query("settings") includeSettings: Boolean = true
    ): TorBoxEnvelope<TorBoxUserDto>

    @GET("torrents/checkcached")
    suspend fun checkCachedHashes(
        @Header("Authorization") bearerToken: String,
        @Query("hash") hashesCsv: String,
        @Query("format") format: String = "object",
        @Query("list_files") listFiles: Boolean = true
    ): TorBoxRawEnvelope

    @Multipart
    @POST("torrents/createtorrent")
    suspend fun createTorrentFromMagnet(
        @Header("Authorization") bearerToken: String,
        @Part("magnet") magnetBody: RequestBody,
        @Part("seed") seedPolicy: RequestBody? = null,
        @Part("allow_zip") allowZip: RequestBody? = null,
        @Part file: MultipartBody.Part? = null
    ): TorBoxEnvelope<TorBoxCreatedTorrentDto>

    @GET("torrents/mylist")
    suspend fun getTorrentById(
        @Header("Authorization") bearerToken: String,
        @Query("id") torrentId: Int,
        @Query("bypass_cache") bypassCache: Boolean = true
    ): TorBoxEnvelope<TorBoxTorrentItemDto>

    @GET("torrents/mylist")
    suspend fun getUserTorrentList(
        @Header("Authorization") bearerToken: String,
        @Query("bypass_cache") bypassCache: Boolean = true
    ): TorBoxEnvelope<List<TorBoxTorrentItemDto>>

    /**
     * Note: Official TorBox GET /torrents/requestdl requires the user token in the `token` query parameter.
     * RedactingLoggingInterceptor automatically scrubs `token=...` from all diagnostic logs.
     */
    @GET("torrents/requestdl")
    suspend fun requestDownloadLink(
        @Query("token") apiKey: String,
        @Query("torrent_id") torrentId: Int,
        @Query("file_id") fileId: Int,
        @Query("zip_link") zipLink: Boolean = false
    ): TorBoxEnvelope<String>

    companion object {
        const val BASE_URL = "https://api.torbox.app/v1/api/"
    }
}