package com.argon.stream

import com.argon.stream.core.update.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun `versions are compared number by number`() {
        assertTrue(UpdateChecker.isNewer("1.7.0", "1.6.1"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.6.1", "1.6.1"))
        assertFalse(UpdateChecker.isNewer("1.6.0", "1.6.1"))
        assertFalse(UpdateChecker.isNewer("1.6.1-beta", "1.6.1"))
    }
}
