package com.argon.stream.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * TMDB issues two kinds of credentials: the short v3 "API Key" (sent as `api_key=`)
 * and the long v4 "API Read Access Token" (a JWT that must be sent as a Bearer header).
 * Users often paste the token, so this moves a JWT-looking `api_key` into the header.
 */
class TmdbAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.queryParameter("api_key")
        if (key == null || !looksLikeReadAccessToken(key)) {
            return chain.proceed(request)
        }
        val url = request.url.newBuilder().removeAllQueryParameters("api_key").build()
        return chain.proceed(
            request.newBuilder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .build()
        )
    }

    companion object {
        fun looksLikeReadAccessToken(key: String): Boolean =
            key.startsWith("eyJ") && key.count { it == '.' } == 2
    }
}
