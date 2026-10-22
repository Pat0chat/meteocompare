package com.meteocompare.app.domain.util

import com.meteocompare.app.domain.util.LocationProviderPolicy.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationProviderPolicyTest {
    @Test fun `on recent Android emulator precise consent checks GPS before fused and network`() {
        val providers = LocationProviderPolicy.priority(apiLevel = 36, fineGranted = true)
        assertEquals(listOf(Source.GPS, Source.FUSED, Source.NETWORK), providers)
    }

    @Test fun `approximate permission must never activate GPS provider`() {
        val providers = LocationProviderPolicy.priority(apiLevel = 36, fineGranted = false)
        assertEquals(listOf(Source.FUSED, Source.NETWORK), providers)
        assertFalse(providers.contains(Source.GPS))
    }

    @Test fun `older Android retains network fallback with and without precise permission`() {
        assertEquals(listOf(Source.NETWORK),
            LocationProviderPolicy.priority(apiLevel = 27, fineGranted = false))
        assertEquals(listOf(Source.GPS, Source.NETWORK),
            LocationProviderPolicy.priority(apiLevel = 27, fineGranted = true))
        assertTrue(LocationProviderPolicy.priority(31, true).contains(Source.FUSED))
        assertFalse(LocationProviderPolicy.priority(30, true).contains(Source.FUSED))
    }
}
