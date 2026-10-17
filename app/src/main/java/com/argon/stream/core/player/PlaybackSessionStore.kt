package com.argon.stream.core.player

import com.argon.stream.domain.model.MediaItem
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the player needs to start a stream and record proper watch history. */
data class PlaybackRequest(
    val streamUrl: String,
    val displayTitle: String,
    val stremioVideoId: String,
    val requestHeaders: Map<String, String> = emptyMap(),
    val media: MediaItem? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

/**
 * Hands a [PlaybackRequest] from the stream picker to the player screen.
 * Navigation arguments only carry the URL/title, which cannot hold headers or artwork.
 */
@Singleton
class PlaybackSessionStore @Inject constructor() {
    @Volatile
    private var pending: PlaybackRequest? = null

    fun put(request: PlaybackRequest) {
        pending = request
    }

    /** Returns the pending request if it matches [streamUrl]; the request stays available for config changes. */
    fun peek(streamUrl: String): PlaybackRequest? = pending?.takeIf { it.streamUrl == streamUrl }
}
