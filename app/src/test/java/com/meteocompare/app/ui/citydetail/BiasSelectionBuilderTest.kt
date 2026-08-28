package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelBias
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
                    BiasVariable.TEMPERATURE, 1.0, 0.0, 30
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
            WeatherModel.GFS,
            BiasVariable.TEMPERATURE,
            state
        )

        val resolvedSelection = requireNotNull(selection)
        val localRank = requireNotNull(resolvedSelection.localRank)
        assertEquals(2, localRank.rank)
        assertEquals(3, localRank.modelCount)
        assertEquals(30, resolvedSelection.dailyForecast.size)
        assertNotNull(resolvedSelection.multiModelReliability)
    }

    @Test
    fun `selection utilise le même socle de dates que le classement`() {
        val commonStart = LocalDate.of(2026, 1, 1)
        fun history(firstDate: LocalDate, error: Double) = List(14) { index ->
            BiasSample(
                targetDate = firstDate.plusDays(index.toLong()),
                forecast = 10.0 + error,
                observation = 10.0
            )
        }
        val state = VariableBiasState(
            biasByModel = mapOf(
                WeatherModel.GFS to ModelBias(
                    BiasVariable.TEMPERATURE, 1.0, 0.0, 15
                )
            ),
            historyByModel = mapOf(
                WeatherModel.GFS to List(15) { index ->
                    BiasSample(commonStart.plusDays(index.toLong()), 11.0, 10.0)
                },
                WeatherModel.ECMWF to history(commonStart.plusDays(1), 0.0),
                WeatherModel.ICON_GLOBAL to history(LocalDate.of(2026, 3, 1), 0.0)
            ),
            yDomainMin = -100.0,
            yDomainMax = 100.0
        )

        val selection = buildBiasSelection(
            WeatherModel.GFS,
            BiasVariable.TEMPERATURE,
            state
        )

        assertNotNull(selection)
        selection!!
        assertEquals(2, selection.localRank?.modelCount)
        assertEquals(2, selection.localRank?.rank)
        assertEquals(14, selection.reliability.sampleSize)
        assertEquals(14, selection.bias.sampleSize)
        assertEquals(14, selection.dailyForecast.size)
        assertEquals(14, selection.dailyObservation.size)
        assertEquals(14, selection.multiModelReliability?.sampleSize)
        assertTrue(selection.yDomainMin > -100.0)
        assertTrue(selection.yDomainMax < 100.0)
    }

    @Test
    fun `un modele seul conserve sa page sans rang trompeur`() {
        val start = LocalDate.of(2026, 1, 1)
        val history = List(14) { index ->
            BiasSample(start.plusDays(index.toLong()), 11.0, 10.0)
        }
        val state = VariableBiasState(
            biasByModel = mapOf(
                WeatherModel.GFS to ModelBias(
                    BiasVariable.TEMPERATURE, 1.0, 0.0, 14
                )
            ),
            historyByModel = mapOf(WeatherModel.GFS to history),
            yDomainMin = 9.0,
            yDomainMax = 12.0
        )

        val selection = buildBiasSelection(
            WeatherModel.GFS,
            BiasVariable.TEMPERATURE,
            state
        )

        assertNotNull(selection)
        assertNull(selection!!.localRank)
        assertNull(selection.multiModelReliability)
        assertEquals(14, selection.reliability.sampleSize)
        assertEquals(14, selection.dailyForecast.size)
    }
}
