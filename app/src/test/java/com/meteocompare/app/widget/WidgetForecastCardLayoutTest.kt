package com.meteocompare.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetForecastCardLayoutTest {

    @Test
    fun `forecast cards adapt across launcher heights`() {
        assertEquals(ForecastCardHeightProfile.DENSE, forecastCardHeightProfile(130f))
        assertEquals(ForecastCardHeightProfile.COMPACT, forecastCardHeightProfile(160f))
        assertEquals(ForecastCardHeightProfile.COMFORTABLE, forecastCardHeightProfile(190f))
        assertEquals(ForecastCardHeightProfile.EXPANDED, forecastCardHeightProfile(240f))
    }

    @Test
    fun `profile boundaries are stable`() {
        assertEquals(ForecastCardHeightProfile.COMPACT, forecastCardHeightProfile(145f))
        assertEquals(ForecastCardHeightProfile.COMFORTABLE, forecastCardHeightProfile(175f))
        assertEquals(ForecastCardHeightProfile.EXPANDED, forecastCardHeightProfile(215f))
    }
}
