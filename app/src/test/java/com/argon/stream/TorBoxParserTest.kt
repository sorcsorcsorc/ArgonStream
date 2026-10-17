package com.argon.stream

import com.argon.stream.data.remote.torbox.TorBoxTorrentFileDto
import com.argon.stream.data.repository.TorBoxRepository
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorBoxParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parseCachedHashesFromDataElement extracts cached hashes from TorBox object format`() {
        val rawDataJson = """
            {
              "0123456789abcdef0123456789abcdef01234567": {
                "name": "Sample.Movie.2024.2160p.mkv",
                "size": 14500000000
              },
              "fedcba9876543210fedcba9876543210fedcba98": null
            }
        """.trimIndent()

        val element = json.parseToJsonElement(rawDataJson)
        val cached = TorBoxRepository.parseCachedHashesFromDataElement(element)

        assertEquals(1, cached.size)
        assertTrue(cached.contains("0123456789abcdef0123456789abcdef01234567"))
    }

    @Test
    fun `matchEpisodeOrFileIdx matches S01E03 inside multi-file season pack`() {
        val files = listOf(
            TorBoxTorrentFileDto(id = 1, name = "Pioneer.One.S01E01.1080p.mkv", size = 800_000_000),
            TorBoxTorrentFileDto(id = 2, name = "Pioneer.One.S01E02.1080p.mkv", size = 810_000_000),
            TorBoxTorrentFileDto(id = 3, name = "Pioneer.One.S01E03.1080p.mkv", size = 850_000_000)
        )

        val matched = TorBoxRepository.matchEpisodeOrFileIdx(
            videoFiles = files,
            stremioVideoId = "tt0903747:1:3",
            fileIdx = null
        )

        assertNotNull(matched)
        assertEquals(3, matched?.id)
    }
}