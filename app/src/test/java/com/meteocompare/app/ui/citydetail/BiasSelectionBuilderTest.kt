package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BiasSelectionBuilderTest {

    @Test
    fun `selection inclut rang local et reference multi modeles`() {
        val start = LocalDate.of(2026, 1, 1)
        fun history(error: Double) = List(30) { index ->
            BiasSample(
                targetDate = start.plusDays(index.toLong()),
                forecast = 10.0 + error,
                observation = 10.0
            )
        }

        val state = VariableBiasState(
            biasByModel = mapOf(
                WeatherModel.GFS to ModelBias(
                    variable = BiasVariable.TEMPERATURE,
                    meanBias = 1.0,
                    stdDev = 0.0,
                    sampleSize = 30
                )
            ),
            historyByModel = mapOf(
                WeatherModel.ECMWF to history(0.0),
                WeatherModel.GFS to history(1.0),
                WeatherModel.ICON_GLOBAL to history(3.0)
            ),
            yDomainMin = 8.0,
            yDomainMax = 14.0
        )

        val selection = buildBiasSelection(
            model = WeatherModel.GFS,
            variable = BiasVariable.TEMPERATURE,
            state = state
        )

        assertNotNull(selection)
        assertEquals(2, selection!!.localRank!!.rank)
        assertEquals(3, selection.localRank!!.modelCount)
        assertEquals(30, selection.dailyForecast.size)
        assertNotNull(selection.multiModelReliability)
    }
}
