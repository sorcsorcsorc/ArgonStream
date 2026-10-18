package com.argon.stream

import com.argon.stream.core.player.DeviceCaps
import com.argon.stream.core.player.StreamCompatibility
import com.argon.stream.domain.model.StreamCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCompatibilityTest {

    private fun stream(resolution: String, codec: String?, hdr: String? = null, audio: String? = null) = StreamCandidate(
        uniqueKey = "k", addonId = "a", addonName = "A", title = "t",
        resolution = resolution, codec = codec, hdrTag = hdr, audioTag = audio
    )

    @Test
    fun `fully capable device has no warnings`() {
        assertNull(StreamCompatibility.warning(stream("2160p", "HEVC", "Dolby Vision", "TrueHD"), DeviceCaps()))
    }

    @Test
    fun `4K HEVC on a 1080p-only decoder is flagged`() {
        val warning = StreamCompatibility.warning(stream("2160p", "HEVC"), DeviceCaps(hevc4k = false))
        assertEquals("4K HEVC is beyond this device's decoder", warning)
        assertNull(StreamCompatibility.warning(stream("1080p", "HEVC"), DeviceCaps(hevc4k = false)))
    }

    @Test
    fun `missing hevc, software-only av1 and silent audio are all reported`() {
        assertTrue(StreamCompatibility.warning(stream("1080p", "HEVC"), DeviceCaps(hevc = false))!!.contains("can't decode HEVC"))
        assertTrue(StreamCompatibility.warning(stream("1080p", "AV1"), DeviceCaps(av1Hardware = false))!!.contains("AV1"))
        val combined = StreamCompatibility.warning(stream("1080p", "H.264", "Dolby Vision", "DTS"), DeviceCaps(dolbyVision = false, dts = false))!!
        assertTrue(combined.contains("Dolby Vision") && combined.contains("DTS"))
    }
}
