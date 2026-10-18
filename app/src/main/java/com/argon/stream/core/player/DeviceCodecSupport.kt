package com.argon.stream.core.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.argon.stream.domain.model.StreamCandidate
import javax.inject.Inject
import javax.inject.Singleton

/** What this device can decode, read once from Android's codec list. */
data class DeviceCaps(
    val hevc: Boolean = true,
    val hevc4k: Boolean = true,
    val avc4k: Boolean = true,
    val av1Hardware: Boolean = true,
    val dolbyVision: Boolean = true,
    val dts: Boolean = true,
    val trueHd: Boolean = true,
    val eac3: Boolean = true
)

/**
 * Explains why a stream may not play well on this device (unsupported video codec, 4K beyond the
 * decoder, Dolby Vision without support, or audio formats phones usually can't decode).
 * Pure logic so it can be unit-tested with any [DeviceCaps].
 */
object StreamCompatibility {
    fun warning(stream: StreamCandidate, caps: DeviceCaps): String? {
        val is4k = stream.resolution == "2160p"
        val reasons = buildList {
            when (stream.codec) {
                "AV1" -> if (!caps.av1Hardware) add("AV1 video isn't hardware-decoded here and may stutter")
                "HEVC" -> when {
                    !caps.hevc -> add("this device can't decode HEVC video")
                    is4k && !caps.hevc4k -> add("4K HEVC is beyond this device's decoder")
                }
                else -> if (is4k && !caps.avc4k && stream.codec == "H.264") add("4K H.264 is beyond this device's decoder")
            }
            if (stream.hdrTag == "Dolby Vision" && !caps.dolbyVision) add("Dolby Vision may show wrong colours")
            when (stream.audioTag) {
                "DTS" -> if (!caps.dts) add("DTS audio may be silent")
                "TrueHD" -> if (!caps.trueHd) add("TrueHD audio may be silent")
                "EAC3" -> if (!caps.eac3) add("Dolby Digital Plus audio may be silent")
            }
        }
        return reasons.takeIf { it.isNotEmpty() }?.joinToString("; ")?.replaceFirstChar { it.uppercase() }
    }
}

@Singleton
class DeviceCodecSupport @Inject constructor() {

    val caps: DeviceCaps by lazy { runCatching { readCaps() }.getOrDefault(DeviceCaps()) }

    private fun readCaps(): DeviceCaps {
        val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }

        fun decodersFor(mime: String) = decoders.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }

        fun MediaCodecInfo.isHardware(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) isHardwareAccelerated
            else !name.startsWith("OMX.google.", ignoreCase = true) && !name.startsWith("c2.android.", ignoreCase = true)

        fun supports4k(mime: String) = decodersFor(mime).any { info ->
            runCatching { info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(3840, 2160) == true }.getOrDefault(false)
        }

        return DeviceCaps(
            hevc = decodersFor("video/hevc").isNotEmpty(),
            hevc4k = supports4k("video/hevc"),
            avc4k = supports4k("video/avc"),
            av1Hardware = decodersFor("video/av01").any { it.isHardware() },
            dolbyVision = decodersFor("video/dolby-vision").isNotEmpty(),
            dts = decodersFor("audio/vnd.dts").isNotEmpty() || decodersFor("audio/vnd.dts.hd").isNotEmpty(),
            trueHd = decodersFor("audio/true-hd").isNotEmpty(),
            eac3 = decodersFor("audio/eac3").isNotEmpty()
        )
    }
}
