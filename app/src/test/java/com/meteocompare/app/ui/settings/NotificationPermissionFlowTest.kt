package com.meteocompare.app.ui.settings

import android.os.Build
import com.meteocompare.app.R
import com.meteocompare.app.ui.components.AppToastType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    @Test
    fun `refus de permission avec activation en attente produit un toast explicite`() {
        val event = notificationPermissionFeedback(granted = false, hadPendingEnable = true)

        assertEquals(R.string.toast_notifications_permission_denied, event?.messageRes)
        assertEquals(AppToastType.WARNING, event?.type)
    }

    @Test
    fun `resultat permission sans activation en attente ne produit pas de toast`() {
        assertNull(notificationPermissionFeedback(granted = false, hadPendingEnable = false))
        assertNull(notificationPermissionFeedback(granted = true, hadPendingEnable = true))
    }

}
