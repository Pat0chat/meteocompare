package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastEvolutionVariableSelectionTest {
    private val temperature = ForecastEvolutionVariable.TEMPERATURE
    private val precipitation = ForecastEvolutionVariable.PRECIPITATION
    private val wind = ForecastEvolutionVariable.WIND

    @Test
    fun `rain remains selected when date changes and options change order`() {
        val firstDay = listOf(temperature, precipitation, wind)
        val nextDay = listOf(wind, precipitation, temperature)

        assertEquals(precipitation, displayedEvolutionVariable(precipitation.name, firstDay))
        assertEquals(precipitation, displayedEvolutionVariable(precipitation.name, nextDay))
    }

    @Test
    fun `wind remains selected when date changes`() {
        assertEquals(wind, displayedEvolutionVariable(wind.name, listOf(temperature, wind)))
        assertEquals(wind, displayedEvolutionVariable(wind.name, listOf(wind, temperature)))
    }

    @Test
    fun `missing rain uses temporary fallback and returns to rain on another date`() {
        val preferredName = precipitation.name
        assertEquals(temperature, displayedEvolutionVariable(preferredName, listOf(temperature, wind)))
        assertEquals(precipitation, displayedEvolutionVariable(preferredName, listOf(wind, precipitation)))
    }

    @Test
    fun `no available variables returns null`() {
        assertNull(displayedEvolutionVariable(precipitation.name, emptyList()))
    }
}
