package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastEvolutionChartThresholdTest {
    @Test
    fun `chart thresholds stay aligned with evolution classification`() {
        assertEquals(0.5, evolutionStableThreshold(ForecastEvolutionVariable.TEMPERATURE), 0.0)
        assertEquals(1.0, evolutionStableThreshold(ForecastEvolutionVariable.PRECIPITATION), 0.0)
        assertEquals(3.0, evolutionStableThreshold(ForecastEvolutionVariable.WIND), 0.0)

        assertEquals(1.0, evolutionNotableThreshold(ForecastEvolutionVariable.TEMPERATURE), 0.0)
        assertEquals(2.0, evolutionNotableThreshold(ForecastEvolutionVariable.PRECIPITATION), 0.0)
        assertEquals(5.0, evolutionNotableThreshold(ForecastEvolutionVariable.WIND), 0.0)
    }
}
