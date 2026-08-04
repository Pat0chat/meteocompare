package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasSample
import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.HourlyConfidenceBand
import com.meteocompare.app.domain.model.WeatherModel
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelRankingTest {

    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun `classe chaque variable independamment sur les mêmes dates`() {
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
        rankings.temperature.entries.forEach { assertEquals(30, it.reliability.sampleSize) }
    }

    @Test
    fun `un seul modèle éligible ne produit pas de rang 1 sur 1`() {
        val state = BiasScreenState(
            temperature = variableState(
                WeatherModel.GFS to samples(error = 0.5, count = 30),
                WeatherModel.ECMWF to samples(error = 0.0, count = 8)
            ),
            precipitation = VariableBiasState.EMPTY,
            wind = VariableBiasState.EMPTY
        )

        val rankings = buildLocalModelRankings(state)

        assertTrue(rankings.temperature.entries.isEmpty())
        assertFalse(rankings.hasAnyRanking)
    }

    @Test
    fun `un modèle excellent sur des dates disjointes est exclu du classement`() {
        val common = LocalDate.of(2026, 1, 1)
        val state = variableState(
            WeatherModel.GFS to samples(error = 1.0, count = 14, firstDate = common),
            WeatherModel.ECMWF to samples(error = 2.0, count = 14, firstDate = common),
            WeatherModel.ICON_GLOBAL to samples(
                error = 0.0,
                count = 14,
                firstDate = LocalDate.of(2026, 3, 1)
            )
        )

        val ranking = buildLocalVariableRanking(BiasVariable.TEMPERATURE, state)

        assertEquals(listOf(WeatherModel.GFS, WeatherModel.ECMWF), ranking.entries.map { it.model })
        assertTrue(ranking.entries.all { it.reliability.sampleSize == 14 })
    }

    @Test
    fun `sélectionne le plus grand groupe de modèles avec quatorze dates communes`() {
        val common = LocalDate.of(2026, 1, 1)
        val state = variableState(
            WeatherModel.GFS to samples(error = 1.0, count = 15, firstDate = common),
            WeatherModel.ECMWF to samples(error = 0.5, count = 14, firstDate = common.plusDays(1)),
            WeatherModel.ICON_GLOBAL to samples(error = 1.5, count = 14, firstDate = common.plusDays(1)),
            WeatherModel.ARPEGE_EUROPE to samples(
                error = 0.0,
                count = 14,
                firstDate = LocalDate.of(2026, 4, 1)
            )
        )

        val comparable = comparableHistoriesForRanking(state.historyByModel)
        val ranking = buildLocalVariableRanking(BiasVariable.TEMPERATURE, state, comparable)

        assertEquals(
            setOf(WeatherModel.GFS, WeatherModel.ECMWF, WeatherModel.ICON_GLOBAL),
            comparable.keys
        )
        assertTrue(comparable.values.all { it.size == 14 })
        assertEquals(3, ranking.entries.size)
    }

    @Test
    fun `choisit la premiere variable réellement classable pour ouvrir la sheet`() {
        val state = BiasScreenState(
            temperature = VariableBiasState.EMPTY,
            precipitation = variableState(
                WeatherModel.GFS to samples(error = 0.5),
                WeatherModel.ECMWF to samples(error = 1.0)
            ),
            wind = variableState(
                WeatherModel.ECMWF to samples(error = 1.0),
                WeatherModel.GFS to samples(error = 2.0)
            )
        )

        val rankings = buildLocalModelRankings(state)

        assertEquals(BiasVariable.PRECIPITATION, rankings.firstAvailableVariable)
        assertEquals(
            BiasVariable.PRECIPITATION,
            rankingVariableFor(BiasVariable.TEMPERATURE, rankings)
        )
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
    fun `les onglets combinent graphiques et classements disponibles`() {
        val rankings = buildLocalModelRankings(
            BiasScreenState(
                temperature = VariableBiasState.EMPTY,
                precipitation = variableState(
                    WeatherModel.GFS to samples(error = 0.5),
                    WeatherModel.ECMWF to samples(error = 1.0)
                ),
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

    private fun samples(
        error: Double,
        count: Int = 30,
        firstDate: LocalDate = start
    ): List<BiasSample> = List(count) { index ->
        val observed = 10.0 + (index % 3)
        BiasSample(
            targetDate = firstDate.plusDays(index.toLong()),
            forecast = observed + error,
            observation = observed
        )
    }
}
