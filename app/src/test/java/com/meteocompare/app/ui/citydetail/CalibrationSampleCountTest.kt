package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrationSampleCountTest {

    private val state = VariableBiasState(
        biasByModel = emptyMap(),
        historyByModel = mapOf(
            WeatherModel.GFS to List(3) { day ->
                BiasSample(LocalDate.of(2026, 9, 1).plusDays(day.toLong()), forecast = 1.0, observation = 1.0)
            }
        ),
        yDomainMin = null,
        yDomainMax = null
    )

    @Test
    fun `modele Forecast API - compteur de jours compares`() {
        assertEquals(3, state.calibrationSampleCount(WeatherModel.GFS))
        assertEquals(0, state.calibrationSampleCount(WeatherModel.ECMWF))
    }

    @Test
    fun `modele d ensemble sans Previous Runs - pas de compteur bloque a 0 sur 14`() {
        assertNull(state.calibrationSampleCount(WeatherModel.GOOGLE_WEATHERNEXT2))
    }
}
