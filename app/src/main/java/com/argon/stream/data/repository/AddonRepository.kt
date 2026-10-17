package com.argon.stream.data.repository

import com.argon.stream.core.network.NetworkResult
import com.argon.stream.core.security.SecretVault
import com.argon.stream.data.local.AddonDao
import com.argon.stream.data.local.AddonEntity
import com.argon.stream.data.remote.addon.AioStreamsConfigParser
import com.argon.stream.data.remote.addon.StreamMetadataParser
import com.argon.stream.data.remote.addon.StremioAddonClient
import com.argon.stream.domain.model.ConfigImportPreview
import com.argon.stream.domain.model.InstalledAddon
import com.argon.stream.domain.model.StreamCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ArgonBackupPayload(
    val version: Int = 1,
    val argonAddons: List<InstalledAddon>
)

@Singleton
class AddonRepository @Inject constructor(
    private val addonDao: AddonDao,
    private val addonClient: StremioAddonClient,
    private val aioParser: AioStreamsConfigParser,
    private val secretVault: SecretVault,
    private val json: Json
) {
    fun observeAddons(): Flow<List<InstalledAddon>> {
        return addonDao.observeAddons().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun ensureDefaultAddonsSeeded() {
        val current = addonDao.observeAddons().first()
        if (current.isNotEmpty()) return

        // Seeds Cinemeta (official Stremio catalog/metadata add-on). Stream add-ons are installed by the user.
        val cinemeta = AddonEntity(
            id = "com.linvo.cinemeta",
            name = "Cinemeta (Official Catalog)",
            version = "3.0.12",
            description = "Official Stremio metadata addon for IMDb movies and series.",
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            baseUrl = "https://v3-cinemeta.strem.io",
            resourcesCsv = "catalog,meta",
            typesCsv = "movie,series",
            idPrefixesCsv = "tt",
            isEnabled = true,
            priorityOrder = 0,
            lastHealthStatus = "VERIFIED",
            updatedAtMs = System.currentTimeMillis()
        )
        addonDao.upsertAddon(cinemeta)
    }

    suspend fun installAddonFromUrl(rawUrl: String): NetworkResult<InstalledAddon> {
        val normalized = addonClient.normalizeManifestUrl(rawUrl)
        val existingList = addonDao.observeAddons().first()

        if (existingList.any { it.manifestUrl.equals(normalized, ignoreCase = true) }) {
            return NetworkResult.Error(
                userMessage = "Duplicate add-on: an add-on with this manifest URL is already installed.",
                diagnosticReason = "DUPLICATE_MANIFEST_URL",
                isRetryable = false
            )
        }

        return when (val result = addonClient.fetchManifest(normalized)) {
            is NetworkResult.Success -> {
                val (finalUrl, dto) = result.data
                val baseUrl = addonClient.deriveBaseUrl(finalUrl)
                val resources = dto.extractResourceNames()

                if (resources.isEmpty()) {
                    return NetworkResult.Error(
                        userMessage = "Manifest at $finalUrl does not declare any supported Stremio resources.",
                        diagnosticReason = "EMPTY_RESOURCES",
                        isRetryable = false
                    )
                }

                // Store authenticated manifest URL inside Keystore vault if it contains credentials/tokens
                val vaultKey = "addon_url_${dto.id}"
                secretVault.putSecret(vaultKey, finalUrl)

                val entity = AddonEntity(
                    id = dto.id,
                    name = dto.name,
                    version = dto.version,
                    description = dto.description,
                    manifestUrl = finalUrl,
                    baseUrl = baseUrl,
                    resourcesCsv = resources.joinToString(","),
                    typesCsv = dto.types.joinToString(","),
                    idPrefixesCsv = dto.extractIdPrefixes().joinToString(","),
                    isEnabled = true,
                    priorityOrder = existingList.size,
                    lastHealthStatus = "OK (${resources.joinToString("/")})",
                    updatedAtMs = System.currentTimeMillis()
                )
                addonDao.upsertAddon(entity)
                NetworkResult.Success(entity.toDomain())
            }
            is NetworkResult.Error -> result
        }
    }

    fun previewConfigImport(rawJson: String): ConfigImportPreview {
        val hasTorBox = !secretVault.getSecret(SecretVault.KEY_TORBOX_API_KEY).isNullOrBlank()
        return aioParser.analyzeJson(rawJson, hasTorBox)
    }

    suspend fun applyConfigImport(preview: ConfigImportPreview): Pair<Int, List<String>> {
        var installedCount = 0
        val errors = mutableListOf<String>()
        preview.discoveredManifestUrls.forEach { url ->
            when (val res = installAddonFromUrl(url)) {
                is NetworkResult.Success -> installedCount++
                is NetworkResult.Error -> errors.add("$url: ${res.userMessage}")
            }
        }
        return installedCount to errors
    }

    suspend fun exportBackupJson(): String {
        val addons = observeAddons().first()
        return json.encodeToString(ArgonBackupPayload(argonAddons = addons))
    }

    suspend fun setAddonEnabled(addon: InstalledAddon, enabled: Boolean) {
        addonDao.upsertAddon(addon.copy(isEnabled = enabled).toEntity())
    }

    suspend fun moveAddonPriority(addonId: String, moveUp: Boolean) {
        val current = addonDao.observeAddons().first().sortedBy { it.priorityOrder }.toMutableList()
        val index = current.indexOfFirst { it.id == addonId }
        if (index == -1) return
        val targetIndex = if (moveUp) index - 1 else index + 1
        if (targetIndex !in current.indices) return

        val temp = current[index]
        current[index] = current[targetIndex]
        current[targetIndex] = temp

        current.forEachIndexed { idx, entity ->
            addonDao.upsertAddon(entity.copy(priorityOrder = idx))
        }
    }

    suspend fun checkAddonHealth(addon: InstalledAddon): String {
        return when (val res = addonClient.fetchManifest(addon.manifestUrl)) {
            is NetworkResult.Success -> {
                val updated = addon.copy(
                    version = res.data.second.version,
                    lastHealthStatus = "ONLINE • v${res.data.second.version}"
                )
                addonDao.upsertAddon(updated.toEntity())
                updated.lastHealthStatus
            }
            is NetworkResult.Error -> {
                val status = "ERROR: ${res.diagnosticReason}"
                addonDao.upsertAddon(addon.copy(lastHealthStatus = status).toEntity())
                status
            }
        }
    }

    suspend fun removeAddon(id: String) {
        secretVault.removeSecret("addon_url_$id")
        addonDao.deleteAddon(id)
    }

    suspend fun queryAllAddonStreams(
        stremioVideoId: String
    ): Pair<List<StreamCandidate>, List<String>> = coroutineScope {
        val mediaType = if (stremioVideoId.contains(":")) "series" else "movie"
        val enabledStreamAddons = observeAddons().first().filter { addon ->
            addon.isEnabled &&
                addon.resources.any { it.equals("stream", ignoreCase = true) } &&
                (addon.types.isEmpty() || addon.types.any { it.equals(mediaType, ignoreCase = true) }) &&
                (addon.idPrefixes.isEmpty() || addon.idPrefixes.any { stremioVideoId.startsWith(it) })
        }

        val providerWarnings = mutableListOf<String>()
        val deferredResults = enabledStreamAddons.map { addon ->
            async {
                when (val res = addonClient.fetchStreams(addon.baseUrl, mediaType, stremioVideoId)) {
                    is NetworkResult.Success -> {
                        res.data.streams.mapIndexedNotNull { idx, dto ->
                            StreamMetadataParser.toCandidate(addon.id, addon.name, dto, idx)
                        }
                    }
                    is NetworkResult.Error -> {
                        synchronized(providerWarnings) {
                            providerWarnings.add("${addon.name}: ${res.userMessage}")
                        }
                        emptyList()
                    }
                }
            }
        }

        val combined = deferredResults.awaitAll().flatten()
            .sortedWith(
                compareByDescending<StreamCandidate> { it.isCachedOnDebrid }
                    .thenByDescending { StreamMetadataParser.resolutionScore(it.resolution) }
                    .thenByDescending { it.seeders ?: 0 }
            )

        combined to providerWarnings
    }

    private fun AddonEntity.toDomain() = InstalledAddon(
        id = id,
        name = name,
        version = version,
        description = description,
        manifestUrl = manifestUrl,
        baseUrl = baseUrl,
        resources = resourcesCsv.split(",").filter { it.isNotBlank() },
        types = typesCsv.split(",").filter { it.isNotBlank() },
        idPrefixes = idPrefixesCsv.split(",").filter { it.isNotBlank() },
        isEnabled = isEnabled,
        priorityOrder = priorityOrder,
        lastHealthStatus = lastHealthStatus
    )

    private fun InstalledAddon.toEntity() = AddonEntity(
        id = id,
        name = name,
        version = version,
        description = description,
        manifestUrl = manifestUrl,
        baseUrl = baseUrl,
        resourcesCsv = resources.joinToString(","),
        typesCsv = types.joinToString(","),
        idPrefixesCsv = idPrefixes.joinToString(","),
        isEnabled = isEnabled,
        priorityOrder = priorityOrder,
        lastHealthStatus = lastHealthStatus,
        updatedAtMs = System.currentTimeMillis()
    )
}