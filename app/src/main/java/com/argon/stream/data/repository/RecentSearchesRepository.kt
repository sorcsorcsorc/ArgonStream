package com.argon.stream.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** The last few searches, kept per profile on this device. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RecentSearchesRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val profileRepository: ProfileRepository
) {
    private val prefs = context.getSharedPreferences("argon_recent_searches", Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(0)

    fun observe(): Flow<List<String>> = profileRepository.activeProfileId.flatMapLatest { profileId ->
        changes.map { read(profileId) }
    }

    fun add(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val profileId = profileRepository.currentProfileId()
        val updated = (listOf(clean) + read(profileId).filterNot { it.equals(clean, ignoreCase = true) }).take(MAX)
        write(profileId, updated)
    }

    fun remove(query: String) {
        val profileId = profileRepository.currentProfileId()
        write(profileId, read(profileId).filterNot { it == query })
    }

    fun clear() = write(profileRepository.currentProfileId(), emptyList())

    private fun read(profileId: Long): List<String> =
        prefs.getString(key(profileId), null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()

    private fun write(profileId: Long, items: List<String>) {
        prefs.edit().putString(key(profileId), items.joinToString(SEPARATOR)).apply()
        changes.value++
    }

    private fun key(profileId: Long) = "profile_$profileId"

    private companion object {
        const val MAX = 10
        const val SEPARATOR = "\u001F"
    }
}
