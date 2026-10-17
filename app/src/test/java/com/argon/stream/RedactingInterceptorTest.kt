package com.argon.stream

import com.argon.stream.core.network.RedactingLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactingInterceptorTest {

    @Test
    fun `sanitizeUrl redacts api_key and token query parameters`() {
        val raw = "https://api.torbox.app/v1/api/torrents/requestdl?token=tb_secret_999&torrent_id=42&file_id=1"
        val sanitized = RedactingLoggingInterceptor.sanitizeUrl(raw)

        assertFalse(sanitized.contains("tb_secret_999"))
        assertEquals(
            "https://api.torbox.app/v1/api/torrents/requestdl?token=[REDACTED]&torrent_id=42&file_id=1",
            sanitized
        )
    }
}