package com.argon.stream

import com.argon.stream.data.remote.addon.AioStreamsConfigParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AioStreamsConfigParserTest {

    private val parser = AioStreamsConfigParser(
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    )

    @Test
    fun `analyzeJson extracts manifest URLs, required services, and server warnings from AIOStreams export`() {
        val sampleAioExport = """
            {
              "services": [
                { "id": "torbox", "enabled": true },
                { "id": "realdebrid", "enabled": true }
              ],
              "presets": [
                { "name": "Custom Addon", "manifestUrl": "https://v3-cinemeta.strem.io/manifest.json" },
                { "name": "ServerSideOnlyPreset", "options": { "useCache": true } }
              ],
              "filters": {
                "resolutions": ["2160p", "1080p"],
                "serverRegex": ".*"
              },
              "formatter": "custom"
            }
        """.trimIndent()

        val preview = parser.analyzeJson(sampleAioExport, hasTorBoxKey = true)

        assertEquals("AIOSTREAMS_EXPORT", preview.formatType)
        assertTrue(preview.isValid)
        assertEquals(listOf("https://v3-cinemeta.strem.io/manifest.json"), preview.discoveredManifestUrls)
        assertEquals(2, preview.requiredServices.size)
        assertEquals("2160p,1080p", preview.preservedSettings["filter.resolutions"])
        assertTrue(preview.unsupportedSettings.contains("formatter"))
        assertTrue(preview.unsupportedSettings.contains("filters.serverRegex"))
        assertTrue(preview.warnings.any { it.contains("1 AIOStreams preset(s)") })
    }
}