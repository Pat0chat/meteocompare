package com.meteocompare.app.domain.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionPolicyTest {
    @Test fun `initial tap always requests Android permission dialog`() {
        assertTrue(LocationPermissionPolicy.shouldRequest(false, false, false))
        assertTrue(LocationPermissionPolicy.shouldRequest(false, false, true))
    }

    @Test fun `existing approximate user gets one optional precision offer`() {
        assertTrue(LocationPermissionPolicy.shouldRequest(true, false, false))
        assertFalse(LocationPermissionPolicy.shouldRequest(true, false, true))
    }

    @Test fun `already precise user never gets a redundant offer`() {
        assertFalse(LocationPermissionPolicy.shouldRequest(true, true, false))
        assertFalse(LocationPermissionPolicy.shouldRequest(true, true, true))
    }
}
