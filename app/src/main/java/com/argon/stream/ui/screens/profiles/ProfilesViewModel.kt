package com.argon.stream.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.data.repository.Profile
import com.argon.stream.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilesUiState(
    val ready: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: Long = 0,
    /** Show "Who's watching?": more than one profile and none picked yet this session. */
    val showPicker: Boolean = false,
    /** The picker was opened from inside the app, so it may be closed without choosing. */
    val canClose: Boolean = false
)

/** UI actions for the profile picker; tests pass a no-op object. */
interface ProfileActions {
    fun select(profile: Profile) {}
    suspend fun verifyPin(profile: Profile, pin: String): Boolean = true
    fun create(name: String, colorIndex: Int, pin: String?) {}
    fun update(profile: Profile, name: String, colorIndex: Int) {}
    fun setPin(profile: Profile, pin: String?) {}
    fun delete(profile: Profile) {}
    fun close() {}
}

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel(), ProfileActions {

    private val ready = MutableStateFlow(false)

    val uiState: StateFlow<ProfilesUiState> = combine(
        repository.profiles,
        repository.activeProfileId,
        repository.sessionConfirmed,
        repository.pickedThisSession,
        ready
    ) { profiles, activeId, confirmed, picked, isReady ->
        ProfilesUiState(
            ready = isReady,
            profiles = profiles,
            activeProfileId = activeId,
            showPicker = isReady && !confirmed && profiles.isNotEmpty(),
            canClose = picked
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfilesUiState())

    init {
        viewModelScope.launch {
            repository.ensureProfileExists()
            ready.value = true
        }
    }

    override fun select(profile: Profile) = repository.select(profile.id)

    override suspend fun verifyPin(profile: Profile, pin: String): Boolean = repository.verifyPin(profile.id, pin)

    override fun create(name: String, colorIndex: Int, pin: String?) {
        viewModelScope.launch { repository.create(name, colorIndex, pin) }
    }

    override fun update(profile: Profile, name: String, colorIndex: Int) {
        viewModelScope.launch { repository.update(profile.id, name, colorIndex) }
    }

    override fun setPin(profile: Profile, pin: String?) {
        viewModelScope.launch { repository.setPin(profile.id, pin) }
    }

    override fun delete(profile: Profile) {
        viewModelScope.launch { repository.delete(profile.id) }
    }

    /** Leave the picker keeping the current profile (only offered when opened on purpose). */
    override fun close() = repository.select(repository.currentProfileId())
}
