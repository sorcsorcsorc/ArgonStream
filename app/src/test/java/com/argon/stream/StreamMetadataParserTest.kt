package com.argon.stream

import com.argon.stream.data.remote.addon.StreamMetadataParser
import com.argon.stream.data.remote.addon.StremioBehaviorHintsDto
import com.argon.stream.data.remote.addon.StremioStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMetadataParserTest {

    @Test
    fun `toCandidate parses resolution, HEVC, Dolby Vision, Atmos, size, seeders, and cached badge`() {
        val dto = StremioStreamDto(
            name = "TorBox ⚡ 4K",
            title = "Sample.Movie.2024.2160p.UHD.BluRay.HEVC.DV.TrueHD.Atmos\n👤 142 💾 18.5 GB Multi",
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            behaviorHints = StremioBehaviorHintsDto(
                filename = "Sample.Movie.2024.2160p.mkv"
            )
        )

        val candidate = StreamMetadataParser.toCandidate(
            addonId = "org.community.torrent",
            addonName = "Community Streams",
            dto = dto,
            index = 0
        )

        assertNotNull(candidate)
        assertEquals("2160p", candidate?.resolution)
        assertEquals("HEVC", candidate?.codec)
        assertEquals("Dolby Vision", candidate?.hdrTag)
        assertEquals("Atmos", candidate?.audioTag)
        assertEquals(142, candidate?.seeders)
        assertEquals("18.5 GB", candidate?.sizeDisplay)
        assertTrue(candidate?.isCachedOnDebrid == true)
        assertTrue(candidate?.languages?.contains("Multi") == true)
    }
}