package com.argon.stream

import com.argon.stream.domain.model.EpisodeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class StremioIdResolutionTest {

    @Test
    fun `buildStremioVideoId formats series episode ID as imdbId_season_episode`() {
        val episode = EpisodeItem(
            id = 101,
            seasonNumber = 2,
            episodeNumber = 5,
            title = "Test Episode",
            overview = "",
            stillUrl = null,
            airDate = "2024-01-01",
            runtimeMinutes = 48,
            rating = 8.2
        )
        assertEquals("tt0903747:2:5", episode.buildStremioVideoId("tt0903747"))
    }
}