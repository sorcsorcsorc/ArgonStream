package com.argon.stream.data.remote.addon

import com.argon.stream.domain.model.StreamCandidate
import java.util.Locale

object StreamMetadataParser {

    private val INFO_HASH_REGEX = Regex("(?i)\\b([a-f0-9]{40})\\b")
    private val SEEDERS_REGEX = Regex("(?i)(?:👤|seeders?:?)\\s*(\\d+)")
    private val SIZE_REGEX = Regex("(?i)(?:💾|size:?)?\\s*(\\d+(?:\\.\\d+)?)\\s*(GB|MB)")

    fun toCandidate(
        addonId: String,
        addonName: String,
        dto: StremioStreamDto,
        index: Int
    ): StreamCandidate? {
        val directUrl = dto.url?.takeIf { it.isNotBlank() }
        val extractedHash = (dto.infoHash ?: extractHashFromText(dto.url) ?: extractHashFromText(dto.sources?.joinToString(" ")))
            ?.lowercase(Locale.US)

        if (directUrl == null && extractedHash == null) return null

        val combinedText = listOfNotNull(
            dto.name,
            dto.title,
            dto.description,
            dto.behaviorHints?.filename
        ).joinToString(" \n ")

        val resolution = detectResolution(combinedText)
        val codec = detectCodec(combinedText)
        val hdr = detectHdr(combinedText)
        val audio = detectAudio(combinedText)
        val rawDisplay = parseSizeDisplay(combinedText)
        val sizeBytes = dto.behaviorHints?.videoSize ?: parseSizeBytes(combinedText)
        val sizeDisplay = rawDisplay ?: sizeBytes?.let { formatBytes(it) }
        val seeders = SEEDERS_REGEX.find(combinedText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val isCached = isMarkedCached(combinedText)

        val headers = dto.behaviorHints?.proxyHeaders?.request.orEmpty()

        return StreamCandidate(
            uniqueKey = "${addonId}_${index}_${extractedHash ?: directUrl.hashCode()}",
            addonId = addonId,
            addonName = addonName,
            title = (dto.title ?: dto.description ?: dto.behaviorHints?.filename ?: dto.name ?: "Stream #${index + 1}").trim(),
            directUrl = directUrl,
            infoHash = extractedHash,
            fileIdx = dto.fileIdx,
            resolution = resolution,
            codec = codec,
            hdrTag = hdr,
            audioTag = audio,
            sizeBytes = sizeBytes,
            sizeDisplay = sizeDisplay,
            seeders = seeders,
            languages = detectLanguages(combinedText),
            requestHeaders = headers,
            filenameHint = dto.behaviorHints?.filename,
            bingeGroup = dto.behaviorHints?.bingeGroup,
            isCachedOnDebrid = isCached
        )
    }

    fun resolutionScore(resolution: String): Int = when (resolution) {
        "2160p" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p" -> 1
        else -> 0
    }

    /**
     * Debrid add-ons mark instantly-playable results with "⚡", "[TB+]"/"[RD+]" style tags or the word
     * "cached". Text like "uncached" / "not cached" must not count.
     */
    fun isMarkedCached(text: String): Boolean {
        if (UNCACHED_REGEX.containsMatchIn(text)) return false
        return text.contains("⚡") ||
            DEBRID_PLUS_TAG_REGEX.containsMatchIn(text) ||
            CACHED_WORD_REGEX.containsMatchIn(text)
    }

    private val UNCACHED_REGEX = Regex("(?i)\\b(uncached|not\\s+cached)\\b|⏳")
    private val DEBRID_PLUS_TAG_REGEX = Regex("(?i)\\[(TB|RD|AD|PM|DL|ED|OC|PKP)\\+]")
    private val CACHED_WORD_REGEX = Regex("(?i)\\bcached\\b")

    private fun extractHashFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return INFO_HASH_REGEX.find(text)?.groupValues?.getOrNull(1)
    }

    private fun detectResolution(text: String): String = when {
        Regex("(?i)\\b(2160p|4k|uhd)\\b").containsMatchIn(text) -> "2160p"
        Regex("(?i)\\b1080p\\b").containsMatchIn(text) -> "1080p"
        Regex("(?i)\\b720p\\b").containsMatchIn(text) -> "720p"
        Regex("(?i)\\b480p\\b").containsMatchIn(text) -> "480p"
        else -> "Auto"
    }

    private fun detectCodec(text: String): String? = when {
        Regex("(?i)\\b(hevc|x265|h\\.?265)\\b").containsMatchIn(text) -> "HEVC"
        Regex("(?i)\\b(av1)\\b").containsMatchIn(text) -> "AV1"
        Regex("(?i)\\b(avc|x264|h\\.?264)\\b").containsMatchIn(text) -> "H.264"
        else -> null
    }

    private fun detectHdr(text: String): String? = when {
        Regex("(?i)\\b(dv|dolby\\s*vision)\\b").containsMatchIn(text) -> "Dolby Vision"
        Regex("(?i)\\bhdr10\\+\\b").containsMatchIn(text) -> "HDR10+"
        Regex("(?i)\\bhdr\\b").containsMatchIn(text) -> "HDR"
        else -> null
    }

    private fun detectAudio(text: String): String? = when {
        Regex("(?i)\\batmos\\b").containsMatchIn(text) -> "Atmos"
        Regex("(?i)\\btruehd\\b").containsMatchIn(text) -> "TrueHD"
        Regex("(?i)\\b(dd\\+|eac3|ddp)\\b").containsMatchIn(text) -> "EAC3"
        Regex("(?i)\\b(dts-hd|dts)\\b").containsMatchIn(text) -> "DTS"
        Regex("(?i)\\b(5\\.1|7\\.1)\\b").containsMatchIn(text) -> "Surround"
        else -> null
    }

    private fun detectLanguages(text: String): List<String> = buildList {
        if (Regex("(?i)\\b(multi|dual)\\b").containsMatchIn(text)) add("Multi")
        if (Regex("(?i)\\b(eng|english)\\b").containsMatchIn(text)) add("EN")
        if (Regex("(?i)\\b(ita|italian)\\b").containsMatchIn(text)) add("IT")
        if (Regex("(?i)\\b(spa|spanish|latino)\\b").containsMatchIn(text)) add("ES")
        if (Regex("(?i)\\b(fre|french|vff)\\b").containsMatchIn(text)) add("FR")
        if (Regex("(?i)\\b(ger|german)\\b").containsMatchIn(text)) add("DE")
    }

    private fun parseSizeBytes(text: String): Long? {
        val match = SIZE_REGEX.find(text) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase(Locale.US)
        return if (unit == "GB") (amount * 1024.0 * 1024.0 * 1024.0).toLong()
        else (amount * 1024.0 * 1024.0).toLong()
    }

    private fun parseSizeDisplay(text: String): String? {
        val match = SIZE_REGEX.find(text) ?: return null
        return "${match.groupValues[1]} ${match.groupValues[2].uppercase(Locale.US)}"
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) {
            val formatted = String.format(Locale.US, "%.2f", gb).trimEnd('0').trimEnd('.')
            "$formatted GB"
        } else {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}