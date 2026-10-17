package com.argon.stream.ui.screens.addons

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.argon.stream.core.network.NetworkResult
import com.argon.stream.data.repository.AddonRepository
import com.argon.stream.domain.model.ConfigImportPreview
import com.argon.stream.domain.model.InstalledAddon
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

data class AddonManagerUiState(
    val addons: List<InstalledAddon> = emptyList(),
    val manifestUrlInput: String = "",
    val jsonImportInput: String = "",
    val isInstalling: Boolean = false,
    val testingAddonId: String? = null,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val importPreview: ConfigImportPreview? = null
)

@HiltViewModel
class AddonManagerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val addonRepository: AddonRepository
) : ViewModel(), AddonManagerActions {

    private val _uiState = MutableStateFlow(AddonManagerUiState())
    val uiState: StateFlow<AddonManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            addonRepository.ensureDefaultAddonsSeeded()
            addonRepository.observeAddons().collect { addons ->
                _uiState.update { it.copy(addons = addons) }
            }
        }
    }

    override fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun showStatus(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(statusMessage = message, isError = isError) }
    }

    override fun onManifestUrlChange(url: String) {
        _uiState.update { it.copy(manifestUrlInput = url) }
    }

    override fun onJsonInputChange(jsonText: String) {
        val preview = if (jsonText.isNotBlank()) addonRepository.previewConfigImport(jsonText) else null
        _uiState.update { it.copy(jsonImportInput = jsonText, importPreview = preview) }
    }

    override fun loadJsonFromUri(uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                showStatus("Couldn't read that file.", isError = true)
            } else {
                onJsonInputChange(text)
            }
        }
    }

    override fun installFromUrl() {
        val url = _uiState.value.manifestUrlInput.trim()
        if (url.isBlank() || _uiState.value.isInstalling) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInstalling = true, statusMessage = null) }
            when (val res = addonRepository.installAddonFromUrl(url)) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(isInstalling = false, manifestUrlInput = "") }
                    showStatus("Installed ${res.data.name} (v${res.data.version})")
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isInstalling = false) }
                    showStatus(res.userMessage, isError = true)
                }
            }
        }
    }

    override fun applyPreviewedImport() {
        val preview = _uiState.value.importPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isInstalling = true) }
            val (installed, errors) = addonRepository.applyConfigImport(preview)
            _uiState.update { it.copy(isInstalling = false, importPreview = null, jsonImportInput = "") }
            if (errors.isEmpty()) {
                showStatus("Imported $installed add-on(s).")
            } else {
                showStatus("Imported $installed add-on(s). ${errors.size} failed: ${errors.first()}", isError = true)
            }
        }
    }

    override fun toggleEnabled(addon: InstalledAddon, enabled: Boolean) {
        viewModelScope.launch { addonRepository.setAddonEnabled(addon, enabled) }
    }

    override fun moveAddon(addonId: String, moveUp: Boolean) {
        viewModelScope.launch { addonRepository.moveAddonPriority(addonId, moveUp) }
    }

    override fun testAddonConnection(addon: InstalledAddon) {
        viewModelScope.launch {
            _uiState.update { it.copy(testingAddonId = addon.id) }
            val status = addonRepository.checkAddonHealth(addon)
            _uiState.update { it.copy(testingAddonId = null) }
            showStatus("${addon.name}: $status", isError = status.startsWith("ERROR"))
        }
    }

    override fun removeAddon(addon: InstalledAddon) {
        viewModelScope.launch {
            addonRepository.removeAddon(addon.id)
            showStatus("Removed ${addon.name}.")
        }
    }

    override fun writeBackupTo(uri: Uri) {
        viewModelScope.launch {
            val backup = addonRepository.exportBackupJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(backup.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            if (ok) showStatus("Backup saved. Import it later to restore your add-ons.")
            else showStatus("Couldn't save the backup file.", isError = true)
        }
    }
}
