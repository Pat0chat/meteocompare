package com.meteocompare.app.ui.citylist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTemperatureTrendTest {
    @Test
    fun `trend uses H plus 3 when available`() {
        val trend = homeTemperatureTrend(
            currentTemp = 12.0,
            hourlyTemps = listOf(12.0, 12.5, 13.0, 15.2, 16.0)
        )
        assertEquals(HomeTemperatureTrendDirection.RISING, trend?.direction)
        assertEquals(15, trend?.targetTemperature)
    }

    @Test
    fun `trend distinguishes falling and stable temperatures`() {
        assertEquals(
            HomeTemperatureTrendDirection.FALLING,
            homeTemperatureTrend(18.0, listOf(18.0, 17.5, 17.0, 15.0))?.direction
        )
        assertEquals(
            HomeTemperatureTrendDirection.STABLE,
            homeTemperatureTrend(18.0, listOf(18.0, 18.1, 18.2, 18.4))?.direction
        )
    }

    @Test
    fun `trend is absent without current or future temperature`() {
        assertNull(homeTemperatureTrend(null, listOf(12.0, 13.0, 14.0, 15.0)))
        assertNull(homeTemperatureTrend(12.0, emptyList()))
    }
}
