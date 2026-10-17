package com.argon.stream.core.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.argon.stream.domain.model.PlaybackDiagnosticError

object PlaybackErrorClassifier {

    fun classify(error: PlaybackException): PlaybackDiagnosticError {
        val cause = error.cause
        return when {
            cause is HttpDataSource.InvalidResponseCodeException -> {
                PlaybackDiagnosticError(
                    category = "SOURCE_HTTP",
                    userSummary = "Stream source returned HTTP ${cause.responseCode}. The link may have expired or hit provider rate limits.",
                    technicalDetail = "HTTP_${cause.responseCode} (${error.errorCodeName})"
                )
            }
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                PlaybackDiagnosticError(
                    category = "NETWORK",
                    userSummary = "Network connection interrupted or timed out while buffering stream.",
                    technicalDetail = error.errorCodeName
                )
            }
            error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                PlaybackDiagnosticError(
                    category = "UNSUPPORTED_FORMAT_DECODER",
                    userSummary = "Your device hardware decoder could not decode this video/audio track (e.g., Dolby Vision / TrueHD profile). Try switching audio track or opening in VLC / Just Player.",
                    technicalDetail = error.errorCodeName
                )
            }
            else -> {
                PlaybackDiagnosticError(
                    category = "PLAYER_INTERNAL",
                    userSummary = "Playback stopped due to a stream or player error.",
                    technicalDetail = "${error.errorCodeName}: ${error.message?.take(100)}"
                )
            }
        }
    }
}