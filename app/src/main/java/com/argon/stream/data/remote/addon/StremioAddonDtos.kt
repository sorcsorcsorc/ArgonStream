package com.argon.stream.data.remote.addon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class StremioManifestDto(
    val id: String,
    val version: String = "1.0.0",
    val name: String,
    val description: String? = null,
    val types: List<String> = emptyList(),
    val resources: List<JsonElement> = emptyList(),
    val idPrefixes: List<String>? = null
) {
    /**
     * Stremio manifests define resources either as strings ("stream", "catalog")
     * or objects ({"name": "stream", "types": ["movie", "series"], "idPrefixes": ["tt"]}).
     */
    fun extractResourceNames(): List<String> {
        return resources.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> (element["name"] as? JsonPrimitive)?.contentOrNull
                else -> null
            }
        }.distinct()
    }

    fun extractIdPrefixes(): List<String> {
        val fromRoot = idPrefixes.orEmpty()
        val fromObjects = resources.mapNotNull { element ->
            (element as? JsonObject)?.get("idPrefixes")
        }.flatMap { elem ->
            (elem as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .orEmpty()
        }
        return (fromRoot + fromObjects).ifEmpty { listOf("tt") }.distinct()
    }
}

@Serializable
data class StremioStreamResponseDto(
    val streams: List<StremioStreamDto> = emptyList()
)

@Serializable
data class StremioStreamDto(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val sources: List<String>? = null,
    val behaviorHints: StremioBehaviorHintsDto? = null
)

@Serializable
data class StremioBehaviorHintsDto(
    val bingeGroup: String? = null,
    val filename: String? = null,
    val videoSize: Long? = null,
    val notWebReady: Boolean? = null,
    val proxyHeaders: StremioProxyHeadersDto? = null
)

@Serializable
data class StremioProxyHeadersDto(
    @SerialName("request") val request: Map<String, String>? = null
)