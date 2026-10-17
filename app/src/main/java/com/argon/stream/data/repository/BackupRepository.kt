package com.argon.stream.data.repository

import com.argon.stream.data.local.UserPreferencesRepository
import com.argon.stream.domain.model.ArgonPreferences
import com.argon.stream.domain.model.InstalledAddon
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Settings + add-ons. API keys are deliberately left out; they stay in the device keystore. */
@Serializable
data class ArgonFullBackup(
    val version: Int = 2,
    val preferences: ArgonPreferences,
    val argonAddons: List<InstalledAddon>
)

data class RestoreResult(
    val settingsRestored: Boolean,
    val addonsInstalled: Int,
    val addonErrors: List<String>
)

@Singleton
class BackupRepository @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val addonRepository: AddonRepository,
    private val json: Json
) {
    suspend fun exportJson(): String = json.encodeToString(
        ArgonFullBackup(
            preferences = preferencesRepository.preferencesFlow.first(),
            argonAddons = addonRepository.observeAddons().first()
        )
    )

    /** Accepts a full backup or an add-ons-only backup from the Add-ons screen. */
    suspend fun restore(rawJson: String): RestoreResult? {
        val root = runCatching { json.parseToJsonElement(rawJson.trim()) as? JsonObject }.getOrNull() ?: return null
        if (!root.containsKey("preferences") && !root.containsKey("argonAddons")) return null

        val settingsRestored = root["preferences"]?.let { preferencesRepository.importSettingsJson(it.toString()) } ?: false

        val preview = addonRepository.previewConfigImport(rawJson)
        val (installed, errors) = if (preview.discoveredManifestUrls.isNotEmpty()) {
            addonRepository.applyConfigImport(preview)
        } else {
            0 to emptyList()
        }
        // Add-ons that are already installed are not a failure when restoring.
        val realErrors = errors.filterNot { it.contains("Duplicate add-on", ignoreCase = true) }
        return RestoreResult(settingsRestored, installed, realErrors)
    }
}
