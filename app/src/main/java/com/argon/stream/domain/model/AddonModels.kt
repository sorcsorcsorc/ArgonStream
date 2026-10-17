package com.argon.stream.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InstalledAddon(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val manifestUrl: String,
    val baseUrl: String,
    val resources: List<String>,
    val types: List<String>,
    val idPrefixes: List<String>,
    val isEnabled: Boolean,
    val priorityOrder: Int,
    val lastHealthStatus: String
)

@Serializable
data class StreamCandidate(
    val uniqueKey: String,
    val addonId: String,
    val addonName: String,
    val title: String,
    val directUrl: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val resolution: String,
    val codec: String?,
    val hdrTag: String?,
    val audioTag: String?,
    val sizeBytes: Long? = null,
    val sizeDisplay: String? = null,
    val seeders: Int? = null,
    val languages: List<String> = emptyList(),
    val requestHeaders: Map<String, String> = emptyMap(),
    val filenameHint: String? = null,
    /** Stremio behaviorHints.bingeGroup: same value = same release/source across episodes. */
    val bingeGroup: String? = null,
    val isCachedOnDebrid: Boolean = false
)

@Serializable
data class ExternalServiceRequirement(
    val serviceId: String,
    val displayName: String,
    val credentialDescription: String,
    val isConfiguredInArgon: Boolean = false
)

@Serializable
data class ConfigImportPreview(
    val formatType: String, // "STREMIO_MANIFEST", "AIOSTREAMS_EXPORT", or "ARGON_BACKUP"
    val discoveredManifestUrls: List<String>,
    val requiredServices: List<ExternalServiceRequirement>,
    val preservedSettings: Map<String, String>,
    val unsupportedSettings: List<String>,
    val warnings: List<String>,
    val isValid: Boolean
)