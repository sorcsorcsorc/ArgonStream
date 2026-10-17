package com.argon.stream.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.argon.stream.core.network.DiagnosticLogEntry
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.network.RedactingLoggingInterceptor
import com.argon.stream.core.player.ExternalPlayerLauncher
import com.argon.stream.core.security.SecretVault
import com.argon.stream.data.local.AddonDao
import com.argon.stream.data.local.UserPreferencesRepository
import com.argon.stream.data.local.WatchHistoryDao
import com.argon.stream.data.local.WatchlistDao
import com.argon.stream.data.repository.CatalogRepository
import com.argon.stream.data.repository.TorBoxRepository
import com.argon.stream.domain.model.ArgonPreferences
import com.argon.stream.domain.model.ExternalPlayerTarget
import com.argon.stream.domain.model.TorBoxAccountInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val preferences: ArgonPreferences = ArgonPreferences(),
    val tmdbKeyInput: String = "",
    val isTmdbSaved: Boolean = false,
    val isSavingTmdb: Boolean = false,
    val torBoxKeyInput: String = "",
    val isTorBoxSaved: Boolean = false,
    val isTestingTorBox: Boolean = false,
    val torBoxAccount: TorBoxAccountInfo? = null,
    val availablePlayers: List<ExternalPlayerTarget> = emptyList(),
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val recentDiagnostics: List<DiagnosticLogEntry> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val torBoxRepository: TorBoxRepository,
    private val catalogRepository: CatalogRepository,
    private val watchHistoryDao: WatchHistoryDao,
    private val watchlistDao: WatchlistDao,
    private val addonDao: AddonDao,
    private val secretVault: SecretVault,
    private val loggingInterceptor: RedactingLoggingInterceptor,
    externalPlayerLauncher: ExternalPlayerLauncher
) : ViewModel(), SettingsActions {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isTmdbSaved = catalogRepository.hasTmdbCredentials(),
            isTorBoxSaved = torBoxRepository.hasApiKey(),
            availablePlayers = externalPlayerLauncher.getInstalledExternalPlayers(),
            recentDiagnostics = loggingInterceptor.getRecentLogs()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
        if (torBoxRepository.hasApiKey()) {
            testTorBoxConnection(showStatus = false)
        }
    }

    override fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun showStatus(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(statusMessage = message, isError = isError) }
    }

    // region TMDB

    override fun onTmdbKeyChange(value: String) {
        _uiState.update { it.copy(tmdbKeyInput = value) }
    }

    override fun saveTmdbKey() {
        val key = _uiState.value.tmdbKeyInput.trim()
        if (key.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingTmdb = true) }
            when (val res = catalogRepository.validateTmdbApiKey(key)) {
                is NetworkResult.Success -> {
                    catalogRepository.saveTmdbApiKey(key)
                    _uiState.update { it.copy(isSavingTmdb = false, tmdbKeyInput = "", isTmdbSaved = true) }
                    showStatus("TMDB key verified and saved. Catalogs now come from TMDB.")
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isSavingTmdb = false) }
                    val message = if (res.code == 401) {
                        "TMDB rejected this key. Paste the \"API Key\" or \"API Read Access Token\" from themoviedb.org/settings/api."
                    } else {
                        "Couldn't verify the TMDB key: ${res.userMessage}"
                    }
                    showStatus(message, isError = true)
                }
            }
            refreshDiagnostics()
        }
    }

    override fun removeTmdbKey() {
        catalogRepository.clearTmdbApiKey()
        _uiState.update { it.copy(isTmdbSaved = false, tmdbKeyInput = "") }
        showStatus("TMDB key removed. Catalogs now come from Cinemeta.")
    }

    // endregion

    // region TorBox

    override fun onTorBoxKeyChange(value: String) {
        _uiState.update { it.copy(torBoxKeyInput = value) }
    }

    override fun saveAndTestTorBoxKey() {
        val key = _uiState.value.torBoxKeyInput.trim()
        if (key.isEmpty()) return
        torBoxRepository.saveApiKey(key)
        _uiState.update { it.copy(torBoxKeyInput = "", isTorBoxSaved = true) }
        testTorBoxConnection(showStatus = true)
    }

    override fun testTorBoxConnection(showStatus: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingTorBox = true) }
            when (val res = torBoxRepository.verifyAccount()) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(isTestingTorBox = false, torBoxAccount = res.data) }
                    if (showStatus) showStatus("TorBox connected: ${res.data.email}")
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isTestingTorBox = false, torBoxAccount = null) }
                    if (showStatus) showStatus(res.userMessage, isError = true)
                }
            }
            refreshDiagnostics()
        }
    }

    override fun disconnectTorBox() {
        torBoxRepository.disconnectTorBox()
        _uiState.update { it.copy(isTorBoxSaved = false, torBoxAccount = null) }
        showStatus("TorBox key removed from this device.")
    }

    // endregion

    // region Preferences

    override fun setDefaultPlayer(target: ExternalPlayerTarget) {
        viewModelScope.launch { preferencesRepository.updateDefaultPlayer(target) }
    }

    override fun setSubtitlesDisabledByDefault(disabled: Boolean) {
        val prefs = _uiState.value.preferences
        viewModelScope.launch {
            preferencesRepository.updateLanguages(prefs.preferredAudioLanguage, prefs.preferredSubtitleLanguage, disabled)
        }
    }

    override fun setPreferredAudioLanguage(code: String) {
        val prefs = _uiState.value.preferences
        viewModelScope.launch {
            preferencesRepository.updateLanguages(code, prefs.preferredSubtitleLanguage, prefs.subtitlesDisabledByDefault)
        }
    }

    override fun setPreferredSubtitleLanguage(code: String) {
        val prefs = _uiState.value.preferences
        viewModelScope.launch {
            preferencesRepository.updateLanguages(prefs.preferredAudioLanguage, code, prefs.subtitlesDisabledByDefault)
        }
    }

    override fun setMaxResolution(resolution: String) {
        val prefs = _uiState.value.preferences
        viewModelScope.launch {
            preferencesRepository.updateStreamFilters(
                maxResolution = resolution,
                preferCached = prefs.preferCachedDebridStreams,
                excludedCodecsCsv = prefs.excludedCodecsCsv,
                autoPlayNext = prefs.autoPlayNextEpisode
            )
        }
    }

    override fun setPreferCached(preferCached: Boolean) {
        val prefs = _uiState.value.preferences
        viewModelScope.launch {
            preferencesRepository.updateStreamFilters(
                maxResolution = prefs.preferredMaxResolution,
                preferCached = preferCached,
                excludedCodecsCsv = prefs.excludedCodecsCsv,
                autoPlayNext = prefs.autoPlayNextEpisode
            )
        }
    }

    override fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateTheme(enabled, _uiState.value.preferences.dynamicColors) }
    }

    override fun setDynamicColors(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateTheme(_uiState.value.preferences.darkTheme, enabled) }
    }

    // endregion

    // region Data

    override fun clearImageCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val loader = SingletonImageLoader.get(appContext)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
            }
            showStatus("Image cache cleared.")
        }
    }

    override fun clearWatchHistory() {
        viewModelScope.launch {
            watchHistoryDao.clearAll()
            showStatus("Watch history and Continue Watching cleared.")
        }
    }

    override fun wipeAllDataAndDisconnect() {
        viewModelScope.launch {
            secretVault.clearAll()
            watchHistoryDao.clearAll()
            watchlistDao.clearAll()
            addonDao.clearAll()
            preferencesRepository.resetAllPreferences()
            loggingInterceptor.clearLogs()
            catalogRepository.clearTmdbApiKey()
            _uiState.update {
                it.copy(
                    isTmdbSaved = false,
                    isTorBoxSaved = false,
                    torBoxAccount = null,
                    recentDiagnostics = emptyList()
                )
            }
            showStatus("All local data, add-ons and keys were wiped.")
        }
    }

    override fun refreshDiagnostics() {
        _uiState.update { it.copy(recentDiagnostics = loggingInterceptor.getRecentLogs()) }
    }

    // endregion
}
