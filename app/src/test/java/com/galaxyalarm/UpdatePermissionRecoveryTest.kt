package com.galaxyalarm

import android.content.pm.PackageInstaller
import com.galaxyalarm.update.fullScreenPermissionStateForUpdate
import com.galaxyalarm.update.shouldPromptForFullScreenRecovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePermissionRecoveryTest {
    @Test
    fun updateSessionExplicitlyGrantsFullScreenPermissionOnAndroid14AndLater() {
        assertNull(fullScreenPermissionStateForUpdate(33))
        assertEquals(
            PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED,
            fullScreenPermissionStateForUpdate(34),
        )
        assertEquals(
            PackageInstaller.SessionParams.PERMISSION_STATE_GRANTED,
            fullScreenPermissionStateForUpdate(36),
        )
    }

    @Test
    fun opensSettingsOnceWhenAnUpdateLostTheExpectedPermission() {
        assertTrue(
            shouldPromptForFullScreenRecovery(
                sdkInt = 36,
                expected = true,
                currentlyAllowed = false,
                handledVersion = 57,
                currentVersion = 58,
            )
        )
        assertFalse(
            shouldPromptForFullScreenRecovery(
                sdkInt = 36,
                expected = true,
                currentlyAllowed = false,
                handledVersion = 58,
                currentVersion = 58,
            )
        )
        assertFalse(
            shouldPromptForFullScreenRecovery(
                sdkInt = 36,
                expected = true,
                currentlyAllowed = true,
                handledVersion = 57,
                currentVersion = 58,
            )
        )
    }
}
