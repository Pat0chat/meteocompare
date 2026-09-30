package com.meteocompare.app.ui.settings

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionFlowTest {
    @Test
    fun `android 13 plus demande la permission avant activation`() {
        assertTrue(
            shouldRequestNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = false
            )
        )
    }

    @Test
    fun `permission deja accordee ne redemande rien`() {
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                permissionGranted = true
            )
        )
    }

    @Test
    fun `avant android 13 aucune permission runtime nest necessaire`() {
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = Build.VERSION_CODES.S_V2,
                permissionGranted = false
            )
        )
    }
}
