package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.ForecastEvolutionThresholds

import com.meteocompare.app.domain.model.ForecastEvolutionVariable
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastEvolutionChartThresholdTest {
    @Test
    fun `chart thresholds stay aligned with evolution classification`() {
        assertEquals(0.5, ForecastEvolutionThresholds.stable(ForecastEvolutionVariable.TEMPERATURE), 0.0)
        assertEquals(1.0, ForecastEvolutionThresholds.stable(ForecastEvolutionVariable.PRECIPITATION), 0.0)
        assertEquals(3.0, ForecastEvolutionThresholds.stable(ForecastEvolutionVariable.WIND), 0.0)

        assertEquals(1.0, ForecastEvolutionThresholds.notable(ForecastEvolutionVariable.TEMPERATURE), 0.0)
        assertEquals(2.0, ForecastEvolutionThresholds.notable(ForecastEvolutionVariable.PRECIPITATION), 0.0)
        assertEquals(5.0, ForecastEvolutionThresholds.notable(ForecastEvolutionVariable.WIND), 0.0)
    }
}
