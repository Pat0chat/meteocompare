package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSettingsTest {
    @Test
    fun `retainingCities purge les ids qui ne sont plus favoris`() {
        val settings = NotificationSettings(
            dailySummaryEnabled = true,
            cityIds = setOf("paris", "deleted")
        )

        assertEquals(
            setOf("paris"),
            settings.retainingCities(setOf("paris", "lyon")).cityIds
        )
    }
}
