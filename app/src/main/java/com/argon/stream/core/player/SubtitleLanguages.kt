package com.argon.stream.core.player

import java.util.Locale

/**
 * Subtitle language handling. Albanian and English are always ranked first (after the user's own
 * preferred language), and the many code styles subtitle sources use ("alb", "sqi", "eng",
 * "Albanian", "shqip") are normalized to two-letter codes ("sq", "en").
 */
object SubtitleLanguages {
    const val ALBANIAN = "sq"
    const val ENGLISH = "en"

    /** Languages tried in this order: the user's choice, then Albanian, then English. */
    fun priorityOrder(preferred: String?): List<String> =
        listOfNotNull(preferred?.let { normalize(it) }?.takeIf { it != "und" }, ALBANIAN, ENGLISH).distinct()

    /** Lower is better; languages outside the priority list share the same rank after it. */
    fun rank(code: String?, preferred: String?): Int {
        val order = priorityOrder(preferred)
        val index = order.indexOf(normalize(code))
        return if (index >= 0) index else order.size
    }

    fun normalize(code: String?): String {
        val raw = code?.trim()?.lowercase(Locale.US).orEmpty()
        if (raw.isEmpty()) return "und"
        val base = raw.substringBefore('-').substringBefore('_')
        NAME_OR_BIBLIOGRAPHIC[base]?.let { return it }
        if (base.length == 2) return base
        if (base.length == 3) ISO3_TO_ISO1[base]?.let { return it }
        return base
    }

    fun displayName(code: String?): String {
        val normalized = normalize(code)
        if (normalized == "und") return "Unknown"
        val name = Locale.forLanguageTag(normalized).getDisplayLanguage(Locale.ENGLISH)
        return name.takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) } ?: normalized.uppercase(Locale.US)
    }

    private val NAME_OR_BIBLIOGRAPHIC = mapOf(
        // Albanian / English spelled every way subtitle sites use
        "alb" to "sq", "sqi" to "sq", "albanian" to "sq", "shqip" to "sq",
        "eng" to "en", "english" to "en",
        // ISO 639-2 bibliographic codes (OpenSubtitles uses these) that differ from Java's terminology codes
        "ger" to "de", "fre" to "fr", "dut" to "nl", "chi" to "zh", "zht" to "zh", "zho" to "zh", "cze" to "cs",
        "gre" to "el", "rum" to "ro", "per" to "fa", "slo" to "sk", "arm" to "hy", "baq" to "eu", "geo" to "ka",
        "ice" to "is", "mac" to "mk", "may" to "ms", "bur" to "my", "wel" to "cy", "tib" to "bo",
        "pob" to "pt", "spl" to "es", "scc" to "sr", "scr" to "hr"
    )

    private val ISO3_TO_ISO1: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { iso1 ->
            runCatching { Locale(iso1).isO3Language to iso1 }.getOrNull()
        }.toMap()
    }
}
