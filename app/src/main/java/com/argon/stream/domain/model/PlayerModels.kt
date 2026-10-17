package com.argon.stream.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalSubtitleTrack(
    val id: String,
    val url: String,
    val languageCode: String,
    val label: String,
    val addonName: String,
    val mimeType: String // "application/x-subrip" or "text/vtt"
)

@Serializable
data class SelectableTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val id: String,
    val label: String,
    val language: String,
    val isSelected: Boolean,
    val isExternal: Boolean = false
)

@Serializable
enum class ExternalPlayerTarget(
    val displayName: String,
    val packageName: String?
) {
    INTERNAL_EXOPLAYER("Internal (Media3 ExoPlayer)", null),
    VLC("VLC for Android", "org.videolan.vlc"),
    JUST_PLAYER("Just (Video) Player", "com.brouken.player"),
    MX_PLAYER_PRO("MX Player Pro", "com.mxtech.videoplayer.pro"),
    MX_PLAYER_FREE("MX Player", "com.mxtech.videoplayer.ad"),
    SYSTEM_CHOOSER("System App Chooser", "")
}

@Serializable
enum class SubtitleScalePreset(val label: String, val fontSizeSp: Float) {
    SMALL("Small", 16f),
    NORMAL("Normal", 20f),
    LARGE("Large", 25f),
    EXTRA_LARGE("Extra Large", 30f)
}

@Serializable
data class PlaybackDiagnosticError(
    val category: String, // "NETWORK", "SOURCE_HTTP", "UNSUPPORTED_FORMAT_DECODER", "PLAYER_INTERNAL"
    val userSummary: String,
    val technicalDetail: String
)