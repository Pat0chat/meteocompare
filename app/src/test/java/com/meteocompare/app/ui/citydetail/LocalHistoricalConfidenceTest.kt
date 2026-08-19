package com.meteocompare.app.ui.citydetail

import com.meteocompare.app.domain.model.BiasVariable
import com.meteocompare.app.domain.model.ModelReliability
import com.meteocompare.app.domain.model.ReliabilityLevel
import com.meteocompare.app.domain.model.ReliabilityTrend
import com.meteocompare.app.domain.model.WeatherModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalHistoricalConfidenceTest {

    @Test
    fun `les variantes apparentees comptent comme une seule famille historique`() {
        val rankings = LocalModelRankings(
            temperature = ranking(
                WeatherModel.AROME_FRANCE_HD to 90,
                WeatherModel.AROME_FRANCE to 70,
                WeatherModel.GFS to 60
            ),
            precipitation = emptyRanking(BiasVariable.PRECIPITATION),
            wind = emptyRanking(BiasVariable.WIND_SPEED)
        )

        // Famille AROME = (90 + 70) / 2 = 80 ; GFS = 60 ; confiance = 70.
        assertEquals(2, rankings.historicalFamilyCount)
        assertEquals(70, rankings.historicalConfidencePercent)
    }

    @Test
    fun `une seule famille ne fabrique pas une confiance historique`() {
        val rankings = LocalModelRankings(
            temperature = ranking(
                WeatherModel.AROME_FRANCE_HD to 90,
                WeatherModel.AROME_FRANCE to 70
            ),
            precipitation = emptyRanking(BiasVariable.PRECIPITATION),
            wind = emptyRanking(BiasVariable.WIND_SPEED)
        )

        assertEquals(1, rankings.historicalFamilyCount)
        assertNull(rankings.historicalConfidencePercent)
    }

    private fun ranking(vararg values: Pair<WeatherModel, Int>): LocalVariableRanking =
        LocalVariableRanking(
            variable = BiasVariable.TEMPERATURE,
            entries = values.mapIndexed { index, (model, score) ->
                LocalModelRankingEntry(index + 1, model, reliability(score))
            }
        )

    private fun emptyRanking(variable: BiasVariable) = LocalVariableRanking(variable, emptyList())

    private fun reliability(score: Int) = ModelReliability(
        variable = BiasVariable.TEMPERATURE,
        score = score,
        level = ReliabilityLevel.GOOD,
        meanBias = 0.0,
        meanAbsoluteError = 1.0,
        rootMeanSquareError = 1.0,
        standardDeviation = 0.0,
        withinToleranceRate = 1.0,
        overestimateRate = 0.0,
        underestimateRate = 0.0,
        closeRate = 1.0,
        overToleranceOverestimateRate = 0.0,
        underToleranceUnderestimateRate = 0.0,
        closeTolerance = 1.5,
        sampleSize = 30,
        windowDays = 30,
        recentMeanAbsoluteError = 1.0,
        previousMeanAbsoluteError = 1.0,
        trend = ReliabilityTrend.STABLE,
        precipitation = null
    )
}
