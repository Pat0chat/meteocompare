package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.WeatherModel
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelRankingTest {

    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun `classe chaque variable independamment`() {
        val state = BiasScreenState(
            temperature = variableState(
                WeatherModel.GFS to samples(error = 2.0),
                WeatherModel.ECMWF to samples(error = 0.5)
            ),
            precipitation = variableState(
                WeatherModel.GFS to samples(error = 0.2),
                WeatherModel.ECMWF to samples(error = 1.0)
            ),
            wind = variableState(
                WeatherModel.GFS to samples(error = 4.0),
                WeatherModel.ECMWF to samples(error = 1.0)
            )
        )

        val rankings = buildLocalModelRankings(state)

        assertEquals(WeatherModel.ECMWF, rankings.temperature.winner?.model)
        assertEquals(WeatherModel.GFS, rankings.precipitation.winner?.model)
        assertEquals(WeatherModel.ECMWF, rankings.wind.winner?.model)
        assertEquals(listOf(1, 2), rankings.temperature.entries.map { it.rank })
    }

    @Test
    fun `ignore les modeles sans historique suffisant`() {
        val state = BiasScreenState(
            temperature = variableState(
                WeatherModel.GFS to samples(error = 0.5, count = 30),
                WeatherModel.ECMWF to samples(error = 0.0, count = 8)
            ),
            precipitation = VariableBiasState.EMPTY,
            wind = VariableBiasState.EMPTY
        )

        val rankings = buildLocalModelRankings(state)

        assertEquals(1, rankings.temperature.entries.size)
        assertEquals(WeatherModel.GFS, rankings.temperature.winner?.model)
        assertTrue(rankings.hasAnyRanking)
        assertEquals(BiasVariable.TEMPERATURE, rankings.firstAvailableVariable)
    }

    @Test
    fun `choisit la premiere variable disponible pour ouvrir la sheet`() {
        val state = BiasScreenState(
            temperature = VariableBiasState.EMPTY,
            precipitation = variableState(
                WeatherModel.GFS to samples(error = 0.5)
            ),
            wind = variableState(
                WeatherModel.ECMWF to samples(error = 1.0)
            )
        )

        val rankings = buildLocalModelRankings(state)

        assertEquals(BiasVariable.PRECIPITATION, rankings.firstAvailableVariable)
    }

    private fun variableState(
        vararg histories: Pair<WeatherModel, List<BiasSample>>
    ): VariableBiasState = VariableBiasState(
        biasByModel = emptyMap(),
        historyByModel = histories.toMap(),
        yDomainMin = null,
        yDomainMax = null
    )

    private fun samples(error: Double, count: Int = 30): List<BiasSample> =
        List(count) { index ->
            val observed = 10.0 + (index % 3)
            BiasSample(
                targetDate = start.plusDays(index.toLong()),
                forecast = observed + error,
                observation = observed
            )
        }
}
