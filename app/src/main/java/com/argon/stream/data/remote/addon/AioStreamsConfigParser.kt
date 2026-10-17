package com.argon.stream.data.remote.addon

import com.argon.stream.domain.model.ConfigImportPreview
import com.argon.stream.domain.model.ExternalServiceRequirement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inspects and validates imported JSON configurations:
 * 1. Direct Stremio Manifest JSON (`id`, `version`, `resources`, `types`)
 * 2. AIOStreams Export JSON (`presets` / `addons`, `services`, `filters`, `formatter`)
 * 3. Argon Addon Backup JSON (`argonAddons`)
 *
 * Never fabricates compatibility: preserves supported settings, flags server-side
 * AIOStreams presets that lack direct manifest URLs, and lists required debrid credentials.
 */
@Singleton
class AioStreamsConfigParser @Inject constructor(
    private val json: Json
) {
    fun analyzeJson(rawJson: String, hasTorBoxKey: Boolean): ConfigImportPreview {
        val rootElement = runCatching { json.parseToJsonElement(rawJson.trim()) }.getOrElse {
            return ConfigImportPreview(
                formatType = "INVALID_JSON",
                discoveredManifestUrls = emptyList(),
                requiredServices = emptyList(),
                preservedSettings = emptyMap(),
                unsupportedSettings = emptyList(),
                warnings = listOf("Malformed JSON syntax: ${it.message?.take(120)}"),
                isValid = false
            )
        }

        val obj = rootElement as? JsonObject ?: return ConfigImportPreview(
            formatType = "UNSUPPORTED_ROOT",
            discoveredManifestUrls = emptyList(),
            requiredServices = emptyList(),
            preservedSettings = emptyMap(),
            unsupportedSettings = emptyList(),
            warnings = listOf("Expected a JSON object at root level."),
            isValid = false
        )

        // Case 1: Argon native backup
        if (obj.containsKey("argonAddons")) {
            val urls = (obj["argonAddons"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("manifestUrl") as? JsonPrimitive }
                ?.mapNotNull { it.contentOrNull }
                .orEmpty()

            return ConfigImportPreview(
                formatType = "ARGON_BACKUP",
                discoveredManifestUrls = urls.distinct(),
                requiredServices = emptyList(),
                preservedSettings = mapOf("addonCount" to urls.size.toString()),
                unsupportedSettings = emptyList(),
                warnings = if (urls.isEmpty()) listOf("Backup file contains 0 add-on URLs.") else emptyList(),
                isValid = urls.isNotEmpty()
            )
        }

        // Case 2: AIOStreams Export JSON (containspresets/services/filters/formatter/addons)
        if (obj.containsKey("presets") || obj.containsKey("services") || obj.containsKey("formatter") || obj.containsKey("addons")) {
            return parseAioStreamsObject(obj, hasTorBoxKey)
        }

        // Case 3: Direct Stremio Manifest JSON
        if (obj.containsKey("id") && obj.containsKey("resources")) {
            val transportUrl = (obj["transportUrl"] as? JsonPrimitive)?.contentOrNull
                ?: (obj["manifestUrl"] as? JsonPrimitive)?.contentOrNull
            val warnings = if (transportUrl == null) {
                listOf("Raw Stremio manifest JSON does not include its remote host URL. Paste the add-on's https://.../manifest.json URL instead.")
            } else emptyList()

            return ConfigImportPreview(
                formatType = "STREMIO_MANIFEST",
                discoveredManifestUrls = listOfNotNull(transportUrl),
                requiredServices = emptyList(),
                preservedSettings = mapOf(
                    "id" to ((obj["id"] as? JsonPrimitive)?.contentOrNull ?: "unknown"),
                    "name" to ((obj["name"] as? JsonPrimitive)?.contentOrNull ?: "unknown")
                ),
                unsupportedSettings = emptyList(),
                warnings = warnings,
                isValid = transportUrl != null
            )
        }

        return ConfigImportPreview(
            formatType = "UNKNOWN_SCHEMA",
            discoveredManifestUrls = emptyList(),
            requiredServices = emptyList(),
            preservedSettings = emptyMap(),
            unsupportedSettings = obj.keys.toList(),
            warnings = listOf("JSON does not match Stremio Manifest, AIOStreams Export, or Argon Backup schema."),
            isValid = false
        )
    }

    private fun parseAioStreamsObject(
        obj: JsonObject,
        hasTorBoxKey: Boolean
    ): ConfigImportPreview {
        val discoveredUrls = mutableListOf<String>()
        val preserved = mutableMapOf<String, String>()
        val unsupported = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val requiredServices = mutableListOf<ExternalServiceRequirement>()

        // Extract direct manifest URLs from `addons` or `presets`
        val addonsArray = (obj["addons"] as? JsonArray) ?: (obj["presets"] as? JsonArray)
        var serverSidePresetCount = 0

        addonsArray?.forEach { item ->
            val itemObj = item as? JsonObject ?: return@forEach
            val directUrl = (itemObj["manifestUrl"] as? JsonPrimitive)?.contentOrNull
                ?: (itemObj["url"] as? JsonPrimitive)?.contentOrNull
                ?: ((itemObj["options"] as? JsonObject)?.get("manifestUrl") as? JsonPrimitive)?.contentOrNull
                ?: ((itemObj["options"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull

            if (!directUrl.isNullOrBlank() && (directUrl.startsWith("http") || directUrl.startsWith("stremio://"))) {
                discoveredUrls.add(directUrl)
            } else {
                serverSidePresetCount++
            }
        }

        if (serverSidePresetCount > 0) {
            warnings.add(
                "$serverSidePresetCount AIOStreams preset(s) rely on an AIOStreams server instance to generate manifests dynamically. To use those presets directly, paste your hosted AIOStreams manifest.json URL."
            )
        }

        // Inspect external services (TorBox, Real-Debrid, AllDebrid, Premiumize, TMDB, RPDB)
        when (val servicesElem = obj["services"]) {
            is JsonArray -> {
                servicesElem.forEach { svc ->
                    val svcObj = svc as? JsonObject ?: return@forEach
                    val id = (svcObj["id"] as? JsonPrimitive)?.contentOrNull
                        ?: (svcObj["name"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
                    val enabled = (svcObj["enabled"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true
                    if (enabled) {
                        requiredServices.add(mapServiceRequirement(id, hasTorBoxKey))
                    }
                }
            }
            is JsonObject -> {
                servicesElem.keys.forEach { id ->
                    requiredServices.add(mapServiceRequirement(id, hasTorBoxKey))
                }
            }
            else -> Unit
        }

        // Preserve compatible filter/sorting settings & report unsupported server-only settings
        val filtersObj = obj["filters"] as? JsonObject
        filtersObj?.forEach { (k, v) ->
            when (k) {
                "resolutions", "qualities", "languages", "maxSize", "minSeeders" -> {
                    preserved["filter.$k"] = v.toCompactString()
                }
                else -> unsupported.add("filters.$k")
            }
        }

        obj.keys.forEach { key ->
            if (key !in setOf("addons", "presets", "services", "filters")) {
                unsupported.add(key)
            }
        }

        if (discoveredUrls.isEmpty()) {
            warnings.add(
                "This AIOStreams export contains server-side pipeline rules but 0 standalone add-on URLs. Export the final manifest URL from your AIOStreams instance or add individual manifest URLs."
            )
        }

        return ConfigImportPreview(
            formatType = "AIOSTREAMS_EXPORT",
            discoveredManifestUrls = discoveredUrls.distinct(),
            requiredServices = requiredServices.distinctBy { it.serviceId },
            preservedSettings = preserved,
            unsupportedSettings = unsupported.distinct(),
            warnings = warnings,
            isValid = discoveredUrls.isNotEmpty() || preserved.isNotEmpty()
        )
    }

    private fun mapServiceRequirement(serviceId: String, hasTorBoxKey: Boolean): ExternalServiceRequirement {
        return when (serviceId.lowercase()) {
            "torbox", "tb" -> ExternalServiceRequirement(
                serviceId = "torbox",
                displayName = "TorBox Debrid",
                credentialDescription = "Requires TorBox API token (Supported natively in Argon Phase 4).",
                isConfiguredInArgon = hasTorBoxKey
            )
            "realdebrid", "rd" -> ExternalServiceRequirement(
                serviceId = "realdebrid",
                displayName = "Real-Debrid",
                credentialDescription = "External debrid service referenced by add-on; requires Real-Debrid API token inside the add-on URL.",
                isConfiguredInArgon = false
            )
            "alldebrid", "ad" -> ExternalServiceRequirement(
                serviceId = "alldebrid",
                displayName = "AllDebrid",
                credentialDescription = "External debrid service referenced by add-on; requires AllDebrid API key inside the add-on URL.",
                isConfiguredInArgon = false
            )
            "tmdb" -> ExternalServiceRequirement(
                serviceId = "tmdb",
                displayName = "TMDB Metadata",
                credentialDescription = "Requires TMDB API v3 key in Argon Settings.",
                isConfiguredInArgon = false
            )
            else -> ExternalServiceRequirement(
                serviceId = serviceId,
                displayName = serviceId.uppercase(),
                credentialDescription = "External provider referenced in configuration; requires its own API credentials.",
                isConfiguredInArgon = false
            )
        }
    }

    private fun JsonElement.toCompactString(): String = when (this) {
        is JsonPrimitive -> contentOrNull ?: toString()
        is JsonArray -> joinToString(",") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
        else -> toString()
    }
}