package com.argon.stream

import com.argon.stream.core.player.SubtitleLanguages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleLanguagesTest {

    @Test
    fun `albanian and english codes from every source are normalized`() {
        listOf("alb", "sqi", "sq", "SQ", "Albanian", "shqip").forEach { assertEquals(it, "sq", SubtitleLanguages.normalize(it)) }
        listOf("eng", "en", "English", "en-US").forEach { assertEquals(it, "en", SubtitleLanguages.normalize(it)) }
        assertEquals("de", SubtitleLanguages.normalize("ger"))
        assertEquals("fr", SubtitleLanguages.normalize("fra"))
    }

    @Test
    fun `albanian ranks first, then english, then everything else`() {
        val langs = listOf("ger", "eng", "spa", "alb", "fre")
        val sorted = langs.sortedBy { SubtitleLanguages.rank(it, preferred = "sq") }
        assertEquals(listOf("alb", "eng"), sorted.take(2))
        assertEquals("Albanian", SubtitleLanguages.displayName("alb"))
    }

    @Test
    fun `a different preferred language still keeps albanian and english right after it`() {
        assertEquals(listOf("it", "sq", "en"), SubtitleLanguages.priorityOrder("ita"))
        assertTrue(SubtitleLanguages.rank("alb", "it") < SubtitleLanguages.rank("ger", "it"))
    }
}
