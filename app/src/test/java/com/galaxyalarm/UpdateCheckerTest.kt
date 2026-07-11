package com.galaxyalarm

import com.galaxyalarm.update.isVersionNewer
import com.galaxyalarm.update.releaseTagFromUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun newerPatchVersionIsDetected() {
        assertTrue(isVersionNewer("2.1.1", "v2.1.2"))
    }

    @Test
    fun equalVersionIsNotNewer() {
        assertFalse(isVersionNewer("2.1.2", "v2.1.2"))
    }

    @Test
    fun olderReleaseIsNotNewer() {
        assertFalse(isVersionNewer("2.2.0", "v2.1.9"))
    }

    @Test
    fun tagIsReadFromLatestReleaseRedirect() {
        assertEquals(
            "v2.1.2",
            releaseTagFromUrl("https://github.com/masakasakasama/Alarm/releases/tag/v2.1.2"),
        )
    }

    @Test
    fun unrelatedUrlHasNoReleaseTag() {
        assertNull(releaseTagFromUrl("https://github.com/masakasakasama/Alarm/releases/latest"))
    }
}
