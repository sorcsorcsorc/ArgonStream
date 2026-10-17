package com.argon.stream

import com.argon.stream.data.remote.addon.StremioSubtitleClient
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleMimeTypeTest {

    @Test
    fun `detectSubtitleMimeType distinguishes WebVTT and SubRip SRT URLs`() {
        assertEquals(
            "text/vtt",
            StremioSubtitleClient.detectSubtitleMimeType("https://subs.example.com/en.vtt?token=123")
        )
        assertEquals(
            "application/x-subrip",
            StremioSubtitleClient.detectSubtitleMimeType("https://subs.example.com/movie.en.srt")
        )
    }
}