package com.argon.stream.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ArgonPreferences(
    val darkTheme: Boolean = true,
    val dynamicColors: Boolean = false,
    val defaultPlayer: ExternalPlayerTarget = ExternalPlayerTarget.INTERNAL_EXOPLAYER,
    val preferredAudioLanguage: String = "en",
    val preferredSubtitleLanguage: String = "sq", // Albanian first, English is always the next fallback
    val subtitlesDisabledByDefault: Boolean = false,
    val autoPlayNextEpisode: Boolean = true,
    val preferredMaxResolution: String = "2160p", // "2160p", "1080p", "720p"
    val preferCachedDebridStreams: Boolean = true,
    val excludedCodecsCsv: String = "", // e.g., "AV1" if unsupported on older hardware
    val networkTimeoutSeconds: Int = 20,
    val imageCacheLimitMb: Int = 250
)