package com.argon.stream.data.repository

import android.content.Context
import com.argon.stream.data.local.ProfileDao
import com.argon.stream.data.local.ProfileEntity
import com.argon.stream.data.local.StreamChoiceDao
import com.argon.stream.data.local.WatchHistoryDao
import com.argon.stream.data.local.WatchedDao
import com.argon.stream.data.local.WatchlistDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class Profile(
    val id: Long,
    val name: String,
    val colorIndex: Int,
    val hasPin: Boolean
)

/**
 * Local "Who's watching?" profiles. No account or server: profiles live on this device and each one
 * has its own watchlist, favorites, history, watched marks and remembered streams.
 */
@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val profileDao: ProfileDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val watchlistDao: WatchlistDao,
    private val watchedDao: WatchedDao,
    private val streamChoiceDao: StreamChoiceDao
) {
    private val prefs = context.getSharedPreferences("argon_profile_state", Context.MODE_PRIVATE)

    private val _activeProfileId = MutableStateFlow(prefs.getLong(KEY_ACTIVE, 1L))
    val activeProfileId: StateFlow<Long> = _activeProfileId.asStateFlow()

    /** False until someone picks a profile in this app session (reset on every launch). */
    private val _sessionConfirmed = MutableStateFlow(false)
    val sessionConfirmed: StateFlow<Boolean> = _sessionConfirmed.asStateFlow()

    /** True once a profile is in use this session, so the picker can then be closed without choosing. */
    private val _pickedThisSession = MutableStateFlow(false)
    val pickedThisSession: StateFlow<Boolean> = _pickedThisSession.asStateFlow()

    val profiles: Flow<List<Profile>> = profileDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun currentProfileId(): Long = _activeProfileId.value

    /** Makes sure at least one profile exists and the active id points at a real profile. */
    suspend fun ensureProfileExists() {
        val all = profileDao.getAll()
        if (all.isEmpty()) {
            val id = profileDao.insert(ProfileEntity(name = "Me", colorIndex = 0, pinHash = null, createdAtMs = System.currentTimeMillis()))
            setActive(id)
        } else if (all.none { it.id == _activeProfileId.value }) {
            setActive(all.first().id)
        }
        if (profileDao.getAll().size <= 1) {
            _sessionConfirmed.value = true
            _pickedThisSession.value = true
        }
    }

    fun select(profileId: Long) {
        setActive(profileId)
        _sessionConfirmed.value = true
        _pickedThisSession.value = true
    }

    /** Shows the "Who's watching?" screen again. */
    fun requestSwitch() {
        _sessionConfirmed.value = false
    }

    suspend fun verifyPin(profileId: Long, pin: String): Boolean {
        val profile = profileDao.get(profileId) ?: return false
        return profile.pinHash == null || profile.pinHash == hashPin(profileId, pin)
    }

    suspend fun create(name: String, colorIndex: Int, pin: String?): Long {
        val id = profileDao.insert(
            ProfileEntity(name = name.trim().ifBlank { "Profile" }, colorIndex = colorIndex, pinHash = null, createdAtMs = System.currentTimeMillis())
        )
        if (!pin.isNullOrBlank()) setPin(id, pin)
        return id
    }

    suspend fun update(profileId: Long, name: String, colorIndex: Int) {
        val profile = profileDao.get(profileId) ?: return
        profileDao.update(profile.copy(name = name.trim().ifBlank { profile.name }, colorIndex = colorIndex))
    }

    /** Pass null or blank to remove the PIN. */
    suspend fun setPin(profileId: Long, pin: String?) {
        val profile = profileDao.get(profileId) ?: return
        profileDao.update(profile.copy(pinHash = pin?.takeIf { it.isNotBlank() }?.let { hashPin(profileId, it) }))
    }

    /** Deletes a profile and its personal data. The last remaining profile can't be deleted. */
    suspend fun delete(profileId: Long): Boolean {
        val all = profileDao.getAll()
        if (all.size <= 1) return false
        watchHistoryDao.clearForProfile(profileId)
        watchlistDao.clearForProfile(profileId)
        watchedDao.clearForProfile(profileId)
        streamChoiceDao.clearForProfile(profileId)
        profileDao.delete(profileId)
        if (_activeProfileId.value == profileId) setActive(all.first { it.id != profileId }.id)
        return true
    }

    private fun setActive(id: Long) {
        _activeProfileId.value = id
        prefs.edit().putLong(KEY_ACTIVE, id).apply()
    }

    private fun ProfileEntity.toDomain() = Profile(id, name, colorIndex, pinHash != null)

    companion object {
        private const val KEY_ACTIVE = "active_profile_id"

        fun hashPin(profileId: Long, pin: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("argon:$profileId:$pin".toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
