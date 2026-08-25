package com.meteocompare.app.domain.model

import com.meteocompare.app.domain.usecase.ForecastConsensus
import org.junit.Assert.assertEquals
import org.junit.Test

class PrecipitationThresholdsTest {

    @Test
    fun `occurrence threshold is shared at one tenth millimetre`() {
        assertEquals(0.1, PrecipitationThresholds.HOURLY_OCCURRENCE_MM, 0.0)
        assertEquals(0.1, PrecipitationThresholds.DAILY_OCCURRENCE_MM, 0.0)
        assertEquals(
            PrecipitationThresholds.DAILY_OCCURRENCE_MM,
            PrecipitationConfidence.PRECIP_THRESHOLD_MM,
            0.0
        )
    }

    @Test
    fun `threshold is strict more than one tenth millimetre`() {
        fun probability(amount: Double): Int? = ForecastConsensus.precipitation(
            rows = listOf(ForecastConsensus.PrecipitationRow(WeatherModel.GFS, amountMm = amount)),
            thresholdMm = PrecipitationThresholds.HOURLY_OCCURRENCE_MM,
            amountTightStdDev = 0.5,
            amountWideStdDev = 4.0
        ).probabilityPercent

        assertEquals(0, probability(0.099))
        assertEquals(0, probability(0.1))
        assertEquals(100, probability(0.1001))
    }
}
