package com.argon.stream.ui.screens.streams

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The exact stream (add-on result key) last played for each video, per profile, so Continue Watching
 * can reopen the very same file instead of asking again.
 */
@Singleton
class LastStreamStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("argon_last_streams", Context.MODE_PRIVATE)

    fun get(profileId: Long, videoId: String): String? = prefs.getString(key(profileId, videoId), null)

    fun put(profileId: Long, videoId: String, streamKey: String) {
        if (videoId.isBlank()) return
        prefs.edit().putString(key(profileId, videoId), streamKey).apply()
    }

    private fun key(profileId: Long, videoId: String) = "$profileId|$videoId"
}
