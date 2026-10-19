package com.argon.stream

import com.argon.stream.data.remote.imdb.ImdbSuggestClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ImdbPosterTest {
    @Test
    fun `imdb posters are requested at card size`() {
        assertEquals(
            "https://m.media-amazon.com/images/M/MV5Babc@._V1_QL75_UX380_.jpg",
            ImdbSuggestClient.smallPoster("https://m.media-amazon.com/images/M/MV5Babc@._V1_.jpg")
        )
    }
}
