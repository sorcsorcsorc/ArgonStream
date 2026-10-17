package com.argon.stream

import com.argon.stream.core.network.TmdbAuthInterceptor
import com.argon.stream.data.remote.addon.StreamMetadataParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedBadgeAndTmdbTokenTest {

    @Test
    fun `cached markers are detected`() {
        assertTrue(StreamMetadataParser.isMarkedCached("[TB+] Torrentio 1080p"))
        assertTrue(StreamMetadataParser.isMarkedCached("[RD+] Movie.2024.2160p"))
        assertTrue(StreamMetadataParser.isMarkedCached("⚡ AIOStreams 4K"))
        assertTrue(StreamMetadataParser.isMarkedCached("TorBox (Cached)"))
    }

    @Test
    fun `uncached streams are not marked instant`() {
        assertFalse(StreamMetadataParser.isMarkedCached("[TB download] Torrentio uncached 1080p"))
        assertFalse(StreamMetadataParser.isMarkedCached("Not cached on TorBox"))
        assertFalse(StreamMetadataParser.isMarkedCached("⏳ AIOStreams 1080p"))
        assertFalse(StreamMetadataParser.isMarkedCached("Plain torrent 720p"))
    }

    @Test
    fun `tmdb read access token is recognised but v3 key is not`() {
        assertTrue(TmdbAuthInterceptor.looksLikeReadAccessToken("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJ4In0.c2lnbmF0dXJl"))
        assertFalse(TmdbAuthInterceptor.looksLikeReadAccessToken("0123456789abcdef0123456789abcdef"))
    }
}
