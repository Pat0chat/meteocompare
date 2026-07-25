package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
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

    @Test
    fun `les onglets de fiabilite excluent les metriques sans graphique ni classement`() {
        val rankings = buildLocalModelRankings(BiasScreenState.EMPTY)

        assertEquals(
            listOf(ConfidenceMetric.TEMPERATURE),
            availableReliabilityMetrics(
                rankings = rankings,
                tempBands = confidenceBands(),
                precipBands = emptyList(),
                windBands = emptyList()
            )
        )
    }

    @Test
    fun `les onglets de fiabilite combinent graphiques et classements disponibles`() {
        val rankings = buildLocalModelRankings(
            BiasScreenState(
                temperature = VariableBiasState.EMPTY,
                precipitation = variableState(WeatherModel.GFS to samples(error = 0.5)),
                wind = VariableBiasState.EMPTY
            )
        )

        assertEquals(
            listOf(ConfidenceMetric.PRECIPITATION, ConfidenceMetric.WIND),
            availableReliabilityMetrics(
                rankings = rankings,
                tempBands = emptyList(),
                precipBands = emptyList(),
                windBands = confidenceBands()
            )
        )
    }

    @Test
    fun `redirige le bouton global vers une variable réellement classée`() {
        val rankings = buildLocalModelRankings(
            BiasScreenState(
                temperature = VariableBiasState.EMPTY,
                precipitation = variableState(WeatherModel.GFS to samples(error = 0.5)),
                wind = VariableBiasState.EMPTY
            )
        )

        assertEquals(
            BiasVariable.PRECIPITATION,
            rankingVariableFor(BiasVariable.TEMPERATURE, rankings)
        )
        assertEquals(
            BiasVariable.PRECIPITATION,
            rankingVariableFor(BiasVariable.PRECIPITATION, rankings)
        )
    }

    private fun confidenceBands(): List<HourlyConfidenceBand> = listOf(
        HourlyConfidenceBand(
            timestamp = Instant.parse("2026-01-01T00:00:00Z"),
            meanValue = 10.0,
            minValue = 9.0,
            maxValue = 11.0,
            stdDev = 1.0,
            percent = 80,
            modelCount = 2
        ),
        HourlyConfidenceBand(
            timestamp = Instant.parse("2026-01-01T01:00:00Z"),
            meanValue = 11.0,
            minValue = 10.0,
            maxValue = 12.0,
            stdDev = 1.0,
            percent = 80,
            modelCount = 2
        )
    )

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
