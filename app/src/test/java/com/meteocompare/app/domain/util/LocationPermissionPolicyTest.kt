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
    @Test fun `one time fine expired but coarse persists offers precision once`() {
        assertTrue(LocationPermissionPolicy.shouldRequest(
            coarseGranted = true, fineGranted = false, precisionOffered = true,
            previouslyFineGranted = true, recoveryOffered = false
        ))
        assertFalse(LocationPermissionPolicy.shouldRequest(
            coarseGranted = true, fineGranted = false, precisionOffered = true,
            previouslyFineGranted = true, recoveryOffered = true
        ))
    }

    @Test fun `explicit widget offers one migration chance for previous approximate grants`() {
        assertTrue(LocationPermissionPolicy.shouldRequest(
            coarseGranted = true, fineGranted = false, precisionOffered = true,
            explicitWidgetAction = true, widgetReofferUsed = false
        ))
        assertFalse(LocationPermissionPolicy.shouldRequest(
            coarseGranted = true, fineGranted = false, precisionOffered = true,
            explicitWidgetAction = true, widgetReofferUsed = true
        ))
        assertFalse(LocationPermissionPolicy.shouldRequest(
            coarseGranted = true, fineGranted = false, precisionOffered = true,
            explicitWidgetAction = false, widgetReofferUsed = false
        ))
    }

    @Test fun `revoke all permissions prompts again even after a refused recovery`() {
        assertTrue(LocationPermissionPolicy.shouldRequest(
            coarseGranted = false, fineGranted = false, precisionOffered = true,
            previouslyFineGranted = true, recoveryOffered = true,
            explicitWidgetAction = true, widgetReofferUsed = true
        ))
    }

    @Test fun `active precise permission never relaunches Android dialog`() {
        assertFalse(LocationPermissionPolicy.shouldRequest(
            coarseGranted = true, fineGranted = true, precisionOffered = true,
            previouslyFineGranted = true, recoveryOffered = false,
            explicitWidgetAction = true, widgetReofferUsed = false
        ))
    }

}
